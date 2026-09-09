package com.campus.growth.modules.point.service;

import com.campus.growth.common.enums.PointBizType;
import com.campus.growth.common.result.PageResult;
import com.campus.growth.modules.point.entity.UserPointAccount;
import com.campus.growth.modules.point.vo.PointAccountVO;
import com.campus.growth.modules.point.vo.PointRecordVO;

/**
 * 积分服务（本地 Service，单体唯一入口）。
 */
public interface PointService {

    /** 获取账户，不存在则初始化（注册用户时也会调用） */
    UserPointAccount getOrCreateAccount(Long userId);

    /** 查询账户，不存在返回 null */
    UserPointAccount getAccount(Long userId);

    /** 账户视图（含等级换算） */
    PointAccountVO getAccountView(Long userId);

    /** 当前可用余额，账户不存在返回 0 */
    int balanceOf(Long userId);

    /**
     * 增加积分（幂等）。
     *
     * @param userId   用户
     * @param bizType  业务类型
     * @param bizNo    业务单号（幂等键的一部分）
     * @param points   正数
     * @param remark   备注
     * @return true = 本次真正入账；false = 该业务单号已入账过（重复消费）
     */
    boolean addPoint(Long userId, PointBizType bizType, String bizNo, int points, String remark);

    /**
     * 扣减积分（幂等，余额不足抛 {@code POINT_NOT_ENOUGH}）。
     *
     * @param points 正数，内部取负
     * @return true = 本次真正扣减
     */
    boolean deductPoint(Long userId, PointBizType bizType, String bizNo, int points, String remark);

    /**
     * 运营手动调整积分（幂等）。
     *
     * <p>与加/扣分走同一套乐观锁 + 流水逻辑，只是 bizType 固定为
     * {@link PointBizType#ADMIN}，便于对账时把人工操作单独筛出来。</p>
     *
     * @param changePoint 正数加分、负数扣分（不能为 0）
     * @param bizNo       幂等键，同一个 bizNo 重复提交不会重复入账
     * @return true = 本次真正调整；false = 该 bizNo 已处理过
     */
    boolean adjustPoint(Long userId, String bizNo, int changePoint, String reason);

    /** 流水分页 */
    PageResult<PointRecordVO> pageRecords(Long userId, String bizType, long page, long size);
}
