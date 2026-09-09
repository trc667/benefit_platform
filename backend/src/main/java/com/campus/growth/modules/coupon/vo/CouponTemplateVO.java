package com.campus.growth.modules.coupon.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 优惠券模板视图（学生端领券列表 / 管理端表格共用）。
 */
@Data
public class CouponTemplateVO {

    private Long id;
    private String templateCode;
    private String title;
    private String couponType;
    private Integer faceValue;
    private Integer discountRate;
    private Integer thresholdPoint;
    private Integer maxDiscount;
    private String scopeType;
    private String scopeValue;
    /** 展示用文案，如"满 300 减 60""8.5 折" */
    private String valueDesc;
    private Integer totalCount;
    private Integer issuedCount;
    private Integer remainCount;
    private Integer perUserLimit;
    private String validType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer validDays;
    private Integer status;
    /** 当前用户是否已领（学生端） */
    private Boolean received;
    /** 当前用户已领张数 */
    private Integer receivedCount;
}
