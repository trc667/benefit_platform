package com.campus.growth.infra.mq;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 事件信封。所有 Kafka 消息都包一层，消费端据此做幂等与路由。
 *
 * @param <T> 业务载荷类型
 */
@Data
public class EventEnvelope<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 全局唯一事件 ID，消费端幂等键 */
    private String eventId;
    /** 事件类型，见 MqTopicConst.EventType */
    private String eventType;
    /** 主题 */
    private String topic;
    /** 业务主键（如 userId:20260909），用于排查与顺序消费 */
    private String bizKey;
    /** 事件发生时间 */
    private LocalDateTime occurredAt;
    /** 事件版本，便于后续兼容老消息 */
    private int version = 1;
    /** 业务载荷 */
    private T payload;

    public static <T> EventEnvelope<T> of(String eventId, String topic, String eventType, String bizKey, T payload) {
        EventEnvelope<T> envelope = new EventEnvelope<>();
        envelope.setEventId(eventId);
        envelope.setTopic(topic);
        envelope.setEventType(eventType);
        envelope.setBizKey(bizKey);
        envelope.setOccurredAt(LocalDateTime.now());
        envelope.setPayload(payload);
        return envelope;
    }
}
