package com.campus.growth.modules.coupon.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 锁顺序实验测试：验证"锁在事务内会超发、锁包住事务不会超发"。
 *
 * <p>这个测试的价值在于把"切面顺序"这个看不见的东西变成可断言的结论。</p>
 */
class LockOrderDemoServiceTest {

    private final LockOrderDemoService service = new LockOrderDemoService();

    @Test
    @DisplayName("锁在事务内 → 超发")
    void wrongOrderOversells() {
        LockOrderDemoService.DemoResult result = service.run(10, 50);
        LockOrderDemoService.ModeResult wrong = result.getWrongOrder();
        assertEquals(50, wrong.getSuccessCount(), "锁提前释放时所有线程都以为自己抢到了");
        assertTrue(wrong.getOversold() > 0, "应当复现超发");
    }

    @Test
    @DisplayName("锁包住事务 → 严格不超发")
    void rightOrderKeepsConsistency() {
        LockOrderDemoService.DemoResult result = service.run(10, 50);
        LockOrderDemoService.ModeResult right = result.getRightOrder();
        assertEquals(10, right.getSuccessCount(), "库存 10 就只能成功 10 次");
        assertEquals(0, right.getOversold());
        assertEquals(0, right.getFinalStock());
    }

    @Test
    @DisplayName("并发数小于库存时全部成功")
    void lessThreadsThanStock() {
        LockOrderDemoService.DemoResult result = service.run(100, 20);
        assertEquals(20, result.getRightOrder().getSuccessCount());
        assertEquals(0, result.getRightOrder().getOversold());
    }
}
