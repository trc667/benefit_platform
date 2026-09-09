package com.campus.growth.modules.coupon.job;

import com.campus.growth.modules.coupon.service.CouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 优惠券过期任务。
 *
 * <h3>为什么必须落库而不是只在查询时过滤</h3>
 * <p>查询时用 {@code expire_time > now()} 过滤能保证"不可用"，但状态字段会一直是 UNUSED：
 * 用户看到过期券仍显示"未使用"，`/coupon/mine?status=EXPIRED` 永远查不到，
 * 运营的券核销率统计也会偏高。所以需要把状态真正推进到 EXPIRED。</p>
 *
 * <h3>批处理</h3>
 * <p>每次最多处理 500 条，避免一次更新锁太多行；每 10 分钟跑一轮，
 * 券过期的时效性对业务不敏感（晚 10 分钟不影响用户，因为查询时已按时间过滤）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponExpireJob {

    private static final int BATCH_SIZE = 500;

    private final CouponService couponService;

    @Scheduled(fixedDelay = 600_000, initialDelay = 30_000)
    public void expireCoupons() {
        try {
            int total = 0;
            // 循环直到本轮没有可过期的券，保证积压时能快速追平
            for (int i = 0; i < 20; i++) {
                int count = couponService.expireOverdueCoupons(BATCH_SIZE);
                total += count;
                if (count < BATCH_SIZE) {
                    break;
                }
            }
            if (total > 0) {
                log.info("优惠券过期处理完成，共 {} 张", total);
            }
        } catch (Exception e) {
            log.error("优惠券过期任务异常", e);
        }
    }
}
