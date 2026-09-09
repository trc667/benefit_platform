package com.campus.growth.modules.ai.vo;

import lombok.Data;

import java.util.List;

/**
 * AI 对话响应。
 */
@Data
public class AiChatVO {

    private String sessionId;
    /** 模型最终回复 */
    private String reply;
    private String model;
    /** 是否为真实大模型（false 表示本地规则引擎离线模式） */
    private Boolean realModel;
    /** 本轮工具调用明细 */
    private List<ToolCallVO> toolCalls;
    private Integer promptTokens;
    private Integer completionTokens;
    private Long costMs;

    @Data
    public static class ToolCallVO {
        private String name;
        /** 工具中文名 */
        private String label;
        private String args;
        private String result;
        private Boolean success;
        private Long costMs;
    }
}
