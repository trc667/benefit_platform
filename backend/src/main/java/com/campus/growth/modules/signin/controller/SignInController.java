package com.campus.growth.modules.signin.controller;

import com.campus.growth.common.annotation.OpLog;
import com.campus.growth.common.annotation.RateLimit;
import com.campus.growth.common.enums.RateLimitType;
import com.campus.growth.common.result.Result;
import com.campus.growth.modules.signin.service.SignInService;
import com.campus.growth.modules.signin.vo.SignInCalendarVO;
import com.campus.growth.modules.signin.vo.SignInResultVO;
import com.campus.growth.modules.signin.vo.SignInStatVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 签到接口。
 *
 * <p>限流策略：签到是典型的瞬时高峰接口，这里用 Guava 单机令牌桶挡掉连点，
 * 集群部署时把 {@code campus.limit.strategy} 改成 REDIS 即可共享配额。</p>
 */
@RestController
@RequestMapping("/api/signin")
@RequiredArgsConstructor
public class SignInController {

    private final SignInService signInService;

    /** 执行签到 */
    @PostMapping("/do")
    @RateLimit(key = "signin", qps = 5, type = RateLimitType.DEFAULT)
    @OpLog(module = "签到", action = "每日签到", saveParams = false)
    public Result<SignInResultVO> signIn(@RequestParam(defaultValue = "APP") String source) {
        return Result.ok(signInService.signIn(source));
    }

    /** 月度签到日历（默认当月） */
    @GetMapping("/calendar")
    public Result<SignInCalendarVO> calendar(@RequestParam(required = false) String month) {
        return Result.ok(signInService.calendar(month));
    }

    /** 签到概览 */
    @GetMapping("/stat")
    public Result<SignInStatVO> stat() {
        return Result.ok(signInService.stat());
    }
}
