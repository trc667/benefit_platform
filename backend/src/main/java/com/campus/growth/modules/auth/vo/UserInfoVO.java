package com.campus.growth.modules.auth.vo;

import lombok.Data;

/**
 * 登录用户信息（不含密码）。
 */
@Data
public class UserInfoVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String phone;
    private String studentNo;
    private String school;
    private String role;
    private Integer growthLevel;
    private Integer status;
    /** 积分余额（来自积分模块） */
    private Integer balance;
    private Integer totalEarned;
}
