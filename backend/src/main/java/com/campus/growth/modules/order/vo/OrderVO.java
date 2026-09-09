package com.campus.growth.modules.order.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单视图。
 */
@Data
public class OrderVO {

    private Long id;
    private String orderNo;
    private Long userId;
    private String nickname;
    private Integer goodsTotalPoint;
    private Integer discountPoint;
    private Integer payPoint;
    private String couponCode;
    private String discountSnapshot;
    private String status;
    private String statusDesc;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime payTime;
    private LocalDateTime cancelTime;
    /** 明细（列表页只放第一条用于展示商品图与标题） */
    private List<OrderItemVO> items;
    /** 是否已申请售后 */
    private Boolean refundApplied;
}
