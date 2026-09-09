package com.campus.growth.modules.ai.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 对话模型的最小抽象（只覆盖本项目用到的 OpenAI 兼容协议子集）。
 *
 * <p>刻意不引 Spring AI / LangChain4j：项目只需要 /chat/completions + tool_calls，
 * 自己封装反而少 30 多个传递依赖，出错时也不用翻框架源码。</p>
 */
public final class ChatModel {

    private ChatModel() {
    }

    /** 一条对话消息 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        /** system / user / assistant / tool */
        private String role;
        private String content;
        /** assistant 消息里携带的工具调用 */
        private List<ToolCall> toolCalls;
        /** role=tool 时必填：对应的工具调用 ID */
        private String toolCallId;

        public static Message system(String content) {
            return new Message("system", content, null, null);
        }

        public static Message user(String content) {
            return new Message("user", content, null, null);
        }

        public static Message assistant(String content) {
            return new Message("assistant", content, null, null);
        }

        public static Message assistantWithTools(String content, List<ToolCall> toolCalls) {
            return new Message("assistant", content, toolCalls, null);
        }

        public static Message tool(String toolCallId, String content) {
            return new Message("tool", content, null, toolCallId);
        }
    }

    /** 工具调用 */
    @Data
    @NoArgsConstructor
    public static class ToolCall {
        private String id;
        private String name;
        /** 模型生成的 JSON 参数串 */
        private String arguments;
    }

    /** 工具定义（JSON Schema） */
    @Data
    public static class ToolDefinition {
        private String name;
        private String description;
        /** 参数的 JSON Schema 字符串 */
        private String parametersJson;

        public static ToolDefinition of(String name, String description, String parametersJson) {
            ToolDefinition def = new ToolDefinition();
            def.setName(name);
            def.setDescription(description);
            def.setParametersJson(parametersJson);
            return def;
        }
    }

    /** 模型响应 */
    @Data
    public static class Response {
        private String content;
        private List<ToolCall> toolCalls = new ArrayList<>();
        private String model;
        private int promptTokens;
        private int completionTokens;
        private long costMs;

        public boolean hasToolCalls() {
            return toolCalls != null && !toolCalls.isEmpty();
        }
    }
}
