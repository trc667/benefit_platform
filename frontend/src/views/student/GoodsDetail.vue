<template>
  <div class="cg-page">
    <PageHeader :title="goods?.title || '权益详情'" :description="goods?.subTitle || ''">
      <el-button :icon="ArrowLeft" @click="$router.back()">返回</el-button>
    </PageHeader>

    <el-skeleton v-if="loading" :rows="8" animated />

    <div v-else-if="!goods" class="cg-card">
      <div class="cg-card__body">
        <EmptyState icon="goods" title="权益不存在或已下架" description="可能已被运营下架，返回商城看看其他权益">
          <el-button type="primary" @click="$router.push('/student/mall')">返回商城</el-button>
        </EmptyState>
      </div>
    </div>

    <el-row v-else :gutter="16">
      <!-- 左：封面 -->
      <el-col :xs="24" :sm="24" :md="10" :lg="9">
        <div class="cg-card detail-cover">
          <el-image
            v-if="goods.coverUrl && !coverFailed"
            :src="goods.coverUrl"
            fit="cover"
            class="detail-cover__img"
            @error="coverFailed = true"
          >
            <template #error>
              <CoverPlaceholder :goods="goods" />
            </template>
          </el-image>
          <CoverPlaceholder v-else :goods="goods" />
        </div>
      </el-col>

      <!-- 右：信息 + 下单 -->
      <el-col :xs="24" :sm="24" :md="14" :lg="15">
        <div class="cg-card">
          <div class="cg-card__body">
            <div class="detail-title">{{ goods.title }}</div>
            <div class="cg-text-3 detail-sub">{{ goods.subTitle || '—' }}</div>

            <div class="detail-tags">
              <el-tag size="small" effect="plain" type="info">
                {{ GOODS_CATEGORY_MAP[goods.category]?.label || goods.category || '其他' }}
              </el-tag>
              <el-tag v-for="tag in tagList" :key="tag" size="small" effect="plain">{{ tag }}</el-tag>
            </div>

            <div class="detail-price-box">
              <div class="detail-price">
                <span class="detail-price__value cg-point">{{ formatPoint(goods.pricePoint) }}</span>
                <span class="detail-price__unit">积分</span>
              </div>
              <div class="detail-price__origin cg-text-3">
                参考原价 ¥{{ (num(goods.originPrice) / 100).toFixed(2) }}
              </div>
            </div>

            <el-descriptions :column="2" border size="small" class="cg-mt-16">
              <el-descriptions-item label="库存">{{ num(goods.stock) }}</el-descriptions-item>
              <el-descriptions-item label="已兑换">{{ num(goods.soldCount) }}</el-descriptions-item>
              <el-descriptions-item label="商品编码">{{ goods.goodsCode || '-' }}</el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag size="small" :type="goods.status === 1 ? 'success' : 'info'" effect="plain">
                  {{ goods.status === 1 ? '上架中' : '已下架' }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="有效期" :span="2">
                {{ goods.startTime || goods.endTime ? `${formatDate(goods.startTime)} ~ ${formatDate(goods.endTime)}` : '长期有效' }}
              </el-descriptions-item>
            </el-descriptions>

            <div class="detail-buy">
              <span class="detail-buy__label">兑换数量</span>
              <el-input-number v-model="quantity" :min="1" :max="maxQuantity" :disabled="!canBuy" />
              <span class="cg-text-3">合计 {{ formatPoint(totalPoint) }} 积分</span>
            </div>

            <div class="detail-actions">
              <el-button type="primary" size="large" :disabled="!canBuy" @click="goSettle">
                {{ buyText }}
              </el-button>
              <el-button size="large" @click="$router.push('/student/mall')">继续逛</el-button>
            </div>
          </div>
        </div>

        <div class="cg-card">
          <div class="cg-card__header">
            <div class="cg-card__title">权益详情</div>
          </div>
          <div class="cg-card__body detail-desc">{{ goods.detail || '运营还没有补充详情说明。' }}</div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import CoverPlaceholder from '@/components/CoverPlaceholder.vue'
import EmptyState from '@/components/EmptyState.vue'
import { getGoodsDetail } from '@/api/benefit'
import { reportTaskProgress } from '@/api/task'
import { GOODS_CATEGORY_MAP } from '@/utils/dict'
import { formatDate, formatPoint, num } from '@/utils/format'
import { handleError } from '@/utils/errorHandler'

/**
 * 权益商品详情。接口：
 *   GET /api/benefit/goods/{id}
 * 结算走 /student/settle/:goodsId?quantity=n
 */
const route = useRoute()
const router = useRouter()

const loading = ref(false)
const coverFailed = ref(false)
const goods = ref(null)
const quantity = ref(1)

const maxQuantity = computed(() => Math.max(1, num(goods.value?.stock, 1)))

const canBuy = computed(() => goods.value?.status === 1 && num(goods.value?.stock) > 0)

const totalPoint = computed(() => num(goods.value?.pricePoint) * quantity.value)

const buyText = computed(() => {
  if (!goods.value) return '去结算'
  if (goods.value.status !== 1) return '已下架'
  if (num(goods.value.stock) <= 0) return '已售罄'
  return '去结算'
})

const tagList = computed(() => {
  const tags = goods.value?.tags
  if (!tags) return []
  return Array.isArray(tags)
    ? tags
    : String(tags)
        .split(',')
        .map((t) => t.trim())
        .filter(Boolean)
})

const loadDetail = async () => {
  loading.value = true
  try {
    goods.value = await getGoodsDetail(route.params.id)
    // 浏览任务进度（失败不影响详情展示）
    reportTaskProgress({ taskCode: 'DAILY_BROWSE', delta: 1 }).catch(() => {})
  } catch (e) {
    goods.value = null
    handleError(e, '加载权益详情')
  } finally {
    loading.value = false
  }
}

const goSettle = () => {
  router.push({ path: `/student/settle/${goods.value.id}`, query: { quantity: quantity.value } })
}

onMounted(loadDetail)
</script>

<style scoped>
.detail-cover {
  overflow: hidden;
  height: 280px;
  border-radius: var(--cg-radius);
}

.detail-cover__img {
  width: 100%;
  height: 100%;
  display: block;
}

.detail-title {
  font-size: 20px;
  font-weight: 600;
  line-height: 1.4;
}

.detail-sub {
  margin-top: 6px;
  font-size: 13px;
}

.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 12px;
}

.detail-price-box {
  margin-top: 16px;
  padding: 14px 16px;
  background: var(--cg-surface-2);
  border-radius: var(--cg-radius);
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.detail-price {
  display: flex;
  align-items: baseline;
  gap: 4px;
  color: var(--cg-jade);
}

.detail-price__value {
  font-size: 28px;
}

.detail-price__unit {
  font-size: 13px;
}

.detail-buy {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--cg-border-light);
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.detail-buy__label {
  font-size: 13px;
  color: var(--cg-text-2);
}

.detail-warn {
  margin-top: 12px;
  padding: 8px 12px;
  border-radius: var(--cg-radius-sm);
  background: var(--cg-amber-bg);
  color: var(--cg-amber);
  font-size: 13px;
}

.detail-actions {
  margin-top: 20px;
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.detail-actions :deep(.el-button) {
  flex: 1 1 140px;
  margin-left: 0;
}

.detail-desc {
  white-space: pre-wrap;
  line-height: 1.8;
  color: var(--cg-text-2);
}

@media (max-width: 768px) {
  .detail-cover {
    height: 200px;
    margin-bottom: 12px;
  }

  .detail-title {
    font-size: 17px;
  }
}
</style>
