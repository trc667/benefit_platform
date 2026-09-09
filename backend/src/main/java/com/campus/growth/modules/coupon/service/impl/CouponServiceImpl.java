package com.campus.growth.modules.coupon.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.growth.common.annotation.DistributedLock;
import com.campus.growth.common.constant.BizConst;
import com.campus.growth.common.constant.RedisKeyConst;
import com.campus.growth.common.context.UserContext;
import com.campus.growth.common.enums.CouponScopeType;
import com.campus.growth.common.enums.CouponStatus;
import com.campus.growth.common.enums.CouponType;
import com.campus.growth.common.enums.CouponValidType;
import com.campus.growth.common.exception.BizException;
import com.campus.growth.common.result.ErrorCode;
import com.campus.growth.common.result.PageResult;
import com.campus.growth.common.util.OrderNoGenerator;
import com.campus.growth.modules.coupon.dto.CouponSaveDTO;
import com.campus.growth.modules.coupon.entity.CouponTemplate;
import com.campus.growth.modules.coupon.entity.UserCoupon;
import com.campus.growth.modules.coupon.mapper.CouponTemplateMapper;
import com.campus.growth.modules.coupon.mapper.UserCouponMapper;
import com.campus.growth.modules.coupon.service.CouponService;
import com.campus.growth.modules.coupon.vo.CouponStatVO;
import com.campus.growth.modules.coupon.vo.CouponTemplateVO;
import com.campus.growth.modules.coupon.vo.UserCouponVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 优惠券服务实现。
 *
 * <h3>领券为什么容易超发（本项目重点演示的问题）</h3>
 * <p>领券是"读-判断-写"三步：读库存 → 判断有没有 → 扣减并写入券记录。
 * 并发下两个线程可能都读到"还剩 1 张"，于是各发一张，超发。</p>
 *
 * <h3>四层防护</h3>
 * <ol>
 *   <li><b>分布式锁</b>（{@link DistributedLock}，切面 order=10）：同一模板串行领券；</li>
 *   <li><b>Redis 预扣库存</b>（Lua 原子 DECR）：把绝大多数并发挡在数据库之前；</li>
 *   <li><b>数据库条件更新</b>（{@code issued_count < total_count}）：最后一道硬约束，行锁保证正确；</li>
 *   <li><b>唯一索引</b>：{@code user_coupon.coupon_code} 与"单人限领"校验，防止重复发券。</li>
 * </ol>
 *
 * <h3>切面顺序（本项目最容易被忽略的坑）</h3>
 * <p>锁必须包住事务。若事务在锁外面（事务切面 order 小于锁切面），执行顺序会变成
 * "开事务 → 加锁 → 业务 → 解锁 → 提交"，锁在数据提交前就释放了，
 * 第二个线程立刻拿到锁并读到未提交的旧库存 → 依然超发。
 * 本项目的顺序在 {@link com.campus.growth.common.aspect.AspectOrder} 里写死：
 * 限流(0) → 锁(10) → 幂等(20) → 事务(30) → 日志(40)。
 * 对比实验见 {@link LockOrderDemoService}，可直接调接口复现。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    /** 预扣库存 Lua：原子地"有货才扣"，避免 GET 与 DECR 之间的竞态 */
    private static final RedisScript<Long> DEDUCT_STOCK = new DefaultRedisScript<>(
            "local stock = redis.call('GET', KEYS[1]) "
                    + "if not stock then return -1 end "
                    + "if tonumber(stock) <= 0 then return 0 end "
                    + "redis.call('DECR', KEYS[1]) "
                    + "return 1",
            Long.class);

    private final CouponTemplateMapper templateMapper;
    private final UserCouponMapper userCouponMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final com.campus.growth.modules.auth.service.UserQueryService userQueryService;

    // ------------------------------------------------------------------
    // 学生端
    // ------------------------------------------------------------------

    @Override
    public PageResult<CouponTemplateVO> pageTemplates(long page, long size) {
        Long userId = UserContext.userId();
        Page<CouponTemplate> result = templateMapper.selectPage(Page.of(page, size),
                new LambdaQueryWrapper<CouponTemplate>()
                        .eq(CouponTemplate::getStatus, BizConst.STATUS_ENABLED)
                        .and(w -> w.isNull(CouponTemplate::getEndTime)
                                .or().gt(CouponTemplate::getEndTime, LocalDateTime.now()))
                        .orderByAsc(CouponTemplate::getThresholdPoint)
                        .orderByDesc(CouponTemplate::getId));
        List<CouponTemplateVO> records = result.getRecords().stream().map(template -> {
            CouponTemplateVO vo = toTemplateVo(template);
            if (userId != null) {
                Long received = userCouponMapper.selectCount(new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getUserId, userId)
                        .eq(UserCoupon::getTemplateId, template.getId()));
                vo.setReceivedCount(received == null ? 0 : received.intValue());
                vo.setReceived(vo.getReceivedCount() >= template.getPerUserLimit());
            }
            return vo;
        }).toList();
        return PageResult.of(records, result.getTotal(), page, size);
    }

    @Override
    @DistributedLock(key = "'coupon:stock:' + #templateId", waitSeconds = 2, leaseSeconds = -1,
            message = "领券人数较多，请稍后再试")
    @Transactional(rollbackFor = Exception.class)
    public UserCouponVO receive(Long templateId) {
        return doReceive(UserContext.requireUserId(), templateId, "RECEIVE");
    }

    @Override
    @DistributedLock(key = "'coupon:stock:' + #templateId", waitSeconds = 2, leaseSeconds = -1,
            message = "领券人数较多，请稍后再试")
    @Transactional(rollbackFor = Exception.class)
    public UserCouponVO receiveBySource(Long userId, Long templateId, String source) {
        return doReceive(userId, templateId, source);
    }

    /**
     * 领券核心逻辑（锁 + 事务由外层方法保证）。
     */
    private UserCouponVO doReceive(Long userId, Long templateId, String source) {
        CouponTemplate template = templateMapper.selectById(templateId);
        validateReceivable(template);

        // 单人限领：数据库计数为准（Redis 计数只做前置拦截，不作为最终依据）
        Long received = userCouponMapper.selectCount(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getTemplateId, templateId));
        if (received != null && received >= template.getPerUserLimit()) {
            throw BizException.of(ErrorCode.COUPON_LIMIT_EXCEED);
        }

        // Redis 预扣库存：先把并发挡在数据库外
        boolean deducted = deductRedisStock(template);
        if (!deducted) {
            throw BizException.of(ErrorCode.COUPON_SOLD_OUT);
        }
        try {
            // 数据库条件更新：issued_count < total_count 才允许 +1
            int updated = templateMapper.update(null, new LambdaUpdateWrapper<CouponTemplate>()
                    .eq(CouponTemplate::getId, templateId)
                    .lt(CouponTemplate::getIssuedCount, template.getTotalCount())
                    .setSql("issued_count = issued_count + 1"));
            if (updated == 0) {
                throw BizException.of(ErrorCode.COUPON_SOLD_OUT);
            }

            UserCoupon coupon = buildUserCoupon(userId, template, source);
            userCouponMapper.insert(coupon);
            log.info("发券成功 userId={} templateId={} source={} code={}", userId, templateId, source,
                    coupon.getCouponCode());
            return toUserCouponVo(coupon, null, null);
        } catch (RuntimeException e) {
            // 事务会回滚数据库，但 Redis 预扣必须显式补偿，否则库存会"凭空少掉"
            restoreRedisStock(templateId);
            throw e;
        }
    }

    @Override
    public PageResult<UserCouponVO> pageMine(String status, long page, long size) {
        Long userId = UserContext.requireUserId();
        Page<UserCoupon> result = userCouponMapper.selectPage(Page.of(page, size),
                new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getUserId, userId)
                        .eq(StringUtils.hasText(status), UserCoupon::getStatus, status)
                        .orderByAsc(UserCoupon::getStatus)
                        .orderByAsc(UserCoupon::getExpireTime)
                        .orderByDesc(UserCoupon::getId));
        List<UserCouponVO> records = result.getRecords().stream()
                .map(c -> toUserCouponVo(c, null, null)).toList();
        return PageResult.of(records, result.getTotal(), page, size);
    }

    @Override
    public List<UserCouponVO> availableCoupons(Long userId, int orderAmount, Long goodsId, String category) {
        List<UserCoupon> coupons = userCouponMapper.selectList(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getStatus, CouponStatus.UNUSED.name())
                .gt(UserCoupon::getExpireTime, LocalDateTime.now())
                .orderByAsc(UserCoupon::getThresholdPoint));
        List<UserCouponVO> list = new ArrayList<>(coupons.size());
        for (UserCoupon coupon : coupons) {
            UserCouponVO vo = toUserCouponVo(coupon, orderAmount, null);
            if (!CouponScopeType.matches(coupon.getScopeType(), coupon.getScopeValue(), goodsId, category)) {
                vo.setUsable(false);
                vo.setUnusableReason("不适用于当前商品");
                vo.setPreviewDiscount(0);
            } else if (orderAmount < coupon.getThresholdPoint()) {
                vo.setUsable(false);
                vo.setUnusableReason("满 " + coupon.getThresholdPoint() + " 积分可用");
                vo.setPreviewDiscount(0);
            } else {
                vo.setUsable(true);
            }
            list.add(vo);
        }
        // 可用的排前面
        list.sort((a, b) -> Boolean.compare(!Boolean.TRUE.equals(b.getUsable()), !Boolean.TRUE.equals(a.getUsable())));
        return list;
    }

    @Override
    public UserCoupon getUsable(Long userId, Long couponId, int orderAmount, Long goodsId, String category) {
        UserCoupon coupon = userCouponMapper.selectById(couponId);
        if (coupon == null || !coupon.getUserId().equals(userId)) {
            throw BizException.of(ErrorCode.COUPON_NOT_AVAILABLE, "优惠券不存在");
        }
        if (!CouponStatus.UNUSED.name().equals(coupon.getStatus())) {
            throw BizException.of(ErrorCode.COUPON_ALREADY_USED);
        }
        if (coupon.getExpireTime().isBefore(LocalDateTime.now())) {
            throw BizException.of(ErrorCode.COUPON_NOT_AVAILABLE, "优惠券已过期");
        }
        if (orderAmount < coupon.getThresholdPoint()) {
            throw BizException.of(ErrorCode.COUPON_NOT_AVAILABLE,
                    "未满 " + coupon.getThresholdPoint() + " 积分，不能使用该券");
        }
        if (!CouponScopeType.matches(coupon.getScopeType(), coupon.getScopeValue(), goodsId, category)) {
            throw BizException.of(ErrorCode.COUPON_NOT_AVAILABLE, "该券不适用于当前商品");
        }
        return coupon;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean lockCoupon(Long userId, Long couponId, String orderNo) {
        // 条件更新：只有 UNUSED 才能变成 USED，天然防止一券多用
        int updated = userCouponMapper.update(null, new LambdaUpdateWrapper<UserCoupon>()
                .eq(UserCoupon::getId, couponId)
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getStatus, CouponStatus.UNUSED.name())
                .set(UserCoupon::getStatus, CouponStatus.USED.name())
                .set(UserCoupon::getUseTime, LocalDateTime.now())
                .set(UserCoupon::getOrderNo, orderNo));
        if (updated == 0) {
            throw BizException.of(ErrorCode.COUPON_ALREADY_USED);
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlockCoupon(Long couponId, String orderNo) {
        if (couponId == null) {
            return;
        }
        userCouponMapper.update(null, new LambdaUpdateWrapper<UserCoupon>()
                .eq(UserCoupon::getId, couponId)
                .eq(UserCoupon::getOrderNo, orderNo)
                .eq(UserCoupon::getStatus, CouponStatus.USED.name())
                .set(UserCoupon::getStatus, CouponStatus.UNUSED.name())
                .set(UserCoupon::getUseTime, null)
                .set(UserCoupon::getOrderNo, null));
    }

    @Override
    public List<UserCoupon> listByIds(Long userId, List<Long> couponIds) {
        if (couponIds == null || couponIds.isEmpty()) {
            return List.of();
        }
        return userCouponMapper.selectList(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .in(UserCoupon::getId, couponIds));
    }

    @Override
    public List<UserCoupon> listByStatus(Long userId, String status) {
        return userCouponMapper.selectList(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .eq(StringUtils.hasText(status), UserCoupon::getStatus, status)
                .orderByDesc(UserCoupon::getId)
                .last("limit 200"));
    }

    // ------------------------------------------------------------------
    // 管理端
    // ------------------------------------------------------------------

    @Override
    public PageResult<CouponTemplateVO> pageForAdmin(String keyword, Integer status, long page, long size) {
        Page<CouponTemplate> result = templateMapper.selectPage(Page.of(page, size),
                new LambdaQueryWrapper<CouponTemplate>()
                        .like(StringUtils.hasText(keyword), CouponTemplate::getTitle, keyword)
                        .eq(status != null, CouponTemplate::getStatus, status)
                        .orderByDesc(CouponTemplate::getId));
        return PageResult.of(result.getRecords().stream().map(this::toTemplateVo).toList(),
                result.getTotal(), page, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveTemplate(CouponSaveDTO dto) {
        CouponTemplate entity = new CouponTemplate();
        entity.setId(dto.getId());
        entity.setTemplateCode(dto.getId() == null ? OrderNoGenerator.templateCode() : null);
        entity.setTitle(dto.getTitle());
        entity.setCouponType(dto.getCouponType());
        entity.setFaceValue(dto.getFaceValue());
        entity.setDiscountRate(dto.getDiscountRate());
        entity.setThresholdPoint(dto.getThresholdPoint());
        entity.setMaxDiscount(dto.getMaxDiscount());
        entity.setScopeType(dto.getScopeType());
        entity.setScopeValue(dto.getScopeValue());
        entity.setTotalCount(dto.getTotalCount());
        entity.setPerUserLimit(dto.getPerUserLimit());
        entity.setValidType(dto.getValidType());
        entity.setStartTime(dto.getStartTime());
        entity.setEndTime(dto.getEndTime());
        entity.setValidDays(dto.getValidDays());
        entity.setStatus(dto.getStatus() == null ? BizConst.STATUS_ENABLED : dto.getStatus());

        if (dto.getId() == null) {
            entity.setIssuedCount(0);
            entity.setVersion(0);
            templateMapper.insert(entity);
        } else {
            templateMapper.updateById(entity);
            // 模板变了，Redis 预扣库存要按新的剩余量重建，否则会继续按旧库存发券
            stringRedisTemplate.delete(RedisKeyConst.couponStock(entity.getId()));
        }
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long templateId, Integer status) {
        CouponTemplate update = new CouponTemplate();
        update.setId(templateId);
        update.setStatus(status);
        templateMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int grant(Long templateId, List<Long> userIds, Integer count) {
        CouponTemplate template = templateMapper.selectById(templateId);
        validateReceivable(template);
        List<Long> targets = userIds;
        if ((targets == null || targets.isEmpty()) && count != null && count > 0) {
            // 未指定用户：取最近注册的学生（限制上限 500，避免一次发放把接口拖死）
            targets = userQueryService.recentStudentIds(count);
        }
        if (targets == null || targets.isEmpty()) {
            return 0;
        }
        int success = 0;
        for (Long userId : targets) {
            try {
                receiveBySource(userId, templateId, "ADMIN");
                success++;
            } catch (BizException e) {
                // 单人超限或库存不足：跳过该用户，不影响其他用户
                log.info("定向发券跳过 userId={} templateId={} reason={}", userId, templateId, e.getMessage());
            }
        }
        log.info("定向发券完成 templateId={} 目标={} 成功={}", templateId, targets.size(), success);
        return success;
    }

    @Override
    public List<CouponStatVO> stat() {
        List<CouponTemplate> templates = templateMapper.selectList(new LambdaQueryWrapper<CouponTemplate>()
                .orderByDesc(CouponTemplate::getId).last("limit 50"));
        List<CouponStatVO> list = new ArrayList<>(templates.size());
        for (CouponTemplate template : templates) {
            Long used = userCouponMapper.selectCount(new LambdaQueryWrapper<UserCoupon>()
                    .eq(UserCoupon::getTemplateId, template.getId())
                    .eq(UserCoupon::getStatus, CouponStatus.USED.name()));
            CouponStatVO vo = new CouponStatVO();
            vo.setTemplateId(template.getId());
            vo.setTitle(template.getTitle());
            vo.setCouponType(template.getCouponType());
            vo.setTotalCount(template.getTotalCount());
            vo.setIssuedCount(template.getIssuedCount());
            vo.setUsedCount(used == null ? 0 : used);
            int issued = template.getIssuedCount() == null || template.getIssuedCount() == 0 ? 1 : template.getIssuedCount();
            vo.setUseRate(Math.round((used == null ? 0 : used) * 10000d / issued) / 100d);
            list.add(vo);
        }
        return list;
    }

    @Override
    public long countIssued() {
        Long count = userCouponMapper.selectCount(null);
        return count == null ? 0 : count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int expireOverdueCoupons(int batchSize) {
        int limit = Math.max(1, Math.min(batchSize, 1000));
        return userCouponMapper.update(null, new LambdaUpdateWrapper<UserCoupon>()
                .eq(UserCoupon::getStatus, CouponStatus.UNUSED.name())
                .lt(UserCoupon::getExpireTime, LocalDateTime.now())
                .set(UserCoupon::getStatus, CouponStatus.EXPIRED.name())
                .last("limit " + limit));
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    private void validateReceivable(CouponTemplate template) {
        if (template == null || template.getStatus() == null || template.getStatus() != BizConst.STATUS_ENABLED) {
            throw BizException.of(ErrorCode.COUPON_TEMPLATE_NOT_FOUND);
        }
        LocalDateTime now = LocalDateTime.now();
        if (template.getStartTime() != null && now.isBefore(template.getStartTime())) {
            throw BizException.of(ErrorCode.COUPON_TEMPLATE_NOT_FOUND, "该券尚未开始发放");
        }
        if (template.getEndTime() != null && now.isAfter(template.getEndTime())) {
            throw BizException.of(ErrorCode.COUPON_TEMPLATE_NOT_FOUND, "该券已停止发放");
        }
    }

    /** Redis 预扣：首次访问时用 SETNX 初始化剩余库存 */
    private boolean deductRedisStock(CouponTemplate template) {
        String stockKey = RedisKeyConst.couponStock(template.getId());
        int remain = template.getTotalCount() - template.getIssuedCount();
        Boolean initialized = stringRedisTemplate.opsForValue()
                .setIfAbsent(stockKey, String.valueOf(Math.max(remain, 0)), 60, TimeUnit.DAYS);
        if (Boolean.TRUE.equals(initialized)) {
            log.info("初始化券库存 Redis 预扣 key={} stock={}", stockKey, remain);
        }
        try {
            Long result = stringRedisTemplate.execute(DEDUCT_STOCK, List.of(stockKey));
            return result != null && result == 1L;
        } catch (Exception e) {
            // Redis 故障时降级：交给数据库条件更新兜底，不因为缓存故障拒绝用户
            log.error("Redis 预扣库存异常，降级为数据库扣减 templateId={}", template.getId(), e);
            return true;
        }
    }

    private void restoreRedisStock(Long templateId) {
        try {
            stringRedisTemplate.opsForValue().increment(RedisKeyConst.couponStock(templateId));
        } catch (Exception e) {
            log.error("回滚 Redis 库存失败 templateId={}（可等待库存重建）", templateId, e);
        }
    }

    private UserCoupon buildUserCoupon(Long userId, CouponTemplate template, String source) {
        LocalDateTime now = LocalDateTime.now();
        UserCoupon coupon = new UserCoupon();
        coupon.setUserId(userId);
        coupon.setTemplateId(template.getId());
        coupon.setCouponCode(OrderNoGenerator.couponCode());
        coupon.setCouponTitle(template.getTitle());
        coupon.setCouponType(template.getCouponType());
        coupon.setFaceValue(template.getFaceValue());
        coupon.setDiscountRate(template.getDiscountRate());
        coupon.setThresholdPoint(template.getThresholdPoint());
        coupon.setMaxDiscount(template.getMaxDiscount());
        coupon.setScopeType(template.getScopeType());
        coupon.setScopeValue(template.getScopeValue());
        coupon.setStatus(CouponStatus.UNUSED.name());
        coupon.setSource(source);
        coupon.setReceiveTime(now);
        coupon.setExpireTime(CouponValidType.resolveExpireTime(
                CouponValidType.valueOf(template.getValidType()), template.getEndTime(),
                template.getValidDays(), now));
        return coupon;
    }

    private CouponTemplateVO toTemplateVo(CouponTemplate template) {
        CouponTemplateVO vo = new CouponTemplateVO();
        vo.setId(template.getId());
        vo.setTemplateCode(template.getTemplateCode());
        vo.setTitle(template.getTitle());
        vo.setCouponType(template.getCouponType());
        vo.setFaceValue(template.getFaceValue());
        vo.setDiscountRate(template.getDiscountRate());
        vo.setThresholdPoint(template.getThresholdPoint());
        vo.setMaxDiscount(template.getMaxDiscount());
        vo.setScopeType(template.getScopeType());
        vo.setScopeValue(template.getScopeValue());
        vo.setValueDesc(valueDesc(template.getCouponType(), template.getFaceValue(),
                template.getDiscountRate(), template.getThresholdPoint()));
        vo.setTotalCount(template.getTotalCount());
        vo.setIssuedCount(template.getIssuedCount());
        vo.setRemainCount(Math.max(0, template.getTotalCount() - template.getIssuedCount()));
        vo.setPerUserLimit(template.getPerUserLimit());
        vo.setValidType(template.getValidType());
        vo.setStartTime(template.getStartTime());
        vo.setEndTime(template.getEndTime());
        vo.setValidDays(template.getValidDays());
        vo.setStatus(template.getStatus());
        vo.setReceived(false);
        vo.setReceivedCount(0);
        return vo;
    }

    private UserCouponVO toUserCouponVo(UserCoupon coupon, Integer orderAmount, String unusedReason) {
        UserCouponVO vo = new UserCouponVO();
        vo.setId(coupon.getId());
        vo.setTemplateId(coupon.getTemplateId());
        vo.setCouponCode(coupon.getCouponCode());
        vo.setCouponTitle(coupon.getCouponTitle());
        vo.setCouponType(coupon.getCouponType());
        vo.setFaceValue(coupon.getFaceValue());
        vo.setDiscountRate(coupon.getDiscountRate());
        vo.setThresholdPoint(coupon.getThresholdPoint());
        vo.setMaxDiscount(coupon.getMaxDiscount());
        vo.setScopeType(coupon.getScopeType());
        vo.setScopeValue(coupon.getScopeValue());
        vo.setValueDesc(valueDesc(coupon.getCouponType(), coupon.getFaceValue(),
                coupon.getDiscountRate(), coupon.getThresholdPoint()));
        vo.setStatus(coupon.getStatus());
        vo.setStatusDesc(statusDesc(coupon.getStatus()));
        vo.setSource(coupon.getSource());
        vo.setReceiveTime(coupon.getReceiveTime());
        vo.setUseTime(coupon.getUseTime());
        vo.setExpireTime(coupon.getExpireTime());
        vo.setOrderNo(coupon.getOrderNo());
        if (orderAmount != null) {
            int discount = CouponType.calcDiscount(CouponType.valueOf(coupon.getCouponType()), orderAmount,
                    coupon.getFaceValue(), coupon.getDiscountRate(), coupon.getMaxDiscount());
            vo.setPreviewDiscount(discount);
        }
        // 只有"未使用且在有效期内"的券才是可用的：
        // 早期版本这里只看 unusedReason，导致已过期/已使用的券在列表里也显示 usable=true
        boolean available = CouponStatus.UNUSED.name().equals(coupon.getStatus())
                && coupon.getExpireTime() != null
                && coupon.getExpireTime().isAfter(LocalDateTime.now());
        vo.setUsable(available && unusedReason == null);
        if (!available && unusedReason == null) {
            vo.setUnusableReason(CouponStatus.EXPIRED.name().equals(coupon.getStatus())
                    || (coupon.getExpireTime() != null && coupon.getExpireTime().isBefore(LocalDateTime.now()))
                    ? "已过期" : "当前状态不可用");
        } else {
            vo.setUnusableReason(unusedReason);
        }
        return vo;
    }

    private String valueDesc(String type, Integer faceValue, Integer discountRate, Integer threshold) {
        if (CouponType.DISCOUNT.name().equals(type)) {
            String rate = discountRate == null ? "100" : String.valueOf(discountRate);
            String base = (Integer.parseInt(rate) % 10 == 0 ? String.valueOf(Integer.parseInt(rate) / 10)
                    : (Integer.parseInt(rate) / 10.0 + "")) + " 折";
            return threshold != null && threshold > 0 ? "满 " + threshold + " 享 " + base : base;
        }
        if (CouponType.DIRECT.name().equals(type)) {
            return "无门槛减 " + faceValue;
        }
        return "满 " + (threshold == null ? 0 : threshold) + " 减 " + faceValue;
    }

    private String statusDesc(String status) {
        return switch (status) {
            case "UNUSED" -> "未使用";
            case "USED" -> "已使用";
            case "EXPIRED" -> "已过期";
            default -> status;
        };
    }
}
