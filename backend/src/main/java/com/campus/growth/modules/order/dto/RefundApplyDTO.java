package com.campus.growth.modules.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 售后申请。
 */
@Data
public class RefundApplyDTO {

    @NotBlank(message = "请选择订单")
    private String orderNo;

    /** RETURN 退货 / EXCHANGE 换货 */
    @NotBlank(message = "请选择售后类型")
    private String refundType;

    @NotBlank(message = "请填写申请原因")
    @Size(max = 255, message = "原因不能超过 255 个字")
    private String reason;
}
