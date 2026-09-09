package com.campus.growth.infra.ratelimit;

import com.campus.growth.common.enums.RateLimitType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 集群限流：Redis 固定窗口计数。
 *
 * <h3>为什么用固定窗口而不是滑动窗口</h3>
 * <p>固定窗口只需一次 Lua 调用（INCR + 首次设置过期），实现简单、Redis 压力小；
 * 代价是窗口边界处可能有 2 倍突刺。对本项目"签到/领券防刷"的诉求足够，
 * 真要严格限流再换 ZSet 滑动窗口或令牌桶 Lua。</p>
 *
 * <h3>原子性</h3>
 * <p>INCR 与 PEXPIRE 必须原子执行，否则进程在两条命令之间挂掉会留下永不过期的计数键。
 * 这里用 Lua 脚本保证原子性；Redis 3.2 已支持 EVAL。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisRateLimiter implements RateLimiterStrategy {

    /** 固定窗口长度（毫秒） */
    private static final long WINDOW_MILLIS = 1000L;

    private static final RedisScript<Long> INCR_WITH_EXPIRE = new DefaultRedisScript<>(
            "local current = redis.call('INCR', KEYS[1]) "
                    + "if tonumber(current) == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end "
                    + "return current",
            Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public RateLimitType supportType() {
        return RateLimitType.REDIS;
    }

    @Override
    public boolean tryAcquire(String key, double qps) {
        if (qps <= 0) {
            return true;
        }
        // 窗口内允许的请求数：qps × 窗口秒数，向上取整，最小 1
        long limit = Math.max(1L, (long) Math.ceil(qps * (WINDOW_MILLIS / 1000.0)));
        String windowKey = key + ":" + (System.currentTimeMillis() / WINDOW_MILLIS);
        try {
            Long current = stringRedisTemplate.execute(INCR_WITH_EXPIRE, List.of(windowKey), String.valueOf(WINDOW_MILLIS));
            boolean acquired = current != null && current <= limit;
            if (!acquired) {
                log.debug("Redis 限流拦截 key={} current={} limit={}", windowKey, current, limit);
            }
            return acquired;
        } catch (Exception e) {
            // 限流组件故障时选择放行（fail-open）：可用性优先于绝对精确的限流
            log.error("Redis 限流执行异常，本次放行 key={}", windowKey, e);
            return true;
        }
    }
}
