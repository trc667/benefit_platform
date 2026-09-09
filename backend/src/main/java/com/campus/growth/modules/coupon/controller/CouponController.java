package com.campus.growth.modules.coupon.controller;

import com.campus.growth.common.annotation.OpLog;
import com.campus.growth.common.annotation.RateLimit;
import com.campus.growth.common.enums.RateLimitType;
import com.campus.growth.common.result.PageResult;
import com.campus.growth.common.result.Result;
import com.campus.growth.common.context.UserContext;
import com.campus.growth.modules.coupon.dto.CouponReceiveDTO;
import com.campus.growth.modules.coupon.service.CouponService;
import com.campus.growth.modules.coupon.vo.CouponTemplateVO;
import com.campus.growth.modules.coupon.vo.UserCouponVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学生端优惠券接口。
 */
@RestController
@RequestMapping("/api/coupon")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    /** 可领取券列表 */
    @GetMapping("/templates")
    public Result<PageResult<CouponTemplateVO>> templates(@RequestParam(defaultValue = "1") long page,
                                                          @RequestParam(defaultValue = "10") long size) {
        return Result.ok(couponService.pageTemplates(page, size));
    }

    /**
     * 领取优惠券。
     * <p>限流 + 分布式锁 + 事务三层保护，防止刷券与超发。</p>
     */
    @PostMapping("/receive")
    @RateLimit(key = "coupon:receive", qps = 10, type = RateLimitType.DEFAULT)
    @OpLog(module = "优惠券", action = "领取优惠券")
    public Result<UserCouponVO> receive(@Valid @RequestBody CouponReceiveDTO dto) {
        return Result.ok(couponService.receive(dto.getTemplateId()));
    }

    /** 我的券 */
    @GetMapping("/mine")
    public Result<PageResult<UserCouponVO>> mine(@RequestParam(required = false) String status,
                                                 @RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "10") long size) {
        return Result.ok(couponService.pageMine(status, page, size));
    }

    /** 结算页可用券 */
    @GetMapping("/available")
    public Result<List<UserCouponVO>> available(@RequestParam(defaultValue = "0") int amount,
                                                @RequestParam(required = false) Long goodsId,
                                                @RequestParam(required = false) String category) {
        return Result.ok(couponService.availableCoupons(UserContext.requireUserId(), amount, goodsId, category));
    }
}
