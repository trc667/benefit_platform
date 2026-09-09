<template>
  <div class="page-header">
    <div class="page-header__main">
      <div class="page-header__title">
        <slot name="title">{{ title }}</slot>
      </div>
      <div v-if="description || $slots.description" class="page-header__desc">
        <slot name="description">{{ description }}</slot>
      </div>
    </div>
    <div v-if="$slots.default" class="page-header__actions">
      <slot />
    </div>
  </div>
</template>

<script setup>
/**
 * 页面标题区：标题 + 描述 + 右侧操作插槽。
 * 窄屏下自动换行，操作按钮不会挤压标题。
 */
defineProps({
  title: { type: String, default: '' },
  description: { type: String, default: '' }
})
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding-bottom: 14px;
  margin-bottom: 18px;
  border-bottom: 1px solid var(--cg-border-light);
}

.page-header__main {
  min-width: 0;
  flex: 1 1 260px;
}

.page-header__title {
  font-size: 20px;
  font-weight: 600;
  letter-spacing: -0.02em;
  color: var(--cg-text-1);
  line-height: 1.35;
}

.page-header__desc {
  margin-top: 5px;
  font-size: 13px;
  color: var(--cg-text-3);
  line-height: 1.6;
}

.page-header__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

@media (max-width: 768px) {
  .page-header__title {
    font-size: 17px;
  }

  .page-header {
    padding-bottom: 12px;
    margin-bottom: 14px;
  }
}
</style>
