package com.campus.growth.common.enums;

/**
 * 订单状态。
 *
 * <pre>
 * CREATED ──支付──► PAID ──核销/收货──► FINISHED
 *    │                │
 *    └──取消──► CANCELLED
 *                     └──售后退货──► REFUNDED
 * </pre>
 *
 * <p>注意：退货的终态是 {@link #REFUNDED} 而不是 {@link #FINISHED}。
 * 早期版本退货后把订单标成 FINISHED，语义是反的（已完成 vs 已退款），
 * 会让"已完成订单数""消耗积分"这类统计口径出错。</p>
 */
public enum OrderStatus {

    /** 已创建，待支付 */
    CREATED,
    /** 已支付 */
    PAID,
    /** 已取消 */
    CANCELLED,
    /** 已完成（学生确认收货 / 运营核销） */
    FINISHED,
    /** 已退款（售后退货完成） */
    REFUNDED;

    public static boolean canPay(String status) {
        return CREATED.name().equals(status);
    }

    public static boolean canCancel(String status) {
        return CREATED.name().equals(status);
    }

    /** 只有已支付/已完成的订单可以申请售后（已退款、已取消不可重复申请） */
    public static boolean canRefund(String status) {
        return PAID.name().equals(status) || FINISHED.name().equals(status);
    }

    /** 只有已支付的订单可以核销完成 */
    public static boolean canFinish(String status) {
        return PAID.name().equals(status);
    }

    /** 计入"已消耗积分"的状态：退款的不算 */
    public static boolean isPointConsumed(String status) {
        return PAID.name().equals(status) || FINISHED.name().equals(status);
    }
}
