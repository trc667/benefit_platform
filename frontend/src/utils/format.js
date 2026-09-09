/**
 * 通用格式化工具。后端时间格式统一 yyyy-MM-dd HH:mm:ss，金额/积分均为整数。
 */

const pad = (n) => String(n).padStart(2, '0')

/** 兼容 "2026-09-09 10:00:00" 与 ISO 字符串，Safari 下需把空格换成 T */
export const toDate = (value) => {
  if (!value) return null
  if (value instanceof Date) return value
  if (typeof value === 'number') return new Date(value)
  const text = String(value).trim()
  const normalized = /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(text) ? text.replace(' ', 'T') : text
  const date = new Date(normalized)
  return Number.isNaN(date.getTime()) ? null : date
}

/** yyyy-MM-dd HH:mm:ss */
export const formatDateTime = (value, fallback = '-') => {
  const d = toDate(value)
  if (!d) return fallback
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(
    d.getMinutes()
  )}:${pad(d.getSeconds())}`
}

/** yyyy-MM-dd */
export const formatDate = (value, fallback = '-') => {
  const d = toDate(value)
  if (!d) return fallback
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** yyyy-MM，签到日历 month 参数即此格式 */
export const formatMonth = (value) => {
  const d = toDate(value) || new Date()
  return `${d.getFullYear()}${pad(d.getMonth() + 1)}`
}

/** 数值占位，避免 null/undefined 直接渲染成空 */
export const num = (value, fallback = 0) => {
  const n = Number(value)
  return Number.isFinite(n) ? n : fallback
}

/** 积分展示：1234 -> "1,234" */
export const formatPoint = (value, fallback = '0') => {
  if (value === null || value === undefined || value === '') return fallback
  return num(value).toLocaleString('zh-CN')
}

/** 带符号的积分变动 */
export const formatChangePoint = (value) => {
  const n = num(value)
  return `${n > 0 ? '+' : ''}${n.toLocaleString('zh-CN')}`
}

/** 券面额文案：满减 / 折扣 / 无门槛 */
export const formatCouponFace = (coupon) => {
  if (!coupon) return '-'
  const type = coupon.couponType || coupon.type
  const face = num(coupon.faceValue ?? coupon.face)
  const rate = num(coupon.discountRate, 100)
  if (type === 'DISCOUNT') return `${(rate / 10).toFixed(1).replace(/\.0$/, '')}折`
  return `${face}`
}

/** 券门槛文案 */
export const formatThreshold = (coupon) => {
  const threshold = num(coupon?.thresholdPoint)
  return threshold > 0 ? `满 ${threshold} 可用` : '无门槛'
}

/** 截断长文本 */
export const truncate = (text, len = 60) => {
  const str = String(text ?? '')
  return str.length > len ? `${str.slice(0, len)}…` : str
}

/** 商品/用户封面占位字符：取标题首字 */
export const initialChar = (text) => {
  const str = String(text ?? '').trim()
  return str ? str.slice(0, 1) : '权'
}

/** 手机号脱敏 */
export const maskPhone = (phone) => {
  const str = String(phone ?? '')
  return /^\d{11}$/.test(str) ? `${str.slice(0, 3)}****${str.slice(7)}` : str || '-'
}

/** 生成前端会话 ID（AI 助手新建会话用，32 位内） */
export const genSessionId = () =>
  `s${Date.now().toString(36)}${Math.random().toString(36).slice(2, 10)}`.slice(0, 32)
