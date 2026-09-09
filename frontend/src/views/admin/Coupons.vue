<template>
  <div class="cg-page">
    <PageHeader title="券模板管理" description="维护优惠券模板、定向发放与领用统计">
      <el-button :icon="TrendCharts" @click="statVisible = true">领用统计</el-button>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增券模板</el-button>
    </PageHeader>

    <SearchBar :loading="loading" @search="onSearch" @reset="onReset">
      <el-form-item label="券名称">
        <el-input v-model="query.title" placeholder="券名称 / 模板编码" clearable style="width: 200px" />
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="query.couponType" placeholder="全部类型" clearable style="width: 140px">
          <el-option v-for="item in COUPON_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
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
      row-key="id"
      :action-width="200"
      empty-text="还没有券模板"
      empty-description="点击右上角「新增券模板」创建第一张券"
      @page-change="onPageChange"
      @size-change="onSizeChange"
    >
      <template #col-couponType="{ row }">
        <el-tag size="small" effect="plain" :type="COUPON_TYPE_MAP[row.couponType]?.type || 'info'">
          {{ COUPON_TYPE_MAP[row.couponType]?.label || row.couponType }}
        </el-tag>
      </template>

      <template #col-faceValue="{ row }">
        <span class="cg-point">{{ faceText(row) }}</span>
      </template>

      <template #col-thresholdPoint="{ row }">
        {{ num(row.thresholdPoint) > 0 ? `满 ${formatPoint(row.thresholdPoint)}` : '无门槛' }}
      </template>

      <template #col-issuedCount="{ row }">
        <span class="cg-point">{{ num(row.issuedCount) }}</span>
        <span class="cg-text-3"> / {{ num(row.totalCount) }}</span>
      </template>

      <template #col-status="{ row }">
        <el-tag size="small" effect="plain" :type="row.status === 1 ? 'success' : 'info'">
          {{ row.status === 1 ? '启用' : '停用' }}
        </el-tag>
      </template>

      <template #actions="{ row }">
        <el-button size="small" @click="openEdit(row)">编辑</el-button>
        <el-button size="small" type="primary" @click="openGrant(row)">定向发放</el-button>
        <el-button size="small" :type="row.status === 1 ? 'warning' : 'success'" @click="onToggleStatus(row)">
          {{ row.status === 1 ? '停用' : '启用' }}
        </el-button>
      </template>
    </DataTable>

    <!-- 新增 / 编辑 -->
    <FormDialog
      v-model="dialogVisible"
      :title="form.id ? '编辑券模板' : '新增券模板'"
      :width="720"
      :model="form"
      :rules="rules"
      :loading="submitting"
      confirm-text="保存"
      @submit="onSubmit"
    >
      <template #form>
        <el-row :gutter="16">
          <el-col :xs="24" :sm="12">
            <el-form-item label="模板编码" prop="templateCode">
              <el-input v-model="form.templateCode" placeholder="如 CT2026001" :disabled="!!form.id" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="券名称" prop="title">
              <el-input v-model="form.title" placeholder="如 满 300 减 60" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="券类型" prop="couponType">
              <el-select v-model="form.couponType" style="width: 100%">
                <el-option v-for="item in COUPON_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="面额（积分）">
              <el-input-number
                v-model="form.faceValue"
                :min="0"
                :max="99999"
                :disabled="form.couponType === 'DISCOUNT'"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="折扣率（85 = 8.5 折）">
              <el-input-number
                v-model="form.discountRate"
                :min="1"
                :max="100"
                :disabled="form.couponType !== 'DISCOUNT'"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="使用门槛（积分）">
              <el-input-number v-model="form.thresholdPoint" :min="0" :max="999999" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="最高抵扣（0 不限）">
              <el-input-number v-model="form.maxDiscount" :min="0" :max="99999" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="适用范围">
              <el-select v-model="form.scopeType" style="width: 100%">
                <el-option v-for="item in COUPON_SCOPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="范围值">
              <el-input
                v-model="form.scopeValue"
                placeholder="分类编码或商品ID，逗号分隔"
                :disabled="form.scopeType === 'ALL'"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="发行总量" prop="totalCount">
              <el-input-number v-model="form.totalCount" :min="1" :max="9999999" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="单人限领">
              <el-input-number v-model="form.perUserLimit" :min="1" :max="99" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="有效期类型">
              <el-select v-model="form.validType" style="width: 100%">
                <el-option
                  v-for="item in COUPON_VALID_TYPE_OPTIONS"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="有效天数">
              <el-input-number
                v-model="form.validDays"
                :min="0"
                :max="3650"
                :disabled="form.validType !== 'RELATIVE'"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col v-if="form.validType === 'FIXED'" :xs="24" :sm="12">
            <el-form-item label="开始时间">
              <el-date-picker
                v-model="form.startTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col v-if="form.validType === 'FIXED'" :xs="24" :sm="12">
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
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio :value="1">启用</el-radio>
                <el-radio :value="0">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
      </template>
    </FormDialog>

    <!-- 定向发放 -->
    <FormDialog
      v-model="grantVisible"
      title="定向发放"
      :width="640"
      :model="grantForm"
      :rules="grantRules"
      :loading="grantSubmitting"
      confirm-text="确认发放"
      @submit="onGrant"
    >
      <template #form>
        <el-form-item label="券模板">
          <span>{{ grantForm.title }}</span>
        </el-form-item>
        <el-form-item label="用户 ID" prop="userIds">
          <el-input
            v-model="grantForm.userIds"
            type="textarea"
            :rows="3"
            placeholder="多个用户 ID 用逗号或换行分隔，如 3,4,5"
          />
        </el-form-item>
        <el-form-item label="每人张数" prop="count">
          <el-input-number v-model="grantForm.count" :min="1" :max="99" />
        </el-form-item>
      </template>
    </FormDialog>

    <!-- 领用统计 -->
    <el-drawer v-model="statVisible" :size="drawerSize" title="券领用统计" direction="rtl">
      <el-skeleton v-if="statLoading" :rows="5" animated />
      <EmptyState v-else-if="!stats.length" title="暂无统计数据" icon="search" />
      <el-table v-else :data="stats" size="small" border style="width: 100%">
        <el-table-column prop="title" label="券名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="totalCount" label="总量" width="80" align="right" />
        <el-table-column prop="issuedCount" label="已发放" width="86" align="right" />
        <el-table-column prop="usedCount" label="已核销" width="86" align="right" />
        <el-table-column label="核销率" width="120">
          <template #default="{ row }">
            <el-progress :percentage="rateOf(row)" :stroke-width="8" :text-inside="false" />
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, TrendCharts } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import SearchBar from '@/components/SearchBar.vue'
import DataTable from '@/components/DataTable.vue'
import FormDialog from '@/components/FormDialog.vue'
import EmptyState from '@/components/EmptyState.vue'
import {
  getAdminCouponTemplatePage,
  getCouponStat,
  grantCoupon,
  saveCouponTemplate,
  updateCouponTemplateStatus
} from '@/api/admin'
import { COUPON_SCOPE_OPTIONS, COUPON_TYPE_MAP, COUPON_TYPE_OPTIONS, COUPON_VALID_TYPE_OPTIONS } from '@/utils/dict'
import { formatPoint, num } from '@/utils/format'
import { handleError } from '@/utils/errorHandler'

/**
 * 券模板管理。接口：
 *   GET  /api/admin/coupon/template/page
 *   POST /api/admin/coupon/template/save
 *   POST /api/admin/coupon/template/status
 *   POST /api/admin/coupon/grant         {templateId,userIds:[],count}
 *   GET  /api/admin/coupon/stat
 */
const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const rows = ref([])
const pagination = reactive({ page: 1, size: 10, total: 0 })
const query = reactive({ title: '', couponType: '', status: '' })

const grantVisible = ref(false)
const grantSubmitting = ref(false)
const grantForm = reactive({ templateId: null, title: '', userIds: '', count: 1 })

const statVisible = ref(false)
const statLoading = ref(false)
const stats = ref([])

const isNarrow = computed(() => typeof window !== 'undefined' && window.innerWidth <= 768)
const drawerSize = computed(() => (isNarrow.value ? '92%' : '560px'))

const columns = [
  { prop: 'title', label: '券名称', minWidth: 180 },
  { prop: 'templateCode', label: '模板编码', width: 130 },
  { prop: 'couponType', label: '类型', width: 100 },
  { prop: 'faceValue', label: '面额/折扣', width: 110, align: 'right' },
  { prop: 'thresholdPoint', label: '门槛', width: 110 },
  { prop: 'issuedCount', label: '发放/总量', width: 120, align: 'right' },
  { prop: 'perUserLimit', label: '限领', width: 80, align: 'center' },
  { prop: 'status', label: '状态', width: 90, align: 'center' },
  { prop: 'endTime', label: '结束时间', width: 170, type: 'date' }
]

const emptyForm = () => ({
  id: null,
  templateCode: '',
  title: '',
  couponType: 'CASH',
  faceValue: 50,
  discountRate: 100,
  thresholdPoint: 0,
  maxDiscount: 0,
  scopeType: 'ALL',
  scopeValue: '',
  totalCount: 1000,
  perUserLimit: 1,
  validType: 'RELATIVE',
  validDays: 30,
  startTime: null,
  endTime: null,
  status: 1
})

const form = reactive(emptyForm())

const rules = {
  templateCode: [{ required: true, message: '请输入模板编码', trigger: 'blur' }],
  title: [{ required: true, message: '请输入券名称', trigger: 'blur' }],
  couponType: [{ required: true, message: '请选择券类型', trigger: 'change' }],
  totalCount: [{ required: true, message: '请输入发行总量', trigger: 'blur' }]
}

const grantRules = {
  userIds: [{ required: true, message: '请输入用户 ID', trigger: 'blur' }],
  count: [{ required: true, message: '请输入每人张数', trigger: 'blur' }]
}

const faceText = (row) => {
  if (row.couponType === 'DISCOUNT') {
    const rate = num(row.discountRate, 100)
    return `${(rate / 10).toFixed(1).replace(/\.0$/, '')} 折`
  }
  return formatPoint(row.faceValue)
}

const rateOf = (row) => {
  if (row.useRate !== undefined && row.useRate !== null) return Math.round(num(row.useRate) * (num(row.useRate) <= 1 ? 100 : 1))
  const issued = num(row.issuedCount)
  if (!issued) return 0
  return Math.min(100, Math.round((num(row.usedCount) / issued) * 100))
}

const loadData = async () => {
  loading.value = true
  try {
    const data = await getAdminCouponTemplatePage({
      page: pagination.page,
      size: pagination.size,
      title: query.title || undefined,
      couponType: query.couponType || undefined,
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
  query.title = ''
  query.couponType = ''
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

const openEdit = (row) => {
  Object.assign(form, emptyForm(), row)
  dialogVisible.value = true
}

const onSubmit = async () => {
  submitting.value = true
  try {
    await saveCouponTemplate({ ...form })
    ElMessage.success(form.id ? '券模板已更新' : '券模板已创建')
    dialogVisible.value = false
    await loadData()
  } catch (e) {
    handleError(e, '保存券模板')
  } finally {
    submitting.value = false
  }
}

const onToggleStatus = async (row) => {
  const nextStatus = row.status === 1 ? 0 : 1
  try {
    await updateCouponTemplateStatus({ id: row.id, status: nextStatus })
    ElMessage.success(nextStatus === 1 ? '已启用' : '已停用')
    await loadData()
  } catch (e) {
    handleError(e, '更新券模板状态')
  }
}

const openGrant = (row) => {
  grantForm.templateId = row.id
  grantForm.title = row.title
  grantForm.userIds = ''
  grantForm.count = 1
  grantVisible.value = true
}

const onGrant = async () => {
  const ids = grantForm.userIds
    .split(/[,，\s]+/)
    .map((item) => Number(item.trim()))
    .filter((item) => Number.isInteger(item) && item > 0)

  if (!ids.length) {
    ElMessage.warning('请至少填写一个合法的用户 ID')
    return
  }

  grantSubmitting.value = true
  try {
    await grantCoupon({ templateId: grantForm.templateId, userIds: ids, count: grantForm.count })
    ElMessage.success(`已向 ${ids.length} 个用户发放 ${grantForm.count} 张券`)
    grantVisible.value = false
    await loadData()
  } catch (e) {
    handleError(e, '定向发放优惠券')
  } finally {
    grantSubmitting.value = false
  }
}

watch(statVisible, async (open) => {
  if (!open) return
  statLoading.value = true
  try {
    const data = await getCouponStat()
    stats.value = Array.isArray(data) ? data : []
  } catch (e) {
    stats.value = []
  } finally {
    statLoading.value = false
  }
})

onMounted(loadData)
</script>
