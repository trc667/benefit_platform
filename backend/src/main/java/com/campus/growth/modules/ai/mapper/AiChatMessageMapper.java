package com.campus.growth.modules.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.growth.modules.ai.entity.AiChatMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 消息 Mapper。
 */
@Mapper
public interface AiChatMessageMapper extends BaseMapper<AiChatMessage> {
}
