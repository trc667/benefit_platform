package com.campus.growth.modules.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 售后处理（管理端）。
 */
@Data
public class RefundHandleDTO {

    @NotBlank(message = "请选择退换单")
    private String refundNo;

    /** true 通过 / false 驳回 */
    @NotNull(message = "请选择处理结果")
    private Boolean approved;

    @Size(max = 255, message = "处理备注不能超过 255 个字")
    private String handleRemark;

    /** 通过时退回的积分；为空则按订单实付金额全额退回 */
    private Integer refundPoint;
}
