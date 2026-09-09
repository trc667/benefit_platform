<template>
  <div class="cg-page">
    <PageHeader title="我的订单" description="查看兑换记录、支付与售后进度">
      <el-button :icon="Refresh" :loading="loading" @click="loadOrders">刷新</el-button>
    </PageHeader>

    <div class="cg-card">
      <div class="cg-card__body">
        <el-tabs v-model="activeStatus" @tab-change="onTabChange">
          <el-tab-pane v-for="tab in tabs" :key="tab.value" :name="tab.value" :label="tab.label" />
        </el-tabs>

        <el-skeleton v-if="loading" :rows="4" animated />

        <EmptyState
          v-else-if="!orders.length"
          icon="doc"
          title="还没有相关订单"
          description="去权益商城逛逛，用积分兑换校园权益"
        >
          <el-button type="primary" @click="$router.push('/student/mall')">去兑换</el-button>
        </EmptyState>

        <div v-else>
          <div v-for="order in orders" :key="order.orderNo" class="order-item">
            <div class="order-item__head">
              <div class="order-item__no">
                <span class="cg-text-3">订单号</span>
                <span class="cg-point">{{ order.orderNo }}</span>
              </div>
              <div class="order-item__head-right">
                <span class="cg-text-3 order-item__time">{{ formatDateTime(order.createTime) }}</span>
                <el-tag size="small" effect="plain" :type="ORDER_STATUS_MAP[order.status]?.type || 'info'">
                  {{ ORDER_STATUS_MAP[order.status]?.label || order.status }}
                </el-tag>
              </div>
            </div>

            <div class="order-item__body">
              <div class="order-item__goods">
                <div class="order-item__cover">
                  <el-image
                    v-if="order.goodsCover || order.coverUrl"
                    :src="order.goodsCover || order.coverUrl"
                    fit="cover"
                    @error="markCoverFailed(order.orderNo)"
                  >
                    <template #error><CoverPlaceholder :goods="order" /></template>
                  </el-image>
                  <CoverPlaceholder v-else :goods="order" />
                </div>
                <div class="order-item__goods-info">
                  <div class="cg-ellipsis-2">{{ order.goodsTitle || order.title || '权益商品' }}</div>
                  <div class="cg-text-3 order-item__goods-sub">
                    数量 ×{{ num(order.quantity, 1) }} · 单价 {{ formatPoint(order.unitPoint ?? order.goodsTotalPoint) }}
                  </div>
                </div>
              </div>

              <div class="order-item__amount">
                <div class="cg-text-3">实付积分</div>
                <div class="order-item__pay cg-point">{{ formatPoint(order.payPoint) }}</div>
                <div v-if="num(order.discountPoint) > 0" class="cg-text-3 order-item__discount">
                  已优惠 {{ formatPoint(order.discountPoint) }}
                </div>
              </div>
            </div>

            <div class="order-item__actions">
              <el-button size="small" @click="openDetail(order)">详情</el-button>
              <el-button v-if="order.status === 'CREATED'" size="small" type="primary" :loading="payingNo === order.orderNo" @click="onPay(order)">
                去支付
              </el-button>
              <el-button v-if="order.status === 'CREATED'" size="small" @click="onCancel(order)">取消订单</el-button>
              <el-button
                v-if="order.status === 'PAID'"
                size="small"
                type="success"
                :loading="finishingNo === order.orderNo"
                @click="onFinish(order)"
              >
                确认完成
              </el-button>
              <el-button
                v-if="order.status === 'PAID' || order.status === 'FINISHED'"
                size="small"
                type="warning"
                @click="openRefund(order)"
              >
                申请售后
              </el-button>
            </div>
          </div>

          <div class="order-pager">
            <el-pagination
              :current-page="pagination.page"
              :page-size="pagination.size"
              :page-sizes="[10, 20, 50]"
              :total="pagination.total"
              :layout="pagerLayout"
              background
              @current-change="onPageChange"
              @size-change="onSizeChange"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- 订单详情抽屉 -->
    <el-drawer v-model="detailVisible" :size="drawerSize" title="订单详情" direction="rtl">
      <el-skeleton v-if="detailLoading" :rows="6" animated />
      <EmptyState v-else-if="!detail" title="详情加载失败" description="请稍后重试" icon="doc" />
      <div v-else class="order-detail">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="订单号">{{ detail.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag size="small" effect="plain" :type="ORDER_STATUS_MAP[detail.status]?.type || 'info'">
              {{ ORDER_STATUS_MAP[detail.status]?.label || detail.status }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="下单时间">{{ formatDateTime(detail.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="支付时间">{{ formatDateTime(detail.payTime) }}</el-descriptions-item>
          <el-descriptions-item label="收货人">
            {{ detail.receiverName || '-' }} {{ detail.receiverPhone ? `· ${detail.receiverPhone}` : '' }}
          </el-descriptions-item>
          <el-descriptions-item label="收货地址">{{ detail.receiverAddress || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{ detail.remark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="cg-mt-16 cg-text-2">商品明细</div>
        <el-table :data="detailItems" size="small" border class="cg-mt-8" style="width: 100%">
          <el-table-column prop="goodsTitle" label="商品" min-width="140" show-overflow-tooltip />
          <el-table-column prop="unitPoint" label="单价" width="80" align="right">
            <template #default="{ row }">{{ formatPoint(row.unitPoint) }}</template>
          </el-table-column>
          <el-table-column prop="quantity" label="数量" width="64" align="center" />
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

    <!-- 申请售后 -->
    <FormDialog
      v-model="refundVisible"
      title="申请售后"
      :width="560"
      :model="refundForm"
      :rules="refundRules"
      :loading="refundSubmitting"
      confirm-text="提交申请"
      @submit="submitRefund"
    >
      <template #form>
        <el-form-item label="订单号">
          <span class="cg-point">{{ refundForm.orderNo }}</span>
        </el-form-item>
        <el-form-item label="售后类型" prop="refundType">
          <el-radio-group v-model="refundForm.refundType">
            <el-radio v-for="item in REFUND_TYPE_OPTIONS" :key="item.value" :value="item.value">
              {{ item.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="申请原因" prop="reason">
          <el-input
            v-model="refundForm.reason"
            type="textarea"
            :rows="3"
            maxlength="120"
            show-word-limit
            placeholder="请说明退换原因，便于运营审核"
          />
        </el-form-item>
      </template>
    </FormDialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import FormDialog from '@/components/FormDialog.vue'
import CoverPlaceholder from '@/components/CoverPlaceholder.vue'
import { applyRefund, cancelOrder, finishOrder, getOrderDetail, getOrderPage, payOrder } from '@/api/order'
import { ORDER_STATUS_MAP, REFUND_TYPE_OPTIONS } from '@/utils/dict'
import { formatDateTime, formatPoint, num } from '@/utils/format'
import { handleError } from '@/utils/errorHandler'

/**
 * 我的订单。接口：
 *   GET  /api/order/page?status=&page=&size=
 *   GET  /api/order/{orderNo}
 *   POST /api/order/pay
 *   POST /api/order/cancel
 *   POST /api/order/refund/apply
 */
const tabs = [
  { value: '', label: '全部' },
  { value: 'CREATED', label: '待支付' },
  { value: 'PAID', label: '已支付' },
  { value: 'FINISHED', label: '已完成' },
  { value: 'CANCELLED', label: '已取消' }
]

const loading = ref(false)
const activeStatus = ref('')
const orders = ref([])
const pagination = reactive({ page: 1, size: 10, total: 0 })
const payingNo = ref('')
const finishingNo = ref('')
const failedCovers = ref([])

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)

const refundVisible = ref(false)
const refundSubmitting = ref(false)
const refundForm = reactive({ orderNo: '', refundType: 'RETURN', reason: '' })
const refundRules = {
  refundType: [{ required: true, message: '请选择售后类型', trigger: 'change' }],
  reason: [{ required: true, message: '请填写申请原因', trigger: 'blur' }]
}

const isNarrow = computed(() => typeof window !== 'undefined' && window.innerWidth <= 768)
const pagerLayout = computed(() =>
  isNarrow.value ? 'prev, pager, next' : 'total, sizes, prev, pager, next'
)
const drawerSize = computed(() => (isNarrow.value ? '92%' : '560px'))

const detailItems = computed(() => {
  const items = detail.value?.items || detail.value?.orderItems || detail.value?.itemList
  return Array.isArray(items) ? items : []
})

const markCoverFailed = (orderNo) => {
  if (!failedCovers.value.includes(orderNo)) failedCovers.value.push(orderNo)
}

const loadOrders = async () => {
  loading.value = true
  try {
    const data = await getOrderPage({
      status: activeStatus.value || undefined,
      page: pagination.page,
      size: pagination.size
    })
    orders.value = Array.isArray(data?.records) ? data.records : []
    pagination.total = num(data?.total)
  } catch (e) {
    orders.value = []
    pagination.total = 0
  } finally {
    loading.value = false
  }
}

const onTabChange = () => {
  pagination.page = 1
  loadOrders()
}

const onPageChange = (page) => {
  pagination.page = page
  loadOrders()
}

const onSizeChange = (size) => {
  pagination.size = size
  pagination.page = 1
  loadOrders()
}

const openDetail = async (order) => {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getOrderDetail(order.orderNo)
  } catch (e) {
    detail.value = null
  } finally {
    detailLoading.value = false
  }
}

const onPay = async (order) => {
  payingNo.value = order.orderNo
  try {
    const data = await payOrder(order.orderNo)
    ElMessage.success(`支付成功，扣减 ${formatPoint(data?.payPoint)} 积分，余额 ${formatPoint(data?.balance)}`)
    await loadOrders()
  } catch (e) {
    // 余额不足等业务失败由拦截器提示
  } finally {
    payingNo.value = ''
  }
}

const onCancel = async (order) => {
  try {
    await ElMessageBox.confirm(`确认取消订单 ${order.orderNo}？`, '取消订单', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await cancelOrder(order.orderNo)
    ElMessage.success('订单已取消')
    await loadOrders()
  } catch (e) {
    handleError(e, '取消订单')
  }
}

const onFinish = async (order) => {
  try {
    await ElMessageBox.confirm(
      `确认已收到「${order.items?.[0]?.goodsTitle || '该权益'}」？确认后订单将标记为已完成。`,
      '确认完成',
      { type: 'info', confirmButtonText: '确认完成' }
    )
  } catch (e) {
    return
  }
  finishingNo.value = order.orderNo
  try {
    await finishOrder(order.orderNo)
    ElMessage.success('订单已完成，感谢使用')
    await loadOrders()
  } catch (e) {
    handleError(e, '确认完成')
  } finally {
    finishingNo.value = ''
  }
}

const openRefund = (order) => {
  refundForm.orderNo = order.orderNo
  refundForm.refundType = 'RETURN'
  refundForm.reason = ''
  refundVisible.value = true
}

const submitRefund = async () => {
  refundSubmitting.value = true
  try {
    const data = await applyRefund({ ...refundForm })
    ElMessage.success(`售后申请已提交，退换单号 ${data?.refundNo || ''}`)
    refundVisible.value = false
  } catch (e) {
    handleError(e, '提交售后申请')
  } finally {
    refundSubmitting.value = false
  }
}

onMounted(loadOrders)
</script>

<style scoped>
.order-item {
  border: 1px solid var(--cg-border-light);
  border-radius: var(--cg-radius);
  padding: 12px 14px;
}

.order-item + .order-item {
  margin-top: 12px;
}

.order-item__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--cg-border-light);
  font-size: 13px;
}

.order-item__no {
  display: flex;
  align-items: center;
  gap: 6px;
}

.order-item__head-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.order-item__time {
  font-size: 12px;
}

.order-item__body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 0;
}

.order-item__goods {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  flex: 1 1 auto;
}

.order-item__cover {
  flex: 0 0 56px;
  width: 56px;
  height: 56px;
  border-radius: var(--cg-radius-sm);
  overflow: hidden;
  background: var(--cg-surface-2);
}

.order-item__goods-info {
  min-width: 0;
  font-size: 13px;
}

.order-item__goods-sub {
  margin-top: 4px;
  font-size: 12px;
}

.order-item__amount {
  flex: 0 0 auto;
  text-align: right;
  font-size: 12px;
}

.order-item__pay {
  font-size: 17px;
  color: var(--cg-jade);
  margin-top: 2px;
}

.order-item__discount {
  margin-top: 2px;
}

.order-item__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
  padding-top: 10px;
  border-top: 1px solid var(--cg-border-light);
}

.order-item__actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.order-pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.order-detail {
  padding-bottom: 16px;
}

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

@media (max-width: 768px) {
  .order-item__body {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }

  .order-item__amount {
    text-align: left;
    width: 100%;
  }

  .order-item__actions {
    justify-content: flex-start;
  }

  .order-pager {
    justify-content: center;
  }
}
</style>
