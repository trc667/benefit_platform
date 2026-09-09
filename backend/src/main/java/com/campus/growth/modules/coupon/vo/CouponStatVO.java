package com.campus.growth.modules.coupon.vo;

import lombok.Data;

/**
 * 券模板发放/核销统计。
 */
@Data
public class CouponStatVO {

    private Long templateId;
    private String title;
    private String couponType;
    private Integer totalCount;
    private Integer issuedCount;
    private Long usedCount;
    /** 核销率（%） */
    private Double useRate;
}
