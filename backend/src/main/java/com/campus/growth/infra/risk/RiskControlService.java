package com.campus.growth.infra.risk;

import com.campus.growth.common.constant.RedisKeyConst;
import com.campus.growth.common.context.DeviceContext;
import com.campus.growth.common.exception.BizException;
import com.campus.growth.common.result.ErrorCode;
import com.campus.growth.common.util.WebUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 风控服务：防止"一个人开一堆小号"薅走权益。
 *
 * <h3>为什么要有它</h3>
 * <p>这个平台的资损链路很短：注册 → 签到攒积分 → 兑换码/权益兑换成实物。
 * 注册零门槛时，一个人可以批量开号把福利薅空。企业里的做法是"身份校验 + 设备/IP 风控"两条腿，
 * 这里两条都补上（身份校验见 {@code RegisterPolicy}）。</p>
 *
 * <h3>三个计数维度</h3>
 * <ul>
 *   <li>{@code reg:ip:{ip}} 单 IP 每日注册上限 —— 挡脚本批量注册；</li>
 *   <li>{@code reg:device:{deviceId}} 单设备每日注册上限 —— 挡"换 IP 继续开号"；</li>
 *   <li>{@code signin:device:{deviceId}} 单设备每日**不同账号**签到集合 —— 挡"一台手机登一堆号签到"。</li>
 * </ul>
 *
 * <p>注意：设备号缺失时不会跳过校验，而是归入 {@code nodev:{ip}} 桶，
 * 否则攻击者不传 {@code X-Device-Id} 就绕过了所有设备维度。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskControlService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StringRedisTemplate stringRedisTemplate;
    /** Spring 会注入当前请求的代理，单例里也能安全拿到 request */
    private final HttpServletRequest request;

    @Value("${campus.risk.enabled:true}")
    private boolean enabled;

    @Value("${campus.risk.register-per-ip:5}")
    private int registerPerIp;

    @Value("${campus.risk.register-per-device:2}")
    private int registerPerDevice;

    @Value("${campus.risk.signin-accounts-per-device:3}")
    private int signinAccountsPerDevice;

    /** 注册前检查：单 IP / 单设备当日注册数量是否超限 */
    public void assertRegisterAllowed(String clientIp) {
        if (!enabled) {
            return;
        }
        String day = LocalDate.now().format(DAY);
        String ipKey = RedisKeyConst.riskRegisterIp(day, clientIp);
        long ipCount = currentCount(ipKey);
        if (ipCount >= registerPerIp) {
            log.warn("[风控] IP 注册超限 ip={} count={} limit={}", clientIp, ipCount, registerPerIp);
            throw BizException.of(ErrorCode.RISK_REJECTED, "该网络今日注册账号过多，请明天再试");
        }

        String deviceKey = RedisKeyConst.riskRegisterDevice(day, deviceBucket(clientIp));
        long deviceCount = currentCount(deviceKey);
        if (deviceCount >= registerPerDevice) {
            log.warn("[风控] 设备注册超限 device={} count={} limit={}", deviceBucket(clientIp), deviceCount, registerPerDevice);
            throw BizException.of(ErrorCode.RISK_REJECTED, "该设备今日注册账号过多，请明天再试");
        }
    }

    /** 注册成功后计数（放在注册成功的分支里，失败不计数） */
    public void markRegister(String clientIp) {
        if (!enabled) {
            return;
        }
        String day = LocalDate.now().format(DAY);
        increment(RedisKeyConst.riskRegisterIp(day, clientIp));
        increment(RedisKeyConst.riskRegisterDevice(day, deviceBucket(clientIp)));
    }

    /**
     * 签到前检查：同一台设备当天最多给 N 个不同账号签到。
     * <p>正常用户一台手机只登自己一个号；小号矩阵会立刻触发。</p>
     */
    public void assertSigninAllowed(Long userId) {
        if (!enabled || userId == null) {
            return;
        }
        String clientIp = WebUtil.getIp(request);
        String key = RedisKeyConst.riskSigninDevice(LocalDate.now().format(DAY), deviceBucket(clientIp));
        Long size = stringRedisTemplate.opsForSet().size(key);
        boolean alreadyIn = Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(key, String.valueOf(userId)));
        if (!alreadyIn && size != null && size >= signinAccountsPerDevice) {
            log.warn("[风控] 设备签到账号数超限 device={} accounts={} limit={}", deviceBucket(clientIp), size, signinAccountsPerDevice);
            throw BizException.of(ErrorCode.RISK_REJECTED, "该设备今日签到账号过多，请使用本人账号");
        }
    }

    /** 签到成功后记录"该设备今天用过这个账号" */
    public void markSignin(Long userId) {
        if (!enabled || userId == null) {
            return;
        }
        String clientIp = WebUtil.getIp(request);
        String key = RedisKeyConst.riskSigninDevice(LocalDate.now().format(DAY), deviceBucket(clientIp));
        stringRedisTemplate.opsForSet().add(key, String.valueOf(userId));
        // 当天有效即可，多给一天余量避免跨零点边界问题
        stringRedisTemplate.expire(key, Duration.ofDays(2));
    }

    /** 设备桶：有设备号用设备号，没有就退化成 IP（防止不传头即绕过） */
    private String deviceBucket(String clientIp) {
        String deviceId = DeviceContext.get();
        return deviceId == null || deviceId.isBlank() ? "nodev:" + clientIp : deviceId;
    }

    private long currentCount(String key) {
        String v = stringRedisTemplate.opsForValue().get(key);
        if (v == null) {
            return 0;
        }
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void increment(String key) {
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key, Duration.ofDays(2));
        }
    }
}
