package com.campus.growth.modules.mq.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 本地消息表。
 * <p>与业务写操作在同一个本地事务里落库，业务提交成功则事件一定存在；
 * 事务提交后再发 Kafka，发送失败由定时任务补偿。这是"不用 Seata 也能保证最终一致"的核心。</p>
 */
@Data
@TableName("mq_event_outbox")
public class MqEventOutbox implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 事件 ID（唯一索引，消费端幂等键） */
    private String eventId;
    private String topic;
    private String eventType;
    /** 业务键，如 userId:20260909 */
    private String bizKey;
    /** 事件体 JSON */
    private String payload;
    /** NEW / SENT / FAILED / DEAD */
    private String status;
    private Integer retryCount;
    /** 下次重试时间（指数退避） */
    private LocalDateTime nextRetryTime;
    private String lastError;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
