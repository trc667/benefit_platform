package com.campus.growth.modules.order.service;

import com.campus.growth.modules.coupon.entity.UserCoupon;
import com.campus.growth.modules.order.vo.DiscountPlanVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 最优优惠组合计算测试。
 */
class OptimalDiscountCalculatorTest {

    private ThreadPoolTaskExecutor executor;
    private OptimalDiscountCalculator calculator;

    @BeforeEach
    void setUp() {
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("test-discount-");
        executor.initialize();
        calculator = new OptimalDiscountCalculator(executor);
    }

    @AfterEach
    void tearDown() {
        executor.shutdown();
    }

    @Test
    @DisplayName("单张满减券：门槛内可用，优惠等于面额")
    void singleCashCoupon() {
        UserCoupon coupon = cash(1L, 60, 300);
        OptimalDiscountCalculator.Result result = calculator.calculate(500, List.of(coupon), 1L, "STUDY");
        assertNotNull(result.getBest());
        assertEquals(60, result.getBest().getDiscountPoint());
        assertEquals(440, result.getBest().getPayPoint());
        assertEquals(1, result.getBest().getCouponIds().size());
    }

    @Test
    @DisplayName("未达门槛的券不参与枚举")
    void belowThreshold() {
        UserCoupon coupon = cash(1L, 60, 1000);
        OptimalDiscountCalculator.Result result = calculator.calculate(500, List.of(coupon), 1L, "STUDY");
        assertEquals(0, result.getCouponCount());
        // 只有"不使用优惠券"这一种方案
        assertEquals(1, result.getCombinationCount());
        assertEquals(500, result.getBest().getPayPoint());
    }

    @Test
    @DisplayName("满减 + 折扣叠加，且按剩余金额计算折扣")
    void stackCashAndDiscount() {
        UserCoupon cash = cash(1L, 60, 300);
        UserCoupon discount = discount(2L, 85, 100, 200);
        OptimalDiscountCalculator.Result result =
                calculator.calculate(500, List.of(cash, discount), 1L, "STUDY");
        // 500 - 60 = 440，再打 8.5 折 → 优惠 66，总优惠 126，实付 374
        assertEquals(126, result.getBest().getDiscountPoint());
        assertEquals(374, result.getBest().getPayPoint());
        assertEquals(2, result.getBest().getCouponIds().size());
    }

    @Test
    @DisplayName("同类型券互斥：两张满减只允许选一张")
    void mutuallyExclusiveSameType() {
        UserCoupon a = cash(1L, 60, 300);
        UserCoupon b = cash(2L, 120, 500);
        OptimalDiscountCalculator.Result result = calculator.calculate(600, List.of(a, b), 1L, "STUDY");
        assertEquals(1, result.getBest().getCouponIds().size());
        assertEquals(120, result.getBest().getDiscountPoint(), "应选面额更大的那张");
        assertEquals(480, result.getBest().getPayPoint());
    }

    @Test
    @DisplayName("分类不匹配的券被过滤")
    void scopeFilter() {
        UserCoupon study = discount(1L, 85, 100, 200);
        study.setScopeType("CATEGORY");
        study.setScopeValue("STUDY");
        OptimalDiscountCalculator.Result result = calculator.calculate(500, List.of(study), 1L, "FOOD");
        assertEquals(0, result.getCouponCount());
    }

    @Test
    @DisplayName("最优方案被标记，候选按实付升序")
    void bestFlagAndOrder() {
        List<UserCoupon> coupons = new ArrayList<>();
        coupons.add(cash(1L, 60, 300));
        coupons.add(discount(2L, 85, 100, 200));
        coupons.add(direct(3L, 50));
        OptimalDiscountCalculator.Result result = calculator.calculate(500, coupons, 1L, "STUDY");

        assertTrue(result.getBest().getBest());
        List<DiscountPlanVO> candidates = result.getCandidates();
        for (int i = 1; i < candidates.size(); i++) {
            assertTrue(candidates.get(i - 1).getPayPoint() <= candidates.get(i).getPayPoint(),
                    "候选应按实付升序");
        }
        // 三种类型各一张是最优：60 + (440*0.15=66) + 50 = 176 → 实付 324
        assertEquals(324, result.getBest().getPayPoint());
    }

    @Test
    @DisplayName("显式选券下单：重算优惠，超 3 张直接拒绝")
    void calcSelected() {
        UserCoupon coupon = cash(1L, 60, 300);
        DiscountPlanVO plan = calculator.calcSelected(500, List.of(coupon));
        assertEquals(60, plan.getDiscountPoint());

        List<UserCoupon> tooMany = List.of(cash(1L, 10, 0), cash(2L, 10, 0),
                direct(3L, 10), direct(4L, 10));
        try {
            calculator.calcSelected(500, tooMany);
            org.junit.jupiter.api.Assertions.fail("应当拒绝超过 3 张券");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("最多"));
        }
    }

    // ------------------------------------------------------------------
    // 测试数据构造
    // ------------------------------------------------------------------

    private UserCoupon cash(Long id, int faceValue, int threshold) {
        UserCoupon coupon = base(id, "CASH");
        coupon.setFaceValue(faceValue);
        coupon.setThresholdPoint(threshold);
        return coupon;
    }

    private UserCoupon discount(Long id, int rate, int threshold, int maxDiscount) {
        UserCoupon coupon = base(id, "DISCOUNT");
        coupon.setDiscountRate(rate);
        coupon.setThresholdPoint(threshold);
        coupon.setMaxDiscount(maxDiscount);
        return coupon;
    }

    private UserCoupon direct(Long id, int faceValue) {
        UserCoupon coupon = base(id, "DIRECT");
        coupon.setFaceValue(faceValue);
        coupon.setThresholdPoint(0);
        return coupon;
    }

    private UserCoupon base(Long id, String type) {
        UserCoupon coupon = new UserCoupon();
        coupon.setId(id);
        coupon.setCouponTitle("测试券" + id);
        coupon.setCouponType(type);
        coupon.setStatus("UNUSED");
        coupon.setScopeType("ALL");
        coupon.setExpireTime(LocalDateTime.now().plusDays(30));
        coupon.setFaceValue(0);
        coupon.setDiscountRate(100);
        coupon.setThresholdPoint(0);
        coupon.setMaxDiscount(0);
        return coupon;
    }
}
