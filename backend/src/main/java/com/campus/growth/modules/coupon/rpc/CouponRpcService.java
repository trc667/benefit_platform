package com.campus.growth.modules.coupon.rpc;

import com.campus.growth.modules.coupon.rpc.dto.CouponTemplateDTO;
import com.campus.growth.modules.coupon.rpc.dto.UserCouponDTO;

import java.util.List;

/**
 * 优惠券 RPC 接口（Dubbo3 预留）。
 *
 * <h3>当前状态</h3>
 * <p>单体部署时 {@code dubbo.enabled=false}，本接口<b>不会被注册、不会被调用</b>；
 * 业务内部一律走 {@code CouponService}（本地方法调用）。</p>
 *
 * <h3>为什么预留</h3>
 * <p>优惠券是"发券库存 + 券状态"这两个状态的拥有者。将来如果把它拆成独立服务，
 * 订单域只需要这几个粗粒度、幂等、无分布式事务的方法即可完成协作，
 * 不需要把 {@code CouponService} 的全部细节暴露出去。</p>
 *
 * <h3>约束</h3>
 * <ul>
 *   <li>只暴露幂等操作，参数与返回值都是简单 DTO（可序列化），不传实体；</li>
 *   <li>不提供"跨服务事务"语义，超时/失败由调用方重试，靠幂等键兜底；</li>
 *   <li>直连模式：{@code dubbo://127.0.0.1:20880}，不部署注册中心。</li>
 * </ul>
 */
public interface CouponRpcService {

    /**
     * 发券（幂等：同一 userId + templateId + bizNo 只会成功一次）。
     *
     * @param bizNo 业务单号，用于幂等
     */
    UserCouponDTO receive(Long userId, Long templateId, String source, String bizNo);

    /** 查询用户券列表 */
    List<UserCouponDTO> listUserCoupons(Long userId, String status);

    /** 校验券是否可用 */
    UserCouponDTO validate(Long userId, Long couponId, int orderAmount, Long goodsId, String category);

    /** 核销券（下单） */
    boolean lock(Long userId, Long couponId, String orderNo);

    /** 回滚核销（取消订单） */
    void unlock(Long couponId, String orderNo);

    /** 查询券模板 */
    CouponTemplateDTO getTemplate(Long templateId);
}
