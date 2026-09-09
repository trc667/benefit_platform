package com.campus.growth.modules.coupon.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 定向发放请求（管理端）。
 */
@Data
public class CouponGrantDTO {

    @NotNull(message = "请选择券模板")
    private Long templateId;

    /** 指定用户 ID 列表；为空时按 count 随机发放给最近注册的学生 */
    private List<Long> userIds;

    /** 随机发放人数（userIds 为空时生效） */
    private Integer count;
}
