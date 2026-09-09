package com.campus.growth.modules.point.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 积分流水。
 * <p>唯一索引 {@code (user_id, biz_type, biz_no)} 是幂等核心：
 * 同一次签到、同一笔订单只会产生一条流水，Kafka 重复消费时插入直接冲突。</p>
 */
@Data
@TableName("point_record")
public class PointRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    /** SIGNIN / TASK / REDEEM / ORDER_PAY / ORDER_REFUND / ADMIN */
    private String bizType;
    /** 业务单号 */
    private String bizNo;
    /** 变动积分，正加负减 */
    private Integer changePoint;
    /** 变动后余额 */
    private Integer balanceAfter;
    private String remark;
    private LocalDateTime createTime;
}
