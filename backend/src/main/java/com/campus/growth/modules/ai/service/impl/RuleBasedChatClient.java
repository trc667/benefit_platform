package com.campus.growth.modules.ai.service.impl;

import com.campus.growth.modules.ai.model.ChatModel;
import com.campus.growth.modules.ai.service.ChatModelClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 本地规则引擎（离线降级实现）。
 *
 * <h3>为什么需要它</h3>
 * <p>未配置大模型 API Key 时（clone 项目的同学大多没有），AI 助手不能变成"点不动的按钮"。
 * 这个实现用关键词 + 正则识别意图，产出与真实模型<b>完全相同结构</b>的 tool_calls，
 * 让 Function Calling 的整条链路（工具注册、执行、审计落库、结果回填）都能真实跑通。</p>
 *
 * <h3>必须说清楚的事</h3>
 * <p>它不是大模型，只是"意图识别 + 固定话术"。日志与接口返回里会带上
 * {@code model=local-rule-based}，前端会明确提示"当前为离线演示模式"，
 * 不会伪装成模型输出。</p>
 */
@Slf4j
@Component
public class RuleBasedChatClient implements ChatModelClient {

    private static final Pattern ORDER_NO = Pattern.compile("(CG\\d{20,26})");
    private static final Pattern NUMBER = Pattern.compile("(\\d+)");

    @Override
    public boolean isRealModel() {
        return false;
    }

    @Override
    public String modelName() {
        return "local-rule-based";
    }

    @Override
    public ChatModel.Response chat(List<ChatModel.Message> messages, List<ChatModel.ToolDefinition> tools) {
        long start = System.currentTimeMillis();
        ChatModel.Response response = new ChatModel.Response();
        response.setModel(modelName());

        // 关键：如果本轮已经执行过工具（messages 里有 role=tool），就直接把工具结果整理成自然语言，
        // 否则会一直重复调用同一个工具，直到耗尽 MAX_TOOL_ROUNDS（离线模式最容易踩的坑）
        ChatModel.Message toolMessage = lastToolMessage(messages);
        if (toolMessage != null) {
            response.setContent(summarize(toolMessage.getContent()));
            response.setCostMs(System.currentTimeMillis() - start);
            return response;
        }

        String userText = lastUserMessage(messages);
        String text = userText == null ? "" : userText.trim();
        List<ChatModel.ToolCall> calls = new ArrayList<>();

        Matcher orderNoMatcher = ORDER_NO.matcher(text.toUpperCase());
        Matcher numberMatcher = NUMBER.matcher(text);

        if (contains(text, "取消", "撤单")) {
            if (orderNoMatcher.find()) {
                calls.add(call("cancelOrder", "{\"orderNo\":\"" + orderNoMatcher.group(1) + "\"}"));
            } else {
                response.setContent("取消订单需要提供订单号，格式形如 CG240101120000123456。你也可以说“我的订单”让我先列出来。");
                response.setCostMs(System.currentTimeMillis() - start);
                return response;
            }
        } else if (contains(text, "订单")) {
            calls.add(call("queryOrders", "{}"));
        } else if (contains(text, "优惠券", "券")) {
            calls.add(call("queryMyCoupons", "{\"status\":\"UNUSED\"}"));
        } else if (contains(text, "任务", "进度")) {
            calls.add(call("queryTaskProgress", "{}"));
        } else if (contains(text, "积分", "余额", "等级")) {
            calls.add(call("queryPointAccount", "{}"));
        } else if (contains(text, "下单", "购买", "兑换") && numberMatcher.find()) {
            // 明确带商品编号才认为是"下单意图"，否则按"逛一逛"处理，避免误下单
            calls.add(call("createOrder", "{\"goodsId\":" + numberMatcher.group(1) + ",\"quantity\":1}"));
        } else if (contains(text, "权益", "商品", "有什么", "看看", "推荐", "商城", "可以换", "兑换")) {
            calls.add(call("queryBenefits", "{}"));
        } else {
            response.setContent("我是校园成长权益助手，可以帮你：\n"
                    + "1. 查权益商城有什么可兑换的；\n"
                    + "2. 查积分余额与成长等级；\n"
                    + "3. 查任务进度、我的优惠券；\n"
                    + "4. 帮我下单兑换（说“兑换 2 号权益”）、取消订单。\n"
                    + "直接说需求就行，例如“看看有什么可以换的”。");
        }
        response.setToolCalls(calls);
        response.setCostMs(System.currentTimeMillis() - start);
        return response;
    }

    // ------------------------------------------------------------------
    // 工具结果 → 自然语言
    // ------------------------------------------------------------------

    private ChatModel.Message lastToolMessage(List<ChatModel.Message> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatModel.Message message = messages.get(i);
            if ("tool".equals(message.getRole()) && StringUtils.hasText(message.getContent())) {
                return message;
            }
        }
        return null;
    }

    /**
     * 把工具返回的 JSON 整理成一句人话。
     * <p>真实模型能自己完成这一步；离线模式用固定模板兜住，保证链路闭环。</p>
     */
    private String summarize(String json) {
        var node = com.campus.growth.common.util.JsonUtil.parse(json,
                com.fasterxml.jackson.databind.JsonNode.class);
        if (node == null) {
            return "我没能读到数据，请稍后再试。";
        }
        if (node.has("success") && !node.path("success").asBoolean()) {
            return "操作没有成功：" + node.path("message").asText("原因未知");
        }
        if (node.has("orderNo")) {
            return "已为你下单，订单号 " + node.path("orderNo").asText()
                    + "，优惠 " + node.path("discountPoint").asInt(0)
                    + " 积分，实付 " + node.path("payPoint").asInt(0)
                    + " 积分。请到「我的订单」完成支付。";
        }
        if (node.has("balance")) {
            return "你当前有 " + node.path("balance").asInt() + " 积分，"
                    + "成长等级 Lv" + node.path("growthLevel").asInt(1) + "，"
                    + "距离下一级还需 " + node.path("nextLevelPoint").asInt() + " 积分。";
        }
        if (node.has("items")) {
            var items = node.path("items");
            if (items.isArray() && items.size() > 0) {
                StringBuilder sb = new StringBuilder("给你找到这些：");
                int limit = Math.min(items.size(), 4);
                for (int i = 0; i < limit; i++) {
                    var item = items.get(i);
                    sb.append("\n").append(i + 1).append(". ")
                            .append(item.path("title").isMissingNode()
                                    ? item.path("taskName").asText("") : item.path("title").asText());
                    if (item.has("pricePoint")) {
                        sb.append("（").append(item.path("pricePoint").asInt()).append(" 积分）");
                    }
                    if (item.has("valueDesc")) {
                        sb.append("（").append(item.path("valueDesc").asText()).append("）");
                    }
                    if (item.has("orderNo")) {
                        sb.append("（订单 ").append(item.path("orderNo").asText())
                                .append(" ").append(item.path("statusDesc").asText("")).append("）");
                    }
                }
                return sb.toString();
            }
            return "目前没有查到数据。";
        }
        if (node.has("message")) {
            return node.path("message").asText();
        }
        return "已为你查询完成：" + json;
    }

    private ChatModel.ToolCall call(String name, String args) {
        ChatModel.ToolCall toolCall = new ChatModel.ToolCall();
        toolCall.setId("local-" + UUID.randomUUID().toString().substring(0, 8));
        toolCall.setName(name);
        toolCall.setArguments(args);
        return toolCall;
    }

    private String lastUserMessage(List<ChatModel.Message> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatModel.Message message = messages.get(i);
            if ("user".equals(message.getRole()) && StringUtils.hasText(message.getContent())) {
                return message.getContent();
            }
        }
        return null;
    }

    private boolean contains(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
