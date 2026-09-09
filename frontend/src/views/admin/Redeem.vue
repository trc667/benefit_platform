<template>
  <div class="cg-page">
    <PageHeader title="兑换码批次" description="兑换码按批次编码，不预生成；核销状态记录在 Redis BitMap">
      <el-button :icon="Refresh" :loading="loading" @click="loadData">刷新</el-button>
      <el-button type="primary" :icon="Plus" @click="openCreate">新建批次</el-button>
    </PageHeader>

    <SearchBar :loading="loading" @search="onSearch" @reset="onReset">
      <el-form-item label="批次号">
        <el-input v-model="query.batchNo" placeholder="批次号 / 名称" clearable style="width: 200px" />
      </el-form-item>
      <el-form-item label="奖励类型">
        <el-select v-model="query.bizType" placeholder="全部类型" clearable style="width: 140px">
          <el-option v-for="item in REDEEM_BIZ_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 130px">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
      </el-form-item>
    </SearchBar>

    <DataTable
      :columns="columns"
      :data="rows"
      :loading="loading"
      :pagination="pagination"
      row-key="batchNo"
      :action-width="210"
      empty-text="还没有兑换码批次"
      empty-description="点击右上角「新建批次」生成第一批兑换码"
      @page-change="onPageChange"
      @size-change="onSizeChange"
    >
      <template #col-bizType="{ row }">
        <el-tag size="small" effect="plain" :type="REDEEM_BIZ_MAP[row.bizType]?.type || 'info'">
          {{ REDEEM_BIZ_MAP[row.bizType]?.label || row.bizType }}
        </el-tag>
      </template>

      <template #col-rewardValue="{ row }">
        <span class="cg-point">{{ formatPoint(row.rewardValue) }}</span>
      </template>

      <template #col-usedCount="{ row }">
        <span class="cg-point">{{ num(row.usedCount) }}</span>
        <span class="cg-text-3"> / {{ num(row.totalCount) }}</span>
      </template>

      <template #col-status="{ row }">
        <el-tag size="small" effect="plain" :type="row.status === 1 ? 'success' : 'info'">
          {{ row.status === 1 ? '启用' : '停用' }}
        </el-tag>
      </template>

      <template #actions="{ row }">
        <el-button size="small" type="primary" @click="openPreview(row)">生成/预览</el-button>
        <el-button size="small" :type="row.status === 1 ? 'warning' : 'success'" @click="onToggleStatus(row)">
          {{ row.status === 1 ? '停用' : '启用' }}
        </el-button>
      </template>
    </DataTable>

    <!-- 新建批次 -->
    <FormDialog
      v-model="dialogVisible"
      title="新建兑换码批次"
      :width="720"
      :model="form"
      :rules="rules"
      :loading="submitting"
      confirm-text="创建批次"
      @submit="onSubmit"
    >
      <template #form>
        <el-row :gutter="16">
          <el-col :xs="24" :sm="12">
            <el-form-item label="批次名称" prop="title">
              <el-input v-model="form.title" placeholder="如 开学季积分兑换码" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="奖励类型" prop="bizType">
              <el-select v-model="form.bizType" style="width: 100%">
                <el-option v-for="item in REDEEM_BIZ_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="关联 ID">
              <el-input-number v-model="form.refId" :min="0" :max="99999999" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="奖励值" prop="rewardValue">
              <el-input-number v-model="form.rewardValue" :min="0" :max="999999" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="码总量" prop="totalCount">
              <el-input-number v-model="form.totalCount" :min="1" :max="1000000" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="开始时间">
              <el-date-picker
                v-model="form.startTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="结束时间">
              <el-date-picker
                v-model="form.endTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="120" show-word-limit />
            </el-form-item>
          </el-col>
        </el-row>
      </template>
    </FormDialog>

    <!-- 生成 / 预览码 -->
    <el-drawer v-model="previewVisible" :size="drawerSize" title="生成 / 预览兑换码" direction="rtl">
      <div class="preview-head">
        <div>
          <div class="cg-text-2">批次号</div>
          <div class="cg-point">{{ currentBatch?.batchNo }}</div>
        </div>
        <div class="preview-head__count">
          <span class="cg-text-3">已核销 {{ num(currentBatch?.usedCount) }} / {{ num(currentBatch?.totalCount) }}</span>
        </div>
      </div>

      <div class="preview-actions">
        <el-input-number v-model="previewCount" :min="1" :max="200" size="small" />
        <el-button type="primary" size="small" :loading="previewLoading" @click="loadCodes">生成 / 预览</el-button>
        <el-button size="small" :icon="CopyDocument" :disabled="!codes.length" @click="copyCodes">复制全部</el-button>
      </div>

      <el-skeleton v-if="previewLoading" :rows="5" animated class="cg-mt-16" />
      <EmptyState
        v-else-if="!codes.length"
        class="cg-mt-16"
        title="尚未生成"
        description="设置数量后点击「生成 / 预览」，从当前进度起返回兑换码"
        icon="doc"
      />
      <div v-else class="code-list cg-mt-16">
        <div v-for="(code, index) in codes" :key="index" class="code-item">
          <span class="cg-point">{{ code }}</span>
          <el-button text size="small" :icon="CopyDocument" @click="copyOne(code)" />
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { CopyDocument, Plus, Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import SearchBar from '@/components/SearchBar.vue'
import DataTable from '@/components/DataTable.vue'
import FormDialog from '@/components/FormDialog.vue'
import EmptyState from '@/components/EmptyState.vue'
import { createRedeemBatch, getRedeemBatchPage, previewRedeemCodes, updateRedeemBatchStatus } from '@/api/admin'
import { REDEEM_BIZ_MAP, REDEEM_BIZ_OPTIONS } from '@/utils/dict'
import { formatPoint, num } from '@/utils/format'
import { handleError } from '@/utils/errorHandler'

/**
 * 兑换码批次管理。接口：
 *   GET  /api/admin/redeem/batch/page
 *   POST /api/admin/redeem/batch/create
 *   GET  /api/admin/redeem/batch/{batchNo}/codes?count=10
 *   POST /api/admin/redeem/batch/status
 */
const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const rows = ref([])
const pagination = reactive({ page: 1, size: 10, total: 0 })
const query = reactive({ batchNo: '', bizType: '', status: '' })

const previewVisible = ref(false)
const previewLoading = ref(false)
const previewCount = ref(10)
const currentBatch = ref(null)
const codes = ref([])

const isNarrow = computed(() => typeof window !== 'undefined' && window.innerWidth <= 768)
const drawerSize = computed(() => (isNarrow.value ? '92%' : '520px'))

const columns = [
  { prop: 'batchNo', label: '批次号', width: 180 },
  { prop: 'title', label: '批次名称', minWidth: 180 },
  { prop: 'bizType', label: '奖励类型', width: 100 },
  { prop: 'rewardValue', label: '奖励值', width: 100, align: 'right' },
  { prop: 'usedCount', label: '已核销/总量', width: 130, align: 'right' },
  { prop: 'status', label: '状态', width: 90, align: 'center' },
  { prop: 'endTime', label: '结束时间', width: 170, type: 'date' },
  { prop: 'remark', label: '备注', minWidth: 160 }
]

const emptyForm = () => ({
  title: '',
  bizType: 'POINT',
  refId: 0,
  rewardValue: 100,
  totalCount: 1000,
  startTime: null,
  endTime: null,
  remark: ''
})

const form = reactive(emptyForm())

const rules = {
  title: [{ required: true, message: '请输入批次名称', trigger: 'blur' }],
  bizType: [{ required: true, message: '请选择奖励类型', trigger: 'change' }],
  rewardValue: [{ required: true, message: '请输入奖励值', trigger: 'blur' }],
  totalCount: [{ required: true, message: '请输入码总量', trigger: 'blur' }]
}

const loadData = async () => {
  loading.value = true
  try {
    const data = await getRedeemBatchPage({
      page: pagination.page,
      size: pagination.size,
      batchNo: query.batchNo || undefined,
      bizType: query.bizType || undefined,
      status: query.status === '' ? undefined : query.status
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
  query.batchNo = ''
  query.bizType = ''
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

const openCreate = () => {
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}

const onSubmit = async () => {
  submitting.value = true
  try {
    await createRedeemBatch({ ...form })
    ElMessage.success('批次已创建')
    dialogVisible.value = false
    await loadData()
  } catch (e) {
    handleError(e, '创建兑换码批次')
  } finally {
    submitting.value = false
  }
}

const onToggleStatus = async (row) => {
  const nextStatus = row.status === 1 ? 0 : 1
  try {
    await updateRedeemBatchStatus({ batchNo: row.batchNo, status: nextStatus })
    ElMessage.success(nextStatus === 1 ? '批次已启用' : '批次已停用')
    await loadData()
  } catch (e) {
    handleError(e, '更新批次状态')
  }
}

const openPreview = (row) => {
  currentBatch.value = row
  codes.value = []
  previewCount.value = 10
  previewVisible.value = true
}

const loadCodes = async () => {
  if (!currentBatch.value) return
  previewLoading.value = true
  try {
    const data = await previewRedeemCodes(currentBatch.value.batchNo, previewCount.value)
    // 后端可能返回字符串数组，也可能返回 {codes:[]}
    codes.value = Array.isArray(data) ? data : Array.isArray(data?.codes) ? data.codes : []
    if (!codes.value.length) ElMessage.warning('该批次没有可生成的兑换码')
  } catch (e) {
    codes.value = []
    handleError(e, '生成兑换码')
  } finally {
    previewLoading.value = false
  }
}

const copyText = async (text) => {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制')
  } catch (e) {
    ElMessage.warning('当前浏览器不支持自动复制，请手动选择')
  }
}

const copyOne = (code) => copyText(code)
const copyCodes = () => copyText(codes.value.join('\n'))

onMounted(loadData)
</script>

<style scoped>
.preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--cg-border-light);
  font-size: 13px;
}

.preview-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  flex-wrap: wrap;
}

.code-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 60vh;
  overflow-y: auto;
}

.code-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 10px;
  background: var(--cg-surface-2);
  border-radius: var(--cg-radius-sm);
  font-size: 13px;
}
</style>
