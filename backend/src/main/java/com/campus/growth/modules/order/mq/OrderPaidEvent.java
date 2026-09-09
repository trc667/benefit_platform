package com.campus.growth.modules.order.mq;

import lombok.Data;

/**
 * 订单支付成功事件体（{@code cg.order.paid} 的 payload）。
 */
@Data
public class OrderPaidEvent {

    private String orderNo;

    private Long userId;

    private Integer payPoint;
}
