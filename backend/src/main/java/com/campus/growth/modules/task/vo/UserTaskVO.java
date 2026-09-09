package com.campus.growth.modules.task.vo;

import lombok.Data;

/**
 * 学生端任务视图。
 */
@Data
public class UserTaskVO {

    private String taskCode;
    private String taskName;
    private String taskType;
    private String icon;
    private String description;
    /** 当前进度 */
    private Integer progress;
    private Integer targetValue;
    private Integer pointAward;
    /** 0 进行中 1 已完成待领取 2 已领取 */
    private Integer status;
    /** 周期键，便于前端区分"今日任务/本周任务" */
    private String periodKey;
    /** 进度百分比（0-100） */
    private Integer percent;
}
