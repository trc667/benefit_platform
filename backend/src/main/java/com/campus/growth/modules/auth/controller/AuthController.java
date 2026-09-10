package com.campus.growth.modules.auth.controller;

import com.campus.growth.common.annotation.OpLog;
import com.campus.growth.common.annotation.RateLimit;
import com.campus.growth.common.enums.RateLimitType;
import com.campus.growth.common.result.Result;
import com.campus.growth.common.util.WebUtil;
import com.campus.growth.infra.risk.RiskControlService;
import com.campus.growth.modules.auth.dto.LoginDTO;
import com.campus.growth.modules.auth.dto.RegisterDTO;
import com.campus.growth.modules.auth.service.AuthService;
import com.campus.growth.modules.auth.service.RegisterPolicy;
import com.campus.growth.modules.auth.vo.LoginVO;
import com.campus.growth.modules.auth.vo.UserInfoVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 认证接口。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RegisterPolicy registerPolicy;
    private final RiskControlService riskControlService;
    private final HttpServletRequest request;

    /** 登录：按 IP 限流，防止撞库（账号维度失败锁定见 AuthServiceImpl） */
    @PostMapping("/login")
    @RateLimit(key = "auth:login", qps = 5, byUser = false, type = RateLimitType.LOCAL)
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.ok(authService.login(dto));
    }

    /**
     * 注册配置（公开接口）：前端据此决定要不要渲染"邀请码 / 学校"。
     * <p>不暴露邀请码本身，只暴露"是否需要邀请码"。</p>
     */
    @GetMapping("/register-config")
    public Result<Map<String, Object>> registerConfig() {
        return Result.ok(Map.of(
                "mode", registerPolicy.getMode(),
                "needInviteCode", registerPolicy.needInviteCode(),
                "needSchool", registerPolicy.needSchool(),
                "schools", registerPolicy.schoolList(),
                "studentNoPattern", registerPolicy.getStudentNoPattern()
        ));
    }

    /** 注册：按 IP 限流 + 设备/IP 风控（防批量开小号） */
    @PostMapping("/register")
    @RateLimit(key = "auth:register", qps = 2, byUser = false, type = RateLimitType.LOCAL)
    @OpLog(module = "认证", action = "学生注册", saveParams = false)
    public Result<LoginVO> register(@Valid @RequestBody RegisterDTO dto) {
        String clientIp = WebUtil.getIp(request);
        riskControlService.assertRegisterAllowed(clientIp);
        LoginVO vo = authService.register(dto);
        riskControlService.markRegister(clientIp);
        return Result.ok(vo);
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.ok();
    }

    @GetMapping("/me")
    public Result<UserInfoVO> me() {
        return Result.ok(authService.currentUser());
    }

    /** 修改个人资料 */
    @org.springframework.web.bind.annotation.PutMapping("/profile")
    @OpLog(module = "认证", action = "修改个人资料")
    public Result<UserInfoVO> updateProfile(
            @Valid @RequestBody com.campus.growth.modules.auth.dto.ProfileUpdateDTO dto) {
        return Result.ok(authService.updateProfile(dto));
    }
}
