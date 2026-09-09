package com.campus.growth.modules.signin.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 签到成功事件载荷。
 * <p>消费端在 {@code modules/point/mq/SignInEventConsumer}，负责加积分与更新排行榜。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignInSuccessEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    /** yyyy-MM-dd，同时作为积分流水的 bizNo */
    private String signDate;
    private Integer continuousDays;
    private Integer basePoint;
    private Integer extraAward;
    private Integer totalAward;
}
