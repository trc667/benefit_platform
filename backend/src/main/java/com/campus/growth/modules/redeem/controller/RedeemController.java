package com.campus.growth.modules.redeem.controller;

import com.campus.growth.common.annotation.OpLog;
import com.campus.growth.common.annotation.RateLimit;
import com.campus.growth.common.enums.RateLimitType;
import com.campus.growth.common.result.Result;
import com.campus.growth.modules.redeem.service.RedeemService;
import com.campus.growth.modules.redeem.vo.RedeemResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生端兑换码接口。
 * <p>限流按用户维度，防止脚本暴力枚举兑换码。</p>
 */
@RestController
@RequestMapping("/api/redeem")
@RequiredArgsConstructor
public class RedeemController {

    private final RedeemService redeemService;

    @PostMapping("/exchange")
    @RateLimit(key = "redeem:exchange", qps = 3, type = RateLimitType.DEFAULT,
            message = "兑换太频繁了，请稍后再试")
    @OpLog(module = "兑换码", action = "兑换码兑换", saveParams = false)
    public Result<RedeemResultVO> exchange(@RequestParam String code) {
        return Result.ok(redeemService.exchange(code));
    }
}
