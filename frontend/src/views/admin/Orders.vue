<template>
  <div class="cg-page">
    <PageHeader title="订单管理" description="查询全部订单、查看明细与优惠方案快照">
      <el-button :icon="Refresh" :loading="loading" @click="loadData">刷新</el-button>
    </PageHeader>

    <SearchBar :loading="loading" @search="onSearch" @reset="onReset">
      <el-form-item label="订单号">
        <el-input v-model="query.orderNo" placeholder="订单号" clearable style="width: 200px" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 140px">
          <el-option v-for="item in ORDER_STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
    </SearchBar>

    <DataTable
      :columns="columns"
      :data="rows"
      :loading="loading"
      :pagination="pagination"
      row-key="orderNo"
      :action-width="100"
      empty-text="没有符合条件的订单"
      empty-description="换个订单号或状态再试试"
      @page-change="onPageChange"
      @size-change="onSizeChange"
    >
      <template #col-orderNo="{ row }">
        <span class="cg-point">{{ row.orderNo }}</span>
      </template>

      <template #col-goodsTotalPoint="{ row }">
        <span class="cg-point">{{ formatPoint(row.goodsTotalPoint) }}</span>
      </template>

      <template #col-discountPoint="{ row }">
        <span class="cg-text-success cg-point">-{{ formatPoint(row.discountPoint) }}</span>
      </template>

      <template #col-payPoint="{ row }">
        <span class="cg-point cg-text-danger">{{ formatPoint(row.payPoint) }}</span>
      </template>

      <template #col-status="{ row }">
        <el-tag size="small" effect="plain" :type="ORDER_STATUS_MAP[row.status]?.type || 'info'">
          {{ ORDER_STATUS_MAP[row.status]?.label || row.status }}
        </el-tag>
      </template>

      <template #actions="{ row }">
        <el-button size="small" @click="openDetail(row)">详情</el-button>
        <el-button
          v-if="row.status === 'PAID'"
          size="small"
          type="success"
          :loading="finishingNo === row.orderNo"
          @click="onFinish(row)"
        >
          核销
        </el-button>
      </template>
    </DataTable>

    <el-drawer v-model="detailVisible" :size="drawerSize" title="订单详情" direction="rtl">
      <el-skeleton v-if="detailLoading" :rows="6" animated />
      <EmptyState v-else-if="!detail" title="详情加载失败" description="请稍后重试" icon="doc" />
      <div v-else>
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="订单号">{{ detail.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="用户 ID">{{ detail.userId ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag size="small" effect="plain" :type="ORDER_STATUS_MAP[detail.status]?.type || 'info'">
              {{ ORDER_STATUS_MAP[detail.status]?.label || detail.status }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="下单时间">{{ formatDateTime(detail.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="支付时间">{{ formatDateTime(detail.payTime) }}</el-descriptions-item>
          <el-descriptions-item label="券码">{{ detail.couponCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="收货人">
            {{ detail.receiverName || '-' }} {{ detail.receiverPhone ? `· ${detail.receiverPhone}` : '' }}
          </el-descriptions-item>
          <el-descriptions-item label="收货地址">{{ detail.receiverAddress || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{ detail.remark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="cg-mt-16 cg-text-2">商品明细</div>
        <el-table :data="detailItems" size="small" border class="cg-mt-8" style="width: 100%">
          <el-table-column prop="goodsTitle" label="商品" min-width="140" show-overflow-tooltip />
          <el-table-column prop="quantity" label="数量" width="64" align="center" />
          <el-table-column prop="unitPoint" label="单价" width="80" align="right">
            <template #default="{ row }">{{ formatPoint(row.unitPoint) }}</template>
          </el-table-column>
          <el-table-column prop="itemAmount" label="行实付" width="88" align="right">
            <template #default="{ row }">{{ formatPoint(row.itemAmount) }}</template>
          </el-table-column>
        </el-table>

        <div class="cg-mt-16 cg-text-2">金额</div>
        <div class="cg-mt-8">
          <div class="plan-row">
            <span class="cg-text-2">商品总额</span>
            <span class="cg-point">{{ formatPoint(detail.goodsTotalPoint) }}</span>
          </div>
          <div class="plan-row">
            <span class="cg-text-2">优惠抵扣</span>
            <span class="cg-text-success cg-point">-{{ formatPoint(detail.discountPoint) }}</span>
          </div>
          <div class="plan-row">
            <span class="cg-text-2">实付积分</span>
            <span class="cg-point cg-text-danger">{{ formatPoint(detail.payPoint) }}</span>
          </div>
        </div>

        <template v-if="detail.discountSnapshot">
          <div class="cg-mt-16 cg-text-2">优惠方案快照</div>
          <pre class="snapshot">{{ detail.discountSnapshot }}</pre>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import SearchBar from '@/components/SearchBar.vue'
import DataTable from '@/components/DataTable.vue'
import EmptyState from '@/components/EmptyState.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAdminOrderDetail, getAdminOrderPage, finishAdminOrder } from '@/api/admin'
import { ORDER_STATUS_MAP, ORDER_STATUS_OPTIONS } from '@/utils/dict'
import { formatDateTime, formatPoint, num } from '@/utils/format'
import { handleError } from '@/utils/errorHandler'

/**
 * 订单管理。接口：
 *   GET /api/admin/order/page?orderNo=&status=&page=&size=
 *   GET /api/order/{orderNo}   （详情复用学生端订单详情接口，含明细与优惠快照）
 */
const loading = ref(false)
const rows = ref([])
const pagination = reactive({ page: 1, size: 10, total: 0 })
const query = reactive({ orderNo: '', status: '' })

const detailVisible = ref(false)
const finishingNo = ref('')
const detailLoading = ref(false)
const detail = ref(null)

const isNarrow = computed(() => typeof window !== 'undefined' && window.innerWidth <= 768)
const drawerSize = computed(() => (isNarrow.value ? '92%' : '560px'))

const columns = [
  { prop: 'orderNo', label: '订单号', width: 180 },
  { prop: 'userId', label: '用户ID', width: 90, align: 'center' },
  { prop: 'goodsTotalPoint', label: '商品总额', width: 100, align: 'right' },
  { prop: 'discountPoint', label: '优惠', width: 90, align: 'right' },
  { prop: 'payPoint', label: '实付积分', width: 100, align: 'right' },
  { prop: 'status', label: '状态', width: 100, align: 'center' },
  { prop: 'receiverName', label: '收货人', width: 100 },
  { prop: 'createTime', label: '下单时间', width: 170, type: 'date' }
]

const detailItems = computed(() => {
  const items = detail.value?.items || detail.value?.orderItems || detail.value?.itemList
  return Array.isArray(items) ? items : []
})

const loadData = async () => {
  loading.value = true
  try {
    const data = await getAdminOrderPage({
      page: pagination.page,
      size: pagination.size,
      orderNo: query.orderNo || undefined,
      status: query.status || undefined
    })
    rows.value = Array.isArray(data?.records) ? data.records : []
    pagination.total = num(data?.total)
  } catch (e) {
    rows.value = []
    pagination.total = 0
  } finally {
    loading.value = false
  }
}

const onSearch = () => {
  pagination.page = 1
  loadData()
}

const onReset = () => {
  query.orderNo = ''
  query.status = ''
  onSearch()
}

const onPageChange = (page) => {
  pagination.page = page
  loadData()
}

const onSizeChange = (size) => {
  pagination.size = size
  pagination.page = 1
  loadData()
}

const openDetail = async (row) => {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    // 用管理端详情接口：学生端 /order/{orderNo} 有属主校验，运营查别人的订单会 404
    detail.value = await getAdminOrderDetail(row.orderNo)
  } catch (e) {
    detail.value = null
  } finally {
    detailLoading.value = false
  }
}

/** 运营核销：把已支付订单标记为已完成 */
const onFinish = async (row) => {
  try {
    await ElMessageBox.confirm(`确认核销订单 ${row.orderNo}？`, '运营核销', {
      type: 'warning',
      confirmButtonText: '确认核销'
    })
  } catch (e) {
    return
  }
  finishingNo.value = row.orderNo
  try {
    await finishAdminOrder(row.orderNo)
    ElMessage.success('核销成功')
    await loadData()
    if (detail.value?.orderNo === row.orderNo) {
      detail.value = await getAdminOrderDetail(row.orderNo)
    }
  } catch (e) {
    handleError(e, '核销订单')
  } finally {
    finishingNo.value = ''
  }
}

onMounted(loadData)
</script>

<style scoped>
.snapshot {
  margin: 8px 0 0;
  padding: 10px;
  background: var(--cg-surface-2);
  border-radius: var(--cg-radius-sm);
  font-size: 12px;
  color: var(--cg-text-2);
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
