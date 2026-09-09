package com.campus.growth.modules.order.controller;

import com.campus.growth.common.annotation.OpLog;
import com.campus.growth.common.result.PageResult;
import com.campus.growth.common.result.Result;
import com.campus.growth.modules.order.dto.RefundHandleDTO;
import com.campus.growth.modules.order.service.OrderService;
import com.campus.growth.modules.order.vo.OrderVO;
import com.campus.growth.modules.order.vo.RefundVO;
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
 * 管理端订单与售后接口。
 */
@RestController
@RequestMapping("/api/admin/order")
@RequiredArgsConstructor
public class OrderAdminController {

    private final OrderService orderService;

    @GetMapping("/page")
    public Result<PageResult<OrderVO>> page(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) String orderNo,
                                            @RequestParam(required = false) String status) {
        return Result.ok(orderService.pageAdmin(orderNo, status, page, size));
    }

    /** 管理端订单详情：运营需要查看任意用户的订单（学生端接口有属主校验，不能用） */
    @GetMapping("/{orderNo}")
    public Result<OrderVO> detail(@PathVariable String orderNo) {
        return Result.ok(orderService.detailForAdmin(orderNo));
    }

    /** 运营核销订单：PAID → FINISHED */
    @PostMapping("/finish")
    @OpLog(module = "订单", action = "运营核销订单")
    public Result<Void> finish(@RequestBody Map<String, String> body) {
        orderService.finishByAdmin(body.get("orderNo"));
        return Result.ok();
    }

    @GetMapping("/refund/page")
    public Result<PageResult<RefundVO>> refundPage(@RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "10") long size,
                                                   @RequestParam(required = false) String status) {
        return Result.ok(orderService.pageRefunds(status, page, size));
    }

    @PostMapping("/refund/handle")
    @OpLog(module = "订单", action = "处理售后")
    public Result<Void> handle(@Valid @RequestBody RefundHandleDTO dto) {
        orderService.handleRefund(dto);
        return Result.ok();
    }
}
