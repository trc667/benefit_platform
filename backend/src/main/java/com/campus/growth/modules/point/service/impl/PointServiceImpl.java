package com.campus.growth.modules.point.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.growth.common.constant.BizConst;
import com.campus.growth.common.enums.PointBizType;
import com.campus.growth.common.exception.BizException;
import com.campus.growth.common.result.ErrorCode;
import com.campus.growth.common.result.PageResult;
import com.campus.growth.modules.point.entity.PointRecord;
import com.campus.growth.modules.point.entity.UserPointAccount;
import com.campus.growth.modules.point.mapper.PointRecordMapper;
import com.campus.growth.modules.point.mapper.UserPointAccountMapper;
import com.campus.growth.modules.point.service.PointService;
import com.campus.growth.modules.point.service.RankService;
import com.campus.growth.modules.point.vo.PointAccountVO;
import com.campus.growth.modules.point.vo.PointRecordVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 积分服务实现。
 *
 * <h3>并发与幂等设计</h3>
 * <ol>
 *   <li><b>幂等</b>：先查 {@code point_record} 唯一键，已存在直接返回 false；
 *       即便并发穿透，最后插入时的唯一索引冲突也会让整个事务回滚，不会重复加积分；</li>
 *   <li><b>并发安全</b>：账户行用 {@code @Version} 乐观锁更新，冲突时在事务内重试（最多 3 次），
 *       比悲观锁更适合"同一用户偶发并发"的签到/任务场景；</li>
 *   <li><b>一致性</b>：账户余额与流水在同一个本地事务内提交，账户更新失败则流水一起回滚。</li>
 * </ol>
 *
 * <p>注意：本方法可能被 Kafka 消费者调用（新事务），也可能被签到服务调用（加入已有事务），
 * 两种方式都正确，因为 {@code @Transactional} 默认 REQUIRED 传播行为。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointServiceImpl implements PointService {

    /** 乐观锁冲突重试次数 */
    /** 运营单次手动调整的绝对值上限，防手抖多打一个零 */
    private static final int MAX_ADJUST_POINT = 100_000;

    private final UserPointAccountMapper accountMapper;
    private final PointRecordMapper recordMapper;
    private final RankService rankService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserPointAccount getOrCreateAccount(Long userId) {
        UserPointAccount account = getAccount(userId);
        if (account != null) {
            return account;
        }
        account = new UserPointAccount();
        account.setUserId(userId);
        account.setBalance(0);
        account.setTotalEarned(0);
        account.setTotalUsed(0);
        account.setVersion(0);
        try {
            accountMapper.insert(account);
        } catch (DuplicateKeyException e) {
            // 并发初始化，重新读取即可
            return getAccount(userId);
        }
        return account;
    }

    @Override
    public UserPointAccount getAccount(Long userId) {
        return accountMapper.selectOne(new LambdaQueryWrapper<UserPointAccount>()
                .eq(UserPointAccount::getUserId, userId));
    }

    @Override
    public PointAccountVO getAccountView(Long userId) {
        UserPointAccount account = getOrCreateAccount(userId);
        PointAccountVO vo = new PointAccountVO();
        vo.setUserId(userId);
        vo.setBalance(account.getBalance());
        vo.setTotalEarned(account.getTotalEarned());
        vo.setTotalUsed(account.getTotalUsed());

        int level = account.getTotalEarned() / BizConst.POINT_PER_LEVEL + 1;
        int currentLevelFloor = (level - 1) * BizConst.POINT_PER_LEVEL;
        int progress = (account.getTotalEarned() - currentLevelFloor) * 100 / BizConst.POINT_PER_LEVEL;
        vo.setGrowthLevel(level);
        vo.setNextLevelPoint(level * BizConst.POINT_PER_LEVEL - account.getTotalEarned());
        vo.setLevelProgress(Math.min(100, Math.max(0, progress)));
        return vo;
    }

    @Override
    public int balanceOf(Long userId) {
        UserPointAccount account = getAccount(userId);
        return account == null ? 0 : account.getBalance();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addPoint(Long userId, PointBizType bizType, String bizNo, int points, String remark) {
        if (points <= 0) {
            throw new IllegalArgumentException("加分数值必须为正");
        }
        return changePoint(userId, bizType, bizNo, points, remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deductPoint(Long userId, PointBizType bizType, String bizNo, int points, String remark) {
        if (points <= 0) {
            throw new IllegalArgumentException("扣减数值必须为正");
        }
        return changePoint(userId, bizType, bizNo, -points, remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean adjustPoint(Long userId, String bizNo, int changePoint, String reason) {
        if (changePoint == 0) {
            throw BizException.of(ErrorCode.PARAM_ERROR, "调整分值不能为 0");
        }
        if (Math.abs(changePoint) > MAX_ADJUST_POINT) {
            throw BizException.of(ErrorCode.PARAM_ERROR,
                    "单次调整绝对值不能超过 " + MAX_ADJUST_POINT);
        }
        return changePoint(userId, PointBizType.ADMIN, bizNo, changePoint, reason);
    }

    /**
     * 积分变动统一入口。
     *
     * <h3>为什么不是"读-改-写 + 乐观锁"</h3>
     * <p>最早这里用的是「查账户 → 算新余额 → 带 version 条件更新 → 冲突重试」。
     * 逻辑上没错，但**同一账户并发写**时（同一用户狂点兑换码、或补发奖励任务重跑）
     * 乐观锁会连环冲突：压测 60 并发写同一账户，只有 3 次成功（5%），
     * 其余全被重试上限挡下并抛出"积分更新冲突过于频繁"。</p>
     *
     * <p>改成数据库原子增减：一条 {@code UPDATE ... SET balance = balance + ?} 完成，
     * 并发请求在行锁上排队（每个事务亚毫秒级），不再有"重试风暴"。
     * 余额不足的判定也下推到 SQL（{@code balance + ? >= 0}），天然防止扣成负数。</p>
     *
     * @param delta 正数加分，负数扣分
     */
    private boolean changePoint(Long userId, PointBizType bizType, String bizNo, int delta, String remark) {
        // 1. 幂等前置检查（快路径）
        Long exists = recordMapper.selectCount(new LambdaQueryWrapper<PointRecord>()
                .eq(PointRecord::getUserId, userId)
                .eq(PointRecord::getBizType, bizType.name())
                .eq(PointRecord::getBizNo, bizNo));
        if (exists != null && exists > 0) {
            log.debug("积分流水已存在，跳过 userId={} bizType={} bizNo={}", userId, bizType, bizNo);
            return false;
        }

        // 2. 账户不存在先初始化（并发下可能重复插入，唯一索引兜底）
        getOrCreateAccount(userId);

        // 3. 原子增减：一条 UPDATE 搞定，条件里带"扣完不能为负"
        int earned = Math.max(delta, 0);
        int used = Math.max(-delta, 0);
        int updated = accountMapper.update(null, new LambdaUpdateWrapper<UserPointAccount>()
                .eq(UserPointAccount::getUserId, userId)
                .apply("balance + {0} >= 0", delta)
                .setSql("balance = balance + " + delta)
                .setSql("total_earned = total_earned + " + earned)
                .setSql("total_used = total_used + " + used)
                .setSql("version = version + 1"));
        if (updated == 0) {
            UserPointAccount current = getAccount(userId);
            int balance = current == null ? 0 : current.getBalance();
            throw BizException.of(ErrorCode.POINT_NOT_ENOUGH,
                    "积分不足，当前可用 " + balance + "，需要 " + Math.abs(delta));
        }

        // 4. 当前读（同一事务内已持有行锁，代价极低）：拿到准确的 balance_after 写进流水
        UserPointAccount fresh = accountMapper.selectOne(new LambdaQueryWrapper<UserPointAccount>()
                .eq(UserPointAccount::getUserId, userId)
                .last("for update"));
        int balanceAfter = fresh == null ? 0 : fresh.getBalance();

        // 5. 写流水；唯一索引冲突说明并发重复入账，整体回滚
        PointRecord record = new PointRecord();
        record.setUserId(userId);
        record.setBizType(bizType.name());
        record.setBizNo(bizNo);
        record.setChangePoint(delta);
        record.setBalanceAfter(balanceAfter);
        record.setRemark(remark);
        try {
            recordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            log.info("积分流水并发重复，回滚本次入账 userId={} bizNo={}", userId, bizNo);
            // 让外层事务回滚：否则余额已经被加过，却没有对应流水
            throw e;
        }

        // 6. 更新排行榜（Redis ZSet，失败不影响积分入账）
        rankService.addScore(userId, delta);
        return true;
    }

    @Override
    public PageResult<PointRecordVO> pageRecords(Long userId, String bizType, long page, long size) {
        Page<PointRecord> result = recordMapper.selectPage(Page.of(page, size),
                new LambdaQueryWrapper<PointRecord>()
                        .eq(PointRecord::getUserId, userId)
                        .eq(bizType != null && !bizType.isBlank(), PointRecord::getBizType, bizType)
                        .orderByDesc(PointRecord::getId));
        List<PointRecordVO> records = result.getRecords().stream().map(this::toVo).toList();
        return PageResult.of(records, result.getTotal(), page, size);
    }

    private PointRecordVO toVo(PointRecord record) {
        PointRecordVO vo = new PointRecordVO();
        vo.setId(record.getId());
        vo.setBizType(record.getBizType());
        vo.setBizTypeDesc(descOf(record.getBizType()));
        vo.setBizNo(record.getBizNo());
        vo.setChangePoint(record.getChangePoint());
        vo.setBalanceAfter(record.getBalanceAfter());
        vo.setRemark(record.getRemark());
        vo.setCreateTime(record.getCreateTime());
        return vo;
    }

    private String descOf(String bizType) {
        return switch (bizType) {
            case "SIGNIN" -> "每日签到";
            case "TASK" -> "任务奖励";
            case "REDEEM" -> "兑换码";
            case "ORDER_PAY" -> "兑换权益";
            case "ORDER_REFUND" -> "售后退回";
            case "ADMIN" -> "后台调整";
            default -> bizType;
        };
    }
}
