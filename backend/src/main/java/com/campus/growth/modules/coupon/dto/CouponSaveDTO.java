package com.campus.growth.modules.coupon.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 券模板新增/编辑请求（管理端）。
 */
@Data
public class CouponSaveDTO {

    private Long id;

    @NotBlank(message = "请填写券名称")
    @Size(max = 64, message = "券名称不能超过 64 个字")
    private String title;

    /** CASH / DISCOUNT / DIRECT */
    @NotBlank(message = "请选择券类型")
    private String couponType;

    @Min(value = 0, message = "面额不能为负")
    private Integer faceValue = 0;

    /** 折扣率 * 100，如 85 表示 8.5 折 */
    @Min(value = 1, message = "折扣率不合法")
    private Integer discountRate = 100;

    @Min(value = 0, message = "门槛不能为负")
    private Integer thresholdPoint = 0;

    @Min(value = 0, message = "最高抵扣不能为负")
    private Integer maxDiscount = 0;

    /** ALL / GOODS / CATEGORY */
    private String scopeType = "ALL";
    private String scopeValue;

    @NotNull(message = "请填写发行总量")
    @Min(value = 1, message = "发行总量至少为 1")
    private Integer totalCount;

    @Min(value = 1, message = "单人限领至少为 1")
    private Integer perUserLimit = 1;

    /** FIXED / RELATIVE */
    private String validType = "RELATIVE";
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    @Min(value = 0, message = "有效天数不能为负")
    private Integer validDays = 30;
    private Integer status = 1;
}
