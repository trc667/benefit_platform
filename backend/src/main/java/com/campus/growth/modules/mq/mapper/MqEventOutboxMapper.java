package com.campus.growth.modules.mq.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.growth.modules.mq.entity.MqEventOutbox;
import org.apache.ibatis.annotations.Mapper;

/**
 * 本地消息表 Mapper。
 */
@Mapper
public interface MqEventOutboxMapper extends BaseMapper<MqEventOutbox> {
}
