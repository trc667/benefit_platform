package com.campus.growth.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池配置。
 *
 * <h3>为什么每个池都要显式声明</h3>
 * <p>{@code CompletableFuture.supplyAsync()} 默认用 ForkJoinPool.commonPool()，
 * 一旦有阻塞任务（查数据库、调大模型），整个 JVM 的并行流都会被拖住。
 * 所以凡是异步都必须落到有名字、有边界、有拒绝策略的池上。</p>
 *
 * <h3>拒绝策略选择</h3>
 * <ul>
 *   <li>算价、积分：{@code CallerRunsPolicy}，宁可让调用线程慢一点，也不能丢业务；</li>
 *   <li>AI 文案、日志：{@code DiscardPolicy} 或直接丢弃，属于可降级的旁路能力。</li>
 * </ul>
 */
@Slf4j
@Configuration
@EnableAsync
@EnableScheduling
public class ThreadPoolConfig {

    /** 结算页优惠组合并行算价 */
    @Bean("discountCalcExecutor")
    public ThreadPoolTaskExecutor discountCalcExecutor() {
        return build(8, 16, 200, "discount-calc-", new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /** 积分异步处理（签到/任务发奖的补充计算） */
    @Bean("pointAsyncExecutor")
    public ThreadPoolTaskExecutor pointAsyncExecutor() {
        return build(4, 8, 500, "point-async-", new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /** 轻量模型文案生成：短超时、可丢弃 */
    @Bean("aiTextExecutor")
    public ThreadPoolTaskExecutor aiTextExecutor() {
        return build(4, 8, 100, "ai-text-", new ThreadPoolExecutor.DiscardPolicy());
    }

    /** 操作日志落库：纯旁路 */
    @Bean("logExecutor")
    public ThreadPoolTaskExecutor logExecutor() {
        return build(2, 4, 1000, "op-log-", new ThreadPoolExecutor.DiscardPolicy());
    }

    /**
     * 事件发送线程池。
     * <p>事务提交后由它去发 Kafka，HTTP 请求线程立刻返回——
     * 避免"Kafka 抖动 2 秒"变成"用户等 2 秒"。发送失败不影响数据，事件已在本地消息表里。</p>
     */
    @Bean("mqSendExecutor")
    public ThreadPoolTaskExecutor mqSendExecutor(
            @org.springframework.beans.factory.annotation.Value("${campus.mq.outbox.sender-core-size:2}") int core,
            @org.springframework.beans.factory.annotation.Value("${campus.mq.outbox.sender-max-size:4}") int max,
            @org.springframework.beans.factory.annotation.Value("${campus.mq.outbox.sender-queue-size:5000}") int queue) {
        return build(core, max, queue, "mq-send-", new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /** 定时任务调度器（本地消息表补偿等） */
    @Bean("taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("campus-sched-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(20);
        scheduler.setErrorHandler(t -> log.error("定时任务执行异常", t));
        return scheduler;
    }

    private ThreadPoolTaskExecutor build(int core, int max, int queue, String prefix,
                                         RejectedExecutionHandler policy) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(queue);
        executor.setThreadNamePrefix(prefix);
        executor.setRejectedExecutionHandler(policy);
        executor.setKeepAliveSeconds(60);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
