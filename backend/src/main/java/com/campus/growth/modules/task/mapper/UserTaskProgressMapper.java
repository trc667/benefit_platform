package com.campus.growth.modules.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.growth.modules.task.entity.UserTaskProgress;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户任务进度 Mapper。
 */
@Mapper
public interface UserTaskProgressMapper extends BaseMapper<UserTaskProgress> {
}
