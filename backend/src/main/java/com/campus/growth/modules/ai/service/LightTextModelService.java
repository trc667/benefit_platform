package com.campus.growth.modules.ai.service;

import com.campus.growth.config.AiProperties;
import com.campus.growth.modules.order.vo.OrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 轻量模型文案服务（HTTP 直连 + 超时降级）。
 *
 * <h3>为什么"订单清单文案"要单独走轻量模型</h3>
 * <p>下单成功后给用户一段"清单文案"属于锦上添花的能力，如果和主模型共用一条 30s 超时的通道，
 * 一旦模型排队，用户就要盯着 loading 等。这里的做法是：</p>
 * <ol>
 *   <li>用独立的 {@code lightModelClient}（8s 超时、max_tokens=256、独立 API Key 可配更便宜的模型）；</li>
 *   <li>提交到独立的 {@code aiTextExecutor} 线程池，主线程最多等 8s；</li>
 *   <li>超时/失败直接返回结构化模板文案——用户永远拿得到结果，只是少了点"文采"。</li>
 * </ol>
 *
 * <p>这就是"大模型工程化"最朴素也最有效的一步：把 AI 能力放在主链路之外，可降级、可超时、可观测。</p>
 */
@Slf4j
@Service
public class LightTextModelService {

    private final RestClient lightModelClient;
    private final AiProperties properties;
    private final ThreadPoolTaskExecutor aiTextExecutor;

    public LightTextModelService(@Qualifier("lightModelClient") RestClient lightModelClient,
                                 AiProperties properties,
                                 @Qualifier("aiTextExecutor") ThreadPoolTaskExecutor aiTextExecutor) {
        this.lightModelClient = lightModelClient;
        this.properties = properties;
        this.aiTextExecutor = aiTextExecutor;
    }

    /**
     * 生成订单清单文案。
     *
     * @return 模型生成或模板降级的文案
     */
    public String generateOrderSummary(OrderVO order) {
        String fallback = template(order);
        AiProperties.LightModel light = properties.getLightModel();
        if (!properties.isEnabled() || !light.isEnabled() || !StringUtils.hasText(light.getApiKey())) {
            log.debug("轻量模型未配置，使用模板文案");
            return fallback;
        }
        try {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(
                    () -> callModel(order), aiTextExecutor);
            return future.get(light.getTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("轻量模型文案生成失败，降级为模板：{}", e.getMessage());
            return fallback;
        }
    }

    private String callModel(OrderVO order) {
        String prompt = "请用 60 字以内、亲切自然的中文，为用户生成一条校园权益兑换成功的订单清单文案。"
                + "包含商品、数量、实付积分、优惠金额，不要用 markdown，不要编造订单号之外的信息。\n"
                + "商品：" + order.getItems().stream()
                .map(i -> i.getGoodsTitle() + " x" + i.getQuantity())
                .collect(Collectors.joining("、"))
                + "\n订单号：" + order.getOrderNo()
                + "\n商品总额：" + order.getGoodsTotalPoint()
                + "\n优惠：" + order.getDiscountPoint()
                + "\n实付积分：" + order.getPayPoint();

        var body = com.campus.growth.common.util.JsonUtil.mapper().createObjectNode();
        body.put("model", properties.getLightModel().getModel());
        body.put("max_tokens", properties.getLightModel().getMaxTokens());
        body.put("temperature", 0.6);
        body.set("messages", com.campus.growth.common.util.JsonUtil.mapper().createArrayNode()
                .add(com.campus.growth.common.util.JsonUtil.mapper().createObjectNode()
                        .put("role", "user").put("content", prompt)));

        String raw = lightModelClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + properties.getLightModel().getApiKey())
                .header("Content-Type", "application/json")
                .body(body.toString())
                .retrieve()
                .body(String.class);
        var root = com.campus.growth.common.util.JsonUtil.parse(raw, com.fasterxml.jackson.databind.JsonNode.class);
        if (root == null) {
            throw new IllegalStateException("轻量模型返回为空");
        }
        String content = root.path("choices").path(0).path("message").path("content").asText(null);
        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("轻量模型未返回内容");
        }
        log.info("轻量模型文案生成成功 model={}", properties.getLightModel().getModel());
        return content.trim();
    }

    /** 模板降级文案：结构化、无歧义 */
    private String template(OrderVO order) {
        String goods = order.getItems().stream()
                .map(i -> i.getGoodsTitle() + " x" + i.getQuantity())
                .collect(Collectors.joining("、"));
        StringBuilder sb = new StringBuilder();
        sb.append("兑换成功！").append(goods)
                .append("，商品总额 ").append(order.getGoodsTotalPoint()).append(" 积分");
        if (order.getDiscountPoint() != null && order.getDiscountPoint() > 0) {
            sb.append("，已优惠 ").append(order.getDiscountPoint()).append(" 积分");
        }
        sb.append("，实付 ").append(order.getPayPoint()).append(" 积分。")
                .append("订单号 ").append(order.getOrderNo());
        // 文案要与订单真实状态一致，不能所有订单都提示"请及时支付"
        if ("PAID".equals(order.getStatus())) {
            sb.append("，已支付完成，请凭订单到服务点核销。");
        } else if ("CREATED".equals(order.getStatus())) {
            sb.append("，请及时完成支付。");
        } else {
            sb.append("，当前状态：").append(order.getStatusDesc()).append("。");
        }
        return sb.toString();
    }

    /** 供调试：返回当前轻量模型是否启用 */
    public boolean isLightModelAvailable() {
        return properties.isEnabled() && properties.getLightModel().isEnabled()
                && StringUtils.hasText(properties.getLightModel().getApiKey());
    }

    /** 暴露给健康检查的模型名 */
    public String lightModelName() {
        return properties.getLightModel().getModel();
    }
}
