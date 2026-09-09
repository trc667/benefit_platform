package com.campus.growth.modules.order.rpc;

import com.campus.growth.modules.order.rpc.dto.OrderCreateRpcDTO;
import com.campus.growth.modules.order.rpc.dto.OrderRpcDTO;

/**
 * 订单 RPC 接口（Dubbo3 预留）。
 *
 * <h3>当前状态</h3>
 * <p>单体部署时 {@code dubbo.enabled=false}，本接口不会被注册也不会被调用；
 * 业务内部一律走 {@code OrderService}。</p>
 *
 * <h3>为什么订单也要预留</h3>
 * <p>订单是典型的"聚合根"：它需要读商品价格、扣库存、核销券、扣积分。
 * 一旦优惠券域先被拆出去，订单域就必须通过 RPC 调券服务。
 * 提前把"创建订单""查订单""取消订单"这几个幂等用例的契约定下来，
 * 拆分时只需要把实现搬到新进程，调用方改一行注入即可。</p>
 *
 * <h3>注意</h3>
 * <p>接口只暴露<b>幂等</b>操作；下单这类需要跨域事务的操作，
 * 拆分时应改成"本地建单 + 异步编排"，而不是把事务跨到 RPC 上。</p>
 */
public interface OrderRpcService {

    /** 创建订单（幂等：同一 requestId 只创建一次） */
    OrderRpcDTO createOrder(OrderCreateRpcDTO dto);

    /** 按订单号查询 */
    OrderRpcDTO getByOrderNo(String orderNo);

    /** 取消订单（幂等） */
    boolean cancelOrder(String orderNo, String reason);

    /** 确认支付（幂等） */
    OrderRpcDTO payOrder(String orderNo);
}
