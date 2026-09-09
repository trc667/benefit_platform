package com.campus.growth.common.enums;

/**
 * 积分流水业务类型。与 {@code point_record.biz_type} 对应。
 */
public enum PointBizType {

    /** 签到 */
    SIGNIN,
    /** 任务奖励 */
    TASK,
    /** 兑换码 */
    REDEEM,
    /** 下单扣减 */
    ORDER_PAY,
    /** 售后退回 */
    ORDER_REFUND,
    /** 后台调整 */
    ADMIN
}
