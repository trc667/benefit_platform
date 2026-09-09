package com.campus.growth.modules.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单主表。
 */
@Data
@TableName("order_main")
public class OrderMain implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;
    private Long userId;
    private String orderType;
    /** 商品总额 */
    private Integer goodsTotalPoint;
    /** 优惠总额 */
    private Integer discountPoint;
    /** 实付积分 */
    private Integer payPoint;
    private Long couponId;
    private String couponCode;
    /** 优惠方案快照（最优组合 JSON 文本），售后核对用 */
    private String discountSnapshot;
    /** CREATED / PAID / CANCELLED / FINISHED */
    private String status;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime payTime;
    private LocalDateTime cancelTime;
    private LocalDateTime finishTime;
    private LocalDateTime updateTime;

    @Version
    private Integer version;
}
