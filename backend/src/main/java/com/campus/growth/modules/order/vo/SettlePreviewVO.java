package com.campus.growth.modules.order.vo;

import com.campus.growth.modules.coupon.vo.UserCouponVO;
import lombok.Data;

import java.util.List;

/**
 * 结算页预览。
 */
@Data
public class SettlePreviewVO {

    private Long goodsId;
    private String goodsTitle;
    private String goodsCover;
    private Integer unitPoint;
    private Integer quantity;
    /** 商品总额 */
    private Integer goodsTotal;

    /** 最优优惠方案 */
    private DiscountPlanVO bestPlan;
    /** 全部候选方案（按优惠金额倒序） */
    private List<DiscountPlanVO> candidates;
    /** 用户可用券（含不可用及原因） */
    private List<UserCouponVO> availableCoupons;

    /** 当前积分余额 */
    private Integer balance;
    /** 余额是否够付 */
    private Boolean balanceEnough;
    /** 本次并行算价总耗时 */
    private Long totalCostMs;
    /** 参与枚举的券数量 / 枚举出的组合数 */
    private Integer couponCount;
    private Integer combinationCount;
}
