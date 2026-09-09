<template>
  <div class="search-bar">
    <div class="search-bar__fields">
      <slot />
    </div>
    <div class="search-bar__actions">
      <slot name="actions">
        <el-button type="primary" :loading="loading" :icon="Search" @click="emit('search')">查询</el-button>
        <el-button :icon="RefreshLeft" @click="emit('reset')">重置</el-button>
      </slot>
    </div>
  </div>
</template>

<script setup>
import { RefreshLeft, Search } from '@element-plus/icons-vue'

/**
 * 查询表单区：字段放默认插槽，按钮放 actions 插槽。
 * 窄屏自动换行，字段宽度由外层 el-col 的 :xs/:sm/:md/:lg 控制。
 */
defineProps({
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['search', 'reset'])
</script>

<style scoped>
.search-bar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding: 16px 16px 4px;
  background: #fff;
  border: 1px solid var(--cg-border-light);
  border-radius: var(--cg-radius);
  margin-bottom: 16px;
}

.search-bar__fields {
  flex: 1 1 420px;
  min-width: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

/* 插槽里的 el-form-item 统一去掉底部间距，交给 gap 控制节奏 */
.search-bar__fields :deep(.el-form-item) {
  margin-bottom: 12px;
  margin-right: 0;
}

.search-bar__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding-bottom: 12px;
}

@media (max-width: 768px) {
  .search-bar {
    padding: 12px 12px 0;
  }

  .search-bar__fields {
    flex: 1 1 100%;
  }

  .search-bar__actions {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
