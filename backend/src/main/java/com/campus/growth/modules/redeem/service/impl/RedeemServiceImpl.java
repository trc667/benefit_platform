package com.campus.growth.modules.redeem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.growth.common.annotation.DistributedLock;
import com.campus.growth.common.constant.BizConst;
import com.campus.growth.common.constant.RedisKeyConst;
import com.campus.growth.common.context.UserContext;
import com.campus.growth.common.enums.PointBizType;
import com.campus.growth.common.enums.RedeemBizType;
import com.campus.growth.common.exception.BizException;
import com.campus.growth.common.result.ErrorCode;
import com.campus.growth.common.result.PageResult;
import com.campus.growth.common.util.OrderNoGenerator;
import com.campus.growth.common.util.RedeemCodeUtil;
import com.campus.growth.modules.coupon.service.CouponService;
import com.campus.growth.modules.coupon.vo.UserCouponVO;
import com.campus.growth.modules.point.service.PointService;
import com.campus.growth.modules.redeem.dto.RedeemBatchCreateDTO;
import com.campus.growth.modules.redeem.entity.RedeemCode;
import com.campus.growth.modules.redeem.entity.RedeemCodeBatch;
import com.campus.growth.modules.redeem.mapper.RedeemCodeBatchMapper;
import com.campus.growth.modules.redeem.mapper.RedeemCodeMapper;
import com.campus.growth.modules.redeem.service.RedeemService;
import com.campus.growth.modules.redeem.vo.RedeemBatchVO;
import com.campus.growth.modules.redeem.vo.RedeemResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 兑换码服务实现（Base32 分段编码 + Redis BitMap 核销）。
 *
 * <h3>码怎么生成</h3>
 * <p>码 = 批次签名(20bit) + 序号(35bit) + 校验位(5bit)，共 60bit → 12 个 Base32 字符。
 * 详见 {@link RedeemCodeUtil}。容量 2^35 ≈ 343 亿，远超需求里的 20 亿级。</p>
 *
 * <h3>核销状态为什么用 BitMap</h3>
 * <p>一个批次 1 亿个码，如果用 SET/Hash 记录已用，光 key 就撑不住；
 * 用位图只需 1 亿 bit = 12.5MB，且 GETBIT/SETBIT 都是 O(1)。
 * 序号天然就是 offset，不需要额外映射。</p>
 *
 * <h3>防重复兑换的三道闸</h3>
 * <ol>
 *   <li>校验位：手抄错码在解析阶段就被拦掉，不打 Redis；</li>
 *   <li>位图 GETBIT：已核销直接返回"已使用"；</li>
 *   <li>分布式锁 + {@code redeem_code.code} 唯一索引：极端并发下也只会有一次真正成功。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedeemServiceImpl implements RedeemService {

    /** 批次签名 → 批次号 的映射缓存（避免每次兑换都全表扫描） */
    private static final String SIG_MAP_KEY = RedisKeyConst.PREFIX + "redeem:sigmap";
    private static final long SIG_MAP_TTL_MINUTES = 10;

    private final RedeemCodeBatchMapper batchMapper;
    private final RedeemCodeMapper redeemCodeMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final PointService pointService;
    private final CouponService couponService;

    @Override
    @DistributedLock(key = "'redeem:' + #code", waitSeconds = 1, leaseSeconds = 15,
            message = "兑换请求处理中，请稍后再试")
    @Transactional(rollbackFor = Exception.class)
    public RedeemResultVO exchange(String code) {
        Long userId = UserContext.requireUserId();

        // 1. 解析码（校验位不过直接判无效）
        RedeemCodeUtil.RedeemCode parsed = RedeemCodeUtil.parse(code);
        if (parsed == null) {
            throw BizException.of(ErrorCode.REDEEM_CODE_INVALID);
        }
        String batchNo = resolveBatchNo(parsed.getBatchSignature());
        if (batchNo == null) {
            throw BizException.of(ErrorCode.REDEEM_CODE_INVALID);
        }

        // 2. 校验批次
        RedeemCodeBatch batch = batchMapper.selectOne(new LambdaQueryWrapper<RedeemCodeBatch>()
                .eq(RedeemCodeBatch::getBatchNo, batchNo));
        if (batch == null || batch.getStatus() == null || batch.getStatus() != BizConst.STATUS_ENABLED) {
            throw BizException.of(ErrorCode.REDEEM_BATCH_INVALID);
        }
        LocalDateTime now = LocalDateTime.now();
        if (batch.getStartTime() != null && now.isBefore(batch.getStartTime())) {
            throw BizException.of(ErrorCode.REDEEM_BATCH_INVALID, "该批次尚未开始");
        }
        if (batch.getEndTime() != null && now.isAfter(batch.getEndTime())) {
            throw BizException.of(ErrorCode.REDEEM_BATCH_INVALID, "该批次已结束");
        }
        if (parsed.getSeqNo() >= batch.getTotalCount()) {
            throw BizException.of(ErrorCode.REDEEM_CODE_INVALID);
        }

        // 3. 位图判重
        String bitmapKey = RedisKeyConst.redeemUsedBitmap(batchNo);
        Boolean used = stringRedisTemplate.opsForValue().getBit(bitmapKey, parsed.getSeqNo());
        if (Boolean.TRUE.equals(used)) {
            throw BizException.of(ErrorCode.REDEEM_CODE_USED);
        }

        // 4. 标记位图（先占位，失败再回滚）
        stringRedisTemplate.opsForValue().setBit(bitmapKey, parsed.getSeqNo(), true);
        stringRedisTemplate.expire(bitmapKey, 180, TimeUnit.DAYS);

        try {
            // 5. 落核销记录（唯一索引兜底）
            String normalizedCode = RedeemCodeUtil.generate(batchNo, parsed.getSeqNo());
            RedeemCode record = new RedeemCode();
            record.setBatchNo(batchNo);
            record.setCode(normalizedCode);
            record.setSeqNo(parsed.getSeqNo());
            record.setUserId(userId);
            record.setStatus(1);
            record.setUseTime(now);
            redeemCodeMapper.insert(record);

            // 6. 批次已用数 +1
            batchMapper.update(null, new LambdaUpdateWrapper<RedeemCodeBatch>()
                    .eq(RedeemCodeBatch::getBatchNo, batchNo)
                    .lt(RedeemCodeBatch::getUsedCount, batch.getTotalCount())
                    .setSql("used_count = used_count + 1"));

            // 7. 发奖
            return grantReward(batch, userId, parsed.getSeqNo(), normalizedCode);
        } catch (DuplicateKeyException e) {
            // 并发重复兑换：唯一索引拦住
            stringRedisTemplate.opsForValue().setBit(bitmapKey, parsed.getSeqNo(), true);
            throw BizException.of(ErrorCode.REDEEM_CODE_USED);
        } catch (RuntimeException e) {
            // 事务回滚 + 位图回滚，保证"失败不消耗码"
            stringRedisTemplate.opsForValue().setBit(bitmapKey, parsed.getSeqNo(), false);
            throw e;
        }
    }

    @Override
    public PageResult<RedeemBatchVO> pageBatches(String keyword, Integer status, long page, long size) {
        Page<RedeemCodeBatch> result = batchMapper.selectPage(Page.of(page, size),
                new LambdaQueryWrapper<RedeemCodeBatch>()
                        .like(StringUtils.hasText(keyword), RedeemCodeBatch::getTitle, keyword)
                        .eq(status != null, RedeemCodeBatch::getStatus, status)
                        .orderByDesc(RedeemCodeBatch::getId));
        return PageResult.of(result.getRecords().stream().map(this::toVo).toList(),
                result.getTotal(), page, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RedeemBatchVO createBatch(RedeemBatchCreateDTO dto) {
        if (RedeemBizType.COUPON.name().equals(dto.getBizType()) && dto.getRefId() == null) {
            throw BizException.of(ErrorCode.PARAM_ERROR, "券类兑换码必须指定券模板");
        }
        if (dto.getTotalCount() > BizConst.REDEEM_MAX_CAPACITY) {
            throw BizException.of(ErrorCode.PARAM_ERROR,
                    "单批次码总量上限为 " + BizConst.REDEEM_MAX_CAPACITY);
        }
        RedeemCodeBatch batch = new RedeemCodeBatch();
        batch.setBatchNo(OrderNoGenerator.redeemBatchNo());
        batch.setTitle(dto.getTitle());
        batch.setBizType(dto.getBizType());
        batch.setRefId(dto.getRefId());
        batch.setRewardValue(dto.getRewardValue());
        batch.setTotalCount(dto.getTotalCount());
        batch.setUsedCount(0);
        batch.setStartTime(dto.getStartTime());
        batch.setEndTime(dto.getEndTime());
        batch.setStatus(BizConst.STATUS_ENABLED);
        batch.setRemark(dto.getRemark());
        batchMapper.insert(batch);

        // 刷新签名映射缓存，新建批次立即可兑换
        stringRedisTemplate.delete(SIG_MAP_KEY);
        log.info("创建兑换码批次 batchNo={} 总量={}", batch.getBatchNo(), batch.getTotalCount());
        return toVo(batch);
    }

    @Override
    public List<String> generateCodes(String batchNo, int count) {
        RedeemCodeBatch batch = batchMapper.selectOne(new LambdaQueryWrapper<RedeemCodeBatch>()
                .eq(RedeemCodeBatch::getBatchNo, batchNo));
        if (batch == null) {
            throw BizException.of(ErrorCode.REDEEM_BATCH_INVALID);
        }
        int limit = Math.max(1, Math.min(count, 500));
        String cursorKey = RedisKeyConst.REDEEM_CURSOR.formatted(batchNo);
        Long start = stringRedisTemplate.opsForValue().increment(cursorKey, limit);
        long from = (start == null ? limit : start) - limit;
        stringRedisTemplate.expire(cursorKey, 180, TimeUnit.DAYS);

        List<String> codes = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            long seq = from + i;
            if (seq >= batch.getTotalCount()) {
                break;
            }
            codes.add(RedeemCodeUtil.format(RedeemCodeUtil.generate(batchNo, seq)));
        }
        return codes;
    }

    @Override
    public RedeemBatchVO stat(String batchNo) {
        RedeemCodeBatch batch = batchMapper.selectOne(new LambdaQueryWrapper<RedeemCodeBatch>()
                .eq(RedeemCodeBatch::getBatchNo, batchNo));
        if (batch == null) {
            throw BizException.of(ErrorCode.REDEEM_BATCH_INVALID);
        }
        RedeemBatchVO vo = toVo(batch);
        // 位图统计比 count(*) 快得多，且能校验"位图与数据库是否一致"
        Long bitmapCount = stringRedisTemplate.execute(
                (org.springframework.data.redis.core.RedisCallback<Long>) connection ->
                        connection.bitCount(RedisKeyConst.redeemUsedBitmap(batchNo)
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        Long dbCount = redeemCodeMapper.selectCount(new LambdaQueryWrapper<RedeemCode>()
                .eq(RedeemCode::getBatchNo, batchNo));
        vo.setRemark("位图核销数=" + (bitmapCount == null ? 0 : bitmapCount)
                + "，数据库核销数=" + (dbCount == null ? 0 : dbCount));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(String batchNo, Integer status) {
        batchMapper.update(null, new LambdaUpdateWrapper<RedeemCodeBatch>()
                .eq(RedeemCodeBatch::getBatchNo, batchNo)
                .set(RedeemCodeBatch::getStatus, status));
        stringRedisTemplate.delete(SIG_MAP_KEY);
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    /**
     * 由批次签名反查批次号。
     * <p>批次数量很少（运营手工创建），这里把"签名 → 批次号"整体缓存到 Redis Hash，
     * 避免每次兑换都查库；缓存未命中时重建一次。</p>
     */
    private String resolveBatchNo(int signature) {
        String field = String.valueOf(signature);
        Object cached = stringRedisTemplate.opsForHash().get(SIG_MAP_KEY, field);
        if (cached != null) {
            return String.valueOf(cached);
        }
        List<RedeemCodeBatch> batches = batchMapper.selectList(new LambdaQueryWrapper<RedeemCodeBatch>()
                .eq(RedeemCodeBatch::getStatus, BizConst.STATUS_ENABLED)
                .last("limit 1000"));
        if (batches.isEmpty()) {
            return null;
        }
        Map<String, String> map = new HashMap<>();
        for (RedeemCodeBatch batch : batches) {
            map.put(String.valueOf(RedeemCodeUtil.batchSignature(batch.getBatchNo())), batch.getBatchNo());
        }
        stringRedisTemplate.opsForHash().putAll(SIG_MAP_KEY, map);
        stringRedisTemplate.expire(SIG_MAP_KEY, SIG_MAP_TTL_MINUTES, TimeUnit.MINUTES);
        return map.get(field);
    }

    /** 按批次类型发放奖励 */
    private RedeemResultVO grantReward(RedeemCodeBatch batch, Long userId, long seqNo, String code) {
        RedeemResultVO vo = new RedeemResultVO();
        vo.setBatchNo(batch.getBatchNo());
        vo.setBizType(batch.getBizType());
        vo.setRewardValue(batch.getRewardValue());

        RedeemBizType bizType = RedeemBizType.valueOf(batch.getBizType());
        switch (bizType) {
            case POINT -> {
                pointService.addPoint(userId, PointBizType.REDEEM, batch.getBatchNo() + ":" + seqNo,
                        batch.getRewardValue(), "兑换码兑换：" + batch.getTitle());
                vo.setBalance(pointService.balanceOf(userId));
                vo.setMessage("兑换成功，获得 " + batch.getRewardValue() + " 积分");
            }
            case COUPON -> {
                UserCouponVO coupon = couponService.receiveBySource(userId, batch.getRefId(), "REDEEM");
                vo.setCouponId(coupon.getId());
                vo.setCouponTitle(coupon.getCouponTitle());
                vo.setBalance(pointService.balanceOf(userId));
                vo.setMessage("兑换成功，获得「" + coupon.getCouponTitle() + "」");
            }
            default -> throw BizException.of(ErrorCode.REDEEM_BATCH_INVALID, "该批次奖励类型暂不支持");
        }
        log.info("兑换成功 userId={} batchNo={} seqNo={} code={}", userId, batch.getBatchNo(), seqNo, code);
        return vo;
    }

    private RedeemBatchVO toVo(RedeemCodeBatch batch) {
        RedeemBatchVO vo = new RedeemBatchVO();
        vo.setId(batch.getId());
        vo.setBatchNo(batch.getBatchNo());
        vo.setTitle(batch.getTitle());
        vo.setBizType(batch.getBizType());
        vo.setRefId(batch.getRefId());
        vo.setRewardValue(batch.getRewardValue());
        vo.setTotalCount(batch.getTotalCount());
        vo.setUsedCount(batch.getUsedCount());
        vo.setStartTime(batch.getStartTime());
        vo.setEndTime(batch.getEndTime());
        vo.setStatus(batch.getStatus());
        vo.setRemark(batch.getRemark());
        vo.setCreateTime(batch.getCreateTime());
        int total = batch.getTotalCount() == null || batch.getTotalCount() == 0 ? 1 : batch.getTotalCount();
        vo.setUseRate(Math.round((batch.getUsedCount() == null ? 0 : batch.getUsedCount()) * 10000d / total) / 100d);
        String cursor = stringRedisTemplate.opsForValue().get(RedisKeyConst.REDEEM_CURSOR.formatted(batch.getBatchNo()));
        vo.setGeneratedCount(cursor == null ? 0L : Long.parseLong(cursor));
        return vo;
    }
}
