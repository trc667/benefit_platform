package com.campus.growth.infra.ratelimit;

import com.campus.growth.common.enums.RateLimitType;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 单机限流：Guava {@code RateLimiter} 令牌桶。
 *
 * <h3>实现要点</h3>
 * <ul>
 *   <li>按 key 维度各自持有独立的令牌桶，避免"一个热点用户打满全局配额"；</li>
 *   <li>用 Caffeine 管理令牌桶实例，10 分钟无访问自动淘汰，
 *       防止 key 无限增长导致内存泄漏（这是手写 {@code Map<String, RateLimiter>} 最容易忽略的问题）；</li>
 *   <li>{@code tryAcquire()} 非阻塞，拿不到直接返回 false，由上层转成 429 业务码；</li>
 *   <li>桶的 qps 首次创建时确定；后续配置变更需重启或等待桶被淘汰，开发期可接受。</li>
 * </ul>
 */
@Slf4j
@Component
public class LocalRateLimiter implements RateLimiterStrategy {

    /** 令牌桶注册表：10 分钟不访问即淘汰 */
    private final Cache<String, com.google.common.util.concurrent.RateLimiter> limiterRegistry = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build();

    @Override
    public RateLimitType supportType() {
        return RateLimitType.LOCAL;
    }

    @Override
    public boolean tryAcquire(String key, double qps) {
        if (qps <= 0) {
            return true;
        }
        com.google.common.util.concurrent.RateLimiter limiter =
                limiterRegistry.get(key, k -> com.google.common.util.concurrent.RateLimiter.create(qps));
        boolean acquired = limiter != null && limiter.tryAcquire();
        if (!acquired) {
            log.debug("本地限流拦截 key={} qps={}", key, qps);
        }
        return acquired;
    }

    /** 供监控/测试使用 */
    public long cachedLimiterCount() {
        limiterRegistry.cleanUp();
        return limiterRegistry.estimatedSize();
    }
}
