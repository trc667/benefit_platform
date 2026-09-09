package com.campus.growth.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口幂等注解。
 * <p>基于 Redis SETNX 实现：同一 key 在 TTL 内只允许一次成功请求。
 * 用于"防重复提交"这类场景，与数据库唯一索引形成双保险。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /**
     * 幂等键，支持 SpEL。例如 {@code "'order:create:' + #userId"}。
     * 为空时自动取"类名.方法名 + 用户ID + 入参哈希"。
     */
    String key() default "";

    /** 幂等窗口（秒），窗口内重复请求直接拒绝 */
    int ttlSeconds() default 5;

    /** 重复请求提示语 */
    String message() default "请勿重复提交";
}
