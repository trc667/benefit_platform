import request from '@/utils/request'

/** 订单与售后：/order */

/** 结算预览 → {goodsTotal,bestPlan,candidates,availableCoupons,balance,balanceEnough} */
export const previewSettle = (params) => request.get('/order/settle/preview', { params })

/** 创建订单 → {orderNo,payPoint} */
export const createOrder = (data) => request.post('/order/create', data)

/** 支付 → {orderNo,payPoint,balance} */
export const payOrder = (orderNo) => request.post('/order/pay', { orderNo })

/** 取消订单 */
export const cancelOrder = (orderNo) => request.post('/order/cancel', { orderNo })

/** 确认完成（收货/核销）：PAID → FINISHED */
export const finishOrder = (orderNo) => request.post('/order/finish', { orderNo })

/** 我的订单分页 → PageResult */
export const getOrderPage = (params) => request.get('/order/page', { params })

/** 订单详情（含明细、券、优惠快照） */
export const getOrderDetail = (orderNo) => request.get(`/order/${orderNo}`)

/** 申请售后 → {refundNo} */
export const applyRefund = (data) => request.post('/order/refund/apply', data)

/** 我的退换单分页 → PageResult */
export const getRefundPage = (params) => request.get('/order/refund/page', { params })
