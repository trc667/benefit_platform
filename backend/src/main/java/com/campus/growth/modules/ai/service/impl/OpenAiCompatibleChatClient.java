package com.campus.growth.modules.ai.service.impl;

import com.campus.growth.common.util.JsonUtil;
import com.campus.growth.config.AiProperties;
import com.campus.growth.modules.ai.model.ChatModel;
import com.campus.growth.modules.ai.service.ChatModelClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI 兼容协议的对话客户端（DeepSeek / 通义 / 智谱 / OpenAI 均可）。
 *
 * <h3>为什么用 HTTP 直连</h3>
 * <p>协议本身就是"一个 POST + JSON"，用 Spring 6.1 的 {@link RestClient} 十几行就能写完。
 * 好处是超时、重试、日志完全可控，也便于把"主模型/轻量模型"两个客户端分开配置。</p>
 *
 * <h3>Function Calling 流程</h3>
 * <pre>
 * 请求 messages + tools
 *   → 模型返回 tool_calls（不是自然语言）
 *   → 本地执行工具，把结果以 role=tool 追加进 messages
 *   → 再次请求模型，得到自然语言回复
 * </pre>
 */
@Slf4j
@Component
public class OpenAiCompatibleChatClient implements ChatModelClient {

    private final RestClient client;
    private final AiProperties properties;

    public OpenAiCompatibleChatClient(@Qualifier("mainModelClient") RestClient client, AiProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public boolean isRealModel() {
        return StringUtils.hasText(properties.getApiKey());
    }

    @Override
    public String modelName() {
        return properties.getModel();
    }

    @Override
    public ChatModel.Response chat(List<ChatModel.Message> messages, List<ChatModel.ToolDefinition> tools) {
        long start = System.currentTimeMillis();
        ObjectNode body = JsonUtil.mapper().createObjectNode();
        body.put("model", properties.getModel());
        body.put("temperature", properties.getTemperature());
        body.put("max_tokens", properties.getMaxTokens());
        body.set("messages", buildMessages(messages));
        if (tools != null && !tools.isEmpty()) {
            body.set("tools", buildTools(tools));
            body.put("tool_choice", "auto");
        }

        try {
            String raw = client.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("Content-Type", "application/json")
                    .body(body.toString())
                    .retrieve()
                    .body(String.class);
            ChatModel.Response response = parse(raw);
            response.setCostMs(System.currentTimeMillis() - start);
            return response;
        } catch (Exception e) {
            log.error("调用大模型失败 model={} 耗时={}ms", properties.getModel(),
                    System.currentTimeMillis() - start, e);
            // 抛出让上层决定降级策略（这里不吞异常，避免"假装成功"）
            throw new IllegalStateException("大模型调用失败：" + e.getMessage(), e);
        }
    }

    private ArrayNode buildMessages(List<ChatModel.Message> messages) {
        ArrayNode array = JsonUtil.mapper().createArrayNode();
        for (ChatModel.Message message : messages) {
            ObjectNode node = JsonUtil.mapper().createObjectNode();
            node.put("role", message.getRole());
            if (message.getContent() != null) {
                node.put("content", message.getContent());
            }
            if (message.getToolCallId() != null) {
                node.put("tool_call_id", message.getToolCallId());
            }
            if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
                ArrayNode calls = JsonUtil.mapper().createArrayNode();
                for (ChatModel.ToolCall call : message.getToolCalls()) {
                    ObjectNode callNode = JsonUtil.mapper().createObjectNode();
                    callNode.put("id", call.getId());
                    callNode.put("type", "function");
                    ObjectNode function = JsonUtil.mapper().createObjectNode();
                    function.put("name", call.getName());
                    function.put("arguments", call.getArguments());
                    callNode.set("function", function);
                    calls.add(callNode);
                }
                node.set("tool_calls", calls);
            }
            array.add(node);
        }
        return array;
    }

    private ArrayNode buildTools(List<ChatModel.ToolDefinition> tools) {
        ArrayNode array = JsonUtil.mapper().createArrayNode();
        for (ChatModel.ToolDefinition tool : tools) {
            ObjectNode node = JsonUtil.mapper().createObjectNode();
            node.put("type", "function");
            ObjectNode function = JsonUtil.mapper().createObjectNode();
            function.put("name", tool.getName());
            function.put("description", tool.getDescription());
            JsonNode params = JsonUtil.parse(tool.getParametersJson(), JsonNode.class);
            function.set("parameters", params == null ? JsonUtil.mapper().createObjectNode() : params);
            node.set("function", function);
            array.add(node);
        }
        return array;
    }

    private ChatModel.Response parse(String raw) {
        ChatModel.Response response = new ChatModel.Response();
        response.setModel(properties.getModel());
        if (raw == null) {
            return response;
        }
        JsonNode root = JsonUtil.parse(raw, JsonNode.class);
        if (root == null) {
            return response;
        }
        JsonNode usage = root.path("usage");
        response.setPromptTokens(usage.path("prompt_tokens").asInt(0));
        response.setCompletionTokens(usage.path("completion_tokens").asInt(0));

        JsonNode message = root.path("choices").path(0).path("message");
        if (message.isMissingNode()) {
            return response;
        }
        response.setContent(message.path("content").isNull() ? null : message.path("content").asText(null));
        JsonNode toolCalls = message.path("tool_calls");
        if (toolCalls.isArray()) {
            List<ChatModel.ToolCall> calls = new ArrayList<>();
            for (JsonNode node : toolCalls) {
                ChatModel.ToolCall call = new ChatModel.ToolCall();
                call.setId(node.path("id").asText());
                call.setName(node.path("function").path("name").asText());
                call.setArguments(node.path("function").path("arguments").asText("{}"));
                calls.add(call);
            }
            response.setToolCalls(calls);
        }
        return response;
    }
}
