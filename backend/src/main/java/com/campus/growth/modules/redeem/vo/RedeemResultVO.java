package com.campus.growth.modules.redeem.vo;

import lombok.Data;

/**
 * 兑换结果。
 */
@Data
public class RedeemResultVO {

    private String batchNo;
    private String bizType;
    private Integer rewardValue;
    /** 兑换后的积分余额（POINT 类型才有意义） */
    private Integer balance;
    private String message;
    /** 若奖励是优惠券，返回券信息 */
    private Long couponId;
    private String couponTitle;
}
