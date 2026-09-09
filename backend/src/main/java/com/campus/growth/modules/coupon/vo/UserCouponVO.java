package com.campus.growth.modules.coupon.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户优惠券视图。
 */
@Data
public class UserCouponVO {

    private Long id;
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
    private String valueDesc;
    /** UNUSED / USED / EXPIRED */
    private String status;
    private String statusDesc;
    private String source;
    private LocalDateTime receiveTime;
    private LocalDateTime useTime;
    private LocalDateTime expireTime;
    private String orderNo;
    /** 结算页展示：当前订单可抵扣金额（仅在 /coupon/available 接口返回） */
    private Integer previewDiscount;
    /** 是否可用（结算页用） */
    private Boolean usable;
    /** 不可用原因 */
    private String unusableReason;
}
