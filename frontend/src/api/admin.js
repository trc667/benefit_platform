import request from '@/utils/request'

/** 管理端：/admin/**（角色 OPERATOR / ADMIN） */

/* ---------------- 仪表盘 ---------------- */

/** 概览 → {userCount,todaySignCount,orderCount,todayOrderCount,pointIssued,couponIssued,goodsCount,refundPending} */
export const getDashboardOverview = () => request.get('/admin/dashboard/overview')

/** 签到趋势 → [{date,count}] */
export const getSigninTrend = (days = 7) => request.get('/admin/dashboard/signin-trend', { params: { days } })

/** 订单趋势 → [{date,count,point}] */
export const getOrderTrend = (days = 7) => request.get('/admin/dashboard/order-trend', { params: { days } })

/* ---------------- 用户管理 ---------------- */

export const getAdminUserPage = (params) => request.get('/admin/user/page', { params })

/** 启停用户 → {userId,status} */
export const updateUserStatus = (data) => request.post('/admin/user/status', data)

/* ---------------- 商品管理 ---------------- */

export const getAdminGoodsPage = (params) => request.get('/admin/benefit/goods/page', { params })

/** 新增/编辑（id 为空即新增） */
export const saveGoods = (data) => request.post('/admin/benefit/goods/save', data)

/** 上下架 → {id,status} */
export const updateGoodsStatus = (data) => request.post('/admin/benefit/goods/status', data)

/* ---------------- 券模板管理 ---------------- */

export const getAdminCouponTemplatePage = (params) => request.get('/admin/coupon/template/page', { params })

export const saveCouponTemplate = (data) => request.post('/admin/coupon/template/save', data)

export const updateCouponTemplateStatus = (data) => request.post('/admin/coupon/template/status', data)

/** 定向发放 → {templateId,userIds:[],count} */
export const grantCoupon = (data) => request.post('/admin/coupon/grant', data)

/** 领用统计 → [{templateId,title,totalCount,issuedCount,usedCount,useRate}] */
export const getCouponStat = () => request.get('/admin/coupon/stat')

/* ---------------- 兑换码批次 ---------------- */

export const getRedeemBatchPage = (params) => request.get('/admin/redeem/batch/page', { params })

export const createRedeemBatch = (data) => request.post('/admin/redeem/batch/create', data)

/** 生成/预览码（从当前进度起） */
export const previewRedeemCodes = (batchNo, count = 10) =>
  request.get(`/admin/redeem/batch/${batchNo}/codes`, { params: { count } })

export const updateRedeemBatchStatus = (data) => request.post('/admin/redeem/batch/status', data)

/* ---------------- 任务配置 ---------------- */

export const getAdminTaskPage = (params) => request.get('/admin/task/page', { params })

export const saveTask = (data) => request.post('/admin/task/save', data)

export const updateTaskStatus = (data) => request.post('/admin/task/status', data)

/* ---------------- 订单与售后 ---------------- */

export const getAdminOrderPage = (params) => request.get('/admin/order/page', { params })

/** 管理端订单详情（不做属主校验，运营可查任意用户订单） */
export const getAdminOrderDetail = (orderNo) => request.get(`/admin/order/${orderNo}`)

/** 运营核销订单：PAID → FINISHED */
export const finishAdminOrder = (orderNo) => request.post('/admin/order/finish', { orderNo })

/** 重建积分排行榜（以积分账户与流水为真源） */
export const rebuildRank = () => request.post('/admin/point/rank/rebuild')

/** 手动调整积分 → {userId,changePoint,bizNo?,reason}（正加负扣，走流水 + 幂等键） */
export const adjustUserPoint = (data) => request.post('/admin/point/adjust', data)

export const getAdminRefundPage = (params) => request.get('/admin/order/refund/page', { params })

/** 售后审核 → {refundNo,approved,handleRemark,refundPoint} */
export const handleRefund = (data) => request.post('/admin/order/refund/handle', data)

/* ---------------- 本地消息表 ---------------- */

export const getOutboxPage = (params) => request.get('/admin/mq/outbox/page', { params })

/** 手动补偿 → {id} */
export const retryOutbox = (id) => request.post('/admin/mq/outbox/retry', { id })

/* ---------------- 缓存与日志 ---------------- */

/** 二级缓存命中率/回源次数 */
export const getCacheStats = () => request.get('/admin/cache/stats')

export const getOperationLogPage = (params) => request.get('/admin/operation/log/page', { params })
