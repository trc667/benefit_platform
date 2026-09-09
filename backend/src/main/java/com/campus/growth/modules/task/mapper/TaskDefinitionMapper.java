package com.campus.growth.modules.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.growth.modules.task.entity.TaskDefinition;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务定义 Mapper。
 */
@Mapper
public interface TaskDefinitionMapper extends BaseMapper<TaskDefinition> {
}
