package com.campus.growth.modules.task.service;

import com.campus.growth.modules.task.vo.UserTaskVO;

import java.util.List;

/**
 * 任务服务。
 */
public interface TaskService {

    /** 当前用户的任务列表（含实时进度） */
    List<UserTaskVO> listMine();

    /**
     * 上报任务进度。
     *
     * @param taskCode 任务编码
     * @param delta    增量，必须为正
     * @return 上报后的任务状态
     */
    UserTaskVO reportProgress(String taskCode, int delta);

    /**
     * 供其他业务域本地调用（签到成功、订单支付等），失败只记日志不抛异常。
     * <p>单体内部一律本地方法调用，不走 Dubbo/RPC。</p>
     */
    void reportProgressQuietly(Long userId, String taskCode, int delta);

    /**
     * 领取任务奖励。
     *
     * @return 本次获得的积分
     */
    int claimReward(String taskCode);

    // ---------------- 管理端 ----------------

    com.campus.growth.common.result.PageResult<com.campus.growth.modules.task.entity.TaskDefinition>
    pageForAdmin(String keyword, Integer status, long page, long size);

    Long save(com.campus.growth.modules.task.dto.TaskSaveDTO dto);

    void updateStatus(Long id, Integer status);
}
