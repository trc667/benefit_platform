package com.campus.growth.modules.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.growth.common.annotation.DistributedLock;
import com.campus.growth.common.annotation.Idempotent;
import com.campus.growth.common.constant.BizConst;
import com.campus.growth.common.constant.MqTopicConst;
import com.campus.growth.common.context.UserContext;
import com.campus.growth.common.enums.OrderStatus;
import com.campus.growth.common.enums.PointBizType;
import com.campus.growth.common.enums.RefundStatus;
import com.campus.growth.common.enums.RefundType;
import com.campus.growth.common.exception.BizException;
import com.campus.growth.common.result.ErrorCode;
import com.campus.growth.common.result.PageResult;
import com.campus.growth.common.util.JsonUtil;
import com.campus.growth.common.util.OrderNoGenerator;
import com.campus.growth.modules.auth.service.UserQueryService;
import com.campus.growth.modules.auth.vo.UserBriefVO;
import com.campus.growth.modules.benefit.entity.BenefitGoods;
import com.campus.growth.modules.benefit.service.BenefitService;
import com.campus.growth.modules.coupon.entity.UserCoupon;
import com.campus.growth.modules.coupon.service.CouponService;
import com.campus.growth.modules.coupon.vo.UserCouponVO;
import com.campus.growth.modules.mq.service.EventPublisher;
import com.campus.growth.modules.order.dto.OrderCreateDTO;
import com.campus.growth.modules.order.dto.RefundApplyDTO;
import com.campus.growth.modules.order.dto.RefundHandleDTO;
import com.campus.growth.modules.order.entity.OrderItem;
import com.campus.growth.modules.order.entity.OrderMain;
import com.campus.growth.modules.order.entity.OrderRefund;
import com.campus.growth.modules.order.mapper.OrderItemMapper;
import com.campus.growth.modules.order.mapper.OrderMainMapper;
import com.campus.growth.modules.order.mapper.OrderRefundMapper;
import com.campus.growth.modules.order.service.OptimalDiscountCalculator;
import com.campus.growth.modules.order.service.OrderService;
import com.campus.growth.modules.order.vo.DiscountPlanVO;
import com.campus.growth.modules.order.vo.OrderItemVO;
import com.campus.growth.modules.order.vo.OrderVO;
import com.campus.growth.modules.order.vo.RefundVO;
import com.campus.growth.modules.order.vo.SettlePreviewVO;
import com.campus.growth.modules.point.service.PointService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 订单服务实现。
 *
 * <h3>下单为什么是两步（create → pay）</h3>
 * <p>积分商城虽然不需要第三方支付，但"预占库存"与"真正扣分"分离更贴近真实电商：
 * 用户点"提交订单"时先把库存和券锁住（CREATED），确认后扣积分（PAID）。
 * 未支付的订单由 {@code OrderTimeoutJob} 在 15 分钟后自动取消并回滚资源，
 * 避免库存被长期占用。</p>
 *
 * <h3>金额一律服务端重算</h3>
 * <p>前端传来的只有"选了哪几张券"，优惠金额由 {@link OptimalDiscountCalculator}
 * 在服务端按同一套规则重算。客户端传来的任何金额字段都不参与计算，
 * 这是防止"改价漏洞"的基本纪律。</p>
 *
 * <h3>并发控制</h3>
 * <ul>
 *   <li>下单：按用户加锁（防连点） + 幂等注解 + 商品库存条件更新（防超卖）；</li>
 *   <li>支付/取消：条件更新 {@code status = CREATED}，天然防止重复支付；</li>
 *   <li>券核销：条件更新 {@code status = UNUSED}，防止一券多用。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMainMapper orderMainMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderRefundMapper orderRefundMapper;
    private final BenefitService benefitService;
    private final CouponService couponService;
    private final PointService pointService;
    private final OptimalDiscountCalculator discountCalculator;
    private final EventPublisher eventPublisher;
    private final UserQueryService userQueryService;

    // ------------------------------------------------------------------
    // 学生端
    // ------------------------------------------------------------------

    @Override
    public SettlePreviewVO preview(Long goodsId, int quantity) {
        Long userId = UserContext.requireUserId();
        BenefitGoods goods = requireOnSaleGoods(goodsId);
        int qty = Math.max(1, quantity);
        int goodsTotal = goods.getPricePoint() * qty;

        // 候选券：直接取用户全部未使用券，由计算器统一做门槛/范围过滤
        List<UserCoupon> coupons = couponService.listByStatus(userId, "UNUSED");
        OptimalDiscountCalculator.Result result =
                discountCalculator.calculate(goodsTotal, coupons, goodsId, goods.getCategory());

        SettlePreviewVO vo = new SettlePreviewVO();
        vo.setGoodsId(goods.getId());
        vo.setGoodsTitle(goods.getTitle());
        vo.setGoodsCover(goods.getCoverUrl());
        vo.setUnitPoint(goods.getPricePoint());
        vo.setQuantity(qty);
        vo.setGoodsTotal(goodsTotal);
        vo.setBestPlan(result.getBest());
        vo.setCandidates(result.getCandidates());
        vo.setCouponCount(result.getCouponCount());
        vo.setCombinationCount(result.getCombinationCount());
        vo.setTotalCostMs(result.getTotalCostMs());

        List<UserCouponVO> available = couponService.availableCoupons(userId, goodsTotal, goodsId, goods.getCategory());
        vo.setAvailableCoupons(available);

        int balance = pointService.balanceOf(userId);
        vo.setBalance(balance);
        vo.setBalanceEnough(result.getBest() == null || balance >= result.getBest().getPayPoint());
        return vo;
    }

    @Override
    @DistributedLock(key = "'order:create'", waitSeconds = 2, leaseSeconds = 20,
            message = "下单请求处理中，请稍后再试")
    @Idempotent(key = "'order:create:' + #dto.goodsId + ':' + #dto.quantity", ttlSeconds = 3)
    @Transactional(rollbackFor = Exception.class)
    public OrderVO create(OrderCreateDTO dto) {
        Long userId = UserContext.requireUserId();
        BenefitGoods goods = requireOnSaleGoods(dto.getGoodsId());
        int quantity = dto.getQuantity();
        if (goods.getStock() < quantity) {
            throw BizException.of(ErrorCode.GOODS_STOCK_NOT_ENOUGH);
        }
        int goodsTotal = goods.getPricePoint() * quantity;

        // 1. 服务端重算优惠
        List<Long> couponIds = normalizeCouponIds(dto);
        List<UserCoupon> selectedCoupons = new ArrayList<>();
        for (Long couponId : couponIds) {
            selectedCoupons.add(couponService.getUsable(userId, couponId, goodsTotal,
                    goods.getId(), goods.getCategory()));
        }
        DiscountPlanVO plan;
        try {
            plan = discountCalculator.calcSelected(goodsTotal, selectedCoupons);
        } catch (IllegalArgumentException e) {
            throw BizException.of(ErrorCode.COUPON_NOT_AVAILABLE, e.getMessage());
        }
        int payPoint = plan.getPayPoint();

        // 2. 余额校验（真正扣减在支付阶段，这里只做前置拦截）
        int balance = pointService.balanceOf(userId);
        if (balance < payPoint) {
            throw BizException.of(ErrorCode.POINT_NOT_ENOUGH,
                    "积分不足，当前 " + balance + "，需支付 " + payPoint);
        }

        // 3. 预扣库存（条件更新，防超卖）
        if (!benefitService.deductStock(goods.getId(), quantity)) {
            throw BizException.of(ErrorCode.GOODS_STOCK_NOT_ENOUGH);
        }

        // 4. 落单
        String orderNo = OrderNoGenerator.orderNo();
        OrderMain order = new OrderMain();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setOrderType("GOODS");
        order.setGoodsTotalPoint(goodsTotal);
        order.setDiscountPoint(plan.getDiscountPoint());
        order.setPayPoint(payPoint);
        order.setCouponId(plan.getCouponIds().isEmpty() ? null : plan.getCouponIds().get(0));
        order.setDiscountSnapshot(JsonUtil.toJson(plan));
        order.setStatus(OrderStatus.CREATED.name());
        order.setReceiverName(dto.getReceiverName());
        order.setReceiverPhone(dto.getReceiverPhone());
        order.setReceiverAddress(dto.getReceiverAddress());
        order.setRemark(dto.getRemark());
        order.setVersion(0);
        orderMainMapper.insert(order);

        OrderItem item = new OrderItem();
        item.setOrderId(order.getId());
        item.setOrderNo(orderNo);
        item.setGoodsId(goods.getId());
        item.setGoodsCode(goods.getGoodsCode());
        item.setGoodsTitle(goods.getTitle());
        item.setGoodsCover(goods.getCoverUrl());
        item.setUnitPoint(goods.getPricePoint());
        item.setQuantity(quantity);
        item.setItemDiscount(plan.getDiscountPoint());
        item.setItemAmount(payPoint);
        orderItemMapper.insert(item);

        // 5. 锁券（条件更新，防止同一张券被两个订单同时占用）
        for (Long couponId : plan.getCouponIds()) {
            couponService.lockCoupon(userId, couponId, orderNo);
        }
        order.setCouponCode(selectedCoupons.isEmpty() ? null : selectedCoupons.get(0).getCouponCode());
        log.info("下单成功 userId={} orderNo={} goods={} 数量={} 优惠={} 实付={}", userId, orderNo,
                goods.getTitle(), quantity, plan.getDiscountPoint(), payPoint);
        return toOrderVo(order, List.of(item));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO pay(String orderNo) {
        Long userId = UserContext.requireUserId();
        OrderMain order = requireOwnOrder(orderNo, userId);
        if (!OrderStatus.canPay(order.getStatus())) {
            throw BizException.of(ErrorCode.ORDER_STATUS_ILLEGAL);
        }
        // 1. 先扣积分（幂等键 = 订单号，重复调用不会重复扣）
        pointService.deductPoint(userId, PointBizType.ORDER_PAY, orderNo, order.getPayPoint(),
                "兑换权益：" + orderNo);
        // 2. 条件更新订单状态，防止并发重复支付
        int updated = orderMainMapper.update(null, new LambdaUpdateWrapper<OrderMain>()
                .eq(OrderMain::getId, order.getId())
                .eq(OrderMain::getStatus, OrderStatus.CREATED.name())
                .set(OrderMain::getStatus, OrderStatus.PAID.name())
                .set(OrderMain::getPayTime, LocalDateTime.now()));
        if (updated == 0) {
            throw BizException.of(ErrorCode.ORDER_STATUS_ILLEGAL, "订单状态已变化，请刷新后重试");
        }
        // 3. 发事件：任务进度、通知等连带动作由 OrderEventConsumer 异步处理，
        //    支付链路只做"扣积分 + 改状态"两件事
        eventPublisher.publish(MqTopicConst.ORDER_PAID, MqTopicConst.EventType.ORDER_PAID, orderNo,
                Map.of("orderNo", orderNo, "userId", userId, "payPoint", order.getPayPoint()));
        order.setStatus(OrderStatus.PAID.name());
        order.setPayTime(LocalDateTime.now());
        log.info("订单支付成功 orderNo={} payPoint={}", orderNo, order.getPayPoint());
        return toOrderVo(order, orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderNo, orderNo)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finish(String orderNo) {
        Long userId = UserContext.requireUserId();
        OrderMain order = requireOwnOrder(orderNo, userId);
        doFinish(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finishByAdmin(String orderNo) {
        OrderMain order = orderMainMapper.selectOne(new LambdaQueryWrapper<OrderMain>()
                .eq(OrderMain::getOrderNo, orderNo));
        if (order == null) {
            throw BizException.of(ErrorCode.ORDER_NOT_FOUND);
        }
        doFinish(order);
        log.info("运营核销订单 orderNo={} operator={}", orderNo, UserContext.username());
    }

    /** 核销/完成的统一状态流转：只有 PAID 才能完成 */
    private void doFinish(OrderMain order) {
        if (!OrderStatus.canFinish(order.getStatus())) {
            throw BizException.of(ErrorCode.ORDER_STATUS_ILLEGAL,
                    "只有已支付订单可以核销，当前状态：" + order.getStatus());
        }
        int updated = orderMainMapper.update(null, new LambdaUpdateWrapper<OrderMain>()
                .eq(OrderMain::getId, order.getId())
                .eq(OrderMain::getStatus, OrderStatus.PAID.name())
                .set(OrderMain::getStatus, OrderStatus.FINISHED.name())
                .set(OrderMain::getFinishTime, LocalDateTime.now()));
        if (updated == 0) {
            throw BizException.of(ErrorCode.ORDER_STATUS_ILLEGAL, "订单状态已变化，请刷新后重试");
        }
        order.setStatus(OrderStatus.FINISHED.name());
        order.setFinishTime(LocalDateTime.now());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(String orderNo) {
        Long userId = UserContext.requireUserId();
        OrderMain order = requireOwnOrder(orderNo, userId);
        if (!OrderStatus.canCancel(order.getStatus())) {
            throw BizException.of(ErrorCode.ORDER_STATUS_ILLEGAL);
        }
        int updated = orderMainMapper.update(null, new LambdaUpdateWrapper<OrderMain>()
                .eq(OrderMain::getId, order.getId())
                .eq(OrderMain::getStatus, OrderStatus.CREATED.name())
                .set(OrderMain::getStatus, OrderStatus.CANCELLED.name())
                .set(OrderMain::getCancelTime, LocalDateTime.now()));
        if (updated == 0) {
            throw BizException.of(ErrorCode.ORDER_STATUS_ILLEGAL);
        }
        rollbackResources(order);
        log.info("订单取消 orderNo={}（库存与优惠券已在本地事务内回滚）", orderNo);
    }

    @Override
    public PageResult<OrderVO> pageMine(String status, long page, long size) {
        Long userId = UserContext.requireUserId();
        return pageOrders(new LambdaQueryWrapper<OrderMain>()
                .eq(OrderMain::getUserId, userId)
                .eq(StringUtils.hasText(status), OrderMain::getStatus, status), page, size);
    }

    @Override
    public OrderVO detail(String orderNo) {
        Long userId = UserContext.requireUserId();
        OrderMain order = requireOwnOrder(orderNo, userId);
        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderNo, orderNo));
        OrderVO vo = toOrderVo(order, items);
        Long refundCount = orderRefundMapper.selectCount(new LambdaQueryWrapper<OrderRefund>()
                .eq(OrderRefund::getOrderNo, orderNo));
        vo.setRefundApplied(refundCount != null && refundCount > 0);
        return vo;
    }

    @Override
    public OrderVO detailForAdmin(String orderNo) {
        OrderMain order = orderMainMapper.selectOne(new LambdaQueryWrapper<OrderMain>()
                .eq(OrderMain::getOrderNo, orderNo));
        if (order == null) {
            throw BizException.of(ErrorCode.ORDER_NOT_FOUND);
        }
        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderNo, orderNo));
        OrderVO vo = toOrderVo(order, items);
        UserBriefVO user = userQueryService.listBrief(List.of(order.getUserId()))
                .stream().findFirst().orElse(null);
        vo.setNickname(user == null ? null : user.getNickname());
        Long refundCount = orderRefundMapper.selectCount(new LambdaQueryWrapper<OrderRefund>()
                .eq(OrderRefund::getOrderNo, orderNo));
        vo.setRefundApplied(refundCount != null && refundCount > 0);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String applyRefund(RefundApplyDTO dto) {
        Long userId = UserContext.requireUserId();
        OrderMain order = requireOwnOrder(dto.getOrderNo(), userId);
        if (!OrderStatus.canRefund(order.getStatus())) {
            throw BizException.of(ErrorCode.ORDER_STATUS_ILLEGAL, "当前订单状态不支持申请售后");
        }
        Long exists = orderRefundMapper.selectCount(new LambdaQueryWrapper<OrderRefund>()
                .eq(OrderRefund::getOrderNo, order.getOrderNo()));
        if (exists != null && exists > 0) {
            throw BizException.of(ErrorCode.REFUND_ALREADY_APPLIED);
        }
        RefundType type;
        try {
            type = RefundType.valueOf(dto.getRefundType());
        } catch (IllegalArgumentException e) {
            throw BizException.of(ErrorCode.PARAM_ERROR, "售后类型不合法");
        }
        OrderRefund refund = new OrderRefund();
        refund.setRefundNo(OrderNoGenerator.refundNo());
        refund.setOrderNo(order.getOrderNo());
        refund.setOrderId(order.getId());
        refund.setUserId(userId);
        refund.setRefundType(type.name());
        refund.setReason(dto.getReason());
        refund.setStatus(RefundStatus.APPLIED.name());
        refund.setRefundPoint(order.getPayPoint());
        refund.setApplyTime(LocalDateTime.now());
        orderRefundMapper.insert(refund);
        log.info("售后申请提交 refundNo={} orderNo={} type={}", refund.getRefundNo(), order.getOrderNo(), type);
        return refund.getRefundNo();
    }

    @Override
    public PageResult<RefundVO> pageMyRefunds(long page, long size) {
        Long userId = UserContext.requireUserId();
        return pageRefundList(new LambdaQueryWrapper<OrderRefund>()
                .eq(OrderRefund::getUserId, userId), page, size);
    }

    // ------------------------------------------------------------------
    // 管理端
    // ------------------------------------------------------------------

    @Override
    public PageResult<OrderVO> pageAdmin(String orderNo, String status, long page, long size) {
        return pageOrders(new LambdaQueryWrapper<OrderMain>()
                .eq(StringUtils.hasText(orderNo), OrderMain::getOrderNo, orderNo)
                .eq(StringUtils.hasText(status), OrderMain::getStatus, status), page, size);
    }

    @Override
    public PageResult<RefundVO> pageRefunds(String status, long page, long size) {
        return pageRefundList(new LambdaQueryWrapper<OrderRefund>()
                .eq(StringUtils.hasText(status), OrderRefund::getStatus, status), page, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleRefund(RefundHandleDTO dto) {
        OrderRefund refund = orderRefundMapper.selectOne(new LambdaQueryWrapper<OrderRefund>()
                .eq(OrderRefund::getRefundNo, dto.getRefundNo()));
        if (refund == null) {
            throw BizException.of(ErrorCode.REFUND_NOT_FOUND);
        }
        if (!RefundStatus.canHandle(refund.getStatus())) {
            throw BizException.of(ErrorCode.REFUND_STATUS_ILLEGAL);
        }
        Long handlerId = UserContext.userId();
        if (!Boolean.TRUE.equals(dto.getApproved())) {
            orderRefundMapper.update(null, new LambdaUpdateWrapper<OrderRefund>()
                    .eq(OrderRefund::getId, refund.getId())
                    .set(OrderRefund::getStatus, RefundStatus.REJECTED.name())
                    .set(OrderRefund::getHandleRemark, dto.getHandleRemark())
                    .set(OrderRefund::getHandlerId, handlerId)
                    .set(OrderRefund::getHandleTime, LocalDateTime.now()));
            log.info("售后退款驳回 refundNo={}", refund.getRefundNo());
            return;
        }

        int refundPoint = dto.getRefundPoint() == null ? refund.getRefundPoint() : dto.getRefundPoint();
        boolean isReturn = RefundType.RETURN.name().equals(refund.getRefundType());
        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderNo, refund.getOrderNo()));

        if (isReturn) {
            // ---------- 退货：退积分 + 回滚库存 + 退券 ----------
            if (refundPoint > 0) {
                // 幂等键 = 退换单号，重复审核不会重复退分
                pointService.addPoint(refund.getUserId(), PointBizType.ORDER_REFUND, refund.getRefundNo(),
                        refundPoint, "售后退回：" + refund.getOrderNo());
            }
            for (OrderItem item : items) {
                benefitService.restoreStock(item.getGoodsId(), item.getQuantity());
            }
            // 退货要把订单占用的优惠券释放回用户（否则券永久 USED，用户白亏一张）
            unlockOrderCoupons(refund);
            // 订单终态是"已退款"，不是"已完成"——语义必须区分，否则统计口径会错
            orderMainMapper.update(null, new LambdaUpdateWrapper<OrderMain>()
                    .eq(OrderMain::getOrderNo, refund.getOrderNo())
                    .set(OrderMain::getStatus, OrderStatus.REFUNDED.name())
                    .set(OrderMain::getFinishTime, LocalDateTime.now()));
        } else {
            // ---------- 换货：不回退积分与券，只回滚库存并把订单标记完成 ----------
            for (OrderItem item : items) {
                benefitService.restoreStock(item.getGoodsId(), item.getQuantity());
            }
            orderMainMapper.update(null, new LambdaUpdateWrapper<OrderMain>()
                    .eq(OrderMain::getOrderNo, refund.getOrderNo())
                    .set(OrderMain::getStatus, OrderStatus.FINISHED.name())
                    .set(OrderMain::getFinishTime, LocalDateTime.now()));
            // 换货不退款，退回积分置 0，避免统计把"换货"算成退款
            refundPoint = 0;
        }
        orderRefundMapper.update(null, new LambdaUpdateWrapper<OrderRefund>()
                .eq(OrderRefund::getId, refund.getId())
                .set(OrderRefund::getStatus, RefundStatus.REFUNDED.name())
                .set(OrderRefund::getRefundPoint, refundPoint)
                .set(OrderRefund::getHandleRemark, dto.getHandleRemark())
                .set(OrderRefund::getHandlerId, handlerId)
                .set(OrderRefund::getHandleTime, LocalDateTime.now()));
        log.info("售后处理完成 refundNo={} type={} 退回积分={}", refund.getRefundNo(),
                refund.getRefundType(), refundPoint);
    }

    /** 释放订单占用的优惠券（退货场景） */
    private void unlockOrderCoupons(OrderRefund refund) {
        List<UserCoupon> used = couponService.listByStatus(refund.getUserId(), "USED").stream()
                .filter(coupon -> refund.getOrderNo().equals(coupon.getOrderNo()))
                .toList();
        for (UserCoupon coupon : used) {
            couponService.unlockCoupon(coupon.getId(), refund.getOrderNo());
        }
        if (!used.isEmpty()) {
            log.info("售后退货释放优惠券 orderNo={} 张数={}", refund.getOrderNo(), used.size());
        }
    }

    @Override
    public long countAll() {
        Long count = orderMainMapper.selectCount(null);
        return count == null ? 0 : count;
    }

    @Override
    public long countToday() {
        Long count = orderMainMapper.selectCount(new LambdaQueryWrapper<OrderMain>()
                .ge(OrderMain::getCreateTime, LocalDate.now().atStartOfDay()));
        return count == null ? 0 : count;
    }

    @Override
    public long sumPaidPoints() {
        List<OrderMain> orders = orderMainMapper.selectList(new LambdaQueryWrapper<OrderMain>()
                .select(OrderMain::getPayPoint)
                .in(OrderMain::getStatus, OrderStatus.PAID.name(), OrderStatus.FINISHED.name()));
        return orders.stream().mapToLong(o -> o.getPayPoint() == null ? 0 : o.getPayPoint()).sum();
    }
    @Override
    public long countPendingRefund() {
        Long count = orderRefundMapper.selectCount(new LambdaQueryWrapper<OrderRefund>()
                .eq(OrderRefund::getStatus, RefundStatus.APPLIED.name()));
        return count == null ? 0 : count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cancelTimeoutOrders(int timeoutMinutes) {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(timeoutMinutes);
        List<OrderMain> timeoutOrders = orderMainMapper.selectList(new LambdaQueryWrapper<OrderMain>()
                .eq(OrderMain::getStatus, OrderStatus.CREATED.name())
                .le(OrderMain::getCreateTime, deadline)
                .last("limit 200"));
        int count = 0;
        for (OrderMain order : timeoutOrders) {
            int updated = orderMainMapper.update(null, new LambdaUpdateWrapper<OrderMain>()
                    .eq(OrderMain::getId, order.getId())
                    .eq(OrderMain::getStatus, OrderStatus.CREATED.name())
                    .set(OrderMain::getStatus, OrderStatus.CANCELLED.name())
                    .set(OrderMain::getCancelTime, LocalDateTime.now())
                    .set(OrderMain::getRemark, "超时未支付，系统自动取消"));
            if (updated > 0) {
                rollbackResources(order);
                count++;
            }
        }
        if (count > 0) {
            log.info("超时订单自动取消 {} 笔", count);
        }
        return count;
    }

    @Override
    public List<com.campus.growth.common.vo.TrendVO> orderTrend(int days) {
        int range = Math.max(1, Math.min(days, 30));
        LocalDate from = LocalDate.now().minusDays(range - 1L);
        List<Map<String, Object>> rows = orderMainMapper.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<OrderMain>()
                        .select("DATE(create_time) AS date", "COUNT(*) AS cnt", "IFNULL(SUM(pay_point),0) AS pts")
                        .ge("create_time", from.atStartOfDay())
                        .groupBy("DATE(create_time)"));
        Map<String, com.campus.growth.common.vo.TrendVO> map = new java.util.HashMap<>();
        for (Map<String, Object> row : rows) {
            String date = String.valueOf(row.get("date"));
            map.put(date, new com.campus.growth.common.vo.TrendVO(date,
                    Long.parseLong(String.valueOf(row.get("cnt"))),
                    Long.parseLong(String.valueOf(row.get("pts")))));
        }
        List<com.campus.growth.common.vo.TrendVO> list = new ArrayList<>(range);
        for (int i = 0; i < range; i++) {
            LocalDate date = from.plusDays(i);
            String key = date.toString();
            list.add(map.getOrDefault(key, new com.campus.growth.common.vo.TrendVO(key, 0L, 0L)));
        }
        return list;
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------
    /** 取消/超时统一回滚：库存 + 券 */
    private void rollbackResources(OrderMain order) {
        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderNo, order.getOrderNo()));
        for (OrderItem item : items) {
            benefitService.restoreStock(item.getGoodsId(), item.getQuantity());
        }
        List<UserCoupon> coupons = couponService.listByStatus(order.getUserId(), "USED").stream()
                .filter(c -> order.getOrderNo().equals(c.getOrderNo()))
                .toList();
        for (UserCoupon coupon : coupons) {
            couponService.unlockCoupon(coupon.getId(), order.getOrderNo());
        }
    }

    private BenefitGoods requireOnSaleGoods(Long goodsId) {
        BenefitGoods goods = benefitService.getFromDb(goodsId);
        if (goods == null) {
            throw BizException.of(ErrorCode.GOODS_NOT_FOUND);
        }
        if (goods.getStatus() == null || goods.getStatus() != BizConst.STATUS_ENABLED) {
            throw BizException.of(ErrorCode.GOODS_OFF_SHELF);
        }
        LocalDateTime now = LocalDateTime.now();
        if (goods.getStartTime() != null && now.isBefore(goods.getStartTime())) {
            throw BizException.of(ErrorCode.GOODS_OFF_SHELF, "该权益尚未开始兑换");
        }
        if (goods.getEndTime() != null && now.isAfter(goods.getEndTime())) {
            throw BizException.of(ErrorCode.GOODS_OFF_SHELF, "该权益已结束兑换");
        }
        return goods;
    }

    private OrderMain requireOwnOrder(String orderNo, Long userId) {
        OrderMain order = orderMainMapper.selectOne(new LambdaQueryWrapper<OrderMain>()
                .eq(OrderMain::getOrderNo, orderNo));
        if (order == null) {
            throw BizException.of(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!order.getUserId().equals(userId)) {
            // 越权访问按"订单不存在"处理，避免暴露他人订单是否存在
            throw BizException.of(ErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    private List<Long> normalizeCouponIds(OrderCreateDTO dto) {
        List<Long> ids = new ArrayList<>();
        if (dto.getCouponIds() != null) {
            ids.addAll(dto.getCouponIds().stream().filter(java.util.Objects::nonNull).distinct().toList());
        }
        if (ids.isEmpty() && dto.getCouponId() != null) {
            ids.add(dto.getCouponId());
        }
        return ids;
    }

    private PageResult<OrderVO> pageOrders(LambdaQueryWrapper<OrderMain> wrapper, long page, long size) {
        Page<OrderMain> result = orderMainMapper.selectPage(Page.of(page, size),
                wrapper.orderByDesc(OrderMain::getId));
        if (result.getRecords().isEmpty()) {
            return PageResult.empty(page, size);
        }
        List<String> orderNos = result.getRecords().stream().map(OrderMain::getOrderNo).toList();
        Map<String, List<OrderItem>> itemMap = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                        .in(OrderItem::getOrderNo, orderNos))
                .stream().collect(Collectors.groupingBy(OrderItem::getOrderNo));
        // 管理端需要展示昵称，批量查一次避免 N+1
        Map<Long, UserBriefVO> userMap = userQueryService
                .listBrief(result.getRecords().stream().map(OrderMain::getUserId).distinct().toList())
                .stream().collect(Collectors.toMap(UserBriefVO::getId, Function.identity()));

        List<OrderVO> records = new ArrayList<>(result.getRecords().size());
        for (OrderMain order : result.getRecords()) {
            OrderVO vo = toOrderVo(order, itemMap.getOrDefault(order.getOrderNo(), List.of()));
            UserBriefVO user = userMap.get(order.getUserId());
            vo.setNickname(user == null ? null : user.getNickname());
            records.add(vo);
        }
        return PageResult.of(records, result.getTotal(), page, size);
    }

    private PageResult<RefundVO> pageRefundList(LambdaQueryWrapper<OrderRefund> wrapper, long page, long size) {
        Page<OrderRefund> result = orderRefundMapper.selectPage(Page.of(page, size),
                wrapper.orderByDesc(OrderRefund::getId));
        if (result.getRecords().isEmpty()) {
            return PageResult.empty(page, size);
        }
        List<String> orderNos = result.getRecords().stream().map(OrderRefund::getOrderNo).toList();
        Map<String, OrderMain> orderMap = orderMainMapper.selectList(new LambdaQueryWrapper<OrderMain>()
                        .in(OrderMain::getOrderNo, orderNos))
                .stream().collect(Collectors.toMap(OrderMain::getOrderNo, Function.identity()));
        Map<String, List<OrderItem>> itemMap = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                        .in(OrderItem::getOrderNo, orderNos))
                .stream().collect(Collectors.groupingBy(OrderItem::getOrderNo));
        Map<Long, UserBriefVO> userMap = userQueryService
                .listBrief(result.getRecords().stream().map(OrderRefund::getUserId).distinct().toList())
                .stream().collect(Collectors.toMap(UserBriefVO::getId, Function.identity()));

        List<RefundVO> records = new ArrayList<>(result.getRecords().size());
        for (OrderRefund refund : result.getRecords()) {
            RefundVO vo = toRefundVo(refund);
            OrderMain order = orderMap.get(refund.getOrderNo());
            if (order != null) {
                vo.setPayPoint(order.getPayPoint());
            }
            List<OrderItem> items = itemMap.get(refund.getOrderNo());
            if (items != null && !items.isEmpty()) {
                vo.setGoodsTitle(items.get(0).getGoodsTitle());
            }
            UserBriefVO user = userMap.get(refund.getUserId());
            vo.setNickname(user == null ? null : user.getNickname());
            records.add(vo);
        }
        return PageResult.of(records, result.getTotal(), page, size);
    }

    private OrderVO toOrderVo(OrderMain order, List<OrderItem> items) {
        OrderVO vo = new OrderVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setUserId(order.getUserId());
        vo.setGoodsTotalPoint(order.getGoodsTotalPoint());
        vo.setDiscountPoint(order.getDiscountPoint());
        vo.setPayPoint(order.getPayPoint());
        vo.setCouponCode(order.getCouponCode());
        vo.setDiscountSnapshot(order.getDiscountSnapshot());
        vo.setStatus(order.getStatus());
        vo.setStatusDesc(statusDesc(order.getStatus()));
        vo.setReceiverName(order.getReceiverName());
        vo.setReceiverPhone(order.getReceiverPhone());
        vo.setReceiverAddress(order.getReceiverAddress());
        vo.setRemark(order.getRemark());
        vo.setCreateTime(order.getCreateTime());
        vo.setPayTime(order.getPayTime());
        vo.setCancelTime(order.getCancelTime());
        vo.setItems(items == null ? List.of() : items.stream().map(this::toItemVo).toList());
        vo.setRefundApplied(false);
        return vo;
    }

    private OrderItemVO toItemVo(OrderItem item) {
        OrderItemVO vo = new OrderItemVO();
        vo.setGoodsId(item.getGoodsId());
        vo.setGoodsCode(item.getGoodsCode());
        vo.setGoodsTitle(item.getGoodsTitle());
        vo.setGoodsCover(item.getGoodsCover());
        vo.setUnitPoint(item.getUnitPoint());
        vo.setQuantity(item.getQuantity());
        vo.setItemDiscount(item.getItemDiscount());
        vo.setItemAmount(item.getItemAmount());
        return vo;
    }

    private RefundVO toRefundVo(OrderRefund refund) {
        RefundVO vo = new RefundVO();
        vo.setId(refund.getId());
        vo.setRefundNo(refund.getRefundNo());
        vo.setOrderNo(refund.getOrderNo());
        vo.setUserId(refund.getUserId());
        vo.setRefundType(refund.getRefundType());
        vo.setRefundTypeDesc(RefundType.RETURN.name().equals(refund.getRefundType()) ? "退货" : "换货");
        vo.setReason(refund.getReason());
        vo.setStatus(refund.getStatus());
        vo.setStatusDesc(refundStatusDesc(refund.getStatus()));
        vo.setRefundPoint(refund.getRefundPoint());
        vo.setHandleRemark(refund.getHandleRemark());
        vo.setApplyTime(refund.getApplyTime());
        vo.setHandleTime(refund.getHandleTime());
        return vo;
    }

    private String statusDesc(String status) {
        return switch (status) {
            case "CREATED" -> "待支付";
            case "PAID" -> "已支付";
            case "CANCELLED" -> "已取消";
            case "FINISHED" -> "已完成";
            case "REFUNDED" -> "已退款";
            default -> status;
        };
    }

    private String refundStatusDesc(String status) {
        return switch (status) {
            case "APPLIED" -> "待审核";
            case "APPROVED" -> "已通过";
            case "REJECTED" -> "已驳回";
            case "REFUNDED" -> "已完成";
            default -> status;
        };
    }
}
