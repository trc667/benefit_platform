package com.campus.growth.modules.mq.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 消费幂等记录。
 * <p>唯一索引 {@code (event_id, consumer_group)} 是幂等的最后一道防线：
 * Kafka 至少一次投递语义下，重复消息在插入时直接冲突，业务逻辑不会被执行第二次。</p>
 */
@Data
@TableName("mq_consume_record")
public class MqConsumeRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventId;
    private String consumerGroup;
    private String topic;
    /** 1 成功 0 失败 */
    private Integer status;
    private String errorMsg;
    private Integer costMs;
    private LocalDateTime createTime;
}
