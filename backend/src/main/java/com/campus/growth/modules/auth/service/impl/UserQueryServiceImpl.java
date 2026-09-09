package com.campus.growth.modules.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.growth.common.constant.RedisKeyConst;
import com.campus.growth.common.result.PageResult;
import com.campus.growth.modules.auth.entity.SysUser;
import com.campus.growth.modules.auth.mapper.SysUserMapper;
import com.campus.growth.modules.auth.service.UserQueryService;
import com.campus.growth.modules.auth.vo.UserBriefVO;
import com.campus.growth.modules.auth.vo.UserInfoVO;
import com.campus.growth.modules.point.entity.UserPointAccount;
import com.campus.growth.modules.point.service.PointService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 用户查询服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserQueryServiceImpl implements UserQueryService {

    private final SysUserMapper userMapper;
    private final PointService pointService;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public SysUser getById(Long userId) {
        return userId == null ? null : userMapper.selectById(userId);
    }

    @Override
    public List<UserBriefVO> listBrief(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        List<SysUser> users = userMapper.selectBatchIds(userIds);
        return users.stream().map(u -> {
            UserBriefVO vo = new UserBriefVO();
            vo.setId(u.getId());
            vo.setUsername(u.getUsername());
            vo.setNickname(u.getNickname());
            vo.setAvatar(u.getAvatar());
            return vo;
        }).toList();
    }

    @Override
    public UserInfoVO toUserInfo(Long userId) {
        SysUser user = getById(userId);
        if (user == null) {
            return null;
        }
        UserInfoVO vo = new UserInfoVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setPhone(user.getPhone());
        vo.setStudentNo(user.getStudentNo());
        vo.setSchool(user.getSchool());
        vo.setRole(user.getRole());
        vo.setGrowthLevel(user.getGrowthLevel());
        vo.setStatus(user.getStatus());
        UserPointAccount account = pointService.getAccount(userId);
        vo.setBalance(account == null ? 0 : account.getBalance());
        vo.setTotalEarned(account == null ? 0 : account.getTotalEarned());
        return vo;
    }

    @Override
    public PageResult<UserInfoVO> pageUsers(String keyword, String role, Integer status, long page, long size) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(role != null && !role.isBlank(), SysUser::getRole, role)
                .eq(status != null, SysUser::getStatus, status)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(SysUser::getUsername, keyword).or()
                        .like(SysUser::getNickname, keyword).or()
                        .like(SysUser::getStudentNo, keyword))
                .orderByDesc(SysUser::getId);
        Page<SysUser> result = userMapper.selectPage(Page.of(page, size), wrapper);
        List<UserInfoVO> records = result.getRecords().stream().map(u -> {
            UserInfoVO vo = toUserInfo(u.getId());
            return vo == null ? new UserInfoVO() : vo;
        }).toList();
        return PageResult.of(records, result.getTotal(), page, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long userId, Integer status) {
        SysUser update = new SysUser();
        update.setId(userId);
        update.setStatus(status);
        userMapper.updateById(update);
        if (status != null && status == 0) {
            // 禁用账号时清掉该用户所有登录态，避免已签发的 token 继续可用
            Set<String> keys = stringRedisTemplate.keys(RedisKeyConst.PREFIX + "token:" + userId + ":*");
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
                log.info("禁用用户 {} 并清理登录态 {} 个", userId, keys.size());
            }
        }
    }

    @Override
    public long countAll() {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getRole, "STUDENT"));
        return count == null ? 0 : count;
    }

    @Override
    public List<Long> recentStudentIds(int limit) {
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .select(SysUser::getId)
                        .eq(SysUser::getRole, "STUDENT")
                        .eq(SysUser::getStatus, 1)
                        .orderByDesc(SysUser::getId)
                        .last("limit " + Math.max(1, Math.min(limit, 500))))
                .stream().map(SysUser::getId).toList();
    }
}
