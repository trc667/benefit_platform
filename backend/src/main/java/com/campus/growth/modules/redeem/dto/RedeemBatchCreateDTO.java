package com.campus.growth.modules.redeem.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 创建兑换码批次（管理端）。
 */
@Data
public class RedeemBatchCreateDTO {

    @NotBlank(message = "请填写批次名称")
    @Size(max = 64, message = "批次名称不能超过 64 个字")
    private String title;

    /** POINT / COUPON / GOODS */
    @NotBlank(message = "请选择奖励类型")
    private String bizType;

    /** bizType=COUPON 时必填 */
    private Long refId;

    @NotNull(message = "请填写奖励值")
    @Min(value = 1, message = "奖励值至少为 1")
    private Integer rewardValue;

    @NotNull(message = "请填写码总量")
    @Min(value = 1, message = "码总量至少为 1")
    private Integer totalCount;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @Size(max = 255, message = "备注不能超过 255 个字")
    private String remark;
}
