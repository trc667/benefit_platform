package com.campus.growth.modules.order.vo;

import lombok.Data;

import java.util.List;

/**
 * 优惠方案（一种合法的券组合及其算价结果）。
 */
@Data
public class DiscountPlanVO {

    /** 参与该方案的券 ID */
    private List<Long> couponIds;
    /** 券名称，展示用 */
    private List<String> couponTitles;
    /** 组合说明，如"满 300 减 60 + 学习类 8.5 折" */
    private String desc;
    /** 优惠总额 */
    private Integer discountPoint;
    /** 实付积分 */
    private Integer payPoint;
    /** 是否是最优方案 */
    private Boolean best;
    /** 计算该方案耗时（毫秒），用于展示并行算价的收益 */
    private Long costMs;
}
