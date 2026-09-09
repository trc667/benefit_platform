package com.campus.growth.modules.order.controller;

import com.campus.growth.common.annotation.OpLog;
import com.campus.growth.common.annotation.RateLimit;
import com.campus.growth.common.enums.RateLimitType;
import com.campus.growth.common.result.PageResult;
import com.campus.growth.common.result.Result;
import com.campus.growth.modules.order.dto.OrderCreateDTO;
import com.campus.growth.modules.order.dto.RefundApplyDTO;
import com.campus.growth.modules.order.service.OrderService;
import com.campus.growth.modules.order.vo.OrderVO;
import com.campus.growth.modules.order.vo.RefundVO;
import com.campus.growth.modules.order.vo.SettlePreviewVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 学生端订单接口。
 */
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /** 结算页预览：最优优惠组合 */
    @GetMapping("/settle/preview")
    public Result<SettlePreviewVO> preview(@RequestParam Long goodsId,
                                           @RequestParam(defaultValue = "1") int quantity) {
        return Result.ok(orderService.preview(goodsId, quantity));
    }

    @PostMapping("/create")
    @RateLimit(key = "order:create", qps = 8, type = RateLimitType.DEFAULT)
    @OpLog(module = "订单", action = "创建订单")
    public Result<OrderVO> create(@Valid @RequestBody OrderCreateDTO dto) {
        return Result.ok(orderService.create(dto));
    }

    @PostMapping("/pay")
    @RateLimit(key = "order:pay", qps = 5, type = RateLimitType.DEFAULT)
    @OpLog(module = "订单", action = "支付订单")
    public Result<OrderVO> pay(@RequestBody Map<String, String> body) {
        return Result.ok(orderService.pay(body.get("orderNo")));
    }

    @PostMapping("/cancel")
    @OpLog(module = "订单", action = "取消订单")
    public Result<Void> cancel(@RequestBody Map<String, String> body) {
        orderService.cancel(body.get("orderNo"));
        return Result.ok();
    }

    /** 学生确认完成（收货/核销）：PAID → FINISHED */
    @PostMapping("/finish")
    @OpLog(module = "订单", action = "确认完成订单")
    public Result<Void> finish(@RequestBody Map<String, String> body) {
        orderService.finish(body.get("orderNo"));
        return Result.ok();
    }

    @GetMapping("/page")
    public Result<PageResult<OrderVO>> page(@RequestParam(required = false) String status,
                                            @RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(orderService.pageMine(status, page, size));
    }

    @GetMapping("/{orderNo}")
    public Result<OrderVO> detail(@PathVariable String orderNo) {
        return Result.ok(orderService.detail(orderNo));
    }

    @PostMapping("/refund/apply")
    @OpLog(module = "订单", action = "申请售后")
    public Result<Map<String, Object>> applyRefund(@Valid @RequestBody RefundApplyDTO dto) {
        return Result.ok(Map.of("refundNo", orderService.applyRefund(dto)));
    }

    @GetMapping("/refund/page")
    public Result<PageResult<RefundVO>> refundPage(@RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "10") long size) {
        return Result.ok(orderService.pageMyRefunds(page, size));
    }
}
