package com.campus.growth.modules.order.service;

import com.campus.growth.common.result.PageResult;
import com.campus.growth.modules.order.dto.OrderCreateDTO;
import com.campus.growth.modules.order.dto.RefundApplyDTO;
import com.campus.growth.modules.order.dto.RefundHandleDTO;
import com.campus.growth.modules.order.vo.OrderVO;
import com.campus.growth.modules.order.vo.RefundVO;
import com.campus.growth.modules.order.vo.SettlePreviewVO;

/**
 * 订单服务（本地 Service，单体唯一入口；RPC 接口只是它的远程壳）。
 */
public interface OrderService {

    // ---------------- 学生端 ----------------

    /** 结算页预览：最优优惠组合 + 候选方案 */
    SettlePreviewVO preview(Long goodsId, int quantity);

    /** 下单（预扣库存 + 锁券，状态 CREATED） */
    OrderVO create(OrderCreateDTO dto);

    /** 支付（扣积分 → PAID，发事件） */
    OrderVO pay(String orderNo);

    /** 取消（回滚库存与券） */
    void cancel(String orderNo);

    /** 学生确认完成（收货/核销） */
    void finish(String orderNo);

    PageResult<OrderVO> pageMine(String status, long page, long size);

    OrderVO detail(String orderNo);

    /** 申请售后 */
    String applyRefund(RefundApplyDTO dto);

    PageResult<RefundVO> pageMyRefunds(long page, long size);

    // ---------------- 管理端 ----------------

    PageResult<OrderVO> pageAdmin(String orderNo, String status, long page, long size);

    /** 管理端订单详情（不做属主校验，运营需要查看任意用户订单） */
    OrderVO detailForAdmin(String orderNo);

    /** 运营核销订单 */
    void finishByAdmin(String orderNo);

    PageResult<RefundVO> pageRefunds(String status, long page, long size);

    /** 处理售后 */
    void handleRefund(RefundHandleDTO dto);

    long countAll();

    long countToday();

    long sumPaidPoints();

    long countPendingRefund();

    /** 超时未支付订单自动取消（定时任务调用） */
    int cancelTimeoutOrders(int timeoutMinutes);

    /** 近 N 天订单趋势（数量 + 消耗积分） */
    java.util.List<com.campus.growth.common.vo.TrendVO> orderTrend(int days);
}
