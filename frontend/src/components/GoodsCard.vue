<template>
  <div class="goods-card" @click="emit('click', goods)">
    <!-- 封面：/img/goods/*.png 资源缺失时走渐变 + 首字占位，不留破图 -->
    <div class="goods-card__cover">
      <el-image v-if="coverUrl && !failed" :src="coverUrl" fit="cover" class="goods-card__img" @error="failed = true">
        <template #error>
          <CoverPlaceholder :goods="goods" />
        </template>
      </el-image>
      <CoverPlaceholder v-else :goods="goods" />

      <div v-if="stockTip" class="goods-card__stock-tip">{{ stockTip }}</div>
    </div>

    <div class="goods-card__body">
      <div class="goods-card__title cg-ellipsis-2">{{ goods.title || '未命名权益' }}</div>
      <div class="goods-card__sub cg-ellipsis">{{ goods.subTitle || '' }}</div>

      <div class="goods-card__tags">
        <el-tag v-for="tag in tagList" :key="tag" size="small" type="info" effect="plain">{{ tag }}</el-tag>
      </div>

      <div class="goods-card__footer">
        <div class="goods-card__price">
          <span class="cg-point">{{ formatPoint(goods.pricePoint) }}</span>
          <span class="goods-card__unit">积分</span>
        </div>
        <div class="goods-card__sold cg-text-3">已兑 {{ num(goods.soldCount) }}</div>
      </div>

      <div v-if="$slots.actions" class="goods-card__actions" @click.stop>
        <slot name="actions" :goods="goods" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import CoverPlaceholder from '@/components/CoverPlaceholder.vue'
import { num, formatPoint } from '@/utils/format'

/**
 * 学生端权益商品卡片。父级用 el-col :xs="24" :sm="12" :md="8" :lg="6" 控制栅格。
 */
const props = defineProps({
  goods: { type: Object, required: true },
  /** 为 true 时不响应点击（例如管理端复用） */
  readonly: { type: Boolean, default: false }
})

const emit = defineEmits(['click'])

const failed = ref(false)
const coverUrl = computed(() => props.goods?.coverUrl || '')

// 换商品时重置加载失败标记
watch(coverUrl, () => {
  failed.value = false
})

const tagList = computed(() => {
  const tags = props.goods?.tags
  if (!tags) return []
  if (Array.isArray(tags)) return tags.slice(0, 3)
  return String(tags)
    .split(',')
    .map((t) => t.trim())
    .filter(Boolean)
    .slice(0, 3)
})

const stockTip = computed(() => {
  const stock = num(props.goods?.stock)
  if (props.goods?.status === 0) return '已下架'
  if (stock <= 0) return '已售罄'
  return ''
})
</script>

<style scoped>
.goods-card {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--cg-surface);
  border: 1px solid var(--cg-border);
  border-radius: var(--cg-radius);
  overflow: hidden;
  cursor: pointer;
  transition: border-color 0.14s var(--cg-ease);
}

.goods-card:hover {
  border-color: var(--cg-border-strong);
}

.goods-card__cover {
  position: relative;
  width: 100%;
  height: 132px;
  background: var(--cg-surface-2);
}

.goods-card__img {
  width: 100%;
  height: 100%;
  display: block;
}

.goods-card__stock-tip {
  position: absolute;
  right: 8px;
  top: 8px;
  padding: 1px 7px;
  border-radius: var(--cg-radius-xs);
  background: rgba(23, 24, 28, 0.72);
  color: #fff;
  font-size: 11.5px;
}

.goods-card__body {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  padding: 12px;
}

.goods-card__title {
  font-size: 14px;
  font-weight: 600;
  line-height: 1.5;
  min-height: 42px;
  letter-spacing: -0.01em;
}

.goods-card__sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--cg-text-3);
}

.goods-card__tags {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
  margin-top: 8px;
  min-height: 22px;
}

.goods-card__footer {
  margin-top: auto;
  padding-top: 10px;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 8px;
}

.goods-card__price {
  color: var(--cg-jade);
  font-size: 17px;
  font-weight: 600;
  letter-spacing: -0.01em;
  display: flex;
  align-items: baseline;
  gap: 3px;
}

.goods-card__unit {
  font-size: 12px;
  font-weight: 400;
  color: var(--cg-text-3);
}

.goods-card__sold {
  font-size: 12px;
}

.goods-card__actions {
  margin-top: 10px;
}
</style>
