package com.campus.growth.modules.mq.service;

import com.campus.growth.modules.mq.entity.MqConsumeRecord;
import com.campus.growth.modules.mq.mapper.MqConsumeRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * 消费幂等支撑。
 *
 * <h3>幂等实现</h3>
 * <p>Kafka 只能保证"至少一次"投递，重复消费不可避免。这里用
 * {@code mq_consume_record} 的唯一索引 {@code (event_id, consumer_group)} 做去重：
 * 插入成功 = 第一次消费，插入冲突 = 已消费过直接跳过。</p>
 *
 * <p>注意：幂等记录必须与业务操作在<b>同一个事务</b>里提交，
 * 否则"记录已插入但业务回滚"会导致消息被永久丢弃。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotentConsumerSupport {

    private final MqConsumeRecordMapper consumeRecordMapper;

    /**
     * 消费前登记，返回 true 表示可以继续处理。
     *
     * @return false = 已消费过，调用方应直接 return
     */
    public boolean tryMarkConsuming(String eventId, String consumerGroup, String topic) {
        MqConsumeRecord record = new MqConsumeRecord();
        record.setEventId(eventId);
        record.setConsumerGroup(consumerGroup);
        record.setTopic(topic);
        record.setStatus(1);
        record.setCostMs(0);
        try {
            consumeRecordMapper.insert(record);
            return true;
        } catch (DuplicateKeyException e) {
            log.info("事件已消费，跳过重复投递 eventId={} group={}", eventId, consumerGroup);
            return false;
        }
    }

    /** 记录消费失败（仅日志，失败的事件由 Kafka 重试或人工处理） */
    public void markFailed(String eventId, String consumerGroup, String topic, String error, int costMs) {
        try {
            MqConsumeRecord record = new MqConsumeRecord();
            record.setEventId(eventId);
            record.setConsumerGroup(consumerGroup);
            record.setTopic(topic);
            record.setStatus(0);
            record.setErrorMsg(error == null ? null : error.substring(0, Math.min(error.length(), 500)));
            record.setCostMs(costMs);
            consumeRecordMapper.insert(record);
        } catch (Exception e) {
            log.warn("记录消费失败信息异常 eventId={}", eventId, e);
        }
    }
}
