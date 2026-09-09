package com.campus.growth.modules.auth.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 个人资料修改。
 */
@Data
public class ProfileUpdateDTO {

    @Size(max = 20, message = "昵称不能超过 20 个字")
    private String nickname;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Size(max = 32, message = "学号不能超过 32 位")
    private String studentNo;

    @Size(max = 64, message = "学校名称不能超过 64 个字")
    private String school;

    @Size(max = 255, message = "头像地址不能超过 255 个字符")
    private String avatar;
}
