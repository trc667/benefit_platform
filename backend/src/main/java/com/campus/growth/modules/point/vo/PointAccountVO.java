package com.campus.growth.modules.point.vo;

import lombok.Data;

/**
 * 积分账户视图。
 */
@Data
public class PointAccountVO {

    private Long userId;
    private Integer balance;
    private Integer totalEarned;
    private Integer totalUsed;
    /** 成长等级：每 1000 累计积分升一级 */
    private Integer growthLevel;
    /** 距离下一级还差多少积分 */
    private Integer nextLevelPoint;
    /** 当前等级内的进度百分比（0-100） */
    private Integer levelProgress;
}
