<template>
  <div class="data-table">
    <el-table
      v-loading="loading"
      :data="data"
      :row-key="rowKey"
      :size="size"
      table-layout="fixed"
      style="width: 100%"
      :max-height="maxHeight"
      :show-overflow-tooltip="showOverflowTooltip"
      @selection-change="(rows) => emit('selection-change', rows)"
      @sort-change="(payload) => emit('sort-change', payload)"
    >
      <!-- 多选列 -->
      <el-table-column v-if="selectable" type="selection" width="46" fixed="left" align="center" />

      <!-- 序号列 -->
      <el-table-column
        v-if="showIndex"
        type="index"
        label="#"
        width="56"
        align="center"
        :index="indexMethod"
        fixed="left"
      />

      <!-- 数据列：由 columns 描述，min-width 保证窄屏下横向滚动而不是挤压 -->
      <el-table-column
        v-for="col in columns"
        :key="col.prop || col.label"
        :prop="col.prop"
        :label="col.label"
        :width="col.width"
        :min-width="col.minWidth || 100"
        :fixed="col.fixed"
        :align="col.align || 'left'"
        :header-align="col.headerAlign || col.align || 'left'"
        :sortable="col.sortable || false"
        :show-overflow-tooltip="col.showOverflowTooltip ?? showOverflowTooltip"
      >
        <template #default="scope">
          <!-- 具名插槽优先：<template #col-xxx> -->
          <slot v-if="$slots[`col-${col.prop}`]" :name="`col-${col.prop}`" v-bind="scope" />
          <span v-else-if="col.formatter">{{ col.formatter(scope.row, scope.$index) }}</span>
          <span v-else>{{ formatCell(scope.row[col.prop], col) }}</span>
        </template>
      </el-table-column>

      <!-- 操作列：固定右侧，宽度可控 -->
      <el-table-column
        v-if="$slots.actions"
        label="操作"
        :width="actionWidth"
        fixed="right"
        align="center"
        :header-align="'center'"
      >
        <template #default="scope">
          <div class="data-table__actions">
            <slot name="actions" v-bind="scope" />
          </div>
        </template>
      </el-table-column>

      <!-- 空状态：接口失败或确实无数据时统一降级 -->
      <template #empty>
        <slot name="empty">
          <EmptyState compact :title="emptyText" :description="emptyDescription" icon="doc" />
        </slot>
      </template>
    </el-table>

    <!-- 分页：窄屏只保留页码 + 上下页 -->
    <div v-if="showPagination" class="data-table__pager">
      <el-pagination
        :current-page="pagination.page"
        :page-size="pagination.size"
        :page-sizes="pageSizes"
        :total="pagination.total"
        :layout="paginationLayout"
        :small="isNarrow"
        background
        @current-change="(page) => emit('page-change', page)"
        @size-change="(size) => emit('size-change', size)"
      />
    </div>
  </div>
</template>


<script setup>
import { computed } from 'vue'
import EmptyState from '@/components/EmptyState.vue'
import { formatDateTime, formatPoint } from '@/utils/format'

/**
 * 表格封装：el-table + 分页 + loading + 空状态。
 * 用法：
 *   <DataTable :columns="columns" :data="rows" :pagination="page" @page-change="load" />
 *   <template #col-status="{ row }"> ... </template>
 *   <template #actions="{ row }"> ... </template>
 */
const props = defineProps({
  /** [{prop,label,width,minWidth,fixed,align,sortable,type,formatter,showOverflowTooltip}] */
  columns: { type: Array, default: () => [] },
  data: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  /** {page,size,total}，传了就渲染分页 */
  pagination: { type: Object, default: null },
  rowKey: { type: String, default: 'id' },
  selectable: { type: Boolean, default: false },
  showIndex: { type: Boolean, default: false },
  actionWidth: { type: [Number, String], default: 160 },
  emptyText: { type: String, default: '暂无数据' },
  emptyDescription: { type: String, default: '调整查询条件后重试，或稍后再来看看' },
  size: { type: String, default: 'default' },
  maxHeight: { type: [Number, String], default: null },
  showOverflowTooltip: { type: Boolean, default: true },
  pageSizes: { type: Array, default: () => [10, 20, 50, 100] }
})

const emit = defineEmits(['page-change', 'size-change', 'selection-change', 'sort-change'])

const isNarrow = computed(() => typeof window !== 'undefined' && window.innerWidth <= 768)

const showPagination = computed(() => !!props.pagination && Number(props.pagination.total || 0) >= 0)

/** 窄屏隐藏每页条数选择，避免分页器换行 */
const paginationLayout = computed(() =>
  isNarrow.value ? 'prev, pager, next' : 'total, sizes, prev, pager, next, jumper'
)

const indexMethod = (index) => {
  const page = props.pagination?.page || 1
  const size = props.pagination?.size || 10
  return (page - 1) * size + index + 1
}

/** 列类型自动格式化：date / point / money */
const formatCell = (value, col) => {
  if (value === null || value === undefined || value === '') return '-'
  if (col.type === 'date') return formatDateTime(value)
  if (col.type === 'point') return formatPoint(value)
  if (col.type === 'bool') return value ? '是' : '否'
  return String(value)
}
</script>

<style scoped>
.data-table {
  background: var(--cg-surface);
  border: 1px solid var(--cg-border);
  border-radius: var(--cg-radius);
  overflow: hidden;
}

.data-table__actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  flex-wrap: wrap;
}

.data-table__actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.data-table__pager {
  display: flex;
  justify-content: flex-end;
  padding: 12px 14px;
  border-top: 1px solid var(--cg-border-light);
  background: var(--cg-surface);
}

@media (max-width: 768px) {
  .data-table__pager {
    justify-content: center;
  }
}
</style>
