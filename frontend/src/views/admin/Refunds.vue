<template>
  <div class="cg-page">
    <PageHeader title="售后审核" description="审核退换申请，通过时可退回积分并回滚库存">
      <el-button :icon="Refresh" :loading="loading" @click="loadData">刷新</el-button>
    </PageHeader>

    <SearchBar :loading="loading" @search="onSearch" @reset="onReset">
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 150px">
          <el-option v-for="item in REFUND_STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="退换单号">
        <el-input v-model="query.refundNo" placeholder="退换单号" clearable style="width: 200px" />
      </el-form-item>
    </SearchBar>

    <DataTable
      :columns="columns"
      :data="rows"
      :loading="loading"
      :pagination="pagination"
      row-key="refundNo"
      :action-width="110"
      empty-text="没有待处理的售后单"
      empty-description="学生提交售后申请后会出现在这里"
      @page-change="onPageChange"
      @size-change="onSizeChange"
    >
      <template #col-refundNo="{ row }">
        <span class="cg-point">{{ row.refundNo }}</span>
      </template>

      <template #col-refundType="{ row }">
        <el-tag size="small" effect="plain" :type="REFUND_TYPE_MAP[row.refundType]?.type || 'info'">
          {{ REFUND_TYPE_MAP[row.refundType]?.label || row.refundType }}
        </el-tag>
      </template>

      <template #col-status="{ row }">
        <el-tag size="small" effect="plain" :type="REFUND_STATUS_MAP[row.status]?.type || 'info'">
          {{ REFUND_STATUS_MAP[row.status]?.label || row.status }}
        </el-tag>
      </template>

      <template #col-refundPoint="{ row }">
        <span class="cg-point">{{ formatPoint(row.refundPoint) }}</span>
      </template>

      <template #actions="{ row }">
        <el-button size="small" type="primary" :disabled="row.status !== 'APPLIED'" @click="openHandle(row)">
          审核
        </el-button>
      </template>
    </DataTable>

    <!-- 审核弹窗 -->
    <FormDialog
      v-model="handleVisible"
      title="售后审核"
      :width="640"
      :model="handleForm"
      :rules="handleRules"
      :loading="handleSubmitting"
      confirm-text="提交审核结果"
      @submit="onHandle"
    >
      <template #form>
        <el-form-item label="退换单号">
          <span class="cg-point">{{ handleForm.refundNo }}</span>
        </el-form-item>
        <el-form-item label="订单号">{{ handleForm.orderNo }}</el-form-item>
        <el-form-item label="申请原因">{{ handleForm.reason || '-' }}</el-form-item>
        <el-form-item label="审核结果" prop="approved">
          <el-radio-group v-model="handleForm.approved">
            <el-radio :value="true">通过</el-radio>
            <el-radio :value="false">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="handleForm.approved" label="退回积分" prop="refundPoint">
          <el-input-number v-model="handleForm.refundPoint" :min="0" :max="999999" style="width: 100%" />
          <div class="cg-text-3 cg-mt-8">退货会退回积分并回滚库存；换货不退回积分。</div>
        </el-form-item>
        <el-form-item label="处理备注" prop="handleRemark">
          <el-input
            v-model="handleForm.handleRemark"
            type="textarea"
            :rows="3"
            maxlength="120"
            show-word-limit
            :placeholder="handleForm.approved ? '选填，如 已确认商品未核销' : '请说明驳回原因'"
          />
        </el-form-item>
      </template>
    </FormDialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import SearchBar from '@/components/SearchBar.vue'
import DataTable from '@/components/DataTable.vue'
import FormDialog from '@/components/FormDialog.vue'
import { getAdminRefundPage, handleRefund } from '@/api/admin'
import { REFUND_STATUS_MAP, REFUND_STATUS_OPTIONS, REFUND_TYPE_MAP } from '@/utils/dict'
import { formatPoint, num } from '@/utils/format'
import { handleError } from '@/utils/errorHandler'

/**
 * 售后审核。接口：
 *   GET  /api/admin/order/refund/page?status=&page=&size=
 *   POST /api/admin/order/refund/handle  {refundNo,approved,handleRemark,refundPoint}
 */
const loading = ref(false)
const rows = ref([])
const pagination = reactive({ page: 1, size: 10, total: 0 })
const query = reactive({ status: '', refundNo: '' })

const handleVisible = ref(false)
const handleSubmitting = ref(false)
const handleForm = reactive({
  refundNo: '',
  orderNo: '',
  reason: '',
  approved: true,
  handleRemark: '',
  refundPoint: 0
})

const handleRules = {
  approved: [{ required: true, message: '请选择审核结果', trigger: 'change' }],
  handleRemark: [
    {
      validator: (rule, value, callback) => {
        if (handleForm.approved === false && !String(value || '').trim()) {
          callback(new Error('驳回时必须填写原因'))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ]
}

const columns = [
  { prop: 'refundNo', label: '退换单号', width: 180 },
  { prop: 'orderNo', label: '订单号', width: 180 },
  { prop: 'userId', label: '用户ID', width: 90, align: 'center' },
  { prop: 'refundType', label: '类型', width: 90, align: 'center' },
  { prop: 'reason', label: '申请原因', minWidth: 160 },
  { prop: 'status', label: '状态', width: 100, align: 'center' },
  { prop: 'refundPoint', label: '退回积分', width: 100, align: 'right' },
  { prop: 'applyTime', label: '申请时间', width: 170, type: 'date' },
  { prop: 'handleRemark', label: '处理备注', minWidth: 140 }
]

const loadData = async () => {
  loading.value = true
  try {
    const data = await getAdminRefundPage({
      page: pagination.page,
      size: pagination.size,
      status: query.status || undefined,
      refundNo: query.refundNo || undefined
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
  query.status = ''
  query.refundNo = ''
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

const openHandle = (row) => {
  handleForm.refundNo = row.refundNo
  handleForm.orderNo = row.orderNo
  handleForm.reason = row.reason
  handleForm.approved = true
  handleForm.handleRemark = ''
  // 默认退回该订单实付积分（后端会再校验）
  handleForm.refundPoint = num(row.refundPoint)
  handleVisible.value = true
}

const onHandle = async () => {
  handleSubmitting.value = true
  try {
    await handleRefund({
      refundNo: handleForm.refundNo,
      approved: handleForm.approved,
      handleRemark: handleForm.handleRemark,
      refundPoint: handleForm.approved ? handleForm.refundPoint : 0
    })
    ElMessage.success(handleForm.approved ? '审核通过，已按配置退回积分' : '已驳回该售后申请')
    handleVisible.value = false
    await loadData()
  } catch (e) {
    handleError(e, '提交售后审核')
  } finally {
    handleSubmitting.value = false
  }
}

onMounted(loadData)
</script>
