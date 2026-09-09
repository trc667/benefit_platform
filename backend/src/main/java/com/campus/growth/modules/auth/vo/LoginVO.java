package com.campus.growth.modules.auth.vo;

import lombok.Data;

/**
 * 登录结果。
 */
@Data
public class LoginVO {

    private String token;
    /** token 过期时间戳（毫秒），前端可据此提前续期 */
    private Long expireAt;
    private UserInfoVO userInfo;
}
