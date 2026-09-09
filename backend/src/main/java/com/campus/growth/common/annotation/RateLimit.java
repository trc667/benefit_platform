package com.campus.growth.common.annotation;

import com.campus.growth.common.enums.RateLimitType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解。
 *
 * <h3>两层限流</h3>
 * <ul>
 *   <li>{@link RateLimitType#LOCAL}：Guava {@code RateLimiter} 令牌桶，进程内零网络开销，
 *       适合单体部署，缺点是多实例时总配额 = 实例数 × qps；</li>
 *   <li>{@link RateLimitType#REDIS}：Redis 滑动窗口计数，多实例共享配额，适合集群部署。</li>
 * </ul>
 * <p>默认跟随配置 {@code campus.limit.strategy}，需要单接口覆盖时显式指定。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 限流维度前缀，最终 key = 前缀 + 维度值（用户 ID 或 IP） */
    String key() default "";

    /** 限流策略，DEFAULT 表示跟随全局配置 */
    RateLimitType type() default RateLimitType.DEFAULT;

    /** 每秒允许的请求数 */
    double qps() default 5;

    /** true 按登录用户维度限流，false 按 IP 维度 */
    boolean byUser() default true;

    /** 被限流时的提示语 */
    String message() default "操作过于频繁，请稍后再试";
}
