<template>
  <div class="empty-state" :style="{ padding: compact ? '16px 8px' : '40px 16px' }">
    <el-icon :size="compact ? 24 : 34" class="empty-state__icon">
      <component :is="iconComponent" />
    </el-icon>
    <div class="empty-state__title">{{ title }}</div>
    <div v-if="description" class="empty-state__desc">{{ description }}</div>
    <div v-if="$slots.default" class="empty-state__action">
      <slot />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Box, Document, Goods, Search } from '@element-plus/icons-vue'

/**
 * 空状态：列表无数据、接口失败降级时统一使用，避免白屏。
 */
const props = defineProps({
  title: { type: String, default: '暂无数据' },
  description: { type: String, default: '' },
  /** 语义化图标：default | search | goods | doc */
  icon: { type: String, default: 'default' },
  /** 表格内使用的小尺寸 */
  compact: { type: Boolean, default: false }
})

const ICONS = { default: Box, search: Search, goods: Goods, doc: Document }
const iconComponent = computed(() => ICONS[props.icon] || Box)
</script>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  color: var(--cg-text-3);
}

.empty-state__icon {
  color: var(--cg-ink-4);
}

.empty-state__title {
  margin-top: 10px;
  font-size: 14px;
  color: var(--cg-text-2);
}

.empty-state__desc {
  margin-top: 4px;
  font-size: 12px;
  color: var(--cg-text-3);
  max-width: 320px;
  line-height: 1.6;
}

.empty-state__action {
  margin-top: 14px;
}
</style>
