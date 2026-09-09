package com.campus.growth.modules.point.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 运营手动调整积分请求。
 *
 * <p>为什么需要它：兑换码核销失败、活动奖励漏发、投诉补偿这些场景，
 * 运营必须能人工改积分。但不能直接改库——必须留流水、必须幂等、必须可追溯，
 * 所以走 {@code point_record}（biz_type=ADMIN）+ 操作日志。</p>
 */
@Data
public class PointAdjustDTO {

    @NotNull(message = "请选择用户")
    private Long userId;

    /** 正数加分，负数扣分（0 无意义，服务层拦截） */
    @NotNull(message = "请输入调整分值")
    @Min(value = -100000, message = "单次调整绝对值不能超过 100000")
    @Max(value = 100000, message = "单次调整绝对值不能超过 100000")
    private Integer changePoint;

    /** 幂等键；不传则由服务端生成。重复提交同一个 bizNo 不会重复入账 */
    @Size(max = 64, message = "业务单号不能超过 64 位")
    private String bizNo;

    @NotBlank(message = "请填写调整原因")
    @Size(max = 100, message = "调整原因不能超过 100 个字")
    private String reason;
}
