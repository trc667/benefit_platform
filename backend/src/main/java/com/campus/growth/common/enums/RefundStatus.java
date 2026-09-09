package com.campus.growth.common.enums;

/**
 * 退换单状态。
 */
public enum RefundStatus {

    /** 已申请，待审核 */
    APPLIED,
    /** 审核通过（退货：待退款；换货：待发货） */
    APPROVED,
    /** 已驳回 */
    REJECTED,
    /** 已完成（积分已退回） */
    REFUNDED;

    public static boolean canHandle(String status) {
        return APPLIED.name().equals(status);
    }
}
