package com.campus.growth.common.enums;

/**
 * 限流策略。
 */
public enum RateLimitType {

    /** Guava RateLimiter：单机令牌桶，无网络开销，适合单体部署 */
    LOCAL,
    /** Redis 窗口计数：多实例共享配额，适合集群部署 */
    REDIS,
    /** 跟随全局配置 campus.limit.strategy */
    DEFAULT
}
