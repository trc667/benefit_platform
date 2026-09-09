package com.campus.growth.modules.signin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.growth.modules.signin.entity.SignInRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 签到流水 Mapper。
 */
@Mapper
public interface SignInRecordMapper extends BaseMapper<SignInRecord> {
}
