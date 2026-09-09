package com.campus.growth.modules.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.growth.modules.ai.entity.AiChatSession;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 会话 Mapper。
 */
@Mapper
public interface AiChatSessionMapper extends BaseMapper<AiChatSession> {
}
