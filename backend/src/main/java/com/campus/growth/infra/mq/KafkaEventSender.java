package com.campus.growth.infra.mq;

import com.campus.growth.common.constant.MqTopicConst;
import com.campus.growth.common.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Kafka 发送封装。
 *
 * <h3>为什么要包一层</h3>
 * <ul>
 *   <li>统一序列化：事件体一律走 {@link JsonUtil}，避免各处自己拼字符串；</li>
 *   <li>统一超时：发送同步等待 3s，避免 Kafka 不可用时把业务线程拖死；</li>
 *   <li>统一降级：发送失败不抛异常，返回 false 交由本地消息表补偿，
 *       保证"业务已成功但消息没发出去"最终能被补上；</li>
 *   <li>统一死信：重试仍失败的进入 {@code cg.dead.letter}。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventSender {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MqProperties mqProperties;

    /**
     * 发送事件。
     *
     * @return true = 发送成功
     */
    public boolean send(String topic, String key, EventEnvelope<?> envelope) {
        if (!mqProperties.isEnabled()) {
            log.debug("Kafka 已关闭，跳过发送 topic={} eventId={}", topic, envelope.getEventId());
            return false;
        }
        String json = JsonUtil.toJson(envelope);
        if (json == null) {
            log.error("事件序列化失败 eventId={}", envelope.getEventId());
            return false;
        }
        try {
            // 同步等待 broker ack（producer 配置 acks=all），确保消息真的落到副本
            kafkaTemplate.send(topic, key, json)
                    .get(mqProperties.getOutbox().getSendTimeoutMs(), TimeUnit.MILLISECONDS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("发送事件被中断 topic={} eventId={}", topic, envelope.getEventId());
            return false;
        } catch (Exception e) {
            log.warn("发送事件失败 topic={} eventId={} cause={}", topic, envelope.getEventId(), e.getMessage());
            return false;
        }
    }

    /** 发送到死信主题 */
    public void sendToDeadLetter(EventEnvelope<?> envelope, String reason) {
        if (!mqProperties.isEnabled()) {
            return;
        }
        try {
            String json = JsonUtil.toJson(envelope);
            kafkaTemplate.send(MqTopicConst.DEAD_LETTER, envelope.getEventId(), json);
            log.error("事件进入死信 topic={} eventId={} reason={}", envelope.getTopic(), envelope.getEventId(), reason);
        } catch (Exception e) {
            log.error("死信发送失败 eventId={}", envelope.getEventId(), e);
        }
    }
}
