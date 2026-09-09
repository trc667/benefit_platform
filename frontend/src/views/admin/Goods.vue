<template>
  <div class="cg-page">
    <PageHeader title="商品管理" description="维护权益商品的库存、积分价与上下架状态">
      <el-button type="primary" :icon="Plus" @click="openCreate">新增商品</el-button>
    </PageHeader>

    <SearchBar :loading="loading" @search="onSearch" @reset="onReset">
      <el-form-item label="关键词">
        <el-input v-model="query.keyword" placeholder="商品标题 / 编码" clearable style="width: 200px" />
      </el-form-item>
      <el-form-item label="分类">
        <el-select v-model="query.category" placeholder="全部分类" clearable style="width: 140px">
          <el-option v-for="item in GOODS_CATEGORY_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 130px">
          <el-option label="上架" :value="1" />
          <el-option label="下架" :value="0" />
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
      empty-text="还没有商品"
      empty-description="点击右上角「新增商品」创建第一个权益"
      @page-change="onPageChange"
      @size-change="onSizeChange"
    >
      <template #col-title="{ row }">
        <div class="goods-cell">
          <div class="goods-cell__cover">
            <el-image v-if="row.coverUrl && !failedIds.includes(row.id)" :src="row.coverUrl" fit="cover" @error="markFailed(row.id)">
              <template #error><CoverPlaceholder :goods="row" /></template>
            </el-image>
            <CoverPlaceholder v-else :goods="row" />
          </div>
          <div class="goods-cell__info">
            <div class="cg-ellipsis">{{ row.title }}</div>
            <div class="cg-text-3 cg-ellipsis">{{ row.subTitle || row.goodsCode }}</div>
          </div>
        </div>
      </template>

      <template #col-category="{ row }">
        <el-tag size="small" effect="plain" type="info">
          {{ GOODS_CATEGORY_MAP[row.category]?.label || row.category || '-' }}
        </el-tag>
      </template>

      <template #col-pricePoint="{ row }">
        <span class="cg-point cg-text-danger">{{ formatPoint(row.pricePoint) }}</span>
      </template>

      <template #col-stock="{ row }">
        <span :class="num(row.stock) <= 0 ? 'cg-text-danger' : ''">{{ num(row.stock) }}</span>
      </template>

      <template #col-status="{ row }">
        <el-tag size="small" effect="plain" :type="row.status === 1 ? 'success' : 'info'">
          {{ row.status === 1 ? '上架' : '下架' }}
        </el-tag>
      </template>

      <template #actions="{ row }">
        <el-button size="small" @click="openEdit(row)">编辑</el-button>
        <el-button size="small" :type="row.status === 1 ? 'warning' : 'success'" @click="onToggleStatus(row)">
          {{ row.status === 1 ? '下架' : '上架' }}
        </el-button>
      </template>
    </DataTable>

    <!-- 新增 / 编辑 -->
    <FormDialog
      v-model="dialogVisible"
      :title="form.id ? '编辑商品' : '新增商品'"
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
            <el-form-item label="商品编码" prop="goodsCode">
              <el-input v-model="form.goodsCode" placeholder="如 GD1001" :disabled="!!form.id" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="商品标题" prop="title">
              <el-input v-model="form.title" placeholder="如 图书馆研修间 2 小时券" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="副标题">
              <el-input v-model="form.subTitle" placeholder="一句话卖点" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="分类" prop="category">
              <el-select v-model="form.category" placeholder="请选择" style="width: 100%">
                <el-option
                  v-for="item in GOODS_CATEGORY_OPTIONS"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="所需积分" prop="pricePoint">
              <el-input-number v-model="form.pricePoint" :min="1" :max="999999" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="参考原价（分）">
              <el-input-number v-model="form.originPrice" :min="0" :max="99999999" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="库存" prop="stock">
              <el-input-number v-model="form.stock" :min="0" :max="999999" style="width: 100%" />
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
                <el-radio :value="1">上架</el-radio>
                <el-radio :value="0">下架</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="封面地址">
              <el-input v-model="form.coverUrl" placeholder="留空则用渐变占位图" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="标签">
              <el-input v-model="form.tags" placeholder="逗号分隔，如 学习,安静" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="开始时间">
              <el-date-picker
                v-model="form.startTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="不填表示立即生效"
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
                placeholder="不填表示长期有效"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="商品详情">
              <el-input v-model="form.detail" type="textarea" :rows="3" maxlength="500" show-word-limit />
            </el-form-item>
          </el-col>
        </el-row>
      </template>
    </FormDialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import SearchBar from '@/components/SearchBar.vue'
import DataTable from '@/components/DataTable.vue'
import FormDialog from '@/components/FormDialog.vue'
import CoverPlaceholder from '@/components/CoverPlaceholder.vue'
import { getAdminGoodsPage, saveGoods, updateGoodsStatus } from '@/api/admin'
import { GOODS_CATEGORY_MAP, GOODS_CATEGORY_OPTIONS } from '@/utils/dict'
import { formatPoint, num } from '@/utils/format'
import { handleError } from '@/utils/errorHandler'

/**
 * 商品管理。接口：
 *   GET  /api/admin/benefit/goods/page
 *   POST /api/admin/benefit/goods/save    （id 为空即新增）
 *   POST /api/admin/benefit/goods/status  {id,status}
 */
const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const rows = ref([])
const failedIds = ref([])
const pagination = reactive({ page: 1, size: 10, total: 0 })
const query = reactive({ keyword: '', category: '', status: '' })

const columns = [
  { prop: 'title', label: '商品', minWidth: 220 },
  { prop: 'category', label: '分类', width: 100 },
  { prop: 'pricePoint', label: '积分价', width: 100, align: 'right' },
  { prop: 'stock', label: '库存', width: 90, align: 'right' },
  { prop: 'soldCount', label: '已兑换', width: 90, align: 'right' },
  { prop: 'status', label: '状态', width: 90, align: 'center' },
  { prop: 'sort', label: '排序', width: 80, align: 'center' },
  { prop: 'createTime', label: '创建时间', width: 170, type: 'date' }
]

const emptyForm = () => ({
  id: null,
  goodsCode: '',
  title: '',
  subTitle: '',
  coverUrl: '',
  category: 'STUDY',
  pricePoint: 100,
  originPrice: 0,
  stock: 100,
  tags: '',
  detail: '',
  startTime: null,
  endTime: null,
  sort: 0,
  status: 1
})

const form = reactive(emptyForm())

const rules = {
  goodsCode: [{ required: true, message: '请输入商品编码', trigger: 'blur' }],
  title: [{ required: true, message: '请输入商品标题', trigger: 'blur' }],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }],
  pricePoint: [{ required: true, message: '请输入所需积分', trigger: 'blur' }],
  stock: [{ required: true, message: '请输入库存', trigger: 'blur' }]
}

const markFailed = (id) => {
  if (!failedIds.value.includes(id)) failedIds.value.push(id)
}

const loadData = async () => {
  loading.value = true
  try {
    const data = await getAdminGoodsPage({
      page: pagination.page,
      size: pagination.size,
      keyword: query.keyword || undefined,
      category: query.category || undefined,
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
  query.keyword = ''
  query.category = ''
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
  Object.assign(form, emptyForm(), {
    ...row,
    tags: Array.isArray(row.tags) ? row.tags.join(',') : row.tags || ''
  })
  dialogVisible.value = true
}

const onSubmit = async () => {
  submitting.value = true
  try {
    await saveGoods({ ...form })
    ElMessage.success(form.id ? '商品已更新' : '商品已创建')
    dialogVisible.value = false
    await loadData()
  } catch (e) {
    handleError(e, '保存商品')
  } finally {
    submitting.value = false
  }
}

const onToggleStatus = async (row) => {
  const nextStatus = row.status === 1 ? 0 : 1
  try {
    await ElMessageBox.confirm(
      `确认${nextStatus === 1 ? '上架' : '下架'}「${row.title}」？`,
      nextStatus === 1 ? '上架商品' : '下架商品',
      { type: 'warning' }
    )
  } catch (e) {
    return
  }
  try {
    await updateGoodsStatus({ id: row.id, status: nextStatus })
    ElMessage.success(nextStatus === 1 ? '已上架' : '已下架')
    await loadData()
  } catch (e) {
    handleError(e, '更新商品状态')
  }
}

onMounted(loadData)
</script>

<style scoped>
.goods-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.goods-cell__cover {
  flex: 0 0 40px;
  width: 40px;
  height: 40px;
  border-radius: var(--cg-radius-sm);
  overflow: hidden;
  background: var(--cg-surface-2);
}

.goods-cell__info {
  min-width: 0;
}
</style>
