package com.campus.growth.common.enums;

/**
 * 用户任务状态。
 */
public enum TaskStatus {

    /** 进行中 */
    DOING(0),
    /** 已完成，待领取奖励 */
    FINISHED(1),
    /** 已领取奖励 */
    CLAIMED(2);

    private final int code;

    TaskStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
