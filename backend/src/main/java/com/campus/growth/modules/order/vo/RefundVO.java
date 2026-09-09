package com.campus.growth.modules.order.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 退换单视图。
 */
@Data
public class RefundVO {

    private Long id;
    private String refundNo;
    private String orderNo;
    private Long userId;
    private String nickname;
    private String refundType;
    private String refundTypeDesc;
    private String reason;
    private String status;
    private String statusDesc;
    private Integer refundPoint;
    private String handleRemark;
    private LocalDateTime applyTime;
    private LocalDateTime handleTime;
    /** 关联订单信息（列表展示用） */
    private Integer payPoint;
    private String goodsTitle;
}
