package com.campus.growth.modules.signin.vo;

import lombok.Data;

/**
 * 签到结果。
 */
@Data
public class SignInResultVO {

    private String signDate;
    private Integer continuousDays;
    /** 本次总奖励（基础 + 连续奖励） */
    private Integer pointAward;
    /** 基础积分 */
    private Integer basePoint;
    /** 连续签到额外奖励 */
    private Integer extraAward;
    /** 本月累计签到天数 */
    private Integer monthCount;
}
