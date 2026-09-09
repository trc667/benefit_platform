package com.campus.growth.modules.ai.service;

import com.campus.growth.modules.ai.model.ChatModel;

import java.util.List;

/**
 * 对话模型客户端。
 * <p>两个实现：真实 HTTP 调用（OpenAI 兼容）与本地规则引擎（未配置 API Key 时的降级）。</p>
 */
public interface ChatModelClient {

    /** 当前实现是否为真实大模型 */
    boolean isRealModel();

    /** 模型名 */
    String modelName();

    /**
     * 发起一次对话。
     *
     * @param messages 完整上下文（含 system 与历史）
     * @param tools    可用工具，为空表示不需要 Function Calling
     */
    ChatModel.Response chat(List<ChatModel.Message> messages, List<ChatModel.ToolDefinition> tools);
}
