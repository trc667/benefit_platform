package com.campus.growth.modules.order.job;

import com.campus.growth.modules.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 超时未支付订单自动取消。
 *
 * <h3>为什么必须有这个任务</h3>
 * <p>下单时已经预扣了库存和优惠券。如果用户下单后不支付也不取消，
 * 这些资源会被永久占用。定时补偿是"预扣 + 超时释放"模式的标准配套。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutJob {

    private final OrderService orderService;

    @Value("${campus.order.timeout-minutes:15}")
    private int timeoutMinutes;

    /** 每 5 分钟扫一次，单次最多处理 200 笔，避免长事务 */
    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    public void cancelTimeoutOrders() {
        try {
            int count = orderService.cancelTimeoutOrders(timeoutMinutes);
            if (count > 0) {
                log.info("超时订单清理完成，共 {} 笔", count);
            }
        } catch (Exception e) {
            log.error("超时订单清理任务异常", e);
        }
    }
}
