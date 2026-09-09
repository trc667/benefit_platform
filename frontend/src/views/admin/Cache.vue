<template>
  <div class="cg-page">
    <PageHeader title="二级缓存统计" description="Caffeine L1 + Redis L2 的命中率与回源次数">
      <el-button :icon="Refresh" :loading="loading" @click="loadData">刷新</el-button>
    </PageHeader>

    <el-skeleton v-if="loading" :rows="6" animated />

    <template v-else>
      <!-- 总览 -->
      <el-row :gutter="16" class="cg-mb-16">
        <el-col :xs="12" :sm="12" :md="6" :lg="6" class="cache-col">
          <StatCard label="命中率" :value="hitRateText" tip="L1 + L2 综合" icon="trend" />
        </el-col>
        <el-col :xs="12" :sm="12" :md="6" :lg="6" class="cache-col">
          <StatCard label="命中次数" :value="hitCount" icon="trend" />
        </el-col>
        <el-col :xs="12" :sm="12" :md="6" :lg="6" class="cache-col">
          <StatCard label="未命中次数" :value="missCount" icon="trend" />
        </el-col>
        <el-col :xs="12" :sm="12" :md="6" :lg="6" class="cache-col">
          <StatCard label="回源次数" :value="loadCount" tip="打到数据库的次数" icon="money" />
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <!-- L1 / L2 分层 -->
        <el-col :xs="24" :sm="24" :md="14" :lg="14" class="cache-col">
          <div class="cg-card">
            <div class="cg-card__header">
              <div class="cg-card__title">分层命中</div>
              <span class="cg-text-3">来自 GET /api/admin/cache/stats</span>
            </div>
            <div class="cg-card__body">
              <EmptyState
                v-if="!layers.length"
                compact
                title="暂无分层数据"
                description="接口可能返回了其他结构，见右侧原始数据"
                icon="search"
              />
              <div v-else>
                <div v-for="layer in layers" :key="layer.name" class="layer-item">
                  <div class="cg-flex-between cg-mb-12">
                    <span class="cg-text-2">{{ layer.name }}</span>
                    <span class="cg-point">{{ layer.rateText }}</span>
                  </div>
                  <el-progress :percentage="layer.rate" :stroke-width="10" :show-text="false" />
                  <div class="layer-item__meta cg-text-3">
                    命中 {{ formatPoint(layer.hit) }} · 未命中 {{ formatPoint(layer.miss) }} · 回源
                    {{ formatPoint(layer.load) }}
                  </div>
                </div>
              </div>
            </div>
          </div>
        </el-col>

        <!-- 原始数据 -->
        <el-col :xs="24" :sm="24" :md="10" :lg="10" class="cache-col">
          <div class="cg-card">
            <div class="cg-card__header">
              <div class="cg-card__title">原始指标</div>
            </div>
            <div class="cg-card__body">
              <EmptyState v-if="!entries.length" compact title="暂无数据" icon="search" />
              <el-descriptions v-else :column="1" border size="small">
                <el-descriptions-item v-for="item in entries" :key="item.key" :label="item.label">
                  <span class="cg-point">{{ item.value }}</span>
                </el-descriptions-item>
              </el-descriptions>
            </div>
          </div>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import { getCacheStats } from '@/api/admin'
import { formatPoint, num } from '@/utils/format'

/**
 * 二级缓存统计。接口：
 *   GET /api/admin/cache/stats
 * 后端可能返回 {l1:{...},l2:{...},total:{...}} 或扁平的键值对，
 * 这里做兼容解析，任何结构都不至于白屏。
 */
const loading = ref(false)
const raw = ref(null)

const pick = (obj, keys) => {
  for (const key of keys) {
    if (obj && obj[key] !== undefined && obj[key] !== null) return num(obj[key])
  }
  return null
}

/** 综合命中率：优先取接口给的比例，否则用命中/（命中+未命中）推算 */
const hitCount = computed(() => pick(raw.value, ['hitCount', 'hits', 'l1HitCount']) ?? pick(raw.value?.total, ['hit', 'hitCount']))
const missCount = computed(() => pick(raw.value, ['missCount', 'misses']) ?? pick(raw.value?.total, ['miss', 'missCount']))
const loadCount = computed(() => pick(raw.value, ['loadCount', 'loads', 'reloadCount']) ?? pick(raw.value?.total, ['load', 'loadCount']))

const hitRateText = computed(() => {
  const rate = pick(raw.value, ['hitRate', 'rate'])
  if (rate !== null) return `${(rate <= 1 ? rate * 100 : rate).toFixed(2)}%`
  const hit = hitCount.value
  const miss = missCount.value
  if (hit === null || miss === null) return '-'
  const total = hit + miss
  return total ? `${((hit / total) * 100).toFixed(2)}%` : '0.00%'
})

/** 分层数据：只渲染接口确实给出的层 */
const layers = computed(() => {
  const source = raw.value
  if (!source || typeof source !== 'object') return []
  const result = []
  const candidates = [
    { key: 'l1', name: 'Caffeine L1（进程内）' },
    { key: 'l2', name: 'Redis L2（分布式）' },
    { key: 'total', name: '合计' }
  ]
  candidates.forEach(({ key, name }) => {
    const item = source[key]
    if (!item || typeof item !== 'object') return
    const hit = pick(item, ['hit', 'hitCount', 'hits']) ?? 0
    const miss = pick(item, ['miss', 'missCount', 'misses']) ?? 0
    const load = pick(item, ['load', 'loadCount', 'reloadCount']) ?? 0
    const rate = pick(item, ['hitRate', 'rate'])
    const total = hit + miss
    const percentage = rate !== null ? Math.round((rate <= 1 ? rate * 100 : rate)) : total ? Math.round((hit / total) * 100) : 0
    result.push({
      name,
      hit,
      miss,
      load,
      rate: Math.min(100, Math.max(0, percentage)),
      rateText: `${Math.min(100, Math.max(0, percentage))}%`
    })
  })
  return result
})

/** 原始指标表格：把接口返回的叶子字段平铺展示 */
const entries = computed(() => {
  const source = raw.value
  if (!source || typeof source !== 'object') return []
  return Object.entries(source)
    .filter(([, value]) => value === null || typeof value !== 'object')
    .map(([key, value]) => ({ key, label: key, value: typeof value === 'number' ? formatPoint(value) : String(value) }))
})

const loadData = async () => {
  loading.value = true
  try {
    raw.value = await getCacheStats()
  } catch (e) {
    raw.value = null
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.cache-col {
  margin-bottom: 16px;
}

.layer-item + .layer-item {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--cg-border-light);
}

.layer-item__meta {
  margin-top: 8px;
  font-size: 12px;
}
</style>
