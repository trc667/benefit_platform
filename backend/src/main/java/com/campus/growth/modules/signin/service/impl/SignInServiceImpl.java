package com.campus.growth.modules.signin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.growth.common.annotation.DistributedLock;
import com.campus.growth.common.constant.MqTopicConst;
import com.campus.growth.common.constant.RedisKeyConst;
import com.campus.growth.common.context.UserContext;
import com.campus.growth.common.exception.BizException;
import com.campus.growth.common.result.ErrorCode;
import com.campus.growth.common.util.PeriodKeyUtil;
import com.campus.growth.config.SigninProperties;
import com.campus.growth.modules.mq.service.EventPublisher;
import com.campus.growth.modules.signin.entity.SignInRecord;
import com.campus.growth.modules.signin.mapper.SignInRecordMapper;
import com.campus.growth.modules.signin.mq.SignInSuccessEvent;
import com.campus.growth.modules.signin.service.SignInService;
import com.campus.growth.modules.signin.vo.SignInCalendarVO;
import com.campus.growth.modules.signin.vo.SignInResultVO;
import com.campus.growth.modules.signin.vo.SignInStatVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 签到服务实现（Redis BitMap + Kafka 异步积分）。
 *
 * <h3>为什么用 BitMap</h3>
 * <p>一个用户一个月最多 31 天，用 31 个 bit（4 字节）就能表示；一年 366 bit（46 字节）。
 * 100 万学生一年只要约 44MB，而用 MySQL 行存储要 3.65 亿行。签到是典型的"写多、
 * 单用户数据量极小、读的时候要整月视图"的场景，BitMap 完美契合。</p>
 *
 * <h3>位偏移约定</h3>
 * <ul>
 *   <li>月图：{@code offset = dayOfMonth - 1}（0~30）</li>
 *   <li>年图：{@code offset = dayOfYear - 1}（0~365）</li>
 * </ul>
 *
 * <h3>一次签到的完整链路</h3>
 * <pre>
 * 限流(切面) → 分布式锁(切面) → 事务开始
 *   ├─ GETBIT 判重（已签到直接返回业务异常）
 *   ├─ SETBIT 月图 + 年图
 *   ├─ 计算连续天数（Redis 计数器，丢失时用位图回溯重建）
 *   ├─ INSERT sign_in_record（唯一索引兜底）
 *   └─ 写本地消息表（与签到同一事务）
 * 事务提交 → 发 Kafka signin-success
 *            → 积分消费者：幂等 + 加积分 + 更新排行榜
 * </pre>
 *
 * <p><b>为什么积分要异步</b>：签到是瞬时高峰（比如 23:59 全员补签），
 * 同步加积分会把 MySQL 积分账户行锁成热点。异步化后签到接口只写一条流水 + 位图，
 * 积分入账由消费者按自己的节奏处理，削峰效果明显。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignInServiceImpl implements SignInService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    /** 连续天数回溯上限（防脏数据导致死循环） */
    private static final int MAX_BACKTRACK_DAYS = 366;

    private final StringRedisTemplate stringRedisTemplate;
    private final SignInRecordMapper signInRecordMapper;
    private final SigninProperties signinProperties;
    private final EventPublisher eventPublisher;
    /** 风控：设备维度防小号 */
    private final com.campus.growth.infra.risk.RiskControlService riskControlService;

    /** 签到成功后联动任务进度（同模块内本地调用） */
    private final com.campus.growth.modules.task.service.TaskService taskService;

    @Override
    @DistributedLock(key = "'signin'", waitSeconds = 0, leaseSeconds = 10,
            message = "签到请求正在处理中，请稍后再试")
    @Transactional(rollbackFor = Exception.class)
    public SignInResultVO signIn(String source) {
        Long userId = UserContext.requireUserId();
        LocalDate today = LocalDate.now();

        // 0. 风控：一台设备一天最多给 N 个账号签到（防"一台手机登一堆小号薅积分"）
        riskControlService.assertSigninAllowed(userId);

        String monthKey = RedisKeyConst.signinMonth(PeriodKeyUtil.monthKey(today), userId);
        String yearKey = RedisKeyConst.signinYear(today.getYear(), userId);
        int monthOffset = today.getDayOfMonth() - 1;
        int yearOffset = today.getDayOfYear() - 1;

        // 1. 位图判重（O(1)）
        Boolean already = stringRedisTemplate.opsForValue().getBit(monthKey, monthOffset);
        if (Boolean.TRUE.equals(already)) {
            throw BizException.of(ErrorCode.SIGNIN_ALREADY);
        }

        // 2. 写位图：月图 + 年图
        stringRedisTemplate.opsForValue().setBit(monthKey, monthOffset, true);
        stringRedisTemplate.opsForValue().setBit(yearKey, yearOffset, true);
        stringRedisTemplate.expire(monthKey, 100, TimeUnit.DAYS);
        stringRedisTemplate.expire(yearKey, 400, TimeUnit.DAYS);

        // 3. 连续天数
        int continuousDays = resolveContinuousDays(userId, today);
        int basePoint = signinProperties.getBasePoint();
        int extraAward = signinProperties.bonusOf(continuousDays);
        int totalAward = basePoint + extraAward;

        // 4. 落流水（唯一索引兜底：位图被误删时也不会重复签到）
        SignInRecord record = new SignInRecord();
        record.setUserId(userId);
        record.setSignDate(today);
        record.setContinuousDays(continuousDays);
        record.setPointAward(totalAward);
        record.setSource(source == null ? "APP" : source);
        try {
            signInRecordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            log.warn("签到流水唯一索引冲突，按重复签到处理 userId={} date={}", userId, today);
            throw BizException.of(ErrorCode.SIGNIN_ALREADY);
        }

        // 5. 发事件（写本地消息表，事务提交后投递 Kafka）
        SignInSuccessEvent event = new SignInSuccessEvent(userId, today.format(DATE_FMT), continuousDays,
                basePoint, extraAward, totalAward);
        eventPublisher.publish(MqTopicConst.SIGNIN_SUCCESS, MqTopicConst.EventType.SIGNIN_SUCCESS,
                userId + ":" + today.format(DATE_FMT), event);

        // 6. 联动"每日签到"任务进度（本地方法调用，失败不影响签到本身）
        taskService.reportProgressQuietly(userId, "DAILY_SIGN_IN", 1);

        // 7. 记录"该设备今天用过这个账号"，用于风控统计
        riskControlService.markSignin(userId);

        long monthCount = countBits(monthKey);
        SignInResultVO vo = new SignInResultVO();
        vo.setSignDate(today.format(DATE_FMT));
        vo.setContinuousDays(continuousDays);
        vo.setBasePoint(basePoint);
        vo.setExtraAward(extraAward);
        vo.setPointAward(totalAward);
        vo.setMonthCount((int) monthCount);
        log.info("签到成功 userId={} date={} continuous={} award={}", userId, today, continuousDays, totalAward);
        return vo;
    }

    @Override
    public SignInCalendarVO calendar(String month) {
        Long userId = UserContext.requireUserId();
        YearMonth yearMonth = (month == null || month.isBlank())
                ? YearMonth.now() : YearMonth.parse(month, DateTimeFormatter.ofPattern("yyyyMM"));
        LocalDate today = LocalDate.now();
        String monthKey = RedisKeyConst.signinMonth(yearMonth.format(PeriodKeyUtil.MONTH_FMT), userId);
        String yearKey = RedisKeyConst.signinYear(yearMonth.getYear(), userId);

        int length = yearMonth.lengthOfMonth();
        // 一次性取出整月位图：用 BITFIELD 或直接 GET 二进制更高效，
        // 这里为了可读性用逐位读取（31 次，同机房 Redis 往返约 1ms 级）
        List<SignInCalendarVO.Day> days = new ArrayList<>(length);
        int signedCount = 0;
        boolean todaySigned = false;
        for (int day = 1; day <= length; day++) {
            LocalDate date = yearMonth.atDay(day);
            Boolean signed = stringRedisTemplate.opsForValue().getBit(monthKey, day - 1);
            boolean isSigned = Boolean.TRUE.equals(signed);
            if (isSigned) {
                signedCount++;
            }
            SignInCalendarVO.Day cell = new SignInCalendarVO.Day();
            cell.setDay(day);
            cell.setSigned(isSigned);
            cell.setFuture(date.isAfter(today));
            cell.setToday(date.isEqual(today));
            if (date.isEqual(today) && isSigned) {
                todaySigned = true;
            }
            days.add(cell);
        }

        SignInCalendarVO vo = new SignInCalendarVO();
        vo.setMonth(yearMonth.format(PeriodKeyUtil.MONTH_FMT));
        vo.setTodaySigned(todaySigned);
        vo.setContinuousDays(currentContinuousDays(userId, today));
        vo.setMonthCount(signedCount);
        vo.setYearCount((int) countBits(yearKey));
        vo.setDays(days);
        return vo;
    }

    @Override
    public SignInStatVO stat() {
        Long userId = UserContext.requireUserId();
        LocalDate today = LocalDate.now();
        String monthKey = RedisKeyConst.signinMonth(PeriodKeyUtil.monthKey(today), userId);
        String yearKey = RedisKeyConst.signinYear(today.getYear(), userId);

        SignInStatVO vo = new SignInStatVO();
        vo.setContinuousDays(currentContinuousDays(userId, today));
        vo.setMonthCount((int) countBits(monthKey));
        vo.setYearCount((int) countBits(yearKey));
        vo.setTodaySigned(Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                .getBit(monthKey, today.getDayOfMonth() - 1)));
        SignInRecord last = signInRecordMapper.selectOne(new LambdaQueryWrapper<SignInRecord>()
                .eq(SignInRecord::getUserId, userId)
                .orderByDesc(SignInRecord::getSignDate)
                .last("limit 1"));
        vo.setLastSignDate(last == null ? null : last.getSignDate().format(DATE_FMT));
        return vo;
    }

    @Override
    public boolean isSigned(Long userId, LocalDate date) {
        if (userId == null || date == null) {
            return false;
        }
        String key = RedisKeyConst.signinMonth(PeriodKeyUtil.monthKey(date), userId);
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().getBit(key, date.getDayOfMonth() - 1));
    }

    @Override
    public long countByDate(LocalDate date) {
        Long count = signInRecordMapper.selectCount(new LambdaQueryWrapper<SignInRecord>()
                .eq(SignInRecord::getSignDate, date));
        return count == null ? 0 : count;
    }

    @Override
    public List<com.campus.growth.common.vo.TrendVO> trend(int days) {
        int range = Math.max(1, Math.min(days, 30));
        LocalDate from = LocalDate.now().minusDays(range - 1L);
        // 一次 group by 查询搞定，避免 N 次 count
        List<java.util.Map<String, Object>> rows = signInRecordMapper.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SignInRecord>()
                        .select("sign_date AS date", "COUNT(*) AS cnt")
                        .ge("sign_date", from)
                        .groupBy("sign_date"));
        java.util.Map<String, Long> countMap = new java.util.HashMap<>();
        for (java.util.Map<String, Object> row : rows) {
            countMap.put(String.valueOf(row.get("date")),
                    Long.parseLong(String.valueOf(row.get("cnt"))));
        }
        List<com.campus.growth.common.vo.TrendVO> list = new java.util.ArrayList<>(range);
        for (int i = 0; i < range; i++) {
            LocalDate date = from.plusDays(i);
            String key = date.format(DATE_FMT);
            list.add(new com.campus.growth.common.vo.TrendVO(key, countMap.getOrDefault(key, 0L), null));
        }
        return list;
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    /**
     * 计算连续签到天数并写入 Redis 计数器。
     * <p>快路径：如果 Redis 里记录了"上次签到日期 = 昨天"，直接 +1；
     * 慢路径：计数器丢失时用位图逐日回溯重建，最多回溯 366 天。</p>
     */
    private int resolveContinuousDays(Long userId, LocalDate today) {
        String counterKey = RedisKeyConst.signinContinuous(userId);
        int continuous = 1;
        String lastDate = stringRedisTemplate.opsForValue().get(counterKey + ":last");
        String countValue = stringRedisTemplate.opsForValue().get(counterKey);

        if (lastDate != null && countValue != null) {
            LocalDate last = LocalDate.parse(lastDate, DATE_FMT);
            if (last.plusDays(1).isEqual(today)) {
                continuous = Integer.parseInt(countValue) + 1;
            }
        } else {
            continuous = backtrack(userId, today);
        }
        stringRedisTemplate.opsForValue().set(counterKey, String.valueOf(continuous), 400, TimeUnit.DAYS);
        stringRedisTemplate.opsForValue().set(counterKey + ":last", today.format(DATE_FMT), 400, TimeUnit.DAYS);
        return continuous;
    }

    /** 读取连续天数；Redis 丢失时以位图回溯结果为准（只读，不写回） */
    private int currentContinuousDays(Long userId, LocalDate today) {
        String counterKey = RedisKeyConst.signinContinuous(userId);
        String lastDate = stringRedisTemplate.opsForValue().get(counterKey + ":last");
        String countValue = stringRedisTemplate.opsForValue().get(counterKey);
        if (lastDate != null && countValue != null) {
            LocalDate last = LocalDate.parse(lastDate, DATE_FMT);
            // 昨天签过 → 连续有效；今天已签 → 也连续有效；否则已中断
            if (last.isEqual(today) || last.plusDays(1).isEqual(today)) {
                return Integer.parseInt(countValue);
            }
            return 0;
        }
        return todaySignedOrYesterdaySigned(userId, today) ? backtrack(userId, today) : 0;
    }

    private boolean todaySignedOrYesterdaySigned(Long userId, LocalDate today) {
        return isSigned(userId, today) || isSigned(userId, today.minusDays(1));
    }

    /** 从今天（或昨天）往前逐日检查位图，直到断签 */
    private int backtrack(Long userId, LocalDate today) {
        int count = 0;
        LocalDate cursor = isSigned(userId, today) ? today : today.minusDays(1);
        for (int i = 0; i < MAX_BACKTRACK_DAYS; i++) {
            if (!isSigned(userId, cursor)) {
                break;
            }
            count++;
            cursor = cursor.minusDays(1);
        }
        return count;
    }

    /**
     * 统计位图中 1 的个数（整月 / 整年）。
     * <p>BITCOUNT 直接返回整串的置位数；月图只存当月、年图只存当年，
     * 所以不需要按字节区间裁剪。Redis 3.2 的 BITCOUNT 支持整串统计。</p>
     */
    private long countBits(String key) {
        try {
            Long count = stringRedisTemplate.execute(
                    (org.springframework.data.redis.core.RedisCallback<Long>) connection ->
                            connection.bitCount(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            return count == null ? 0 : count;
        } catch (Exception e) {
            log.error("统计位图失败 key={}", key, e);
            return 0;
        }
    }
}
