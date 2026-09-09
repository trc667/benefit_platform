<template>
  <div class="cg-page">
    <PageHeader title="仪表盘" description="平台核心指标与近 7 天趋势">
      <el-button :icon="Refresh" :loading="loading" @click="loadAll">刷新</el-button>
    </PageHeader>

    <!-- 核心指标 -->
    <el-row :gutter="16" class="cg-mb-16">
      <el-col :xs="12" :sm="12" :md="6" :lg="6" class="dash-col">
        <StatCard label="用户总数" :value="overview.userCount" icon="user" />
      </el-col>
      <el-col :xs="12" :sm="12" :md="6" :lg="6" class="dash-col">
        <StatCard label="今日签到" :value="overview.todaySignCount" icon="trend" />
      </el-col>
      <el-col :xs="12" :sm="12" :md="6" :lg="6" class="dash-col">
        <StatCard label="订单总数" :value="overview.orderCount" icon="money" />
      </el-col>
      <el-col :xs="12" :sm="12" :md="6" :lg="6" class="dash-col">
        <StatCard label="今日订单" :value="overview.todayOrderCount" icon="money" />
      </el-col>
      <el-col :xs="12" :sm="12" :md="6" :lg="6" class="dash-col">
        <StatCard label="累计发放积分" :value="overview.pointIssued" icon="coin" />
      </el-col>
      <el-col :xs="12" :sm="12" :md="6" :lg="6" class="dash-col">
        <StatCard label="累计发放券" :value="overview.couponIssued" icon="ticket" />
      </el-col>
      <el-col :xs="12" :sm="12" :md="6" :lg="6" class="dash-col">
        <StatCard label="在架商品" :value="overview.goodsCount" icon="goods" />
      </el-col>
      <el-col :xs="12" :sm="12" :md="6" :lg="6" class="dash-col">
        <StatCard
          label="待审核售后"
          :value="overview.refundPending"
          icon="trend"
          clickable
          @click="$router.push('/admin/refunds')"
        />
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <!-- 签到趋势 -->
      <el-col :xs="24" :sm="24" :md="12" :lg="12" class="dash-col">
        <div class="cg-card">
          <div class="cg-card__header">
            <div class="cg-card__title">
              <el-icon><TrendCharts /></el-icon>
              近 7 天签到趋势
            </div>
            <span class="cg-text-3">峰值 {{ signinMax }}</span>
          </div>
          <div class="cg-card__body">
            <el-skeleton v-if="loading" :rows="5" animated />
            <EmptyState v-else-if="!signinTrend.length" title="暂无签到数据" icon="search" compact />
            <div v-else class="bar-list">
              <div v-for="item in signinTrend" :key="item.date" class="bar-row">
                <span class="bar-row__label">{{ shortDate(item.date) }}</span>
                <el-progress
                  class="bar-row__bar"
                  :percentage="percent(item.count, signinMax)"
                  :show-text="false"
                  :stroke-width="12"
                />
                <span class="bar-row__value cg-point">{{ formatPoint(item.count) }}</span>
              </div>
            </div>
          </div>
        </div>
      </el-col>

      <!-- 订单趋势 -->
      <el-col :xs="24" :sm="24" :md="12" :lg="12" class="dash-col">
        <div class="cg-card">
          <div class="cg-card__header">
            <div class="cg-card__title">
              <el-icon><TrendCharts /></el-icon>
              近 7 天订单趋势
            </div>
            <span class="cg-text-3">峰值 {{ orderMax }} 单</span>
          </div>
          <div class="cg-card__body">
            <el-skeleton v-if="loading" :rows="5" animated />
            <EmptyState v-else-if="!orderTrend.length" title="暂无订单数据" icon="search" compact />
            <div v-else class="bar-list">
              <div v-for="item in orderTrend" :key="item.date" class="bar-row">
                <span class="bar-row__label">{{ shortDate(item.date) }}</span>
                <el-progress
                  class="bar-row__bar"
                  :percentage="percent(item.count, orderMax)"
                  :show-text="false"
                  :stroke-width="12"
                  color="#0d7a5f"
                />
                <span class="bar-row__value cg-point">
                  {{ formatPoint(item.count) }}
                  <span class="cg-text-3 bar-row__sub">{{ formatPoint(item.point) }} 分</span>
                </span>
              </div>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Refresh, TrendCharts } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import { getDashboardOverview, getOrderTrend, getSigninTrend } from '@/api/admin'
import { formatPoint, num } from '@/utils/format'

/**
 * 仪表盘。接口：
 *   GET /api/admin/dashboard/overview
 *   GET /api/admin/dashboard/signin-trend?days=7
 *   GET /api/admin/dashboard/order-trend?days=7
 * 趋势用 el-progress 横条呈现，不引入图表库。
 */
const loading = ref(false)
const signinTrend = ref([])
const orderTrend = ref([])
const overview = reactive({
  userCount: null,
  todaySignCount: null,
  orderCount: null,
  todayOrderCount: null,
  pointIssued: null,
  couponIssued: null,
  goodsCount: null,
  refundPending: null
})

const signinMax = computed(() => Math.max(1, ...signinTrend.value.map((item) => num(item.count))))
const orderMax = computed(() => Math.max(1, ...orderTrend.value.map((item) => num(item.count))))

const percent = (value, max) => Math.min(100, Math.round((num(value) / max) * 100))

/** 2026-09-09 → 09-09 */
const shortDate = (date) => {
  const text = String(date || '')
  return text.length >= 10 ? text.slice(5) : text
}

const loadOverview = async () => {
  try {
    const data = await getDashboardOverview()
    Object.keys(overview).forEach((key) => {
      overview[key] = data?.[key] ?? 0
    })
  } catch (e) {
    Object.keys(overview).forEach((key) => {
      overview[key] = null
    })
  }
}

const loadTrends = async () => {
  const [signin, order] = await Promise.all([
    getSigninTrend(7).catch(() => []),
    getOrderTrend(7).catch(() => [])
  ])
  signinTrend.value = Array.isArray(signin) ? signin : []
  orderTrend.value = Array.isArray(order) ? order : []
}

const loadAll = async () => {
  loading.value = true
  await Promise.all([loadOverview(), loadTrends()])
  loading.value = false
}

onMounted(loadAll)
</script>

<style scoped>
.dash-col {
  margin-bottom: 16px;
}

.bar-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.bar-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.bar-row__label {
  flex: 0 0 44px;
  font-size: 12px;
  color: var(--cg-text-3);
}

.bar-row__bar {
  flex: 1 1 auto;
  min-width: 0;
}

.bar-row__value {
  flex: 0 0 auto;
  min-width: 84px;
  text-align: right;
  font-size: 13px;
}

.bar-row__sub {
  font-size: 12px;
  font-weight: 400;
}

@media (max-width: 768px) {
  .bar-row__value {
    min-width: 64px;
  }
}
</style>
