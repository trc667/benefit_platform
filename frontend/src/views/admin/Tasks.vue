<template>
  <div class="cg-page">
    <PageHeader title="任务配置" description="任务定义决定学生端的任务列表与奖励积分">
      <el-button :icon="Refresh" :loading="loading" @click="loadData">刷新</el-button>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增任务</el-button>
    </PageHeader>

    <SearchBar :loading="loading" @search="onSearch" @reset="onReset">
      <el-form-item label="任务名称">
        <el-input v-model="query.keyword" placeholder="任务名称 / 编码" clearable style="width: 200px" />
      </el-form-item>
      <el-form-item label="周期类型">
        <el-select v-model="query.taskType" placeholder="全部周期" clearable style="width: 140px">
          <el-option v-for="item in TASK_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
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
      :action-width="150"
      empty-text="还没有任务配置"
      empty-description="点击右上角「新增任务」配置第一个任务"
      @page-change="onPageChange"
      @size-change="onSizeChange"
    >
      <template #col-taskType="{ row }">
        <el-tag size="small" effect="plain" :type="TASK_TYPE_MAP[row.taskType]?.type || 'info'">
          {{ TASK_TYPE_MAP[row.taskType]?.label || row.taskType }}
        </el-tag>
      </template>

      <template #col-pointAward="{ row }">
        <span class="cg-point cg-text-danger">{{ formatPoint(row.pointAward) }}</span>
      </template>

      <template #col-status="{ row }">
        <el-tag size="small" effect="plain" :type="row.status === 1 ? 'success' : 'info'">
          {{ row.status === 1 ? '启用' : '停用' }}
        </el-tag>
      </template>

      <template #actions="{ row }">
        <el-button size="small" @click="openEdit(row)">编辑</el-button>
        <el-button size="small" :type="row.status === 1 ? 'warning' : 'success'" @click="onToggleStatus(row)">
          {{ row.status === 1 ? '停用' : '启用' }}
        </el-button>
      </template>
    </DataTable>

    <FormDialog
      v-model="dialogVisible"
      :title="form.id ? '编辑任务' : '新增任务'"
      :width="640"
      :model="form"
      :rules="rules"
      :loading="submitting"
      confirm-text="保存"
      @submit="onSubmit"
    >
      <template #form>
        <el-row :gutter="16">
          <el-col :xs="24" :sm="12">
            <el-form-item label="任务编码" prop="taskCode">
              <el-input v-model="form.taskCode" placeholder="如 DAILY_SIGN_IN" :disabled="!!form.id" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="任务名称" prop="taskName">
              <el-input v-model="form.taskName" placeholder="如 每日签到" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="周期类型" prop="taskType">
              <el-select v-model="form.taskType" style="width: 100%">
                <el-option v-for="item in TASK_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="目标值" prop="targetValue">
              <el-input-number v-model="form.targetValue" :min="1" :max="9999" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="奖励积分" prop="pointAward">
              <el-input-number v-model="form.pointAward" :min="0" :max="99999" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="图标名">
              <el-input v-model="form.icon" placeholder="Element Plus 图标名，如 Calendar" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="排序">
              <el-input-number v-model="form.sort" :min="0" :max="9999" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio :value="1">启用</el-radio>
                <el-radio :value="0">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="任务描述">
              <el-input v-model="form.description" type="textarea" :rows="2" maxlength="120" show-word-limit />
            </el-form-item>
          </el-col>
        </el-row>
      </template>
    </FormDialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import SearchBar from '@/components/SearchBar.vue'
import DataTable from '@/components/DataTable.vue'
import FormDialog from '@/components/FormDialog.vue'
import { getAdminTaskPage, saveTask, updateTaskStatus } from '@/api/admin'
import { TASK_TYPE_MAP, TASK_TYPE_OPTIONS } from '@/utils/dict'
import { formatPoint, num } from '@/utils/format'
import { handleError } from '@/utils/errorHandler'

/**
 * 任务配置。接口：
 *   GET  /api/admin/task/page
 *   POST /api/admin/task/save
 *   POST /api/admin/task/status  {id,status}
 */
const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const rows = ref([])
const pagination = reactive({ page: 1, size: 10, total: 0 })
const query = reactive({ keyword: '', taskType: '', status: '' })

const columns = [
  { prop: 'taskCode', label: '任务编码', width: 160 },
  { prop: 'taskName', label: '任务名称', minWidth: 160 },
  { prop: 'taskType', label: '周期', width: 110 },
  { prop: 'targetValue', label: '目标值', width: 90, align: 'right' },
  { prop: 'pointAward', label: '奖励积分', width: 100, align: 'right' },
  { prop: 'sort', label: '排序', width: 80, align: 'center' },
  { prop: 'status', label: '状态', width: 90, align: 'center' },
  { prop: 'description', label: '描述', minWidth: 180 }
]

const emptyForm = () => ({
  id: null,
  taskCode: '',
  taskName: '',
  taskType: 'DAILY',
  targetValue: 1,
  pointAward: 10,
  icon: '',
  description: '',
  sort: 0,
  status: 1
})

const form = reactive(emptyForm())

const rules = {
  taskCode: [{ required: true, message: '请输入任务编码', trigger: 'blur' }],
  taskName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  taskType: [{ required: true, message: '请选择周期类型', trigger: 'change' }],
  targetValue: [{ required: true, message: '请输入目标值', trigger: 'blur' }],
  pointAward: [{ required: true, message: '请输入奖励积分', trigger: 'blur' }]
}

const loadData = async () => {
  loading.value = true
  try {
    const data = await getAdminTaskPage({
      page: pagination.page,
      size: pagination.size,
      keyword: query.keyword || undefined,
      taskType: query.taskType || undefined,
      status: query.status === '' ? undefined : query.status
    })
    rows.value = Array.isArray(data?.records) ? data.records : Array.isArray(data) ? data : []
    pagination.total = num(data?.total ?? rows.value.length)
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
  query.keyword = ''
  query.taskType = ''
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
    await saveTask({ ...form })
    ElMessage.success(form.id ? '任务已更新' : '任务已创建')
    dialogVisible.value = false
    await loadData()
  } catch (e) {
    handleError(e, '保存任务')
  } finally {
    submitting.value = false
  }
}

const onToggleStatus = async (row) => {
  const nextStatus = row.status === 1 ? 0 : 1
  try {
    await updateTaskStatus({ id: row.id, status: nextStatus })
    ElMessage.success(nextStatus === 1 ? '任务已启用' : '任务已停用')
    await loadData()
  } catch (e) {
    handleError(e, '更新任务状态')
  }
}

onMounted(loadData)
</script>
