package com.campus.growth.modules.system.controller;

import com.campus.growth.common.result.Result;
import com.campus.growth.common.vo.TrendVO;
import com.campus.growth.modules.auth.service.UserQueryService;
import com.campus.growth.modules.benefit.service.BenefitService;
import com.campus.growth.modules.coupon.service.CouponService;
import com.campus.growth.modules.order.service.OrderService;
import com.campus.growth.modules.signin.service.SignInService;
import com.campus.growth.modules.system.vo.DashboardVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 管理端仪表盘接口。
 * <p>只做聚合，不写业务逻辑；每个指标都由对应域的服务提供，
 * 避免 Dashboard 直接跨模块查表。</p>
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final UserQueryService userQueryService;
    private final SignInService signInService;
    private final OrderService orderService;
    private final CouponService couponService;
    private final BenefitService benefitService;

    @GetMapping("/overview")
    public Result<DashboardVO> overview() {
        DashboardVO vo = new DashboardVO();
        vo.setUserCount(userQueryService.countAll());
        vo.setTodaySignCount(signInService.countByDate(LocalDate.now()));
        vo.setOrderCount(orderService.countAll());
        vo.setTodayOrderCount(orderService.countToday());
        vo.setPointUsed(orderService.sumPaidPoints());
        vo.setCouponIssued(couponService.countIssued());
        vo.setGoodsCount(benefitService.countAll());
        vo.setRefundPending(orderService.countPendingRefund());
        return Result.ok(vo);
    }

    @GetMapping("/signin-trend")
    public Result<List<TrendVO>> signinTrend(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(signInService.trend(days));
    }

    @GetMapping("/order-trend")
    public Result<List<TrendVO>> orderTrend(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(orderService.orderTrend(days));
    }
}
