package com.campus.growth.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 学生注册请求。
 */
@Data
public class RegisterDTO {

    @NotBlank(message = "请输入账号")
    @Pattern(regexp = "^[a-zA-Z0-9_]{4,20}$", message = "账号为 4-20 位字母、数字或下划线")
    private String username;

    @NotBlank(message = "请输入密码")
    @Size(min = 6, max = 32, message = "密码长度需在 6-32 位之间")
    private String password;

    @NotBlank(message = "请输入昵称")
    @Size(max = 20, message = "昵称不能超过 20 个字")
    private String nickname;

    @Size(max = 32, message = "学号不能超过 32 位")
    private String studentNo;

    @Size(max = 64, message = "学校名称不能超过 64 个字")
    private String school;

    /** 邀请码：仅当 campus.auth.register.mode=INVITE 时必填 */
    @Size(max = 32, message = "邀请码不能超过 32 位")
    private String inviteCode;
}
