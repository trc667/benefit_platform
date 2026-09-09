import request from '@/utils/request'

/** 优惠券：/coupon */

/** 可领取券模板分页 → PageResult（含 received、remainCount） */
export const getCouponTemplates = (params) => request.get('/coupon/templates', { params })

/** 领券 → UserCouponVO */
export const receiveCoupon = (templateId) => request.post('/coupon/receive', { templateId })

/** 我的券 → PageResult（status: UNUSED|USED|EXPIRED） */
export const getMyCoupons = (params) => request.get('/coupon/mine', { params })

/** 结算页可用券（已按门槛过滤） → 列表 */
export const getAvailableCoupons = (amount) => request.get('/coupon/available', { params: { amount } })
