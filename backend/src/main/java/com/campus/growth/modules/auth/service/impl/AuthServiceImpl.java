package com.campus.growth.modules.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.growth.common.constant.BizConst;
import com.campus.growth.common.constant.RedisKeyConst;
import com.campus.growth.common.context.UserContext;
import com.campus.growth.common.exception.BizException;
import com.campus.growth.common.result.ErrorCode;
import com.campus.growth.modules.auth.dto.LoginDTO;
import com.campus.growth.modules.auth.dto.RegisterDTO;
import com.campus.growth.modules.auth.entity.SysUser;
import com.campus.growth.modules.auth.mapper.SysUserMapper;
import com.campus.growth.modules.auth.service.AuthService;
import com.campus.growth.modules.auth.service.JwtTokenProvider;
import com.campus.growth.modules.auth.service.UserQueryService;
import com.campus.growth.modules.auth.vo.LoginVO;
import com.campus.growth.modules.auth.vo.UserInfoVO;
import com.campus.growth.modules.point.service.PointService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务实现。
 *
 * <h3>登录态设计</h3>
 * <p>JWT 本身是无状态的，但"禁用账号要立刻生效"这个需求必须有状态。
 * 这里把 token 的 {@code jti} 写入 Redis 白名单，拦截器每次校验；
 * 代价是每个请求多一次 Redis 查询（走本地连接，微秒级），换来可控的登录态。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate stringRedisTemplate;
    private final UserQueryService userQueryService;
    private final PointService pointService;
    /** 完善资料后联动一次性任务（本地调用，失败不影响资料保存） */
    private final com.campus.growth.modules.task.service.TaskService taskService;

    /** 注册准入策略（邀请码 / 学校白名单 / 学号格式） */
    private final com.campus.growth.modules.auth.service.RegisterPolicy registerPolicy;

    /** 连续登录失败几次后锁定 */
    @Value("${campus.auth.lock-threshold:5}")
    private int lockThreshold;

    /** 锁定分钟数 */
    @Value("${campus.auth.lock-minutes:10}")
    private int lockMinutes;

    @Override
    public LoginVO login(LoginDTO dto) {
        String failKey = RedisKeyConst.authFail(dto.getUsername());
        // 1. 先看是否已被锁定（防慢速爆破：接口限流只能挡高频，挡不住"每分钟试 4 次"）
        String failCount = stringRedisTemplate.opsForValue().get(failKey);
        if (failCount != null && Integer.parseInt(failCount) >= lockThreshold) {
            Long ttl = stringRedisTemplate.getExpire(failKey, TimeUnit.SECONDS);
            long minutes = ttl == null || ttl <= 0 ? 1 : (ttl + 59) / 60;
            throw BizException.of(ErrorCode.USER_LOCKED,
                    "密码错误次数过多，请 " + minutes + " 分钟后再试");
        }

        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername()));
        // 账号不存在与密码错误返回同一提示，避免账号枚举
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            recordLoginFailure(failKey);
            throw BizException.of(ErrorCode.USER_PASSWORD_ERROR);
        }
        if (user.getStatus() != null && user.getStatus() == BizConst.STATUS_DISABLED) {
            throw BizException.of(ErrorCode.USER_DISABLED);
        }
        // 2. 登录成功清空失败计数
        stringRedisTemplate.delete(failKey);
        // 首次登录时补齐积分账户（历史数据/后台建号场景）
        pointService.getOrCreateAccount(user.getId());

        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(update);

        return issueToken(user);
    }

    /**
     * 记录一次登录失败：计数 + 滑动过期。
     * <p>失败次数达到阈值后，键的 TTL 会被重置为锁定时长，用户必须等这段时间过去。</p>
     */
    private void recordLoginFailure(String failKey) {
        Long count = stringRedisTemplate.opsForValue().increment(failKey);
        if (count != null && count == 1L) {
            // 首次失败：给一个"观察窗口"，避免计数永远不过期
            stringRedisTemplate.expire(failKey, lockMinutes, TimeUnit.MINUTES);
        }
        if (count != null && count >= lockThreshold) {
            stringRedisTemplate.expire(failKey, lockMinutes, TimeUnit.MINUTES);
            log.warn("[安全] 账号登录失败达到 {} 次，已锁定 {} 分钟 key={}", count, lockMinutes, failKey);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO register(RegisterDTO dto) {
        // 1. 准入策略：邀请码 / 学校白名单 / 学号格式（见 RegisterPolicy）
        registerPolicy.validate(dto);

        Long exists = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername()));
        if (exists != null && exists > 0) {
            throw BizException.of(ErrorCode.USERNAME_EXISTS);
        }

        // 2. 学号唯一：等价于"一个人一个账号"，数据库层面还有唯一索引兜底
        String studentNo = dto.getStudentNo() == null ? null : dto.getStudentNo().trim();
        if (studentNo != null && !studentNo.isEmpty()) {
            Long sameNo = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getStudentNo, studentNo));
            if (sameNo != null && sameNo > 0) {
                throw BizException.of(ErrorCode.STUDENT_NO_EXISTS);
            }
        }

        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(dto.getNickname());
        user.setStudentNo(studentNo);
        user.setSchool(dto.getSchool());
        user.setRole(BizConst.ROLE_STUDENT);
        user.setGrowthLevel(1);
        user.setStatus(BizConst.STATUS_ENABLED);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发注册同一学号/账号：唯一索引兜底，转成明确的业务错误
            log.info("注册唯一索引冲突 username={} studentNo={}", dto.getUsername(), studentNo);
            throw BizException.of(ErrorCode.STUDENT_NO_EXISTS);
        }

        // 注册即开积分账户，后续任何积分操作都不用再判空
        pointService.getOrCreateAccount(user.getId());
        log.info("新用户注册成功 userId={} username={} school={}", user.getId(), user.getUsername(), user.getSchool());
        return issueToken(user);
    }

    @Override
    public void logout() {
        Long userId = UserContext.requireUserId();
        String jti = UserContext.get().getTokenId();
        stringRedisTemplate.delete(String.format(RedisKeyConst.TOKEN, userId, jti));
        log.info("用户退出登录 userId={}", userId);
    }

    @Override
    public UserInfoVO currentUser() {
        return userQueryService.toUserInfo(UserContext.requireUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserInfoVO updateProfile(com.campus.growth.modules.auth.dto.ProfileUpdateDTO dto) {
        Long userId = UserContext.requireUserId();
        SysUser update = new SysUser();
        update.setId(userId);
        update.setNickname(dto.getNickname());
        update.setPhone(dto.getPhone());
        update.setStudentNo(dto.getStudentNo());
        update.setSchool(dto.getSchool());
        update.setAvatar(dto.getAvatar());
        userMapper.updateById(update);

        // 补齐学校与学号即视为"完善个人资料"完成（一次性任务，幂等）
        if (org.springframework.util.StringUtils.hasText(dto.getSchool())
                && org.springframework.util.StringUtils.hasText(dto.getStudentNo())) {
            taskService.reportProgressQuietly(userId, "ONCE_PROFILE", 1);
        }
        log.info("用户资料更新 userId={}", userId);
        return userQueryService.toUserInfo(userId);
    }

    /** 签发 token 并写白名单 */
    private LoginVO issueToken(SysUser user) {
        JwtTokenProvider.TokenPair pair = jwtTokenProvider.create(user.getId(), user.getUsername(), user.getRole());
        stringRedisTemplate.opsForValue().set(
                String.format(RedisKeyConst.TOKEN, user.getId(), pair.jti()),
                user.getUsername(),
                jwtTokenProvider.getExpireMinutes(), TimeUnit.MINUTES);

        LoginVO vo = new LoginVO();
        vo.setToken(pair.token());
        vo.setExpireAt(pair.expireAtMillis());
        vo.setUserInfo(userQueryService.toUserInfo(user.getId()));
        return vo;
    }
}
