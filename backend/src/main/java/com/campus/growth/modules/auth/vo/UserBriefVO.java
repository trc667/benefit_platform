package com.campus.growth.modules.auth.vo;

import lombok.Data;

/**
 * 用户简要信息（跨模块使用，避免各模块直接依赖 sys_user 表）。
 */
@Data
public class UserBriefVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
}
