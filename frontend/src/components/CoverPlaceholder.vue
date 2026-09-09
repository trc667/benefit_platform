<template>
  <div class="cg-cover" :class="`cg-cover--${categoryClass}`">
    <el-icon v-if="iconComponent" :size="34" color="rgba(255,255,255,0.95)">
      <component :is="iconComponent" />
    </el-icon>
    <span v-else class="cg-cover__char">{{ char }}</span>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Coffee, Collection, Football, Notebook, Reading } from '@element-plus/icons-vue'
import { initialChar } from '@/utils/format'

/**
 * 商品封面占位：后端 cover_url 指向 /img/goods/*.png，静态资源未提供时用
 * 「分类渐变底色 + 分类图标 / 标题首字」兜底，避免出现破图。
 */
const props = defineProps({
  goods: { type: Object, default: () => ({}) }
})

const CATEGORY_ICONS = {
  STUDY: Reading,
  FOOD: Coffee,
  LIFE: Collection,
  SPORT: Football,
  OTHER: Notebook
}

const categoryClass = computed(() => {
  const category = props.goods?.category
  return CATEGORY_ICONS[category] ? category : 'OTHER'
})

const iconComponent = computed(() => CATEGORY_ICONS[categoryClass.value] || null)

const char = computed(() => initialChar(props.goods?.title || props.goods?.goodsTitle))
</script>
