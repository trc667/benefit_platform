/**
 * 字典与枚举映射，字段取值全部来自后端枚举定义（backend/.../common/enums）。
 * 页面里统一 import 这里的 map，避免各处硬编码字符串。
 */

/** 角色：STUDENT / OPERATOR / ADMIN */
export const ROLE_MAP = {
  STUDENT: { label: '学生', type: 'info' },
  OPERATOR: { label: '运营', type: 'warning' },
  ADMIN: { label: '管理员', type: 'danger' }
}

export const ROLE_OPTIONS = Object.entries(ROLE_MAP).map(([value, item]) => ({ value, label: item.label }))

/** 订单状态：CREATED / PAID / CANCELLED / FINISHED */
export const ORDER_STATUS_MAP = {
  CREATED: { label: '待支付', type: 'warning' },
  PAID: { label: '已支付', type: 'primary' },
  CANCELLED: { label: '已取消', type: 'info' },
  FINISHED: { label: '已完成', type: 'success' }
}

export const ORDER_STATUS_OPTIONS = Object.entries(ORDER_STATUS_MAP).map(([value, item]) => ({
  value,
  label: item.label
}))

/** 用户券状态：UNUSED / USED / EXPIRED */
export const COUPON_STATUS_MAP = {
  UNUSED: { label: '未使用', type: 'primary' },
  USED: { label: '已使用', type: 'info' },
  EXPIRED: { label: '已过期', type: 'info' }
}

/** 券类型：CASH 满减 / DISCOUNT 折扣 / DIRECT 无门槛 */
export const COUPON_TYPE_MAP = {
  CASH: { label: '满减券', type: 'danger' },
  DISCOUNT: { label: '折扣券', type: 'warning' },
  DIRECT: { label: '无门槛券', type: 'success' }
}

export const COUPON_TYPE_OPTIONS = Object.entries(COUPON_TYPE_MAP).map(([value, item]) => ({
  value,
  label: item.label
}))

/** 券适用范围：ALL / GOODS / CATEGORY */
export const COUPON_SCOPE_MAP = {
  ALL: { label: '全场通用' },
  GOODS: { label: '指定商品' },
  CATEGORY: { label: '指定分类' }
}

export const COUPON_SCOPE_OPTIONS = Object.entries(COUPON_SCOPE_MAP).map(([value, item]) => ({
  value,
  label: item.label
}))

/** 券有效期类型：FIXED / RELATIVE */
export const COUPON_VALID_TYPE_MAP = {
  FIXED: { label: '固定区间' },
  RELATIVE: { label: '领取后 N 天' }
}

export const COUPON_VALID_TYPE_OPTIONS = Object.entries(COUPON_VALID_TYPE_MAP).map(([value, item]) => ({
  value,
  label: item.label
}))

/** 商品分类：与 sql/data.sql 的 category 保持一致 */
export const GOODS_CATEGORY_MAP = {
  STUDY: { label: '学习', cover: 'STUDY' },
  FOOD: { label: '餐饮', cover: 'FOOD' },
  LIFE: { label: '生活', cover: 'LIFE' },
  SPORT: { label: '运动', cover: 'SPORT' },
  OTHER: { label: '其他', cover: 'OTHER' }
}

export const GOODS_CATEGORY_OPTIONS = Object.entries(GOODS_CATEGORY_MAP).map(([value, item]) => ({
  value,
  label: item.label
}))

/** 商品排序：default | hot | priceAsc | priceDesc */
export const GOODS_SORT_OPTIONS = [
  { value: 'default', label: '综合排序' },
  { value: 'hot', label: '人气优先' },
  { value: 'priceAsc', label: '积分从低到高' },
  { value: 'priceDesc', label: '积分从高到低' }
]

/** 任务周期：DAILY / WEEKLY / ONCE */
export const TASK_TYPE_MAP = {
  DAILY: { label: '每日任务', type: 'success' },
  WEEKLY: { label: '每周任务', type: 'warning' },
  ONCE: { label: '一次性任务', type: 'info' }
}

export const TASK_TYPE_OPTIONS = Object.entries(TASK_TYPE_MAP).map(([value, item]) => ({
  value,
  label: item.label
}))

/** 用户任务状态：0 进行中 1 已完成待领 2 已领取 */
export const TASK_STATUS_MAP = {
  0: { label: '进行中', type: 'info' },
  1: { label: '待领奖', type: 'warning' },
  2: { label: '已领取', type: 'success' }
}

/** 积分流水业务类型：PointBizType */
export const POINT_BIZ_MAP = {
  SIGNIN: { label: '签到', type: 'success' },
  TASK: { label: '任务奖励', type: 'primary' },
  REDEEM: { label: '兑换码', type: 'warning' },
  ORDER_PAY: { label: '下单扣减', type: 'danger' },
  ORDER_REFUND: { label: '售后退回', type: 'success' },
  ADMIN: { label: '后台调整', type: 'info' }
}

export const POINT_BIZ_OPTIONS = Object.entries(POINT_BIZ_MAP).map(([value, item]) => ({
  value,
  label: item.label
}))

/** 售后类型：RETURN 退货 / EXCHANGE 换货 */
export const REFUND_TYPE_MAP = {
  RETURN: { label: '退货', type: 'danger' },
  EXCHANGE: { label: '换货', type: 'warning' }
}

export const REFUND_TYPE_OPTIONS = Object.entries(REFUND_TYPE_MAP).map(([value, item]) => ({
  value,
  label: item.label
}))

/** 退换单状态：APPLIED / APPROVED / REJECTED / REFUNDED */
export const REFUND_STATUS_MAP = {
  APPLIED: { label: '待审核', type: 'warning' },
  APPROVED: { label: '已通过', type: 'primary' },
  REJECTED: { label: '已驳回', type: 'danger' },
  REFUNDED: { label: '已退款', type: 'success' }
}

export const REFUND_STATUS_OPTIONS = Object.entries(REFUND_STATUS_MAP).map(([value, item]) => ({
  value,
  label: item.label
}))

/** 兑换码奖励类型：POINT / COUPON / GOODS */
export const REDEEM_BIZ_MAP = {
  POINT: { label: '积分', type: 'success' },
  COUPON: { label: '优惠券', type: 'warning' },
  GOODS: { label: '权益商品', type: 'primary' }
}

export const REDEEM_BIZ_OPTIONS = Object.entries(REDEEM_BIZ_MAP).map(([value, item]) => ({
  value,
  label: item.label
}))

/** 本地消息表状态：NEW / SENT / FAILED / DEAD */
export const OUTBOX_STATUS_MAP = {
  NEW: { label: '待发送', type: 'warning' },
  SENT: { label: '已发送', type: 'success' },
  FAILED: { label: '发送失败', type: 'danger' },
  DEAD: { label: '死信', type: 'danger' }
}

export const OUTBOX_STATUS_OPTIONS = Object.entries(OUTBOX_STATUS_MAP).map(([value, item]) => ({
  value,
  label: item.label
}))

/** 通用启停状态：1 启用 / 0 停用 */
export const ENABLE_STATUS_MAP = {
  1: { label: '启用', type: 'success' },
  0: { label: '停用', type: 'info' }
}

/** 通用：按字典取值，取不到给原值 */
export const dictLabel = (map, value, fallback = '-') => map?.[value]?.label ?? (value ?? fallback)

export const dictType = (map, value, fallback = 'info') => map?.[value]?.type ?? fallback
