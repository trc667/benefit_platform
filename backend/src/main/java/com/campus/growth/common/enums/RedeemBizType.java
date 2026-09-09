package com.campus.growth.common.enums;

/**
 * 兑换码奖励类型。
 */
public enum RedeemBizType {

    /** 直接发放积分 */
    POINT,
    /** 发放优惠券（ref_id 指向券模板） */
    COUPON,
    /** 发放权益商品（ref_id 指向商品，实际落成一张 0 积分订单） */
    GOODS
}
