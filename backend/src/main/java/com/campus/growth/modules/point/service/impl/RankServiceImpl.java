package com.campus.growth.modules.point.service.impl;

import com.campus.growth.common.constant.RedisKeyConst;
import com.campus.growth.common.util.PeriodKeyUtil;
import com.campus.growth.modules.point.entity.PointRecord;
import com.campus.growth.modules.point.entity.UserPointAccount;
import com.campus.growth.modules.point.mapper.PointRecordMapper;
import com.campus.growth.modules.point.mapper.UserPointAccountMapper;
import com.campus.growth.modules.point.service.RankService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 排行榜实现（Redis ZSet）。
 *
 * <p>降级策略：Redis 不可用时排行榜接口返回空榜，不抛异常——
 * 排行榜是"锦上添花"的能力，不能因为它的故障让整个首页打不开。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankServiceImpl implements RankService {

    public static final String TYPE_TOTAL = "TOTAL";
    public static final String TYPE_MONTH = "MONTH";

    /** 月榜保留 40 天，跨月后自动过期 */
    private static final long MONTH_RANK_TTL_DAYS = 40;

    private final StringRedisTemplate stringRedisTemplate;
    private final UserPointAccountMapper accountMapper;
    private final PointRecordMapper recordMapper;

    @Override
    public void addScore(Long userId, int delta) {
        if (userId == null || delta == 0) {
            return;
        }
        try {
            // 总榜只增不减
            if (delta > 0) {
                stringRedisTemplate.opsForZSet().incrementScore(RedisKeyConst.POINT_RANK_TOTAL, String.valueOf(userId), delta);
            }
            // 月榜同样只增不减
            String monthKey = RedisKeyConst.pointRankMonth(PeriodKeyUtil.monthKey(LocalDate.now()));
            if (delta > 0) {
                stringRedisTemplate.opsForZSet().incrementScore(monthKey, String.valueOf(userId), delta);
                stringRedisTemplate.expire(monthKey, MONTH_RANK_TTL_DAYS, TimeUnit.DAYS);
            }
        } catch (Exception e) {
            log.error("更新排行榜失败 userId={} delta={}", userId, delta, e);
        }
    }

    @Override
    public List<RankEntry> top(String type, int limit) {
        String key = resolveKey(type);
        if (key == null) {
            return List.of();
        }
        try {
            Set<ZSetOperations.TypedTuple<String>> tuples =
                    stringRedisTemplate.opsForZSet().reverseRangeWithScores(key, 0, limit - 1L);
            if (tuples == null || tuples.isEmpty()) {
                return List.of();
            }
            List<RankEntry> list = new ArrayList<>(tuples.size());
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                if (tuple.getValue() == null || tuple.getScore() == null) {
                    continue;
                }
                list.add(new RankEntry(Long.valueOf(tuple.getValue()), tuple.getScore().longValue()));
            }
            return list;
        } catch (Exception e) {
            log.error("查询排行榜失败 type={}", type, e);
            return List.of();
        }
    }

    @Override
    public Integer rankOf(Long userId, String type) {
        String key = resolveKey(type);
        if (key == null || userId == null) {
            return null;
        }
        try {
            Long rank = stringRedisTemplate.opsForZSet().reverseRank(key, String.valueOf(userId));
            return rank == null ? null : rank.intValue() + 1;
        } catch (Exception e) {
            log.error("查询我的排名失败 userId={}", userId, e);
            return null;
        }
    }

    @Override
    public long scoreOf(Long userId, String type) {
        String key = resolveKey(type);
        if (key == null || userId == null) {
            return 0L;
        }
        try {
            Double score = stringRedisTemplate.opsForZSet().score(key, String.valueOf(userId));
            return score == null ? 0L : score.longValue();
        } catch (Exception e) {
            log.error("查询我的积分失败 userId={}", userId, e);
            return 0L;
        }
    }

    @Override
    public long totalOf(String type) {
        String key = resolveKey(type);
        if (key == null) {
            return 0L;
        }
        try {
            Long size = stringRedisTemplate.opsForZSet().zCard(key);
            return size == null ? 0L : size;
        } catch (Exception e) {
            log.error("查询榜单人数失败 type={}", type, e);
            return 0L;
        }
    }

    @Override
    public RebuildResult rebuild() {
        // 总榜真源：积分账户的累计获得（与 addScore 只增不减的语义一致）
        List<UserPointAccount> accounts = accountMapper.selectList(new LambdaQueryWrapper<UserPointAccount>()
                .gt(UserPointAccount::getTotalEarned, 0));
        Map<String, Double> totalScores = new LinkedHashMap<>();
        for (UserPointAccount account : accounts) {
            totalScores.put(String.valueOf(account.getUserId()), account.getTotalEarned().doubleValue());
        }
        writeRank(RedisKeyConst.POINT_RANK_TOTAL, totalScores, null);

        // 月榜真源：本月正向积分流水按用户汇总
        String monthKey = RedisKeyConst.pointRankMonth(PeriodKeyUtil.monthKey(LocalDate.now()));
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        List<Map<String, Object>> rows = recordMapper.selectMaps(
                new QueryWrapper<PointRecord>()
                        .select("user_id AS userId", "SUM(change_point) AS pts")
                        .gt("change_point", 0)
                        .ge("create_time", monthStart)
                        .groupBy("user_id"));
        Map<String, Double> monthScores = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            monthScores.put(String.valueOf(row.get("userId")),
                    Double.parseDouble(String.valueOf(row.get("pts"))));
        }
        writeRank(monthKey, monthScores, MONTH_RANK_TTL_DAYS);

        log.info("排行榜重建完成 总榜={} 人 月榜={} 人", totalScores.size(), monthScores.size());
        return new RebuildResult(totalScores.size(), monthScores.size());
    }

    /** 覆盖写 ZSet：先删后写，失败不影响旧数据 */
    private void writeRank(String key, Map<String, Double> scores, Long ttlDays) {
        try {
            stringRedisTemplate.delete(key);
            if (scores.isEmpty()) {
                return;
            }
            Set<ZSetOperations.TypedTuple<String>> tuples = new LinkedHashSet<>();
            scores.forEach((member, score) ->
                    tuples.add(ZSetOperations.TypedTuple.of(member, score)));
            stringRedisTemplate.opsForZSet().add(key, tuples);
            if (ttlDays != null) {
                stringRedisTemplate.expire(key, ttlDays, TimeUnit.DAYS);
            }
        } catch (Exception e) {
            log.error("写入排行榜失败 key={}", key, e);
        }
    }

    private String resolveKey(String type) {
        if (TYPE_MONTH.equalsIgnoreCase(type)) {
            return RedisKeyConst.pointRankMonth(PeriodKeyUtil.monthKey(LocalDate.now()));
        }
        return RedisKeyConst.POINT_RANK_TOTAL;
    }
}
