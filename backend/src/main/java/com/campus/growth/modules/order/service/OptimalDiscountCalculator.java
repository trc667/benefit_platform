package com.campus.growth.modules.order.service;

import com.campus.growth.common.constant.BizConst;
import com.campus.growth.common.enums.CouponScopeType;
import com.campus.growth.common.enums.CouponStatus;
import com.campus.growth.common.enums.CouponType;
import com.campus.growth.modules.coupon.entity.UserCoupon;
import com.campus.growth.modules.order.vo.DiscountPlanVO;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * 最优优惠组合计算器。
 *
 * <h3>问题</h3>
 * <p>用户可能持有十几张券，券之间还能叠加（满减 + 折扣 + 无门槛）。
 * 要在结算页告诉用户"怎么用最省"，本质是一个组合优化问题：
 * 枚举所有合法组合 → 逐个算价 → 取最优。组合数是 2^n，n=10 时就有 1023 种。</p>
 *
 * <h3>为什么用 CompletableFuture 并行</h3>
 * <p>每种组合的算价是纯 CPU 计算，彼此独立，天然适合并行。
 * 但并行必须落在<b>有界的业务线程池</b>上（{@code discountCalcExecutor}），
 * 不能用 {@code commonPool()}：那是给并行流用的，一旦有阻塞任务会拖垮整个 JVM。</p>
 *
 * <h3>剪枝策略</h3>
 * <ul>
 *   <li>先按门槛与适用范围过滤掉不可用券；</li>
 *   <li>候选券超过 {@link BizConst#MAX_COUPON_ENUMERATE} 张时，按"潜在优惠额"取前 N 张再枚举；</li>
 *   <li>叠加规则：同一类型券最多用 1 张（满减/折扣/无门槛各 1 张），组合上限 3 张。</li>
 * </ul>
 *
 * <h3>叠加顺序（业务约定）</h3>
 * <p>先减满减券 → 再打折 → 最后减无门槛券，每一步都基于"剩余金额"计算，
 * 保证优惠不会超过订单金额。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OptimalDiscountCalculator {

    private final @Qualifier("discountCalcExecutor") ThreadPoolTaskExecutor discountCalcExecutor;

    /**
     * 计算最优优惠方案。
     *
     * @param orderAmount 订单商品总额
     * @param coupons     用户持有的券（会在此方法内过滤）
     * @param goodsId     商品 ID，用于适用范围判断
     * @param category    商品分类
     */
    public Result calculate(int orderAmount, List<UserCoupon> coupons, Long goodsId, String category) {
        long start = System.currentTimeMillis();
        Result result = new Result();

        // 1. 过滤：状态、有效期、门槛、适用范围
        List<UserCoupon> usable = new ArrayList<>();
        if (coupons != null) {
            LocalDateTime now = LocalDateTime.now();
            for (UserCoupon coupon : coupons) {
                if (!CouponStatus.UNUSED.name().equals(coupon.getStatus())) {
                    continue;
                }
                if (coupon.getExpireTime() == null || coupon.getExpireTime().isBefore(now)) {
                    continue;
                }
                if (orderAmount < coupon.getThresholdPoint()) {
                    continue;
                }
                if (!CouponScopeType.matches(coupon.getScopeType(), coupon.getScopeValue(), goodsId, category)) {
                    continue;
                }
                usable.add(coupon);
            }
        }
        result.setCouponCount(usable.size());

        // 2. 剪枝：候选过多时按潜在优惠额取前 N
        if (usable.size() > BizConst.MAX_COUPON_ENUMERATE) {
            usable.sort(Comparator.comparingInt((UserCoupon c) -> potentialDiscount(c, orderAmount)).reversed());
            usable = new ArrayList<>(usable.subList(0, BizConst.MAX_COUPON_ENUMERATE));
        }

        // 3. 枚举合法组合（含空组合作为基线）
        List<List<UserCoupon>> combinations = enumerate(usable);
        result.setCombinationCount(combinations.size());

        // 4. 并行算价
        List<Future<DiscountPlanVO>> futures = new ArrayList<>(combinations.size());
        for (List<UserCoupon> combination : combinations) {
            futures.add(discountCalcExecutor.submit(() -> calcPlan(orderAmount, combination)));
        }
        List<DiscountPlanVO> plans = new ArrayList<>(combinations.size());
        for (Future<DiscountPlanVO> future : futures) {
            try {
                plans.add(future.get());
            } catch (Exception e) {
                // 单个组合算价失败不能影响整体：记录并跳过
                log.error("优惠组合算价失败", e);
            }
        }

        // 5. 排序并选出最优
        plans.sort(Comparator.comparingInt(DiscountPlanVO::getPayPoint)
                .thenComparing(Comparator.comparingInt(DiscountPlanVO::getDiscountPoint).reversed())
                .thenComparingInt(p -> p.getCouponIds() == null ? 0 : p.getCouponIds().size()));
        if (!plans.isEmpty()) {
            plans.get(0).setBest(true);
            result.setBest(plans.get(0));
        }
        result.setCandidates(plans);
        result.setTotalCostMs(System.currentTimeMillis() - start);
        log.debug("优惠组合计算完成 券={} 组合={} 最优实付={} 耗时={}ms", usable.size(), combinations.size(),
                result.getBest() == null ? null : result.getBest().getPayPoint(), result.getTotalCostMs());
        return result;
    }

    /**
     * 按"用户明确选中的券"计算优惠（下单时使用）。
     * <p>下单不接受前端传来的优惠金额，必须由服务端按同样的叠加规则重算，
     * 否则用户可以伪造 discount 把订单金额改成 0。</p>
     */
    public DiscountPlanVO calcSelected(int orderAmount, List<UserCoupon> selected) {
        if (selected == null || selected.isEmpty()) {
            return calcPlan(orderAmount, List.of());
        }
        if (selected.size() > BizConst.MAX_COUPON_COMBINATION_SIZE) {
            throw new IllegalArgumentException("单笔订单最多叠加 " + BizConst.MAX_COUPON_COMBINATION_SIZE + " 张券");
        }
        // 互斥校验：同类型券不能叠加
        long cash = selected.stream().filter(c -> CouponType.CASH.name().equals(c.getCouponType())).count();
        long discount = selected.stream().filter(c -> CouponType.DISCOUNT.name().equals(c.getCouponType())).count();
        long direct = selected.stream().filter(c -> CouponType.DIRECT.name().equals(c.getCouponType())).count();
        if (cash > 1 || discount > 1 || direct > 1) {
            throw new IllegalArgumentException("同类型优惠券不能叠加使用");
        }
        return calcPlan(orderAmount, selected);
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    /**
     * 枚举所有合法组合。
     * <p>用位掩码遍历 2^n 个子集，再按"同类型最多 1 张 + 总数不超过 3 张"过滤。</p>
     */
    private List<List<UserCoupon>> enumerate(List<UserCoupon> coupons) {
        List<List<UserCoupon>> result = new ArrayList<>();
        // 空组合：不使用任何券，作为基线方案
        result.add(List.of());
        int size = coupons.size();
        if (size == 0) {
            return result;
        }
        int total = 1 << size;
        for (int mask = 1; mask < total; mask++) {
            List<UserCoupon> combination = new ArrayList<>(3);
            int cash = 0;
            int discount = 0;
            int direct = 0;
            boolean valid = true;
            for (int i = 0; i < size; i++) {
                if ((mask & (1 << i)) == 0) {
                    continue;
                }
                UserCoupon coupon = coupons.get(i);
                switch (CouponType.valueOf(coupon.getCouponType())) {
                    case CASH -> cash++;
                    case DISCOUNT -> discount++;
                    case DIRECT -> direct++;
                }
                // 互斥规则：同类型只能一张
                if (cash > 1 || discount > 1 || direct > 1) {
                    valid = false;
                    break;
                }
                combination.add(coupon);
            }
            if (valid && !combination.isEmpty()
                    && combination.size() <= BizConst.MAX_COUPON_COMBINATION_SIZE) {
                result.add(combination);
            }
        }
        return result;
    }

    /** 计算单个组合的优惠与实付 */
    private DiscountPlanVO calcPlan(int orderAmount, List<UserCoupon> combination) {
        long start = System.currentTimeMillis();
        int remaining = orderAmount;
        int totalDiscount = 0;
        List<Long> ids = new ArrayList<>(combination.size());
        List<String> titles = new ArrayList<>(combination.size());

        // 叠加顺序：满减 → 折扣 → 无门槛
        for (String type : new String[]{CouponType.CASH.name(), CouponType.DISCOUNT.name(), CouponType.DIRECT.name()}) {
            for (UserCoupon coupon : combination) {
                if (!type.equals(coupon.getCouponType())) {
                    continue;
                }
                int discount = CouponType.calcDiscount(CouponType.valueOf(coupon.getCouponType()),
                        remaining, coupon.getFaceValue(), coupon.getDiscountRate(), coupon.getMaxDiscount());
                discount = Math.min(discount, remaining);
                remaining -= discount;
                totalDiscount += discount;
                ids.add(coupon.getId());
                titles.add(coupon.getCouponTitle());
            }
        }

        DiscountPlanVO plan = new DiscountPlanVO();
        plan.setCouponIds(ids);
        plan.setCouponTitles(titles);
        plan.setDesc(titles.isEmpty() ? "不使用优惠券" : String.join(" + ", titles));
        plan.setDiscountPoint(totalDiscount);
        plan.setPayPoint(Math.max(0, orderAmount - totalDiscount));
        plan.setBest(false);
        plan.setCostMs(System.currentTimeMillis() - start);
        return plan;
    }

    /** 券的"潜在优惠额"，用于剪枝排序 */
    private int potentialDiscount(UserCoupon coupon, int orderAmount) {
        return CouponType.calcDiscount(CouponType.valueOf(coupon.getCouponType()), orderAmount,
                coupon.getFaceValue(), coupon.getDiscountRate(), coupon.getMaxDiscount());
    }

    /** 计算结果 */
    @Getter
    public static class Result {
        private DiscountPlanVO best;
        private List<DiscountPlanVO> candidates = List.of();
        private int couponCount;
        private int combinationCount;
        private long totalCostMs;

        void setBest(DiscountPlanVO best) {
            this.best = best;
        }

        void setCandidates(List<DiscountPlanVO> candidates) {
            this.candidates = candidates;
        }

        void setCouponCount(int couponCount) {
            this.couponCount = couponCount;
        }

        void setCombinationCount(int combinationCount) {
            this.combinationCount = combinationCount;
        }

        void setTotalCostMs(long totalCostMs) {
            this.totalCostMs = totalCostMs;
        }
    }
}
