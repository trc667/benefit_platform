<template>
  <div class="cg-page">
    <PageHeader title="本地消息表监控" description="outbox 事件最终必达：失败或死信可手动补偿">
      <el-button :icon="Refresh" :loading="loading" @click="loadData">刷新</el-button>
    </PageHeader>

    <SearchBar :loading="loading" @search="onSearch" @reset="onReset">
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 150px">
          <el-option v-for="item in OUTBOX_STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="Topic">
        <el-input v-model="query.topic" placeholder="topic 名称" clearable style="width: 200px" />
      </el-form-item>
    </SearchBar>

    <DataTable
      :columns="columns"
      :data="rows"
      :loading="loading"
      :pagination="pagination"
      row-key="id"
      :action-width="120"
      empty-text="暂无消息记录"
      empty-description="业务产生事件后会在 outbox 表留痕"
      @page-change="onPageChange"
      @size-change="onSizeChange"
    >
      <template #col-eventId="{ row }">
        <span class="cg-point">{{ row.eventId }}</span>
      </template>

      <template #col-status="{ row }">
        <el-tag size="small" effect="plain" :type="OUTBOX_STATUS_MAP[row.status]?.type || 'info'">
          {{ OUTBOX_STATUS_MAP[row.status]?.label || row.status }}
        </el-tag>
      </template>

      <template #col-retryCount="{ row }">
        <span :class="num(row.retryCount) > 0 ? 'cg-text-danger' : ''">{{ num(row.retryCount) }}</span>
      </template>

      <template #actions="{ row }">
        <el-button
          size="small"
          type="primary"
          :loading="retryingId === row.id"
          :disabled="row.status === 'SENT'"
          @click="onRetry(row)"
        >
          手动重试
        </el-button>
      </template>
    </DataTable>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import SearchBar from '@/components/SearchBar.vue'
import DataTable from '@/components/DataTable.vue'
import { getOutboxPage, retryOutbox } from '@/api/admin'
import { OUTBOX_STATUS_MAP, OUTBOX_STATUS_OPTIONS } from '@/utils/dict'
import { num } from '@/utils/format'
import { handleError } from '@/utils/errorHandler'

/**
 * 本地消息表监控。接口：
 *   GET  /api/admin/mq/outbox/page?status=&page=&size=
 *   POST /api/admin/mq/outbox/retry  {id}
 */
const loading = ref(false)
const rows = ref([])
const retryingId = ref(null)
const pagination = reactive({ page: 1, size: 10, total: 0 })
const query = reactive({ status: '', topic: '' })

const columns = [
  { prop: 'eventId', label: '事件ID', width: 180 },
  { prop: 'topic', label: 'Topic', width: 170 },
  { prop: 'eventType', label: '事件类型', width: 140 },
  { prop: 'bizKey', label: '业务键', width: 140 },
  { prop: 'status', label: '状态', width: 100, align: 'center' },
  { prop: 'retryCount', label: '重试次数', width: 90, align: 'center' },
  { prop: 'nextRetryTime', label: '下次重试', width: 170, type: 'date' },
  { prop: 'lastError', label: '最近错误', minWidth: 160 },
  { prop: 'createTime', label: '创建时间', width: 170, type: 'date' }
]

const loadData = async () => {
  loading.value = true
  try {
    const data = await getOutboxPage({
      page: pagination.page,
      size: pagination.size,
      status: query.status || undefined,
      topic: query.topic || undefined
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
  query.topic = ''
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

const onRetry = async (row) => {
  try {
    await ElMessageBox.confirm(`确认手动重试事件 ${row.eventId}？`, '手动补偿', { type: 'warning' })
  } catch (e) {
    return
  }
  retryingId.value = row.id
  try {
    await retryOutbox(row.id)
    ElMessage.success('已触发重试，请稍后刷新查看状态')
    await loadData()
  } catch (e) {
    handleError(e, '手动重试消息')
  } finally {
    retryingId.value = null
  }
}

onMounted(loadData)
</script>
