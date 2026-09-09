<template>
  <div class="cg-page">
    <PageHeader title="权益商城" description="用积分兑换校园学习、生活与运动权益">
      <el-input
        v-model="query.keyword"
        placeholder="搜索权益名称"
        clearable
        :prefix-icon="Search"
        class="mall-search"
        @keyup.enter="onSearch"
        @clear="onSearch"
      />
      <el-select v-model="query.sort" class="mall-sort" @change="onSearch">
        <el-option v-for="item in GOODS_SORT_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="onSearch">搜索</el-button>
    </PageHeader>

    <!-- 分类筛选 -->
    <div class="cg-card mall-filter">
      <div class="cg-card__body">
        <el-skeleton v-if="categoryLoading" :rows="1" animated />
        <template v-else>
          <div class="mall-filter__row">
            <span class="mall-filter__label">分类</span>
            <div class="mall-filter__tags">
              <el-check-tag :checked="!query.category" @change="selectCategory('')">全部</el-check-tag>
              <el-check-tag
                v-for="item in categories"
                :key="item.code"
                :checked="query.category === item.code"
                @change="selectCategory(item.code)"
              >
                {{ item.name }}<span v-if="item.count !== undefined" class="cg-text-3">（{{ item.count }}）</span>
              </el-check-tag>
            </div>
          </div>
        </template>
      </div>
    </div>

    <!-- 商品网格：窄屏单列 / 平板两列 / 中屏三列 / 宽屏四列 -->
    <el-row v-if="loading" :gutter="16">
      <el-col v-for="n in 8" :key="n" :xs="24" :sm="12" :md="8" :lg="6" class="cg-mb-16">
        <div class="mall-skeleton"><el-skeleton :rows="4" animated /></div>
      </el-col>
    </el-row>

    <div v-else-if="!goodsList.length" class="cg-card">
      <div class="cg-card__body">
        <EmptyState
          icon="goods"
          title="没有找到符合条件的权益"
          description="换个关键词或分类试试，也可以清空筛选条件"
        >
          <el-button @click="onReset">清空筛选</el-button>
        </EmptyState>
      </div>
    </div>

    <el-row v-else :gutter="16" class="goods-grid">
      <el-col v-for="goods in goodsList" :key="goods.id" :xs="24" :sm="12" :md="8" :lg="6" class="cg-mb-16">
        <GoodsCard :goods="goods" @click="goDetail(goods)">
          <template #actions>
            <el-button
              type="primary"
              size="small"
              style="width: 100%"
              :disabled="goods.status === 0 || num(goods.stock) <= 0"
              @click="goDetail(goods)"
            >
              {{ goods.status === 0 ? '已下架' : num(goods.stock) <= 0 ? '已售罄' : '立即兑换' }}
            </el-button>
          </template>
        </GoodsCard>
      </el-col>
    </el-row>

    <div v-if="!loading && goodsList.length" class="mall-pager">
      <el-pagination
        :current-page="pagination.page"
        :page-size="pagination.size"
        :page-sizes="[12, 24, 48]"
        :total="pagination.total"
        :layout="pagerLayout"
        background
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import GoodsCard from '@/components/GoodsCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import { getGoodsCategories, getGoodsPage } from '@/api/benefit'
import { reportTaskProgress } from '@/api/task'
import { GOODS_SORT_OPTIONS } from '@/utils/dict'
import { num } from '@/utils/format'
import { handleError } from '@/utils/errorHandler'

/**
 * 权益商城。接口：
 *   GET /api/benefit/categories
 *   GET /api/benefit/goods/page?page&size&category&keyword&sort
 *   POST /api/task/progress/report  （浏览任务 DAILY_BROWSE 进度上报）
 */
const router = useRouter()

const loading = ref(false)
const categoryLoading = ref(false)
const categories = ref([])
const goodsList = ref([])

const query = reactive({ category: '', keyword: '', sort: 'default' })
const pagination = reactive({ page: 1, size: 12, total: 0 })

/** 浏览进度去重上报，避免每次翻页都打接口 */
const reportedGoods = new Set()

const isNarrow = computed(() => typeof window !== 'undefined' && window.innerWidth <= 768)
const pagerLayout = computed(() =>
  isNarrow.value ? 'prev, pager, next' : 'total, sizes, prev, pager, next, jumper'
)

const loadCategories = async () => {
  categoryLoading.value = true
  try {
    const data = await getGoodsCategories()
    categories.value = Array.isArray(data) ? data : []
  } catch (e) {
    categories.value = []
  } finally {
    categoryLoading.value = false
  }
}

const loadGoods = async () => {
  loading.value = true
  try {
    const data = await getGoodsPage({
      page: pagination.page,
      size: pagination.size,
      category: query.category || undefined,
      keyword: query.keyword || undefined,
      sort: query.sort || undefined
    })
    goodsList.value = Array.isArray(data?.records) ? data.records : []
    pagination.total = num(data?.total)
  } catch (e) {
    goodsList.value = []
    pagination.total = 0
  } finally {
    loading.value = false
  }
}

const onSearch = () => {
  pagination.page = 1
  loadGoods()
}

const onReset = () => {
  query.category = ''
  query.keyword = ''
  query.sort = 'default'
  pagination.page = 1
  loadGoods()
}

const selectCategory = (code) => {
  query.category = code
  pagination.page = 1
  loadGoods()
}

const onPageChange = (page) => {
  pagination.page = page
  loadGoods()
}

const onSizeChange = (size) => {
  pagination.size = size
  pagination.page = 1
  loadGoods()
}

/** 上报“浏览权益商城”任务进度（后端按 taskCode 累加，失败不影响浏览） */
const reportBrowse = (goodsId) => {
  if (reportedGoods.has(goodsId)) return
  reportedGoods.add(goodsId)
  reportTaskProgress({ taskCode: 'DAILY_BROWSE', delta: 1 }).catch(() => {})
}

const goDetail = (goods) => {
  if (!goods?.id) return
  reportBrowse(goods.id)
  router.push(`/student/goods/${goods.id}`)
}

onMounted(() => {
  loadCategories()
  loadGoods().catch((e) => handleError(e, '加载商品列表'))
})
</script>

<style scoped>
.mall-search {
  width: 220px;
}

.mall-sort {
  width: 150px;
}

.mall-filter {
  margin-bottom: 16px;
}

.mall-filter__row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.mall-filter__label {
  flex: 0 0 auto;
  color: var(--cg-text-3);
  font-size: 13px;
  line-height: 24px;
}

.mall-filter__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.mall-skeleton {
  background: #fff;
  border: 1px solid var(--cg-border-light);
  border-radius: var(--cg-radius);
  padding: 12px;
}

.mall-pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 4px;
}

@media (max-width: 768px) {
  .mall-search {
    width: 100%;
  }

  .mall-sort {
    width: 100%;
  }

  .mall-filter__row {
    flex-direction: column;
    gap: 8px;
  }

  .mall-pager {
    justify-content: center;
  }
}
</style>
