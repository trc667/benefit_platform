<template>
  <div class="coupon-card" :class="{ 'is-disabled': disabled }">
    <!-- 左：面额 -->
    <div class="coupon-card__face">
      <div class="coupon-card__face-value">
        <span class="cg-point">{{ faceText }}</span>
        <span v-if="unitText" class="coupon-card__face-unit">{{ unitText }}</span>
      </div>
      <div class="coupon-card__face-threshold">{{ thresholdText }}</div>
    </div>

    <!-- 右：信息 -->
    <div class="coupon-card__info">
      <div class="coupon-card__title cg-ellipsis">{{ title }}</div>
      <div class="coupon-card__meta">
        <el-tag size="small" :type="typeTag.type" effect="plain">{{ typeTag.label }}</el-tag>
        <span class="cg-text-3">{{ scopeText }}</span>
      </div>
      <div class="coupon-card__time cg-text-3">
        <template v-if="coupon.expireTime">有效期至 {{ formatDateTime(coupon.expireTime) }}</template>
        <template v-else-if="coupon.endTime">有效期至 {{ formatDateTime(coupon.endTime) }}</template>
        <template v-else>长期有效</template>
      </div>
      <div class="coupon-card__bottom">
        <span class="cg-text-3">{{ bottomText }}</span>
        <div v-if="$slots.actions" class="coupon-card__actions">
          <slot name="actions" :coupon="coupon" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { COUPON_SCOPE_MAP, COUPON_TYPE_MAP } from '@/utils/dict'
import { formatDateTime, formatPoint, num } from '@/utils/format'

/**
 * 券卡片：左面额 + 右信息；已过期/已使用/已领取的券整体灰化。
 * 兼容券模板（有 remainCount/received）与用户券（有 status/expireTime）两种数据。
 */
const props = defineProps({
  coupon: { type: Object, required: true },
  /** 外部强制灰化（如模板已抢光） */
  forceDisabled: { type: Boolean, default: false }
})

const type = computed(() => props.coupon?.couponType || props.coupon?.type || 'CASH')
const typeTag = computed(() => COUPON_TYPE_MAP[type.value] || { label: type.value, type: 'info' })

const disabled = computed(() => {
  if (props.forceDisabled) return true
  const status = props.coupon?.status
  return status === 'EXPIRED' || status === 'USED' || props.coupon?.expired === true
})

const faceText = computed(() => {
  if (type.value === 'DISCOUNT') {
    const rate = num(props.coupon?.discountRate, 100)
    return (rate / 10).toFixed(1).replace(/\.0$/, '')
  }
  return formatPoint(props.coupon?.faceValue)
})

const unitText = computed(() => (type.value === 'DISCOUNT' ? '折' : ''))

const thresholdText = computed(() => {
  const threshold = num(props.coupon?.thresholdPoint)
  if (threshold > 0) return `满 ${threshold} 可用`
  return '无门槛'
})

const title = computed(() => props.coupon?.couponTitle || props.coupon?.title || '优惠券')

const scopeText = computed(() => {
  const scope = props.coupon?.scopeType
  const base = COUPON_SCOPE_MAP[scope]?.label || '全场通用'
  const value = props.coupon?.scopeValue
  if (scope === 'CATEGORY' && value) return `${base}：${value}`
  if (scope === 'GOODS' && value) return `${base}：${value}`
  return base
})

/** 底部说明：模板看剩余量，用户券看状态/来源 */
const bottomText = computed(() => {
  const coupon = props.coupon || {}
  if (coupon.remainCount !== undefined && coupon.remainCount !== null) {
    return `剩余 ${formatPoint(coupon.remainCount)} 张`
  }
  if (coupon.status === 'USED') return `已使用${coupon.orderNo ? `（${coupon.orderNo}）` : ''}`
  if (coupon.status === 'EXPIRED') return '已过期'
  if (coupon.status === 'UNUSED') return `未使用${coupon.couponCode ? ` · ${coupon.couponCode}` : ''}`
  return coupon.validType === 'RELATIVE' && coupon.validDays ? `领取后 ${coupon.validDays} 天有效` : ''
})
</script>

<style scoped>
.coupon-card {
  display: flex;
  width: 100%;
  min-height: 108px;
  background: var(--cg-surface);
  border: 1px solid var(--cg-border);
  border-radius: var(--cg-radius);
  overflow: hidden;
}

.coupon-card.is-disabled {
  opacity: 0.55;
  filter: grayscale(1);
}

.coupon-card__face {
  flex: 0 0 108px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 12px 8px;
  background: var(--cg-jade-bg);
  border-right: 1px dashed var(--cg-border);
  color: var(--cg-jade-deep);
}

.coupon-card.is-disabled .coupon-card__face {
  background: var(--cg-surface-2);
  color: var(--cg-text-2);
}

.coupon-card__face-value {
  font-size: 26px;
  line-height: 1.1;
  font-weight: 600;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
  display: flex;
  align-items: baseline;
  gap: 2px;
}

.coupon-card__face-unit {
  font-size: 13px;
  font-weight: 600;
}

.coupon-card__face-threshold {
  font-size: 12px;
  color: var(--cg-text-3);
}

.coupon-card__info {
  flex: 1 1 auto;
  min-width: 0;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.coupon-card__title {
  font-size: 14px;
  font-weight: 600;
}

.coupon-card__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 12px;
}

.coupon-card__time {
  font-size: 12px;
}

.coupon-card__bottom {
  margin-top: auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
}

.coupon-card__actions {
  flex: 0 0 auto;
}

@media (max-width: 480px) {
  .coupon-card__face {
    flex: 0 0 88px;
  }

  .coupon-card__face-value {
    font-size: 22px;
  }
}
</style>
