<template>
  <div class="stat-card" :class="{ 'is-clickable': clickable }" @click="onClick">
    <div class="stat-card__body">
      <div class="stat-card__label">{{ label }}</div>
      <div class="stat-card__value">
        <span class="cg-point">{{ displayValue }}</span>
        <span v-if="unit" class="stat-card__unit">{{ unit }}</span>
      </div>
      <div v-if="tip" class="stat-card__tip">{{ tip }}</div>
    </div>
    <div class="stat-card__icon">
      <el-icon :size="22"><component :is="iconComponent" /></el-icon>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Coin, Goods, Money, Ticket, TrendCharts, User } from '@element-plus/icons-vue'
import { formatPoint } from '@/utils/format'

/**
 * 指标卡片：标题 / 数值 / 单位 / 图标。
 * 数值为空时显示 "-"，避免接口失败时出现 "0" 这种误导性数据。
 */
const props = defineProps({
  label: { type: String, required: true },
  value: { type: [Number, String], default: null },
  unit: { type: String, default: '' },
  tip: { type: String, default: '' },
  /** 图标名，见 ICONS */
  icon: { type: String, default: 'trend' },
  clickable: { type: Boolean, default: false }
})

const emit = defineEmits(['click'])

const ICONS = { coin: Coin, goods: Goods, money: Money, ticket: Ticket, trend: TrendCharts, user: User }
const iconComponent = computed(() => ICONS[props.icon] || TrendCharts)

const displayValue = computed(() => {
  if (props.value === null || props.value === undefined || props.value === '') return '-'
  return typeof props.value === 'number' ? formatPoint(props.value) : String(props.value)
})

const onClick = () => {
  if (props.clickable) emit('click')
}
</script>

<style scoped>
.stat-card {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  background: var(--cg-surface);
  border: 1px solid var(--cg-border);
  border-radius: var(--cg-radius);
  padding: 16px;
  height: 100%;
  transition: border-color 0.14s var(--cg-ease);
}

.stat-card.is-clickable {
  cursor: pointer;
}

.stat-card.is-clickable:hover {
  border-color: var(--cg-border-strong);
}

.stat-card__body {
  min-width: 0;
}

.stat-card__label {
  font-size: 12.5px;
  color: var(--cg-text-3);
}

.stat-card__value {
  margin-top: 8px;
  font-size: 24px;
  line-height: 1.15;
  letter-spacing: -0.02em;
  color: var(--cg-text-1);
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.stat-card__unit {
  font-size: 12px;
  font-weight: 400;
  color: var(--cg-text-3);
}

.stat-card__tip {
  margin-top: 6px;
  font-size: 12px;
  color: var(--cg-text-3);
}

.stat-card__icon {
  flex: 0 0 auto;
  width: 34px;
  height: 34px;
  border-radius: var(--cg-radius-sm);
  background: var(--cg-jade-bg);
  color: var(--cg-jade);
  display: flex;
  align-items: center;
  justify-content: center;
}

@media (max-width: 768px) {
  .stat-card {
    padding: 13px;
  }

  .stat-card__value {
    font-size: 19px;
  }
}
</style>
