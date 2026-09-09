<template>
  <div class="cg-page">
    <PageHeader title="用户管理" description="查询平台用户、查看角色与成长等级、启停账号">
      <el-button :icon="Refresh" :loading="loading" @click="loadData">刷新</el-button>
    </PageHeader>

    <SearchBar :loading="loading" @search="onSearch" @reset="onReset">
      <el-form-item label="关键词">
        <el-input v-model="query.keyword" placeholder="账号 / 昵称 / 学号" clearable style="width: 200px" />
      </el-form-item>
      <el-form-item label="角色">
        <el-select v-model="query.role" placeholder="全部角色" clearable style="width: 140px">
          <el-option v-for="item in ROLE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
    </SearchBar>

    <DataTable
      :columns="columns"
      :data="rows"
      :loading="loading"
      :pagination="pagination"
      row-key="id"
      :action-width="160"
      empty-text="没有符合条件的用户"
      empty-description="换个关键词或角色再试试"
      @page-change="onPageChange"
      @size-change="onSizeChange"
    >
      <template #col-nickname="{ row }">
        <div class="user-cell">
          <div class="app-header__avatar">{{ (row.nickname || row.username || '?').slice(0, 1) }}</div>
          <div class="user-cell__info">
            <div class="cg-ellipsis">{{ row.nickname || '-' }}</div>
            <div class="cg-text-3 cg-ellipsis">{{ row.username }}</div>
          </div>
        </div>
      </template>

      <template #col-role="{ row }">
        <el-tag size="small" effect="plain" :type="ROLE_MAP[row.role]?.type || 'info'">
          {{ ROLE_MAP[row.role]?.label || row.role }}
        </el-tag>
      </template>

      <template #col-growthLevel="{ row }">
        <span class="cg-point">Lv.{{ num(row.growthLevel, 1) }}</span>
      </template>

      <template #col-status="{ row }">
        <el-tag size="small" effect="plain" :type="row.status === 1 ? 'success' : 'danger'">
          {{ row.status === 1 ? '正常' : '已禁用' }}
        </el-tag>
      </template>

      <template #actions="{ row }">
        <el-button size="small" @click="onAdjustPoint(row)">调整积分</el-button>
        <el-button size="small" :type="row.status === 1 ? 'danger' : 'success'" @click="onToggleStatus(row)">
          {{ row.status === 1 ? '禁用' : '启用' }}
        </el-button>
      </template>
    </DataTable>

    <el-dialog v-model="adjustVisible" title="手动调整积分" width="440px">
      <el-form ref="adjustFormRef" :model="adjustForm" :rules="adjustRules" label-width="90px">
        <el-form-item label="用户">
          <span>{{ adjustTarget?.nickname || adjustTarget?.username }}（当前 {{ num(adjustTarget?.balance) }} 分）</span>
        </el-form-item>
        <el-form-item label="调整分值" prop="changePoint">
          <el-input-number v-model="adjustForm.changePoint" :min="-100000" :max="100000" :step="10" style="width: 100%" />
          <div class="cg-text-3" style="font-size: 12px">正数加分，负数扣分</div>
        </el-form-item>
        <el-form-item label="调整原因" prop="reason">
          <el-input v-model="adjustForm.reason" type="textarea" :rows="3" maxlength="100" show-word-limit
            placeholder="例如：活动奖励漏发补偿" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustVisible = false">取消</el-button>
        <el-button type="primary" :loading="adjusting" @click="onSubmitAdjust">确认调整</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import SearchBar from '@/components/SearchBar.vue'
import DataTable from '@/components/DataTable.vue'
import { getAdminUserPage, updateUserStatus, adjustUserPoint } from '@/api/admin'
import { ROLE_MAP, ROLE_OPTIONS } from '@/utils/dict'
import { num } from '@/utils/format'
import { handleError } from '@/utils/errorHandler'

/**
 * 用户管理。接口：
 *   GET  /api/admin/user/page?keyword=&role=&page=&size=
 *   POST /api/admin/user/status  {userId,status}
 *   POST /api/admin/point/adjust {userId,changePoint,bizNo?,reason}
 */
const loading = ref(false)
const rows = ref([])
const pagination = reactive({ page: 1, size: 10, total: 0 })
const query = reactive({ keyword: '', role: '' })

const adjustVisible = ref(false)
const adjusting = ref(false)
const adjustTarget = ref(null)
const adjustFormRef = ref()
const adjustForm = reactive({ changePoint: 100, reason: '' })
const adjustRules = {
  changePoint: [
    { required: true, message: '请输入调整分值', trigger: 'blur' },
    {
      validator: (rule, value, callback) =>
        !value ? callback(new Error('调整分值不能为 0')) : callback(),
      trigger: 'blur'
    }
  ],
  reason: [
    { required: true, message: '请填写调整原因', trigger: 'blur' },
    { min: 2, max: 100, message: '原因 2-100 个字', trigger: 'blur' }
  ]
}

const columns = [
  { prop: 'nickname', label: '用户', minWidth: 180 },
  { prop: 'studentNo', label: '学号', width: 120 },
  { prop: 'school', label: '学校', minWidth: 140 },
  { prop: 'role', label: '角色', width: 100, align: 'center' },
  { prop: 'growthLevel', label: '成长等级', width: 100, align: 'center' },
  { prop: 'phone', label: '手机号', width: 130 },
  { prop: 'status', label: '状态', width: 90, align: 'center' },
  { prop: 'createTime', label: '注册时间', width: 170, type: 'date' }
]

const loadData = async () => {
  loading.value = true
  try {
    const data = await getAdminUserPage({
      page: pagination.page,
      size: pagination.size,
      keyword: query.keyword || undefined,
      role: query.role || undefined
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
  query.keyword = ''
  query.role = ''
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

/** 打开调整积分弹窗 */
const onAdjustPoint = (row) => {
  adjustTarget.value = row
  adjustForm.changePoint = 100
  adjustForm.reason = ''
  adjustVisible.value = true
  adjustFormRef.value?.clearValidate()
}

/** 提交调整：走运营接口，服务端记流水 + 幂等键 + 操作日志 */
const onSubmitAdjust = async () => {
  const valid = await adjustFormRef.value?.validate().catch(() => false)
  if (!valid) return
  adjusting.value = true
  try {
    const data = await adjustUserPoint({
      userId: adjustTarget.value.id,
      changePoint: adjustForm.changePoint,
      reason: adjustForm.reason
    })
    ElMessage.success(
      data?.applied
        ? `调整成功，当前余额 ${num(data.balance)} 分`
        : `该请求已处理过（幂等键 ${data?.bizNo}），余额 ${num(data?.balance)} 分`
    )
    adjustVisible.value = false
    await loadData()
  } catch (e) {
    handleError(e, '调整积分')
  } finally {
    adjusting.value = false
  }
}

const onToggleStatus = async (row) => {
  const nextStatus = row.status === 1 ? 0 : 1
  try {
    await ElMessageBox.confirm(
      `确认${nextStatus === 1 ? '启用' : '禁用'}账号「${row.nickname || row.username}」？${
        nextStatus === 0 ? '禁用后该用户将无法登录。' : ''
      }`,
      nextStatus === 1 ? '启用账号' : '禁用账号',
      { type: 'warning' }
    )
  } catch (e) {
    return
  }
  try {
    await updateUserStatus({ userId: row.id, status: nextStatus })
    ElMessage.success(nextStatus === 1 ? '账号已启用' : '账号已禁用')
    await loadData()
  } catch (e) {
    handleError(e, '更新用户状态')
  }
}

onMounted(loadData)
</script>

<style scoped>
.user-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-cell__info {
  min-width: 0;
}
</style>
