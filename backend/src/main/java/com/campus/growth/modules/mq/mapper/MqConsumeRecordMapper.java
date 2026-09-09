package com.campus.growth.modules.mq.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.growth.modules.mq.entity.MqConsumeRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消费幂等记录 Mapper。
 */
@Mapper
public interface MqConsumeRecordMapper extends BaseMapper<MqConsumeRecord> {
}
