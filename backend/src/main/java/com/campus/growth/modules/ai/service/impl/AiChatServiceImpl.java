package com.campus.growth.modules.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.growth.common.context.UserContext;
import com.campus.growth.common.exception.BizException;
import com.campus.growth.common.result.ErrorCode;
import com.campus.growth.common.util.JsonUtil;
import com.campus.growth.common.util.OrderNoGenerator;
import com.campus.growth.config.AiProperties;
import com.campus.growth.modules.ai.dto.AiChatDTO;
import com.campus.growth.modules.ai.entity.AiChatMessage;
import com.campus.growth.modules.ai.entity.AiChatSession;
import com.campus.growth.modules.ai.mapper.AiChatMessageMapper;
import com.campus.growth.modules.ai.mapper.AiChatSessionMapper;
import com.campus.growth.modules.ai.model.ChatModel;
import com.campus.growth.modules.ai.service.AiChatService;
import com.campus.growth.modules.ai.service.AiToolRegistry;
import com.campus.growth.modules.ai.service.ChatModelClient;
import com.campus.growth.modules.ai.service.LightTextModelService;
import com.campus.growth.modules.ai.vo.AiChatVO;
import com.campus.growth.modules.ai.vo.AiMessageVO;
import com.campus.growth.modules.ai.vo.AiSessionVO;
import com.campus.growth.modules.order.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * AI 助手服务实现。
 *
 * <h3>一次对话的完整流程</h3>
 * <pre>
 * 用户提问
 *  ├─ 组装 system + 最近 N 轮历史 + 本轮提问
 *  ├─ 调用模型（带 tools 定义）
 *  ├─ 模型返回 tool_calls？ ── 是 → 执行工具（内部走本地 Service）
 *  │                              → 把结果以 role=tool 追加 → 再次调用模型
 *  └─ 否 → 直接返回自然语言
 *  └─ 全程落库：会话、用户消息、助手消息、每次工具调用的入参/结果/耗时
 * </pre>
 *
 * <h3>模型选择</h3>
 * <p>配置了 API Key 时用真实模型（{@link com.campus.growth.modules.ai.service.impl.OpenAiCompatibleChatClient}）；
 * 未配置时用本地规则引擎（{@link com.campus.growth.modules.ai.service.impl.RuleBasedChatClient}），
 * 响应里会带 {@code realModel=false}，前端明确提示"离线演示模式"。</p>
 *
 * <h3>工具调用轮次上限</h3>
 * <p>最多 3 轮，防止模型陷入"反复调工具"的死循环（真实项目里这是烧钱大户）。</p>
 */
@Slf4j
@Service
public class AiChatServiceImpl implements AiChatService {

    private static final int MAX_TOOL_ROUNDS = 3;
    private static final String SYSTEM_PROMPT = """
            你是"校园成长权益平台"的智能助手，服务对象是高校学生。
            平台能力：每日签到得积分、做任务得积分、积分排行榜、用积分兑换权益商品、优惠券与兑换码。
            规则：
            1. 涉及商品、积分、券、订单、任务的问题，必须调用工具获取真实数据，禁止编造；
            2. 下单前先确认商品与数量；用户没有明确说要下单时不要调用 createOrder；
            3. 回答简洁、口语化，控制在 120 字以内，不要输出 markdown 表格；
            4. 工具返回 success=false 时，把失败原因用友好语气告诉用户，并给出下一步建议。
            """;

    private final AiChatSessionMapper sessionMapper;
    private final AiChatMessageMapper messageMapper;
    private final ChatModelClient chatModelClient;
    private final AiToolRegistry toolRegistry;
    private final LightTextModelService lightTextModelService;
    private final OrderService orderService;
    private final AiProperties aiProperties;

    public AiChatServiceImpl(AiChatSessionMapper sessionMapper,
                             AiChatMessageMapper messageMapper,
                             @org.springframework.beans.factory.annotation.Qualifier("openAiCompatibleChatClient")
                             ChatModelClient openAiClient,
                             @org.springframework.beans.factory.annotation.Qualifier("ruleBasedChatClient")
                             ChatModelClient ruleBasedClient,
                             AiToolRegistry toolRegistry,
                             LightTextModelService lightTextModelService,
                             OrderService orderService,
                             AiProperties aiProperties) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.toolRegistry = toolRegistry;
        this.lightTextModelService = lightTextModelService;
        this.orderService = orderService;
        this.aiProperties = aiProperties;
        // 配置了 API Key 才用真实大模型，否则退到本地规则引擎。
        // 两个实现都不会"假装"是模型，响应里的 realModel 字段会如实告诉前端。
        this.chatModelClient = StringUtils.hasText(aiProperties.getApiKey()) ? openAiClient : ruleBasedClient;
        log.info("AI 助手初始化完成 模型={} 真实模型={}", chatModelClient.modelName(), chatModelClient.isRealModel());
    }

    @Override
    public AiChatVO chat(AiChatDTO dto) {
        Long userId = UserContext.requireUserId();
        AiChatSession session = resolveSession(userId, dto);
        saveMessage(session.getSessionId(), userId, "user", dto.getMessage(), null, null, null, 0);

        List<ChatModel.Message> messages = new ArrayList<>();
        messages.add(ChatModel.Message.system(SYSTEM_PROMPT));
        messages.addAll(loadHistory(session.getSessionId(), userId));
        messages.add(ChatModel.Message.user(dto.getMessage()));

        AiChatVO vo = new AiChatVO();
        vo.setSessionId(session.getSessionId());
        vo.setModel(chatModelClient.modelName());
        vo.setRealModel(chatModelClient.isRealModel());
        List<AiChatVO.ToolCallVO> toolCallVos = new ArrayList<>();

        long start = System.currentTimeMillis();
        int promptTokens = 0;
        int completionTokens = 0;
        String reply = null;

        for (int round = 1; round <= MAX_TOOL_ROUNDS; round++) {
            ChatModel.Response response;
            try {
                response = chatModelClient.chat(messages, toolRegistry.definitions());
            } catch (Exception e) {
                // 模型不可用：返回明确提示，不返回假内容
                log.error("AI 对话失败 sessionId={}", session.getSessionId(), e);
                throw BizException.of(ErrorCode.AI_NOT_AVAILABLE,
                        "AI 助手暂时不可用：" + e.getMessage());
            }
            promptTokens += response.getPromptTokens();
            completionTokens += response.getCompletionTokens();

            if (!response.hasToolCalls()) {
                reply = response.getContent();
                break;
            }
            // 有工具调用：记录 assistant 的 tool_calls，再逐个执行
            messages.add(ChatModel.Message.assistantWithTools(response.getContent(), response.getToolCalls()));
            for (ChatModel.ToolCall call : response.getToolCalls()) {
                AiToolRegistry.ToolResult result = toolRegistry.execute(call.getName(), call.getArguments());
                messages.add(ChatModel.Message.tool(call.getId(), result.getResultJson()));

                AiChatVO.ToolCallVO callVo = new AiChatVO.ToolCallVO();
                callVo.setName(call.getName());
                callVo.setLabel(labelOf(call.getName()));
                callVo.setArgs(call.getArguments());
                callVo.setResult(JsonUtil.abbreviate(result.getResultJson()));
                callVo.setSuccess(result.isSuccess());
                callVo.setCostMs(result.getCostMs());
                toolCallVos.add(callVo);

                saveMessage(session.getSessionId(), userId, "tool", null, call.getName(),
                        call.getArguments(), result.getResultJson(), (int) result.getCostMs());
            }
            if (round == MAX_TOOL_ROUNDS) {
                reply = "抱歉，我连续调用了多次工具仍未得到结果，请换个说法再试一次。";
                log.warn("AI 工具调用轮次达到上限 sessionId={}", session.getSessionId());
            }
        }

        long cost = System.currentTimeMillis() - start;
        if (!StringUtils.hasText(reply)) {
            reply = "我暂时没有生成有效回复，请再描述一下你的需求。";
        }
        vo.setReply(reply);
        vo.setToolCalls(toolCallVos);
        vo.setPromptTokens(promptTokens);
        vo.setCompletionTokens(completionTokens);
        vo.setCostMs(cost);
        saveMessage(session.getSessionId(), userId, "assistant", reply, null, null, null, (int) cost);

        sessionMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AiChatSession>()
                .eq(AiChatSession::getId, session.getId())
                .set(AiChatSession::getMessageCount, (session.getMessageCount() == null ? 0 : session.getMessageCount()) + 2)
                .set(AiChatSession::getModel, chatModelClient.modelName()));
        return vo;
    }

    @Override
    public List<AiSessionVO> sessions() {
        Long userId = UserContext.requireUserId();
        return sessionMapper.selectList(new LambdaQueryWrapper<AiChatSession>()
                        .eq(AiChatSession::getUserId, userId)
                        .orderByDesc(AiChatSession::getId)
                        .last("limit 50"))
                .stream().map(session -> {
                    AiSessionVO vo = new AiSessionVO();
                    vo.setSessionId(session.getSessionId());
                    vo.setTitle(session.getTitle());
                    vo.setModel(session.getModel());
                    vo.setMessageCount(session.getMessageCount());
                    vo.setCreateTime(session.getCreateTime());
                    vo.setUpdateTime(session.getUpdateTime());
                    return vo;
                }).toList();
    }

    @Override
    public List<AiMessageVO> messages(String sessionId) {
        Long userId = UserContext.requireUserId();
        requireOwnSession(sessionId, userId);
        return messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                        .eq(AiChatMessage::getSessionId, sessionId)
                        .orderByAsc(AiChatMessage::getId)
                        .last("limit 200"))
                .stream().map(message -> {
                    AiMessageVO vo = new AiMessageVO();
                    vo.setId(message.getId());
                    vo.setRole(message.getRole());
                    vo.setContent(message.getContent());
                    vo.setToolName(message.getToolName());
                    vo.setToolArgs(message.getToolArgs());
                    vo.setToolResult(message.getToolResult());
                    vo.setCostMs(message.getCostMs());
                    vo.setCreateTime(message.getCreateTime());
                    return vo;
                }).toList();
    }

    @Override
    public void deleteSession(String sessionId) {
        Long userId = UserContext.requireUserId();
        requireOwnSession(sessionId, userId);
        sessionMapper.delete(new LambdaQueryWrapper<AiChatSession>()
                .eq(AiChatSession::getSessionId, sessionId)
                .eq(AiChatSession::getUserId, userId));
        messageMapper.delete(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getSessionId, sessionId)
                .eq(AiChatMessage::getUserId, userId));
    }

    @Override
    public String orderSummary(String orderNo) {
        return lightTextModelService.generateOrderSummary(orderService.detail(orderNo));
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    private AiChatSession resolveSession(Long userId, AiChatDTO dto) {
        if (StringUtils.hasText(dto.getSessionId())) {
            return requireOwnSession(dto.getSessionId(), userId);
        }
        AiChatSession session = new AiChatSession();
        session.setSessionId(OrderNoGenerator.sessionId());
        session.setUserId(userId);
        session.setTitle(dto.getMessage().length() > 20 ? dto.getMessage().substring(0, 20) : dto.getMessage());
        session.setModel(chatModelClient.modelName());
        session.setMessageCount(0);
        sessionMapper.insert(session);
        return session;
    }

    private AiChatSession requireOwnSession(String sessionId, Long userId) {
        AiChatSession session = sessionMapper.selectOne(new LambdaQueryWrapper<AiChatSession>()
                .eq(AiChatSession::getSessionId, sessionId));
        if (session == null || !session.getUserId().equals(userId)) {
            throw BizException.of(ErrorCode.AI_SESSION_NOT_FOUND);
        }
        return session;
    }

    /** 载入最近 N 轮对话（不含 tool 消息，工具结果已包含在助手的最终回复里） */
    private List<ChatModel.Message> loadHistory(String sessionId, Long userId) {
        List<AiChatMessage> history = messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getSessionId, sessionId)
                .eq(AiChatMessage::getUserId, userId)
                .in(AiChatMessage::getRole, "user", "assistant")
                .orderByDesc(AiChatMessage::getId)
                .last("limit " + (aiProperties.getMaxHistoryRounds() * 2)));
        Collections.reverse(history);
        List<ChatModel.Message> messages = new ArrayList<>(history.size());
        for (AiChatMessage message : history) {
            if ("user".equals(message.getRole())) {
                messages.add(ChatModel.Message.user(message.getContent()));
            } else {
                messages.add(ChatModel.Message.assistant(message.getContent()));
            }
        }
        return messages;
    }

    private void saveMessage(String sessionId, Long userId, String role, String content,
                             String toolName, String toolArgs, String toolResult, int costMs) {
        AiChatMessage message = new AiChatMessage();
        message.setSessionId(sessionId);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setToolName(toolName);
        message.setToolArgs(toolArgs);
        message.setToolResult(toolResult);
        message.setPromptTokens(0);
        message.setCompletionTokens(0);
        message.setCostMs(costMs);
        message.setCreateTime(LocalDateTime.now());
        try {
            messageMapper.insert(message);
        } catch (Exception e) {
            // 消息落库失败不应影响对话本身
            log.error("AI 消息落库失败 sessionId={} role={}", sessionId, role, e);
        }
    }

    private String labelOf(String toolName) {
        return switch (toolName) {
            case "queryBenefits" -> "查询权益商品";
            case "queryPointAccount" -> "查询积分账户";
            case "queryMyCoupons" -> "查询我的优惠券";
            case "queryTaskProgress" -> "查询任务进度";
            case "queryOrders" -> "查询我的订单";
            case "createOrder" -> "下单兑换";
            case "cancelOrder" -> "取消订单";
            default -> toolName;
        };
    }

    /** 供健康检查/调试 */
    public Map<String, Object> modelInfo() {
        return Map.of("model", chatModelClient.modelName(), "realModel", chatModelClient.isRealModel(),
                "lightModelAvailable", lightTextModelService.isLightModelAvailable(),
                "lightModel", lightTextModelService.lightModelName());
    }
}
