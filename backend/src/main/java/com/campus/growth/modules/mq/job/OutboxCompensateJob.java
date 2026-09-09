package com.campus.growth.modules.mq.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.growth.common.util.JsonUtil;
import com.campus.growth.infra.mq.EventEnvelope;
import com.campus.growth.infra.mq.KafkaEventSender;
import com.campus.growth.infra.mq.MqProperties;
import com.campus.growth.modules.mq.entity.MqEventOutbox;
import com.campus.growth.modules.mq.mapper.MqEventOutboxMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 本地消息表补偿任务。
 *
 * <h3>补偿策略</h3>
 * <ul>
 *   <li>扫描 {@code status in (NEW, FAILED)} 且 {@code next_retry_time <= now} 的事件；</li>
 *   <li>指数退避：30s → 60s → 120s → 240s → 480s，避免 Kafka 长时间不可用时疯狂重试；</li>
 *   <li>超过 {@code max-retry} 次置为 {@code DEAD}，由管理端页面人工重试；</li>
 *   <li>发送成功置为 {@code SENT}，保留记录便于追溯（可按时间归档清理）。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "campus.mq.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxCompensateJob {

    private final MqEventOutboxMapper outboxMapper;
    private final KafkaEventSender kafkaEventSender;
    private final MqProperties mqProperties;

    @Scheduled(fixedDelayString = "${campus.mq.outbox.fixed-delay-ms:30000}", initialDelay = 15000)
    public void compensate() {
        if (!mqProperties.isEnabled()) {
            return;
        }
        List<MqEventOutbox> list = outboxMapper.selectList(new LambdaQueryWrapper<MqEventOutbox>()
                .in(MqEventOutbox::getStatus, "NEW", "FAILED")
                .le(MqEventOutbox::getNextRetryTime, LocalDateTime.now())
                .orderByAsc(MqEventOutbox::getId)
                .last("limit " + mqProperties.getOutbox().getBatchSize()));
        if (list.isEmpty()) {
            return;
        }
        int success = 0;
        for (MqEventOutbox outbox : list) {
            if (resend(outbox)) {
                success++;
            }
        }
        log.info("本地消息表补偿完成 扫描={} 成功={}", list.size(), success);
    }

    /** 单条重发，供补偿任务与管理端手动重试共用 */
    public boolean resend(MqEventOutbox outbox) {
        EventEnvelope<Object> envelope = EventEnvelope.of(outbox.getEventId(), outbox.getTopic(),
                outbox.getEventType(), outbox.getBizKey(), JsonUtil.parse(outbox.getPayload(), Object.class));
        boolean ok = kafkaEventSender.send(outbox.getTopic(), outbox.getBizKey(), envelope);

        MqEventOutbox update = new MqEventOutbox();
        update.setId(outbox.getId());
        if (ok) {
            update.setStatus("SENT");
            update.setLastError(null);
            outboxMapper.updateById(update);
            return true;
        }
        int retry = (outbox.getRetryCount() == null ? 0 : outbox.getRetryCount()) + 1;
        update.setRetryCount(retry);
        if (retry >= mqProperties.getOutbox().getMaxRetry()) {
            update.setStatus("DEAD");
            update.setLastError("重试 " + retry + " 次仍失败，已置为死信");
            kafkaEventSender.sendToDeadLetter(envelope, "outbox retry exhausted");
        } else {
            update.setStatus("FAILED");
            // 指数退避：30s * 2^(retry-1)
            long delaySeconds = 30L * (1L << Math.min(retry - 1, 6));
            update.setNextRetryTime(LocalDateTime.now().plusSeconds(delaySeconds));
            update.setLastError("第 " + retry + " 次发送失败");
        }
        outboxMapper.updateById(update);
        return false;
    }
}
