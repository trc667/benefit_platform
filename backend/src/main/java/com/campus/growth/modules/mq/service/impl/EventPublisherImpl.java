package com.campus.growth.modules.mq.service.impl;

import com.campus.growth.common.util.JsonUtil;
import com.campus.growth.common.util.OrderNoGenerator;
import com.campus.growth.infra.mq.EventEnvelope;
import com.campus.growth.infra.mq.KafkaEventSender;
import com.campus.growth.modules.mq.entity.MqEventOutbox;
import com.campus.growth.modules.mq.mapper.MqEventOutboxMapper;
import com.campus.growth.modules.mq.service.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

/**
 * 事件发布器实现：本地消息表 + 事务提交后发送。
 *
 * <h3>为什么不在事务里直接发 Kafka</h3>
 * <p>若在事务内发送，Kafka 消息可能先于数据库提交到达消费端，消费端查不到数据；
 * 更糟的是事务最终回滚，消息却已经发出去了。所以这里注册
 * {@link TransactionSynchronization#afterCommit()}，等事务真正提交后再发。</p>
 *
 * <h3>为什么还要本地消息表</h3>
 * <p>afterCommit 回调只是"尽力发送"，进程此刻崩溃消息就丢了。把事件先落库，
 * 由 {@code OutboxCompensateJob} 兜底重发，才是真正的最终一致。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventPublisherImpl implements EventPublisher {

    private final MqEventOutboxMapper outboxMapper;
    private final KafkaEventSender kafkaEventSender;
    /** 事务提交后在独立线程发送，避免 Kafka 抖动拖慢 HTTP 响应 */
    private final ThreadPoolTaskExecutor mqSendExecutor;

    @Override
    public String publish(String topic, String eventType, String bizKey, Object payload) {
        String eventId = OrderNoGenerator.eventId();
        EventEnvelope<Object> envelope = EventEnvelope.of(eventId, topic, eventType, bizKey, payload);

        // 1. 落本地消息表（与业务同一事务，业务回滚时事件一起回滚）
        MqEventOutbox outbox = new MqEventOutbox();
        outbox.setEventId(eventId);
        outbox.setTopic(topic);
        outbox.setEventType(eventType);
        outbox.setBizKey(bizKey);
        outbox.setPayload(JsonUtil.toJson(payload));
        outbox.setStatus("NEW");
        outbox.setRetryCount(0);
        outbox.setNextRetryTime(LocalDateTime.now());
        outboxMapper.insert(outbox);

        // 2. 事务提交后异步发送；无事务则立即异步发送
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch(eventId, topic, bizKey, envelope);
                }
            });
        } else {
            dispatch(eventId, topic, bizKey, envelope);
        }
        return eventId;
    }

    /** 提交到发送线程池；线程池满时 CallerRuns 兜底，事件始终不会丢 */
    private void dispatch(String eventId, String topic, String bizKey, EventEnvelope<?> envelope) {
        try {
            mqSendExecutor.execute(() -> sendNow(eventId, topic, bizKey, envelope));
        } catch (Exception e) {
            // 线程池异常：同步发一次，失败仍由补偿任务兜底
            log.warn("事件发送任务提交失败，改为同步发送 eventId={} cause={}", eventId, e.getMessage());
            sendNow(eventId, topic, bizKey, envelope);
        }
    }

    @Override
    public void publishDirect(String topic, String eventType, String bizKey, Object payload) {
        String eventId = OrderNoGenerator.eventId();
        EventEnvelope<Object> envelope = EventEnvelope.of(eventId, topic, eventType, bizKey, payload);
        kafkaEventSender.send(topic, bizKey, envelope);
    }

    /** 发送并把结果写回 outbox，失败留待补偿 */
    private void sendNow(String eventId, String topic, String bizKey, EventEnvelope<?> envelope) {
        boolean ok = kafkaEventSender.send(topic, bizKey, envelope);
        MqEventOutbox update = new MqEventOutbox();
        update.setId(null);
        update.setEventId(eventId);
        update.setStatus(ok ? "SENT" : "FAILED");
        if (!ok) {
            update.setRetryCount(1);
            update.setNextRetryTime(LocalDateTime.now().plusSeconds(30));
            update.setLastError("首次发送失败，等待补偿");
        }
        try {
            // 按 event_id 唯一索引更新，避免并发补偿重复发送造成状态覆盖
            outboxMapper.update(update,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<MqEventOutbox>()
                            .eq(MqEventOutbox::getEventId, eventId));
        } catch (Exception e) {
            log.error("更新 outbox 状态失败 eventId={}", eventId, e);
        }
    }
}
