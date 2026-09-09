package com.campus.growth.modules.task.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 任务进度事件载荷（Redis → Kafka → MySQL 的搬运工）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskProgressEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private String taskCode;
    private String periodKey;
    /** 上报后的最新进度（单调递增） */
    private Integer progress;
    private Integer targetValue;
    /** 0 进行中 1 已完成 */
    private Integer status;
}
