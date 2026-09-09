package com.campus.growth.infra.ratelimit;

import com.campus.growth.common.enums.RateLimitType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 限流门面：根据注解或全局配置选择单机 / 集群策略。
 * <p>新增限流实现只需实现 {@link RateLimiter} 并交给 Spring，门面自动收录。</p>
 */
@Slf4j
@Component
public class RateLimitFacade {

    private final Map<RateLimitType, RateLimiterStrategy> limiterMap = new EnumMap<>(RateLimitType.class);

    @Value("${campus.limit.enabled:true}")
    private boolean enabled;

    @Value("${campus.limit.strategy:LOCAL}")
    private RateLimitType defaultStrategy;

    public RateLimitFacade(List<RateLimiterStrategy> limiters) {
        for (RateLimiterStrategy limiter : limiters) {
            limiterMap.put(limiter.supportType(), limiter);
        }
        log.info("限流器注册完成: {}", limiterMap.keySet());
    }

    /**
     * 尝试获取令牌。
     *
     * @param key  限流键
     * @param qps  配额
     * @param type 指定策略，DEFAULT 时取全局配置
     */
    public boolean tryAcquire(String key, double qps, RateLimitType type) {
        if (!enabled) {
            return true;
        }
        RateLimitType actual = (type == null || type == RateLimitType.DEFAULT) ? defaultStrategy : type;
        RateLimiterStrategy limiter = limiterMap.get(actual);
        if (limiter == null) {
            log.warn("未找到限流实现 type={}，本次放行", actual);
            return true;
        }
        return limiter.tryAcquire(key, qps);
    }

    public RateLimitType getDefaultStrategy() {
        return defaultStrategy;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
