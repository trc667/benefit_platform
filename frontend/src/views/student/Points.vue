<template>
  <div class="cg-page">
    <PageHeader title="积分明细" description="签到、任务、兑换码、下单与售后的全部积分流水" />

    <!-- 账户概览 -->
    <el-row :gutter="16" class="cg-mb-16">
      <el-col :xs="12" :sm="12" :md="6" :lg="6">
        <StatCard label="可用积分" :value="account.balance" icon="coin" />
      </el-col>
      <el-col :xs="12" :sm="12" :md="6" :lg="6">
        <StatCard label="累计获得" :value="account.totalEarned" icon="coin" />
      </el-col>
      <el-col :xs="12" :sm="12" :md="6" :lg="6">
        <StatCard label="累计消耗" :value="account.totalUsed" icon="coin" />
      </el-col>
      <el-col :xs="12" :sm="12" :md="6" :lg="6">
        <StatCard
          label="成长等级"
          :value="account.growthLevel ? `Lv.${account.growthLevel}` : null"
          :tip="account.nextLevelPoint ? `下一级 ${formatPoint(account.nextLevelPoint)} 积分` : ''"
          icon="user"
        />
      </el-col>
    </el-row>

    <SearchBar :loading="loading" @search="onSearch" @reset="onReset">
      <el-form-item label="业务类型">
        <el-select v-model="query.bizType" placeholder="全部类型" clearable style="width: 160px">
          <el-option v-for="item in POINT_BIZ_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
    </SearchBar>

    <DataTable
      :columns="columns"
      :data="records"
      :loading="loading"
      :pagination="pagination"
      row-key="id"
      empty-text="还没有积分流水"
      empty-description="完成签到或任务后，这里会记录每一笔积分变动"
      @page-change="onPageChange"
      @size-change="onSizeChange"
    >
      <template #col-bizType="{ row }">
        <el-tag size="small" effect="plain" :type="POINT_BIZ_MAP[row.bizType]?.type || 'info'">
          {{ POINT_BIZ_MAP[row.bizType]?.label || row.bizType || '-' }}
        </el-tag>
      </template>

      <template #col-changePoint="{ row }">
        <span class="cg-point" :class="num(row.changePoint) >= 0 ? 'cg-text-success' : 'cg-text-danger'">
          {{ formatChangePoint(row.changePoint) }}
        </span>
      </template>

      <template #col-balanceAfter="{ row }">
        <span class="cg-point">{{ formatPoint(row.balanceAfter) }}</span>
      </template>
    </DataTable>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import SearchBar from '@/components/SearchBar.vue'
import DataTable from '@/components/DataTable.vue'
import StatCard from '@/components/StatCard.vue'
import { getPointAccount, getPointRecords } from '@/api/point'
import { POINT_BIZ_MAP, POINT_BIZ_OPTIONS } from '@/utils/dict'
import { formatChangePoint, formatPoint, num } from '@/utils/format'

/**
 * 积分明细。接口：
 *   GET /api/point/account
 *   GET /api/point/records?page=&size=&bizType=
 */
const loading = ref(false)
const records = ref([])
const pagination = reactive({ page: 1, size: 10, total: 0 })
const query = reactive({ bizType: '' })
const account = reactive({ balance: null, totalEarned: null, totalUsed: null, growthLevel: null, nextLevelPoint: null })

const columns = [
  { prop: 'bizType', label: '业务类型', width: 110 },
  { prop: 'bizNo', label: '业务单号', minWidth: 170 },
  { prop: 'changePoint', label: '变动积分', width: 110, align: 'right' },
  { prop: 'balanceAfter', label: '变动后余额', width: 120, align: 'right' },
  { prop: 'remark', label: '备注', minWidth: 160 },
  { prop: 'createTime', label: '时间', width: 170, type: 'date' }
]

const loadAccount = async () => {
  try {
    const data = await getPointAccount()
    Object.assign(account, {
      balance: data?.balance ?? 0,
      totalEarned: data?.totalEarned ?? 0,
      totalUsed: data?.totalUsed ?? 0,
      growthLevel: data?.growthLevel ?? null,
      nextLevelPoint: data?.nextLevelPoint ?? null
    })
  } catch (e) {
    // 概览失败不影响流水列表
  }
}

const loadRecords = async () => {
  loading.value = true
  try {
    const data = await getPointRecords({
      page: pagination.page,
      size: pagination.size,
      bizType: query.bizType || undefined
    })
    records.value = Array.isArray(data?.records) ? data.records : []
    pagination.total = num(data?.total)
  } catch (e) {
    records.value = []
    pagination.total = 0
  } finally {
    loading.value = false
  }
}

const onSearch = () => {
  pagination.page = 1
  loadRecords()
}

const onReset = () => {
  query.bizType = ''
  onSearch()
}

const onPageChange = (page) => {
  pagination.page = page
  loadRecords()
}

const onSizeChange = (size) => {
  pagination.size = size
  pagination.page = 1
  loadRecords()
}

onMounted(() => {
  loadAccount()
  loadRecords()
})
</script>
