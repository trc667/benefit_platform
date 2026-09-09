package com.campus.growth.modules.auth.service;

import com.campus.growth.common.result.PageResult;
import com.campus.growth.modules.auth.entity.SysUser;
import com.campus.growth.modules.auth.vo.UserBriefVO;
import com.campus.growth.modules.auth.vo.UserInfoVO;

import java.util.Collection;
import java.util.List;

/**
 * 用户查询服务。
 * <p>跨模块只依赖本接口，不直接访问 {@code sys_user} 表，保证模块边界清晰。</p>
 */
public interface UserQueryService {

    SysUser getById(Long userId);

    /** 批量取昵称/头像，用于排行榜、订单列表等展示 */
    List<UserBriefVO> listBrief(Collection<Long> userIds);

    /** 转成前端可用的用户信息（含积分） */
    UserInfoVO toUserInfo(Long userId);

    /** 管理端分页查询 */
    PageResult<UserInfoVO> pageUsers(String keyword, String role, Integer status, long page, long size);

    /** 启用/禁用账号（禁用会同时清掉登录态） */
    void updateStatus(Long userId, Integer status);

    long countAll();

    /** 最近注册的学生 ID（管理端"随机发放"用，避免全表扫描） */
    List<Long> recentStudentIds(int limit);
}
