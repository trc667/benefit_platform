package com.campus.growth.modules.coupon.controller;

import com.campus.growth.common.annotation.OpLog;
import com.campus.growth.common.result.PageResult;
import com.campus.growth.common.result.Result;
import com.campus.growth.modules.coupon.dto.CouponGrantDTO;
import com.campus.growth.modules.coupon.dto.CouponSaveDTO;
import com.campus.growth.modules.coupon.service.CouponService;
import com.campus.growth.modules.coupon.service.LockOrderDemoService;
import com.campus.growth.modules.coupon.vo.CouponStatVO;
import com.campus.growth.modules.coupon.vo.CouponTemplateVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理端优惠券接口。
 */
@RestController
@RequestMapping("/api/admin/coupon")
@RequiredArgsConstructor
public class CouponAdminController {

    private final CouponService couponService;
    private final LockOrderDemoService lockOrderDemoService;

    @GetMapping("/template/page")
    public Result<PageResult<CouponTemplateVO>> page(@RequestParam(defaultValue = "1") long page,
                                                     @RequestParam(defaultValue = "10") long size,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) Integer status) {
        return Result.ok(couponService.pageForAdmin(keyword, status, page, size));
    }

    @PostMapping("/template/save")
    @OpLog(module = "优惠券", action = "保存券模板")
    public Result<Map<String, Object>> save(@Valid @RequestBody CouponSaveDTO dto) {
        return Result.ok(Map.of("id", couponService.saveTemplate(dto)));
    }

    @PostMapping("/template/status")
    @OpLog(module = "优惠券", action = "修改券模板状态")
    public Result<Void> status(@RequestBody CouponSaveDTO dto) {
        couponService.updateStatus(dto.getId(), dto.getStatus());
        return Result.ok();
    }

    /** 定向发放 */
    @PostMapping("/grant")
    @OpLog(module = "优惠券", action = "定向发券")
    public Result<Map<String, Object>> grant(@Valid @RequestBody CouponGrantDTO dto) {
        int success = couponService.grant(dto.getTemplateId(), dto.getUserIds(), dto.getCount());
        return Result.ok(Map.of("success", success));
    }

    /** 券发放/核销统计 */
    @GetMapping("/stat")
    public Result<List<CouponStatVO>> stat() {
        return Result.ok(couponService.stat());
    }

    /**
     * 立即执行券过期处理（等价于定时任务跑一轮）。
     * <p>定时任务每 10 分钟一次，运营做活动下线或需要立刻对齐状态时可手动触发。</p>
     */
    @PostMapping("/expire-now")
    @OpLog(module = "优惠券", action = "手动执行券过期")
    public Result<Map<String, Object>> expireNow() {
        int count = couponService.expireOverdueCoupons(1000);
        return Result.ok(Map.of("expiredCount", count));
    }

    /**
     * 锁顺序对比实验：复现"锁在事务内导致超发"，并验证正确顺序下的结果。
     * <p>用于演示与回归，不涉及真实数据。</p>
     */
    @PostMapping("/demo/lock-order")
    public Result<LockOrderDemoService.DemoResult> lockOrderDemo(@RequestParam(defaultValue = "10") int stock,
                                                                 @RequestParam(defaultValue = "50") int threads) {
        return Result.ok(lockOrderDemoService.run(stock, threads));
    }
}
