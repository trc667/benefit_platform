package com.campus.growth.common.enums;

/**
 * 任务周期类型。
 */
public enum TaskType {

    /** 每日任务：periodKey = yyyyMMdd */
    DAILY,
    /** 每周任务：periodKey = yyyyWww（如 2026W37） */
    WEEKLY,
    /** 一次性任务：periodKey = ONCE */
    ONCE
}
