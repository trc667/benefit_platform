package com.campus.growth.modules.task.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 任务配置（管理端）。
 */
@Data
public class TaskSaveDTO {

    private Long id;

    @NotBlank(message = "请填写任务编码")
    @Size(max = 32, message = "任务编码不能超过 32 位")
    private String taskCode;

    @NotBlank(message = "请填写任务名称")
    @Size(max = 64, message = "任务名称不能超过 64 个字")
    private String taskName;

    /** DAILY / WEEKLY / ONCE */
    @NotBlank(message = "请选择任务周期")
    private String taskType;

    @NotNull(message = "请填写目标值")
    @Min(value = 1, message = "目标值至少为 1")
    private Integer targetValue;

    @NotNull(message = "请填写奖励积分")
    @Min(value = 0, message = "奖励积分不能为负")
    private Integer pointAward;

    private String icon;
    @Size(max = 255, message = "任务描述不能超过 255 个字")
    private String description;
    private Integer sort = 0;
    private Integer status = 1;
}
