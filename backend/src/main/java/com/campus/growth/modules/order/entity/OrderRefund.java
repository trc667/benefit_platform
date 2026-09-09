package com.campus.growth.modules.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 退换单。
 */
@Data
@TableName("order_refund")
public class OrderRefund implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String refundNo;
    private String orderNo;
    private Long orderId;
    private Long userId;
    /** RETURN 退货 / EXCHANGE 换货 */
    private String refundType;
    private String reason;
    /** APPLIED / APPROVED / REJECTED / REFUNDED */
    private String status;
    /** 退回积分 */
    private Integer refundPoint;
    private String handleRemark;
    private Long handlerId;
    private LocalDateTime applyTime;
    private LocalDateTime handleTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
