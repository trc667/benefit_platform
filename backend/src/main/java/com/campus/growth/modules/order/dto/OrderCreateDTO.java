package com.campus.growth.modules.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 下单请求。
 */
@Data
public class OrderCreateDTO {

    @NotNull(message = "请选择权益商品")
    private Long goodsId;

    @NotNull(message = "请填写兑换数量")
    @Min(value = 1, message = "兑换数量至少为 1")
    private Integer quantity;

    /**
     * 选用的优惠券 ID 列表（来自结算页的最优方案）。
     * <p>服务端会重新校验每一张券并重算优惠金额，不信任前端传来的金额。</p>
     */
    private List<Long> couponIds;

    /** 兼容单券场景：与 couponIds 二选一 */
    private Long couponId;

    @Size(max = 64, message = "收货人不能超过 64 个字")
    private String receiverName;

    @Size(max = 20, message = "联系电话不能超过 20 位")
    private String receiverPhone;

    @Size(max = 255, message = "收货地址不能超过 255 个字")
    private String receiverAddress;

    @Size(max = 255, message = "备注不能超过 255 个字")
    private String remark;
}
