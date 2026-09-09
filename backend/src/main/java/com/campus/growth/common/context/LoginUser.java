package com.campus.growth.common.context;

import lombok.Data;

import java.io.Serializable;

/**
 * 登录用户快照。由网关/拦截器从 JWT 解析后放入 {@link UserContext}。
 */
@Data
public class LoginUser implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private String username;
    private String nickname;
    private String role;
    /** JWT 的 jti，用于 Redis 白名单校验（支持主动踢下线） */
    private String tokenId;

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    public boolean isOperator() {
        return "OPERATOR".equals(role) || isAdmin();
    }
}
