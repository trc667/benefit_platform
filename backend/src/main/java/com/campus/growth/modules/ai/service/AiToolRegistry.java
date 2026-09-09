package com.campus.growth.modules.ai.service;

import com.campus.growth.common.util.JsonUtil;
import com.campus.growth.modules.ai.model.ChatModel;
import com.campus.growth.modules.benefit.service.BenefitService;
import com.campus.growth.modules.benefit.vo.GoodsVO;
import com.campus.growth.modules.coupon.service.CouponService;
import com.campus.growth.modules.coupon.vo.UserCouponVO;
import com.campus.growth.modules.order.dto.OrderCreateDTO;
import com.campus.growth.modules.order.service.OrderService;
import com.campus.growth.modules.order.vo.OrderVO;
import com.campus.growth.modules.point.service.PointService;
import com.campus.growth.modules.point.vo.PointAccountVO;
import com.campus.growth.modules.task.service.TaskService;
import com.campus.growth.modules.task.vo.UserTaskVO;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 工具注册中心（Function Calling 的"工具侧"）。
 *
 * <h3>关键设计：工具内部走本地 Service</h3>
 * <p>模型调用 {@code createOrder} 时，最终执行的是 {@link OrderService#create}，
 * 与用户在页面上点"提交订单"走的是同一条链路——同样校验库存、同样扣积分、同样锁券。
 * 绝不能给 AI 单独开一条"后门"，否则权限、风控、审计全都失效。</p>
 *
 * <h3>错误处理</h3>
 * <p>工具执行失败（如积分不足）不抛异常给模型，而是返回
 * {@code {"success":false,"message":"..."}}，让模型用自然语言向用户解释，
 * 这也是 Function Calling 的推荐做法。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiToolRegistry {

    private final BenefitService benefitService;
    private final PointService pointService;
    private final CouponService couponService;
    private final TaskService taskService;
    private final OrderService orderService;

    /** 工具定义（喂给模型的 JSON Schema） */
    public List<ChatModel.ToolDefinition> definitions() {
        return List.of(
                ChatModel.ToolDefinition.of("queryBenefits",
                        "查询校园权益商城里可兑换的商品列表，可按关键词或分类筛选。",
                        """
                        {"type":"object","properties":{
                          "keyword":{"type":"string","description":"商品关键词，如 咖啡、自习"},
                          "category":{"type":"string","description":"分类编码 STUDY/FOOD/LIFE/SPORT/OTHER"}
                        },"required":[]}"""),
                ChatModel.ToolDefinition.of("queryPointAccount",
                        "查询当前用户的积分余额、累计获得与成长等级。",
                        """
                        {"type":"object","properties":{},"required":[]}"""),
                ChatModel.ToolDefinition.of("queryMyCoupons",
                        "查询当前用户的优惠券。",
                        """
                        {"type":"object","properties":{
                          "status":{"type":"string","description":"UNUSED/USED/EXPIRED，不传则查未使用"}
                        },"required":[]}"""),
                ChatModel.ToolDefinition.of("queryTaskProgress",
                        "查询当前用户的每日/每周任务进度与可领取奖励。",
                        """
                        {"type":"object","properties":{},"required":[]}"""),
                ChatModel.ToolDefinition.of("queryOrders",
                        "查询当前用户的订单列表。",
                        """
                        {"type":"object","properties":{
                          "status":{"type":"string","description":"CREATED/PAID/CANCELLED/FINISHED"}
                        },"required":[]}"""),
                ChatModel.ToolDefinition.of("createOrder",
                        "帮用户下单兑换权益商品（会真实扣积分、扣库存，请先与用户确认商品与数量）。",
                        """
                        {"type":"object","properties":{
                          "goodsId":{"type":"integer","description":"商品ID"},
                          "quantity":{"type":"integer","description":"数量，默认1"},
                          "couponIds":{"type":"array","items":{"type":"integer"},"description":"选用的优惠券ID"}
                        },"required":["goodsId"]}"""),
                ChatModel.ToolDefinition.of("cancelOrder",
                        "取消指定订单号的订单（仅待支付订单可取消）。",
                        """
                        {"type":"object","properties":{
                          "orderNo":{"type":"string","description":"订单号"}
                        },"required":["orderNo"]}""")
        );
    }

    /** 执行工具，返回 JSON 字符串（结果会回填给模型） */
    public ToolResult execute(String name, String argsJson) {
        long start = System.currentTimeMillis();
        JsonNode args = JsonUtil.parse(argsJson == null || argsJson.isBlank() ? "{}" : argsJson, JsonNode.class);
        try {
            Object data = switch (name) {
                case "queryBenefits" -> queryBenefits(args);
                case "queryPointAccount" -> queryPointAccount();
                case "queryMyCoupons" -> queryMyCoupons(args);
                case "queryTaskProgress" -> queryTaskProgress();
                case "queryOrders" -> queryOrders(args);
                case "createOrder" -> createOrder(args);
                case "cancelOrder" -> cancelOrder(args);
                default -> throw new IllegalArgumentException("未知工具：" + name);
            };
            return new ToolResult(name, true, JsonUtil.toJson(data),
                    System.currentTimeMillis() - start, null);
        } catch (Exception e) {
            log.warn("AI 工具执行失败 name={} args={} reason={}", name, argsJson, e.getMessage());
            return new ToolResult(name, false,
                    JsonUtil.toJson(Map.of("success", false, "message", e.getMessage() == null ? "执行失败" : e.getMessage())),
                    System.currentTimeMillis() - start, e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // 工具实现
    // ------------------------------------------------------------------

    private Object queryBenefits(JsonNode args) {
        String keyword = args.path("keyword").asText(null);
        String category = args.path("category").asText(null);
        var page = benefitService.pageGoods(category, keyword, "default", 1, 5);
        List<Map<String, Object>> items = page.getRecords().stream().map(goods -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("goodsId", goods.getId());
            item.put("title", goods.getTitle());
            item.put("pricePoint", goods.getPricePoint());
            item.put("stock", goods.getStock());
            item.put("category", goods.getCategoryName());
            return item;
        }).toList();
        return Map.of("total", page.getTotal(), "items", items);
    }

    private Object queryPointAccount() {
        PointAccountVO account = pointService.getAccountView(com.campus.growth.common.context.UserContext.requireUserId());
        return Map.of("balance", account.getBalance(), "totalEarned", account.getTotalEarned(),
                "growthLevel", account.getGrowthLevel(), "nextLevelPoint", account.getNextLevelPoint());
    }

    private Object queryMyCoupons(JsonNode args) {
        String status = args.path("status").asText("UNUSED");
        List<Map<String, Object>> items = couponService
                .pageMine(status, 1, 10).getRecords().stream().map(coupon -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("couponId", coupon.getId());
                    item.put("title", coupon.getCouponTitle());
                    item.put("valueDesc", coupon.getValueDesc());
                    item.put("expireTime", coupon.getExpireTime() == null ? null : coupon.getExpireTime().toString());
                    return item;
                }).toList();
        return Map.of("count", items.size(), "items", items);
    }

    private Object queryTaskProgress() {
        List<Map<String, Object>> items = taskService.listMine().stream().map(task -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("taskCode", task.getTaskCode());
            item.put("taskName", task.getTaskName());
            item.put("progress", task.getProgress());
            item.put("targetValue", task.getTargetValue());
            item.put("status", task.getStatus());
            item.put("pointAward", task.getPointAward());
            return item;
        }).toList();
        return Map.of("items", items);
    }

    private Object queryOrders(JsonNode args) {
        String status = args.path("status").asText(null);
        List<Map<String, Object>> items = orderService.pageMine(status, 1, 5).getRecords().stream()
                .map(order -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("orderNo", order.getOrderNo());
                    item.put("status", order.getStatus());
                    item.put("statusDesc", order.getStatusDesc());
                    item.put("payPoint", order.getPayPoint());
                    item.put("goodsTitle", order.getItems().isEmpty() ? null : order.getItems().get(0).getGoodsTitle());
                    item.put("createTime", order.getCreateTime() == null ? null : order.getCreateTime().toString());
                    return item;
                }).toList();
        return Map.of("count", items.size(), "items", items);
    }

    private Object createOrder(JsonNode args) {
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setGoodsId(args.path("goodsId").asLong());
        dto.setQuantity(args.path("quantity").asInt(1));
        if (args.path("couponIds").isArray()) {
            List<Long> couponIds = new java.util.ArrayList<>();
            args.path("couponIds").forEach(node -> couponIds.add(node.asLong()));
            dto.setCouponIds(couponIds);
        }
        OrderVO order = orderService.create(dto);
        return Map.of("success", true, "orderNo", order.getOrderNo(), "payPoint", order.getPayPoint(),
                "discountPoint", order.getDiscountPoint(), "status", order.getStatus(),
                "hint", "订单已创建，请在结算页确认支付");
    }

    private Object cancelOrder(JsonNode args) {
        String orderNo = args.path("orderNo").asText();
        orderService.cancel(orderNo);
        return Map.of("success", true, "orderNo", orderNo, "message", "订单已取消，库存与优惠券已回滚");
    }

    /** 工具执行结果 */
    @Data
    public static class ToolResult {
        private final String name;
        private final boolean success;
        private final String resultJson;
        private final long costMs;
        private final String error;
    }
}
