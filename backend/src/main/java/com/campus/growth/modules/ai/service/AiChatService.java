package com.campus.growth.modules.ai.service;

import com.campus.growth.modules.ai.dto.AiChatDTO;
import com.campus.growth.modules.ai.vo.AiChatVO;
import com.campus.growth.modules.ai.vo.AiMessageVO;
import com.campus.growth.modules.ai.vo.AiSessionVO;

import java.util.List;

/**
 * AI 助手服务。
 */
public interface AiChatService {

    /** 对话（含 Function Calling 多轮工具调用） */
    AiChatVO chat(AiChatDTO dto);

    List<AiSessionVO> sessions();

    List<AiMessageVO> messages(String sessionId);

    void deleteSession(String sessionId);

    /** 订单清单文案（轻量模型 HTTP 直连） */
    String orderSummary(String orderNo);
}
