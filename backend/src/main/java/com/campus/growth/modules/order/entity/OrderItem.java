package com.campus.growth.modules.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单明细（字段级快照，商品改名改价不影响历史订单）。
 */
@Data
@TableName("order_item")
public class OrderItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;
    private String orderNo;
    private Long goodsId;
    private String goodsCode;
    private String goodsTitle;
    private String goodsCover;
    private Integer unitPoint;
    private Integer quantity;
    private Integer itemDiscount;
    private Integer itemAmount;
    private LocalDateTime createTime;
}
