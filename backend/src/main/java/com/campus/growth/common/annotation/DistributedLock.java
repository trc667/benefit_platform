package com.campus.growth.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 分布式锁注解（Redisson）。
 *
 * <h3>为什么必须放在事务外层</h3>
 * <p>Spring 的 {@code @Transactional} 默认切面顺序是 {@code Ordered.LOWEST_PRECEDENCE}。
 * 若锁切面的顺序值比事务切面"更靠内"（order 更大），执行顺序会变成：</p>
 * <pre>
 * 事务开启 → 加锁 → 业务 → 解锁 → 事务提交
 *                    ↑ 锁在这里就释放了，但数据还没提交
 * </pre>
 * <p>此时第二个线程能立刻拿到锁，读到的是未提交前的旧值，于是"锁没锁住"，典型表现就是领券超发。
 * 本项目通过 {@link com.campus.growth.common.aspect.AspectOrder} 显式规定
 * 锁切面 order=0、事务切面 order=10，保证"锁包住整个事务边界"。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    /**
     * 锁的 key，支持 SpEL（参数名可用，编译时已开启 -parameters）。
     * 例如：{@code "'coupon:stock:' + #templateId"}，最终前缀由 RedisKeyConst.LOCK 统一补上。
     */
    String key();

    /** 获取锁的等待时间，0 表示不等待（拿不到直接失败） */
    long waitSeconds() default 3;

    /** 锁持有时间，-1 表示启用 Redisson 看门狗自动续期 */
    long leaseSeconds() default -1;

    /** 拿不到锁时抛出的提示语 */
    String message() default "操作正在处理中，请稍后再试";
}
