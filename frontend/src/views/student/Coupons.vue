<template>
  <div class="cg-page">
    <PageHeader title="我的优惠券" description="查看已领取的券，或去领券中心补充新券">
      <el-button :icon="Refresh" :loading="loading" @click="loadMine">刷新</el-button>
    </PageHeader>

    <!-- 我的券 -->
    <div class="cg-card">
      <div class="cg-card__body">
        <el-tabs v-model="activeStatus" @tab-change="onTabChange">
          <el-tab-pane v-for="tab in statusTabs" :key="tab.value" :name="tab.value" :label="tab.label" />
        </el-tabs>

        <el-row v-if="loading" :gutter="16">
          <el-col v-for="n in 4" :key="n" :xs="24" :sm="12" :md="12" :lg="12" class="cg-mb-16">
            <el-skeleton :rows="2" animated />
          </el-col>
        </el-row>

        <EmptyState
          v-else-if="!myCoupons.length"
          icon="search"
          title="这里还没有优惠券"
          :description="activeStatus === 'UNUSED' ? '去下方领券中心领取，结算时可自动抵扣' : '换个状态看看'"
        />

        <el-row v-else :gutter="16" class="coupon-grid">
          <el-col v-for="coupon in myCoupons" :key="coupon.id" :xs="24" :sm="12" :md="12" :lg="12" class="cg-mb-16">
            <CouponCard :coupon="coupon">
              <template #actions>
                <el-button
                  v-if="coupon.status === 'UNUSED'"
                  size="small"
                  type="primary"
                  @click="$router.push('/student/mall')"
                >
                  去使用
                </el-button>
                <el-tag v-else size="small" type="info" effect="plain">
                  {{ COUPON_STATUS_MAP[coupon.status]?.label || coupon.status }}
                </el-tag>
              </template>
            </CouponCard>
          </el-col>
        </el-row>

        <div v-if="!loading && myCoupons.length" class="coupon-pager">
          <el-pagination
            :current-page="pagination.page"
            :page-size="pagination.size"
            :total="pagination.total"
            :layout="pagerLayout"
            background
            @current-change="onPageChange"
          />
        </div>
      </div>
    </div>

    <!-- 领券中心 -->
    <div class="cg-card">
      <div class="cg-card__header">
        <div class="cg-card__title">
          <el-icon><Ticket /></el-icon>
          领券中心
        </div>
        <el-button text type="primary" :loading="templateLoading" @click="loadTemplates">刷新</el-button>
      </div>
      <div class="cg-card__body">
        <el-skeleton v-if="templateLoading" :rows="3" animated />

        <EmptyState
          v-else-if="!templates.length"
          icon="search"
          title="暂无可领取的券"
          description="运营还没有配置券模板，稍后再来看看"
        />

        <el-row v-else :gutter="16" class="coupon-grid">
          <el-col v-for="template in templates" :key="template.id" :xs="24" :sm="12" :md="12" :lg="12" class="cg-mb-16">
            <CouponCard :coupon="template" :force-disabled="num(template.remainCount) <= 0">
              <template #actions>
                <el-button
                  v-if="template.received"
                  size="small"
                  disabled
                >
                  已领取
                </el-button>
                <el-button
                  v-else
                  size="small"
                  type="primary"
                  :loading="receivingId === template.id"
                  :disabled="num(template.remainCount) <= 0"
                  @click="onReceive(template)"
                >
                  {{ num(template.remainCount) <= 0 ? '已抢光' : '立即领取' }}
                </el-button>
              </template>
            </CouponCard>
          </el-col>
        </el-row>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Ticket } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import CouponCard from '@/components/CouponCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import { getCouponTemplates, getMyCoupons, receiveCoupon } from '@/api/coupon'
import { COUPON_STATUS_MAP } from '@/utils/dict'
import { num } from '@/utils/format'
import { handleError } from '@/utils/errorHandler'

/**
 * 我的优惠券 + 领券中心。接口：
 *   GET  /api/coupon/mine?status=&page=&size=
 *   GET  /api/coupon/templates?page=&size=
 *   POST /api/coupon/receive
 */
const statusTabs = [
  { value: 'UNUSED', label: '未使用' },
  { value: 'USED', label: '已使用' },
  { value: 'EXPIRED', label: '已过期' }
]

const loading = ref(false)
const templateLoading = ref(false)
const receivingId = ref(null)

const activeStatus = ref('UNUSED')
const myCoupons = ref([])
const pagination = reactive({ page: 1, size: 10, total: 0 })
const templates = ref([])

const isNarrow = computed(() => typeof window !== 'undefined' && window.innerWidth <= 768)
const pagerLayout = computed(() => (isNarrow.value ? 'prev, pager, next' : 'total, prev, pager, next'))

const loadMine = async () => {
  loading.value = true
  try {
    const data = await getMyCoupons({
      status: activeStatus.value,
      page: pagination.page,
      size: pagination.size
    })
    myCoupons.value = Array.isArray(data?.records) ? data.records : []
    pagination.total = num(data?.total)
  } catch (e) {
    myCoupons.value = []
    pagination.total = 0
  } finally {
    loading.value = false
  }
}

const loadTemplates = async () => {
  templateLoading.value = true
  try {
    const data = await getCouponTemplates({ page: 1, size: 20 })
    templates.value = Array.isArray(data?.records) ? data.records : Array.isArray(data) ? data : []
  } catch (e) {
    templates.value = []
  } finally {
    templateLoading.value = false
  }
}

const onTabChange = () => {
  pagination.page = 1
  loadMine()
}

const onPageChange = (page) => {
  pagination.page = page
  loadMine()
}

const onReceive = async (template) => {
  receivingId.value = template.id
  try {
    await receiveCoupon(template.id)
    ElMessage.success(`领取成功：${template.title || '优惠券'}`)
    // 领券后同步刷新「我的券」与模板余量
    await Promise.all([loadMine(), loadTemplates()])
  } catch (e) {
    handleError(e, '领取优惠券')
  } finally {
    receivingId.value = null
  }
}

onMounted(() => {
  loadMine()
  loadTemplates()
})
</script>

<style scoped>
.coupon-pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 4px;
}

@media (max-width: 768px) {
  .coupon-pager {
    justify-content: center;
  }
}
</style>
