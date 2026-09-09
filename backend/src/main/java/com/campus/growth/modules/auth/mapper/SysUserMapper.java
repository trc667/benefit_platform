package com.campus.growth.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.growth.modules.auth.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
