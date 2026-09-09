package com.campus.growth.modules.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务定义。
 */
@Data
@TableName("task_definition")
public class TaskDefinition implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskCode;
    private String taskName;
    /** DAILY / WEEKLY / ONCE */
    private String taskType;
    /** 目标值 */
    private Integer targetValue;
    /** 完成奖励积分 */
    private Integer pointAward;
    private String icon;
    private String description;
    private Integer sort;
    /** 1 启用 0 停用 */
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
