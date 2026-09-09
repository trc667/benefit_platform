<template>
  <div class="signin-calendar">
    <div class="signin-calendar__head">
      <el-button text :icon="ArrowLeft" :disabled="loading" @click="emit('prev')" />
      <span class="signin-calendar__month">{{ monthText }}</span>
      <el-button text :icon="ArrowRight" :disabled="loading" @click="emit('next')" />
    </div>

    <div class="signin-grid">
      <div v-for="week in WEEK_LABELS" :key="week" class="signin-grid__head">{{ week }}</div>

      <!-- 月初补空格，让 1 号对齐星期 -->
      <div v-for="blank in offset" :key="`blank-${blank}`"></div>

      <div
        v-for="day in days"
        :key="day.day"
        class="signin-cell"
        :class="{
          'signin-cell--signed': day.signed,
          'signin-cell--future': day.future,
          'signin-cell--today': day.today
        }"
      >
        <span>{{ day.day }}</span>
        <el-icon v-if="day.signed" :size="11"><Check /></el-icon>
      </div>
    </div>

    <div class="signin-calendar__foot">
      <span>已签到 <b class="cg-text-primary">{{ signedCount }}</b> 天</span>
      <span>连续 <b class="cg-text-primary">{{ continuousDays }}</b> 天</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { ArrowLeft, ArrowRight, Check } from '@element-plus/icons-vue'

/**
 * 月度签到日历（BitMap 可视化）。
 * 数据来自 GET /api/signin/calendar?month=yyyyMM，days 为 [{day,signed,future}]。
 * 后端没返回 future 时，用“是否超过今天”本地兜底判断。
 */
const props = defineProps({
  /** {month,todaySigned,continuousDays,monthCount,totalCount,days:[]} */
  calendar: { type: Object, default: null },
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['prev', 'next'])

const WEEK_LABELS = ['一', '二', '三', '四', '五', '六', '日']

const monthText = computed(() => {
  const month = props.calendar?.month
  if (!month) return '—'
  const text = String(month)
  return text.length === 6 ? `${text.slice(0, 4)} 年 ${Number(text.slice(4))} 月` : text
})

const rawDays = computed(() => (Array.isArray(props.calendar?.days) ? props.calendar.days : []))

const days = computed(() => {
  const now = new Date()
  const month = String(props.calendar?.month || '')
  const sameMonth = month === `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}`
  return rawDays.value.map((item) => ({
    day: item.day,
    signed: !!item.signed,
    future: item.future !== undefined ? !!item.future : sameMonth ? item.day > now.getDate() : false,
    today: sameMonth && item.day === now.getDate()
  }))
})

/** 计算 1 号是周几，用于首行缩进 */
const offset = computed(() => {
  const month = String(props.calendar?.month || '')
  if (month.length !== 6) return 0
  const year = Number(month.slice(0, 4))
  const monthIndex = Number(month.slice(4)) - 1
  const weekDay = new Date(year, monthIndex, 1).getDay()
  return (weekDay + 6) % 7
})

const signedCount = computed(() => props.calendar?.monthCount ?? days.value.filter((d) => d.signed).length)
const continuousDays = computed(() => props.calendar?.continuousDays ?? 0)
</script>

<style scoped>
.signin-calendar__head {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-bottom: 10px;
}

.signin-calendar__month {
  font-size: 14px;
  font-weight: 600;
  min-width: 110px;
  text-align: center;
}

.signin-calendar__foot {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid var(--cg-border-light);
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: var(--cg-text-2);
}
</style>
