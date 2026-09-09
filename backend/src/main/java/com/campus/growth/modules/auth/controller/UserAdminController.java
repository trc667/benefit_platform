package com.campus.growth.modules.auth.controller;

import com.campus.growth.common.annotation.OpLog;
import com.campus.growth.common.result.PageResult;
import com.campus.growth.common.result.Result;
import com.campus.growth.modules.auth.service.UserQueryService;
import com.campus.growth.modules.auth.vo.UserInfoVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端用户接口。
 */
@RestController
@RequestMapping("/api/admin/user")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserQueryService userQueryService;

    @GetMapping("/page")
    public Result<PageResult<UserInfoVO>> page(@RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "10") long size,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) String role,
                                               @RequestParam(required = false) Integer status) {
        return Result.ok(userQueryService.pageUsers(keyword, role, status, page, size));
    }

    /** 启用/禁用账号；禁用会同步清理登录态 */
    @PostMapping("/status")
    @OpLog(module = "用户管理", action = "修改账号状态")
    public Result<Void> status(@RequestBody Map<String, Object> body) {
        userQueryService.updateStatus(Long.valueOf(String.valueOf(body.get("userId"))),
                Integer.valueOf(String.valueOf(body.get("status"))));
        return Result.ok();
    }
}
