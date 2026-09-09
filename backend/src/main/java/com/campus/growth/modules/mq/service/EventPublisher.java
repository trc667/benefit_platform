package com.campus.growth.modules.mq.service;

/**
 * 事件发布器。
 * <p>业务代码只依赖这个接口，不直接接触 KafkaTemplate。</p>
 */
public interface EventPublisher {

    /**
     * 发布事件。
     * <p>行为约定：</p>
     * <ol>
     *   <li>事件体写入本地消息表（与业务同一个事务）；</li>
     *   <li>若当前存在事务，则在事务提交后异步发送 Kafka，避免"事务回滚但消息已发出"；</li>
     *   <li>发送失败不抛异常，留给补偿任务重试。</li>
     * </ol>
     *
     * @param topic     主题
     * @param eventType 事件类型
     * @param bizKey    业务键
     * @param payload   事件载荷
     * @return 事件 ID
     */
    String publish(String topic, String eventType, String bizKey, Object payload);

    /**
     * 立即发送（跳过 outbox，仅用于可容忍丢失的非关键通知，如埋点）。
     */
    void publishDirect(String topic, String eventType, String bizKey, Object payload);
}
