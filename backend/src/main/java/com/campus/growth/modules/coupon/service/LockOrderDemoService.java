package com.campus.growth.modules.coupon.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 「锁在事务内」导致分布式锁失效的对比实验。
 *
 * <h3>问题本质</h3>
 * <p>Spring AOP 的多个切面构成责任链，order 越小越靠外。事务切面默认 order 是
 * {@code Ordered.LOWEST_PRECEDENCE}（最内层）。如果锁切面没有显式指定更小的 order，
 * 执行顺序会变成：</p>
 * <pre>
 * 开启事务 → 加锁 → 读库存 → 解锁 → 提交
 *                              ↑ 锁在这里释放，数据还没提交
 * </pre>
 * <p>第二个线程立刻拿到锁，读到的是「第一个线程尚未提交」的旧库存，
 * 于是两个线程都认为"还有货"，最终超发。这就是"锁在事务外层、但事务在锁里面"的反面案例。</p>
 *
 * <h3>实验设计</h3>
 * <p>本类用两段确定性模拟复现，不依赖任何中间件，直接调管理端接口即可观察：</p>
 * <ul>
 *   <li><b>WRONG</b>：锁在读库存之后立刻释放（模拟锁在事务内），所有线程都能读到同一份旧库存 → 超发；</li>
 *   <li><b>RIGHT</b>：锁一直持有到"提交"完成（模拟锁在事务外），线程串行执行 → 严格不超发。</li>
 * </ul>
 *
 * <p>真实修复方式见 {@link com.campus.growth.common.aspect.AspectOrder}：
 * 锁切面 {@code @Order(10)}、事务切面 {@code @Order(30)}，
 * 由 {@code TransactionConfig} 显式固定事务切面顺序。</p>
 */
@Slf4j
@Service
public class LockOrderDemoService {

    /**
     * 执行对比实验。
     *
     * @param stock   初始库存
     * @param threads 并发线程数
     */
    public DemoResult run(int stock, int threads) {
        int initialStock = Math.max(1, Math.min(stock, 1000));
        int threadCount = Math.max(2, Math.min(threads, 200));

        DemoResult result = new DemoResult();
        result.setInitialStock(initialStock);
        result.setThreads(threadCount);
        result.setWrongOrder(simulate(initialStock, threadCount, false));
        result.setRightOrder(simulate(initialStock, threadCount, true));
        result.setConclusion("锁在事务内 → 成功 " + result.getWrongOrder().getSuccessCount()
                + " 次（超发 " + Math.max(0, result.getWrongOrder().getSuccessCount() - initialStock)
                + " 张）；锁包住事务 → 成功 " + result.getRightOrder().getSuccessCount()
                + " 次，与库存一致。");
        return result;
    }

    private ModeResult simulate(int stock, int threads, boolean lockOutsideTransaction) {
        // committedStock 代表"已提交、对其他事务可见"的库存
        AtomicInteger committedStock = new AtomicInteger(stock);
        AtomicInteger success = new AtomicInteger(0);
        ReentrantLock lock = new ReentrantLock();
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finishGate = new CountDownLatch(threads);
        // WRONG 模式用它保证"所有线程都读完了才允许提交"，从而稳定复现超发
        CyclicBarrier readBarrier = new CyclicBarrier(threads);

        List<Thread> workers = new ArrayList<>(threads);
        for (int i = 0; i < threads; i++) {
            Thread worker = new Thread(() -> {
                try {
                    startGate.await();
                    if (lockOutsideTransaction) {
                        // 正确顺序：锁 → 读 → 提交 → 解锁（锁包住整个事务边界）
                        lock.lock();
                        try {
                            int read = committedStock.get();
                            // 模拟事务内的业务耗时，让并发窗口真实存在
                            sleepQuietly(2);
                            if (read > 0) {
                                committedStock.decrementAndGet();
                                success.incrementAndGet();
                            }
                        } finally {
                            lock.unlock();
                        }
                    } else {
                        // 错误顺序：开事务 → 加锁 → 读 → 解锁 → 提交
                        lock.lock();
                        int read;
                        try {
                            read = committedStock.get();
                        } finally {
                            lock.unlock();
                        }
                        // 关键：所有线程都在"锁已释放、但自己的写还没提交"的状态下等待，
                        // 保证它们读到的是同一份旧库存（真实场景里这正是锁失效的瞬间）
                        // 用超时版 await，避免任何异常导致测试永久挂起
                        readBarrier.await(5, java.util.concurrent.TimeUnit.SECONDS);
                        // 提交阶段：此时锁已经没了，多个线程拿着同一份 read 值一起提交
                        sleepQuietly(2);
                        if (read > 0) {
                            committedStock.decrementAndGet();
                            success.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finishGate.countDown();
                }
            }, "lock-demo-" + i);
            worker.setDaemon(true);
            workers.add(worker);
            worker.start();
        }

        startGate.countDown();
        try {
            if (!finishGate.await(30, java.util.concurrent.TimeUnit.SECONDS)) {
                log.warn("[锁顺序实验] 等待线程结束超时，结果可能不完整");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        ModeResult mode = new ModeResult();
        mode.setSuccessCount(success.get());
        mode.setFinalStock(committedStock.get());
        mode.setOversold(Math.max(0, success.get() - stock));
        mode.setOrder(lockOutsideTransaction ? "限流(0) → 锁(10) → 事务(30)" : "事务(-) → 锁(10) 锁先释放");
        log.info("[锁顺序实验] 模式={} 初始库存={} 成功={} 剩余={} 超发={}",
                lockOutsideTransaction ? "RIGHT" : "WRONG", stock, mode.getSuccessCount(),
                mode.getFinalStock(), mode.getOversold());
        return mode;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 实验总结果 */
    @Data
    public static class DemoResult {
        private int initialStock;
        private int threads;
        private ModeResult wrongOrder;
        private ModeResult rightOrder;
        private String conclusion;
    }

    /** 单次实验结果 */
    @Data
    public static class ModeResult {
        private String order;
        private int successCount;
        private int finalStock;
        private int oversold;
    }
}
