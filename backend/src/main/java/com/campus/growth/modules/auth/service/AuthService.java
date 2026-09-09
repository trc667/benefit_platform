package com.campus.growth.modules.auth.service;

import com.campus.growth.modules.auth.dto.LoginDTO;
import com.campus.growth.modules.auth.dto.RegisterDTO;
import com.campus.growth.modules.auth.vo.LoginVO;
import com.campus.growth.modules.auth.vo.UserInfoVO;

/**
 * 认证服务。
 */
public interface AuthService {

    /** 登录 */
    LoginVO login(LoginDTO dto);

    /** 学生注册（自动初始化积分账户） */
    LoginVO register(RegisterDTO dto);

    /** 退出登录（清除 Redis 白名单） */
    void logout();

    /** 当前登录用户信息 */
    UserInfoVO currentUser();

    /** 修改个人资料（补齐学校/学号后会联动"完善个人资料"任务） */
    UserInfoVO updateProfile(com.campus.growth.modules.auth.dto.ProfileUpdateDTO dto);
}
