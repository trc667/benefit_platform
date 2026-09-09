package com.campus.growth.common.enums;

/**
 * 优惠券类型。
 */
public enum CouponType {

    /** 满减券：达到门槛减固定面额 */
    CASH,
    /** 折扣券：按折扣率打折，可设最高抵扣 */
    DISCOUNT,
    /** 无门槛券：直接抵扣面额 */
    DIRECT;

    /** 计算抵扣金额（单位：积分，向下取整，最小 0） */
    public static int calcDiscount(CouponType type, int orderAmount, int faceValue, int discountRate, int maxDiscount) {
        int discount;
        switch (type) {
            case CASH, DIRECT -> discount = faceValue;
            case DISCOUNT -> {
                // discountRate 存的是 折扣率*100，如 85 表示 8.5 折
                int rate = discountRate <= 0 ? 100 : discountRate;
                discount = orderAmount - (int) Math.floor(orderAmount * rate / 100.0);
                if (maxDiscount > 0) {
                    discount = Math.min(discount, maxDiscount);
                }
            }
            default -> discount = 0;
        }
        return Math.max(0, Math.min(discount, orderAmount));
    }
}
