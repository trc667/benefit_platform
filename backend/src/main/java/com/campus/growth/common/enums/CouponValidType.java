package com.campus.growth.common.enums;

import java.time.LocalDateTime;

/**
 * 优惠券有效期类型。
 */
public enum CouponValidType {

    /** 固定区间：以模板 start_time / end_time 为准 */
    FIXED,
    /** 相对有效期：领取时间 + valid_days 天 */
    RELATIVE;

    /** 计算券的过期时间 */
    public static LocalDateTime resolveExpireTime(CouponValidType type, LocalDateTime templateEndTime,
                                                  int validDays, LocalDateTime receiveTime) {
        if (type == RELATIVE && validDays > 0) {
            return receiveTime.plusDays(validDays);
        }
        return templateEndTime == null ? receiveTime.plusDays(30) : templateEndTime;
    }
}
