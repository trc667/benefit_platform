<template>
  <div class="cg-page">
    <PageHeader title="积分排行榜" description="总榜与月榜均来自 Redis ZSet，实时更新">
      <el-radio-group v-model="type" @change="loadAll">
        <el-radio-button value="TOTAL">总榜</el-radio-button>
        <el-radio-button value="MONTH">月榜</el-radio-button>
      </el-radio-group>
      <el-button :icon="Refresh" :loading="loading" @click="loadAll">刷新</el-button>
    </PageHeader>

    <el-row :gutter="16">
      <!-- 我的排名 -->
      <el-col :xs="24" :sm="24" :md="8" :lg="8">
        <div class="cg-card my-rank">
          <div class="cg-card__header">
            <div class="cg-card__title">我的排名</div>
            <el-tag size="small" effect="plain">{{ type === 'TOTAL' ? '总榜' : '月榜' }}</el-tag>
          </div>
          <div class="cg-card__body">
            <div class="my-rank__row">
              <div class="app-header__avatar my-rank__avatar">{{ userStore.avatarChar }}</div>
              <div class="my-rank__info">
                <div class="my-rank__name">{{ userStore.nickname }}</div>
                <div class="cg-text-3 my-rank__sub">
                  {{ userStore.userInfo?.school || '未填写学校' }}
                </div>
              </div>
            </div>

            <div class="my-rank__stats">
              <div class="my-rank__stat">
                <div class="cg-text-3">名次</div>
                <div class="my-rank__stat-value cg-point">
                  {{ myRank.rank ? `No.${myRank.rank}` : '-' }}
                </div>
              </div>
              <div class="my-rank__stat">
                <div class="cg-text-3">积分</div>
                <div class="my-rank__stat-value cg-point">{{ formatPoint(myRank.point) }}</div>
              </div>
              <div class="my-rank__stat">
                <div class="cg-text-3">参与人数</div>
                <div class="my-rank__stat-value cg-point">{{ formatPoint(myRank.total) }}</div>
              </div>
            </div>
          </div>
        </div>

        <div class="cg-card">
          <div class="cg-card__header">
            <div class="cg-card__title">榜单说明</div>
          </div>
          <div class="cg-card__body cg-text-3 rank-note">
            <div>· 总榜累计全部获得的积分，月榜按自然月统计</div>
            <div>· 签到、任务、兑换码获得的积分都会计入</div>
            <div>· 数据来自 Redis ZSet，接口返回实时值</div>
          </div>
        </div>
      </el-col>

      <!-- 榜单 -->
      <el-col :xs="24" :sm="24" :md="16" :lg="16">
        <div class="cg-card">
          <div class="cg-card__header">
            <div class="cg-card__title">
              <el-icon><Trophy /></el-icon>
              {{ type === 'TOTAL' ? '总积分榜' : '本月积分榜' }}
            </div>
            <span class="cg-text-3">Top {{ rankList.length }}</span>
          </div>
          <div class="cg-card__body">
            <el-skeleton v-if="loading" :rows="6" animated />

            <EmptyState
              v-else-if="!rankList.length"
              icon="search"
              title="榜单暂时为空"
              description="等同学们签到攒积分后就会有数据"
            />

            <div v-else>
              <div
                v-for="item in rankList"
                :key="item.userId || item.rank"
                class="rank-item"
                :class="{ 'rank-item--top': item.rank <= 3, 'rank-item--me': item.isMe }"
              >
                <div class="rank-badge" :class="item.rank <= 3 ? `rank-badge--${item.rank}` : ''">
                  {{ item.rank }}
                </div>
                <div class="app-header__avatar">{{ (item.nickname || '同学').slice(0, 1) }}</div>
                <div class="rank-item__info">
                  <div class="rank-item__name">
                    {{ item.nickname || '匿名同学' }}
                    <el-tag v-if="item.isMe" size="small" type="primary" effect="plain">我</el-tag>
                  </div>
                  <div class="cg-text-3 rank-item__school">{{ item.school || '' }}</div>
                </div>
                <div class="rank-item__point cg-point">{{ formatPoint(item.point) }}</div>
              </div>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Refresh, Trophy } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import { getMyRank, getPointRank } from '@/api/point'
import { useUserStore } from '@/stores/user'
import { formatPoint } from '@/utils/format'

/**
 * 积分排行榜。接口：
 *   GET /api/point/rank?type=TOTAL|MONTH&limit=20
 *   GET /api/point/rank/me?type=TOTAL|MONTH
 */
const userStore = useUserStore()

const type = ref('TOTAL')
const loading = ref(false)
const rankList = ref([])
const myRank = reactive({ rank: null, point: null, total: null })

const loadRank = async () => {
  try {
    const data = await getPointRank({ type: type.value, limit: 20 })
    rankList.value = Array.isArray(data) ? data : []
  } catch (e) {
    rankList.value = []
  }
}

const loadMyRank = async () => {
  try {
    const data = await getMyRank(type.value)
    myRank.rank = data?.rank ?? null
    myRank.point = data?.point ?? null
    myRank.total = data?.total ?? null
  } catch (e) {
    Object.assign(myRank, { rank: null, point: null, total: null })
  }
}

const loadAll = async () => {
  loading.value = true
  await Promise.all([loadRank(), loadMyRank()])
  loading.value = false
}

onMounted(loadAll)
</script>

<style scoped>
.my-rank__row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.my-rank__avatar {
  width: 44px;
  height: 44px;
  font-size: 18px;
}

.my-rank__name {
  font-size: 15px;
  font-weight: 600;
}

.my-rank__sub {
  margin-top: 2px;
  font-size: 12px;
}

.my-rank__stats {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--cg-border-light);
  display: flex;
  justify-content: space-between;
  gap: 8px;
}

.my-rank__stat {
  text-align: center;
  flex: 1 1 0;
  font-size: 12px;
}

.my-rank__stat-value {
  margin-top: 6px;
  font-size: 16px;
  color: var(--cg-text-1);
}

.rank-item__info {
  flex: 1 1 auto;
  min-width: 0;
}

.rank-item__name {
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.rank-item__school {
  margin-top: 2px;
  font-size: 12px;
}

.rank-item__point {
  flex: 0 0 auto;
  font-size: 16px;
  color: var(--cg-primary);
}

.rank-note {
  font-size: 12px;
  line-height: 2;
}
</style>
