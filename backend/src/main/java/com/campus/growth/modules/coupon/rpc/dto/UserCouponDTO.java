package com.campus.growth.modules.coupon.rpc.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * RPC 传输对象：用户优惠券。
 * <p>刻意与实体解耦：实体加字段不会影响 RPC 契约，避免"改表就破坏接口"。</p>
 */
@Data
public class UserCouponDTO implements Serializable {

    private static final long serialVersionUID = 1L;

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
    private String status;
    private String expireTime;
    private String orderNo;
}
