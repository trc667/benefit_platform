<template>
  <div class="cg-page">
    <PageHeader title="操作日志" description="由 @OpLog 切面自动落库，可按模块与用户排查问题">
      <el-button :icon="Refresh" :loading="loading" @click="loadData">刷新</el-button>
    </PageHeader>

    <SearchBar :loading="loading" @search="onSearch" @reset="onReset">
      <el-form-item label="模块">
        <el-input v-model="query.module" placeholder="如 coupon / order" clearable style="width: 180px" />
      </el-form-item>
      <el-form-item label="用户名">
        <el-input v-model="query.username" placeholder="操作人账号" clearable style="width: 180px" />
      </el-form-item>
      <el-form-item label="结果">
        <el-select v-model="query.resultCode" placeholder="全部" clearable style="width: 130px">
          <el-option label="成功" :value="0" />
          <el-option label="失败" :value="1" />
        </el-select>
      </el-form-item>
    </SearchBar>

    <DataTable
      :columns="columns"
      :data="rows"
      :loading="loading"
      :pagination="pagination"
      row-key="id"
      :action-width="90"
      empty-text="暂无操作日志"
      empty-description="运营在管理端的关键操作会记录在这里"
      @page-change="onPageChange"
      @size-change="onSizeChange"
    >
      <template #col-resultCode="{ row }">
        <el-tag size="small" effect="plain" :type="num(row.resultCode) === 0 ? 'success' : 'danger'">
          {{ num(row.resultCode) === 0 ? '成功' : '失败' }}
        </el-tag>
      </template>

      <template #col-costMs="{ row }">
        <span :class="num(row.costMs) > 1000 ? 'cg-text-danger' : ''">{{ num(row.costMs) }} ms</span>
      </template>

      <template #actions="{ row }">
        <el-button size="small" @click="openDetail(row)">详情</el-button>
      </template>
    </DataTable>

    <el-drawer v-model="detailVisible" :size="drawerSize" title="日志详情" direction="rtl">
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="操作人">
          {{ current?.username || '-' }} <span class="cg-text-3">（ID {{ current?.userId ?? '-' }}）</span>
        </el-descriptions-item>
        <el-descriptions-item label="模块">{{ current?.module || '-' }}</el-descriptions-item>
        <el-descriptions-item label="动作">{{ current?.action || '-' }}</el-descriptions-item>
        <el-descriptions-item label="方法">{{ current?.method || '-' }}</el-descriptions-item>
        <el-descriptions-item label="结果码">{{ current?.resultCode ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ num(current?.costMs) }} ms</el-descriptions-item>
        <el-descriptions-item label="IP">{{ current?.ip || '-' }}</el-descriptions-item>
        <el-descriptions-item label="时间">{{ formatDateTime(current?.createTime) }}</el-descriptions-item>
      </el-descriptions>

      <div class="cg-mt-16 cg-text-2">入参</div>
      <pre class="log-pre">{{ pretty(current?.params) || '-' }}</pre>

      <template v-if="current?.errorMsg">
        <div class="cg-mt-16 cg-text-2">错误信息</div>
        <pre class="log-pre log-pre--error">{{ current.errorMsg }}</pre>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import SearchBar from '@/components/SearchBar.vue'
import DataTable from '@/components/DataTable.vue'
import { getOperationLogPage } from '@/api/admin'
import { formatDateTime, num } from '@/utils/format'

/**
 * 操作日志。接口：
 *   GET /api/admin/operation/log/page
 */
const loading = ref(false)
const rows = ref([])
const pagination = reactive({ page: 1, size: 10, total: 0 })
const query = reactive({ module: '', username: '', resultCode: '' })

const detailVisible = ref(false)
const current = ref(null)

const isNarrow = computed(() => typeof window !== 'undefined' && window.innerWidth <= 768)
const drawerSize = computed(() => (isNarrow.value ? '92%' : '560px'))

const columns = [
  { prop: 'username', label: '操作人', width: 120 },
  { prop: 'module', label: '模块', width: 110 },
  { prop: 'action', label: '动作', minWidth: 140 },
  { prop: 'method', label: '方法', minWidth: 200 },
  { prop: 'resultCode', label: '结果', width: 90, align: 'center' },
  { prop: 'costMs', label: '耗时', width: 100, align: 'right' },
  { prop: 'ip', label: 'IP', width: 130 },
  { prop: 'createTime', label: '时间', width: 170, type: 'date' }
]

const pretty = (value) => {
  if (!value) return ''
  if (typeof value === 'object') {
    try {
      return JSON.stringify(value, null, 2)
    } catch (e) {
      return String(value)
    }
  }
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch (e) {
    return String(value)
  }
}

const loadData = async () => {
  loading.value = true
  try {
    const data = await getOperationLogPage({
      page: pagination.page,
      size: pagination.size,
      module: query.module || undefined,
      username: query.username || undefined,
      resultCode: query.resultCode === '' ? undefined : query.resultCode
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
  query.module = ''
  query.username = ''
  query.resultCode = ''
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

const openDetail = (row) => {
  current.value = row
  detailVisible.value = true
}

onMounted(loadData)
</script>

<style scoped>
.log-pre {
  margin: 8px 0 0;
  padding: 10px;
  background: var(--cg-surface-2);
  border-radius: var(--cg-radius-sm);
  font-size: 12px;
  color: var(--cg-text-2);
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 280px;
  overflow: auto;
}

.log-pre--error {
  background: var(--cg-red-bg);
  color: var(--cg-red);
}
</style>
