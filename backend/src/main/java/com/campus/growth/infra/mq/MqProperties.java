package com.campus.growth.infra.mq;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MQ 与本地消息表配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "campus.mq")
public class MqProperties {

    /** Kafka 总开关：本地没有 Kafka 时置 false，事件仍会写入 outbox 表，不会静默丢弃 */
    private boolean enabled = true;

    private Outbox outbox = new Outbox();

    @Data
    public static class Outbox {
        /** 补偿任务开关 */
        private boolean enabled = true;
        /** 每批补偿条数 */
        private int batchSize = 100;
        /** 最大重试次数，超过后置为 DEAD 等人工处理 */
        private int maxRetry = 5;
        /** 补偿任务执行间隔（毫秒） */
        private long fixedDelayMs = 30_000;
        /** 单条事件发送超时（毫秒） */
        private long sendTimeoutMs = 1500;
        /** 事件发送线程池：核心线程数 */
        private int senderCoreSize = 2;
        /** 事件发送线程池：最大线程数 */
        private int senderMaxSize = 4;
        /** 事件发送线程池：队列容量 */
        private int senderQueueSize = 5000;
    }
}
