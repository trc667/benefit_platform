package com.campus.growth.modules.signin.service;

import com.campus.growth.modules.signin.vo.SignInCalendarVO;
import com.campus.growth.modules.signin.vo.SignInResultVO;
import com.campus.growth.modules.signin.vo.SignInStatVO;

/**
 * 签到服务。
 */
public interface SignInService {

    /** 执行签到（幂等：重复签到抛 {@code SIGNIN_ALREADY}） */
    SignInResultVO signIn(String source);

    /** 月度签到日历 */
    SignInCalendarVO calendar(String month);

    /** 签到概览 */
    SignInStatVO stat();

    /** 指定用户某天是否已签到（供任务模块判断"每日签到"任务进度） */
    boolean isSigned(Long userId, java.time.LocalDate date);

    /** 今日签到人数（仪表盘） */
    long countByDate(java.time.LocalDate date);

    /** 近 N 天签到趋势 */
    java.util.List<com.campus.growth.common.vo.TrendVO> trend(int days);
}
