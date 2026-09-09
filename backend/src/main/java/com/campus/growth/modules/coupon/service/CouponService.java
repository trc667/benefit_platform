package com.campus.growth.modules.coupon.service;

import com.campus.growth.common.result.PageResult;
import com.campus.growth.modules.coupon.dto.CouponSaveDTO;
import com.campus.growth.modules.coupon.entity.UserCoupon;
import com.campus.growth.modules.coupon.vo.CouponStatVO;
import com.campus.growth.modules.coupon.vo.CouponTemplateVO;
import com.campus.growth.modules.coupon.vo.UserCouponVO;

import java.util.List;

/**
 * 优惠券服务（本地 Service，单体唯一入口；RPC 接口只是它的远程壳）。
 */
public interface CouponService {

    // ---------------- 学生端 ----------------

    /** 可领取的券模板列表 */
    PageResult<CouponTemplateVO> pageTemplates(long page, long size);

    /** 领取优惠券（分布式锁 + 事务，防超发） */
    UserCouponVO receive(Long templateId);

    /**
     * 按来源发券（兑换码 / 后台发放复用同一套防超发逻辑）。
     *
     * @param source RECEIVE / REDEEM / ADMIN
     */
    UserCouponVO receiveBySource(Long userId, Long templateId, String source);

    /** 我的券 */
    PageResult<UserCouponVO> pageMine(String status, long page, long size);

    /**
     * 结算页可用券（已按门槛/适用范围过滤，并预算好可抵扣金额）。
     *
     * @param orderAmount 订单商品总额
     */
    List<UserCouponVO> availableCoupons(Long userId, int orderAmount, Long goodsId, String category);

    /** 校验并返回可用券，不可用抛业务异常（下单时调用） */
    UserCoupon getUsable(Long userId, Long couponId, int orderAmount, Long goodsId, String category);

    /** 核销券（下单）：条件更新，防止一券多用 */
    boolean lockCoupon(Long userId, Long couponId, String orderNo);

    /** 取消订单时回滚券状态 */
    void unlockCoupon(Long couponId, String orderNo);

    /** 按 ID 批量查询（结算页算价用） */
    List<UserCoupon> listByIds(Long userId, List<Long> couponIds);

    /** 按状态查询用户券（RPC 用） */
    List<UserCoupon> listByStatus(Long userId, String status);

    // ---------------- 管理端 ----------------

    PageResult<CouponTemplateVO> pageForAdmin(String keyword, Integer status, long page, long size);

    Long saveTemplate(CouponSaveDTO dto);

    void updateStatus(Long templateId, Integer status);

    /** 定向发放，返回成功发放张数 */
    int grant(Long templateId, List<Long> userIds, Integer count);

    /** 券发放/核销统计 */
    List<CouponStatVO> stat();

    /** 已发放券总数（仪表盘用） */
    long countIssued();

    /**
     * 把已过期但仍为 UNUSED 的券批量置为 EXPIRED（定时任务调用）。
     *
     * @return 本次处理条数
     */
    int expireOverdueCoupons(int batchSize);
}
