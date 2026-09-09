package com.campus.growth.modules.coupon.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 领券请求。
 */
@Data
public class CouponReceiveDTO {

    @NotNull(message = "请选择要领取的优惠券")
    private Long templateId;
}
