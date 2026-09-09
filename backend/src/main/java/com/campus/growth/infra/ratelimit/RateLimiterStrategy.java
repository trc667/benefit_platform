package com.campus.growth.infra.ratelimit;

import com.campus.growth.common.enums.RateLimitType;

/**
 * 限流器统一接口。
 * <p>命名刻意避开 Guava 的 {@code RateLimiter}，否则同包类型会与 import 冲突。</p>
 */
public interface RateLimiterStrategy {

    /** 支持的策略 */
    RateLimitType supportType();

    /**
     * 尝试获取一个令牌。
     *
     * @param key 限流键（已含业务前缀与维度值）
     * @param qps 每秒配额
     * @return true = 放行
     */
    boolean tryAcquire(String key, double qps);
}
