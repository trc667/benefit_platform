package com.campus.growth.infra.lock;

import com.campus.growth.common.constant.RedisKeyConst;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 分布式锁模板。
 *
 * <h3>封装目标</h3>
 * <ol>
 *   <li>统一 key 前缀（{@code cg:lock:}），避免与缓存键混淆；</li>
 *   <li>统一"拿不到锁"的降级策略：不阻塞、快速失败，让上游限流与前端重试兜底；</li>
 *   <li>解锁前判断 {@code isHeldByCurrentThread}，防止锁因业务超时自动过期后，
 *       误删其他线程持有的锁（这是手写 Redis 锁最经典的 bug）；</li>
 *   <li>leaseSeconds = -1 时启用 Redisson 看门狗（默认 30s 自动续期），
 *       适合"业务耗时不可预估"的场景，例如批量发券。</li>
 * </ol>
 *
 * <p><b>调用约定</b>：本模板必须包在事务外层调用。若在事务内调用，锁会在事务提交前释放，
 * 并发下依然会读到旧值。参考 {@link com.campus.growth.common.annotation.DistributedLock} 与
 * {@link com.campus.growth.common.aspect.AspectOrder}。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DistributedLockTemplate {

    private final RedissonClient redissonClient;

    /**
     * 加锁执行（带返回值）。
     *
     * @param bizKey       业务键，内部会拼上 {@code cg:lock:} 前缀
     * @param waitSeconds  获取锁的最长等待秒数
     * @param leaseSeconds 锁自动释放秒数，-1 表示启用看门狗
     * @param supplier     业务逻辑
     * @return 业务结果；拿不到锁返回 null
     */
    public <T> T executeWithResult(String bizKey, long waitSeconds, long leaseSeconds, Supplier<T> supplier) {
        RLock lock = redissonClient.getLock(RedisKeyConst.lock(bizKey));
        boolean locked = false;
        try {
            locked = tryLock(lock, waitSeconds, leaseSeconds);
            if (!locked) {
                log.warn("获取分布式锁失败 bizKey={}", bizKey);
                return null;
            }
            return supplier.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("获取分布式锁被中断 bizKey={}", bizKey);
            return null;
        } finally {
            unlock(lock, bizKey);
        }
    }

    /**
     * 加锁执行（无返回值）。
     *
     * @return true 表示成功拿到锁并执行完业务
     */
    public boolean execute(String bizKey, long waitSeconds, long leaseSeconds, Runnable runnable) {
        Boolean ok = executeWithResult(bizKey, waitSeconds, leaseSeconds, () -> {
            runnable.run();
            return Boolean.TRUE;
        });
        return Boolean.TRUE.equals(ok);
    }

    /** 仅尝试加锁，由调用方负责解锁（用于"锁 + 手动事务"的场景） */
    public boolean tryLock(String bizKey, long waitSeconds, long leaseSeconds) throws InterruptedException {
        return tryLock(redissonClient.getLock(RedisKeyConst.lock(bizKey)), waitSeconds, leaseSeconds);
    }

    /** 释放锁 */
    public void unlock(String bizKey) {
        unlock(redissonClient.getLock(RedisKeyConst.lock(bizKey)), bizKey);
    }

    private boolean tryLock(RLock lock, long waitSeconds, long leaseSeconds) throws InterruptedException {
        if (leaseSeconds > 0) {
            return lock.tryLock(waitSeconds, leaseSeconds, TimeUnit.SECONDS);
        }
        // 看门狗模式：leaseTime 传 -1，Redisson 每 10s 续期到 30s
        return lock.tryLock(waitSeconds, TimeUnit.SECONDS);
    }

    private void unlock(RLock lock, String bizKey) {
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (Exception e) {
            // 解锁失败不影响业务结果，锁最终会因 TTL 自动释放
            log.error("释放分布式锁异常 bizKey={}", bizKey, e);
        }
    }
}
