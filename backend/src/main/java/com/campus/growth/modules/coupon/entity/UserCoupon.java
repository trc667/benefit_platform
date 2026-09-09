package com.campus.growth.modules.coupon.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户优惠券。
 * <p>冗余了模板的名称/面额/门槛/适用范围快照：运营改模板后，
 * 用户手里的券语义不应随之变化，历史订单的优惠也要能对得上。</p>
 */
@Data
@TableName("user_coupon")
public class UserCoupon implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long templateId;
    private String couponCode;
    private String couponTitle;
    private String couponType;
    private Integer faceValue;
    private Integer discountRate;
    private Integer thresholdPoint;
    private Integer maxDiscount;
    private String scopeType;
    private String scopeValue;
    /** UNUSED / USED / EXPIRED */
    private String status;
    /** RECEIVE 主动领取 / REDEEM 兑换码 / ADMIN 后台发放 */
    private String source;
    private LocalDateTime receiveTime;
    private LocalDateTime useTime;
    private LocalDateTime expireTime;
    /** 核销订单号 */
    private String orderNo;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
