package com.campus.growth.modules.system.vo;

import lombok.Data;

/**
 * 管理端仪表盘概览。
 */
@Data
public class DashboardVO {

    /** 学生总数 */
    private Long userCount;
    /** 今日签到人数 */
    private Long todaySignCount;
    /** 订单总数 */
    private Long orderCount;
    /** 今日订单数 */
    private Long todayOrderCount;
    /** 累计消耗积分（已支付/已完成订单） */
    private Long pointUsed;
    /** 已发放优惠券张数 */
    private Long couponIssued;
    /** 在售权益商品数 */
    private Long goodsCount;
    /** 待审核售后单数 */
    private Long refundPending;
}
