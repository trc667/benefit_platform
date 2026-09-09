package com.campus.growth.modules.coupon.rpc.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * RPC 传输对象：券模板。
 */
@Data
public class CouponTemplateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

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
    private Integer totalCount;
    private Integer issuedCount;
    private Integer perUserLimit;
    private Integer status;
}
