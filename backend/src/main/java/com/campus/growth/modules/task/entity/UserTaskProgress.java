package com.campus.growth.modules.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户任务进度（MySQL 归档表）。
 * <p>Redis Hash 是"热数据真源"，本表由 Kafka 消费者异步批量 upsert 写入，
 * 用于报表、对账与 Redis 数据丢失后的恢复。</p>
 */
@Data
@TableName("user_task_progress")
public class UserTaskProgress implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String taskCode;
    /** 周期键：20260909 / 2026W37 / ONCE */
    private String periodKey;
    private Integer progress;
    private Integer targetValue;
    /** 0 进行中 1 已完成 2 已发奖 */
    private Integer status;
    private LocalDateTime finishTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
