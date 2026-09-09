package com.campus.growth.modules.order.vo;

import lombok.Data;

/**
 * 订单明细视图。
 */
@Data
public class OrderItemVO {

    private Long goodsId;
    private String goodsCode;
    private String goodsTitle;
    private String goodsCover;
    private Integer unitPoint;
    private Integer quantity;
    private Integer itemDiscount;
    private Integer itemAmount;
}
