package com.campus.growth.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录请求。
 */
@Data
public class LoginDTO {

    @NotBlank(message = "请输入账号")
    @Size(max = 64, message = "账号长度不能超过 64")
    private String username;

    @NotBlank(message = "请输入密码")
    @Size(min = 6, max = 32, message = "密码长度需在 6-32 位之间")
    private String password;
}
