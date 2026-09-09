<template>
  <div class="cg-page">
    <PageHeader title="成长首页" :description="welcomeText">
      <el-button :icon="Refresh" :loading="loading" @click="loadAll">刷新</el-button>
    </PageHeader>

    <el-row :gutter="16">
      <!-- 左：签到 -->
      <el-col :xs="24" :sm="24" :md="12" :lg="10">
        <div class="cg-card">
          <div class="cg-card__header">
            <div class="cg-card__title">
              <el-icon><Calendar /></el-icon>
              每日签到
            </div>
            <el-tag v-if="calendar?.todaySigned" type="success" size="small" effect="plain">今日已签到</el-tag>
            <el-tag v-else type="warning" size="small" effect="plain">今日未签到</el-tag>
          </div>

          <div class="cg-card__body">
            <el-row :gutter="12">
              <el-col :span="8">
                <StatCard label="连续签到" :value="stat.continuousDays" unit="天" icon="trend" />
              </el-col>
              <el-col :span="8">
                <StatCard label="本月签到" :value="stat.monthCount" unit="天" icon="trend" />
              </el-col>
              <el-col :span="8">
                <StatCard label="今年签到" :value="stat.yearCount" unit="天" icon="trend" />
              </el-col>
            </el-row>

            <div class="cg-mt-16">
              <SigninCalendar
                :calendar="calendar"
                :loading="calendarLoading"
                @prev="changeMonth(-1)"
                @next="changeMonth(1)"
              />
            </div>

            <el-button
              class="cg-mt-16"
              type="primary"
              size="large"
              style="width: 100%"
              :loading="signing"
              :disabled="calendar?.todaySigned"
              @click="onSignIn"
            >
              {{ calendar?.todaySigned ? '今天已签到' : '立即签到' }}
            </el-button>

            <div v-if="signResult" class="signin-result">
              签到成功：+{{ formatPoint(signResult.pointAward) }} 积分，连续 {{ signResult.continuousDays }} 天
              <span v-if="signResult.extraAward">，额外奖励 +{{ formatPoint(signResult.extraAward) }}</span>
            </div>
          </div>
        </div>
      </el-col>

      <!-- 右：积分账户 + 今日任务 -->
      <el-col :xs="24" :sm="24" :md="12" :lg="14">
        <div class="cg-card">
          <div class="cg-card__header">
            <div class="cg-card__title">
              <el-icon><Wallet /></el-icon>
              积分账户
            </div>
            <el-link type="primary" :underline="false" @click="$router.push('/student/points')">积分明细</el-link>
          </div>
          <div class="cg-card__body">
            <el-row :gutter="12">
              <el-col :xs="12" :sm="6">
                <StatCard label="可用积分" :value="account.balance" icon="coin" />
              </el-col>
              <el-col :xs="12" :sm="6">
                <StatCard label="累计获得" :value="account.totalEarned" icon="coin" />
              </el-col>
              <el-col :xs="12" :sm="6">
                <StatCard label="累计消耗" :value="account.totalUsed" icon="coin" />
              </el-col>
              <el-col :xs="12" :sm="6">
                <StatCard
                  label="成长等级"
                  :value="account.growthLevel ? `Lv.${account.growthLevel}` : null"
                  icon="user"
                />
              </el-col>
            </el-row>

            <div v-if="account.nextLevelPoint" class="level-tip">
              <div class="cg-flex-between cg-mb-12">
                <span class="cg-text-2">距离下一等级还需 {{ formatPoint(nextLevelGap) }} 积分</span>
                <span class="cg-text-3">{{ formatPoint(account.totalEarned) }} / {{ formatPoint(account.nextLevelPoint) }}</span>
              </div>
              <el-progress :percentage="levelPercent" :show-text="false" :stroke-width="10" />
            </div>
          </div>
        </div>

        <div class="cg-card">
          <div class="cg-card__header">
            <div class="cg-card__title">
              <el-icon><List /></el-icon>
              今日任务
            </div>
            <el-tag size="small" effect="plain" type="info">共 {{ tasks.length }} 个</el-tag>
          </div>
          <div class="cg-card__body">
            <el-skeleton v-if="taskLoading" :rows="3" animated />

            <EmptyState
              v-else-if="!tasks.length"
              title="暂无进行中的任务"
              description="任务由运营在管理端配置，稍后再来看看"
              icon="doc"
            />

            <div v-else>
              <div v-for="task in tasks" :key="task.taskCode" class="task-item">
                <div class="task-item__main">
                  <div class="task-item__title">
                    {{ task.taskName }}
                    <el-tag size="small" effect="plain" :type="TASK_TYPE_MAP[task.taskType]?.type || 'info'">
                      {{ TASK_TYPE_MAP[task.taskType]?.label || task.taskType }}
                    </el-tag>
                  </div>
                  <div class="task-item__desc cg-text-3">{{ task.description || '—' }}</div>
                  <el-progress
                    class="cg-mt-8"
                    :percentage="percentOf(task)"
                    :stroke-width="8"
                    :status="task.status === 1 ? 'success' : undefined"
                  />
                  <div class="task-item__meta cg-text-3">
                    进度 {{ task.progress ?? 0 }} / {{ task.targetValue ?? 1 }} · 奖励
                    {{ formatPoint(task.pointAward) }} 积分
                  </div>
                </div>

                <div class="task-item__action">
                  <el-button
                    v-if="task.status === 1"
                    type="primary"
                    size="small"
                    :loading="claimingCode === task.taskCode"
                    @click="onClaim(task)"
                  >
                    领奖
                  </el-button>
                  <el-tag v-else-if="task.status === 2" type="success" size="small" effect="plain">已领取</el-tag>
                  <el-tag v-else type="info" size="small" effect="plain">进行中</el-tag>
                </div>
              </div>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Calendar, List, Refresh, Wallet } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import SigninCalendar from '@/components/SigninCalendar.vue'
import { getSignInCalendar, getSignInStat, doSignIn } from '@/api/signin'
import { getPointAccount } from '@/api/point'
import { getTaskList, claimTaskReward } from '@/api/task'
import { useUserStore } from '@/stores/user'
import { TASK_TYPE_MAP } from '@/utils/dict'
import { formatMonth, formatPoint, num } from '@/utils/format'
import { handleError } from '@/utils/errorHandler'

/**
 * 学生端首页。接口对齐 docs/03-api.md：
 *   GET  /api/signin/calendar?month=yyyyMM
 *   GET  /api/signin/stat
 *   POST /api/signin/do
 *   GET  /api/point/account
 *   GET  /api/task/list
 *   POST /api/task/reward/claim
 */
const userStore = useUserStore()

const loading = ref(false)
const calendarLoading = ref(false)
const signing = ref(false)
const taskLoading = ref(false)
const claimingCode = ref('')

const month = ref(formatMonth(new Date()))
const calendar = ref(null)
const signResult = ref(null)
const tasks = ref([])
const stat = reactive({ continuousDays: null, monthCount: null, yearCount: null, lastSignDate: '' })
const account = reactive({ balance: null, totalEarned: null, totalUsed: null, growthLevel: null, nextLevelPoint: null })

const welcomeText = computed(() => {
  const name = userStore.userInfo?.nickname || userStore.userInfo?.username || '同学'
  const school = userStore.userInfo?.school
  return school ? `${school} · ${name}，今天也要记得签到` : `${name}，今天也要记得签到`
})

const nextLevelGap = computed(() => Math.max(0, num(account.nextLevelPoint) - num(account.totalEarned)))

const levelPercent = computed(() => {
  const target = num(account.nextLevelPoint)
  if (!target) return 0
  return Math.min(100, Math.round((num(account.totalEarned) / target) * 100))
})

const percentOf = (task) => {
  const target = num(task.targetValue, 1) || 1
  return Math.min(100, Math.round((num(task.progress) / target) * 100))
}

/** 签到日历 */
const loadCalendar = async () => {
  calendarLoading.value = true
  try {
    calendar.value = await getSignInCalendar(month.value)
  } catch (e) {
    calendar.value = null
  } finally {
    calendarLoading.value = false
  }
}

const loadStat = async () => {
  try {
    const data = await getSignInStat()
    Object.assign(stat, {
      continuousDays: data?.continuousDays ?? 0,
      monthCount: data?.monthCount ?? 0,
      yearCount: data?.yearCount ?? 0,
      lastSignDate: data?.lastSignDate || ''
    })
  } catch (e) {
    Object.assign(stat, { continuousDays: null, monthCount: null, yearCount: null })
  }
}

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
    // 同步顶栏积分
    if (userStore.userInfo) userStore.userInfo.balance = account.balance
  } catch (e) {
    // 保持 null，StatCard 会显示 "-"
  }
}

const loadTasks = async () => {
  taskLoading.value = true
  try {
    const data = await getTaskList()
    tasks.value = Array.isArray(data) ? data : []
  } catch (e) {
    tasks.value = []
  } finally {
    taskLoading.value = false
  }
}

const loadAll = async () => {
  loading.value = true
  await Promise.all([loadCalendar(), loadStat(), loadAccount(), loadTasks()])
  loading.value = false
}

const changeMonth = (delta) => {
  const text = month.value
  const year = Number(text.slice(0, 4))
  const monthIndex = Number(text.slice(4)) - 1 + delta
  const date = new Date(year, monthIndex, 1)
  month.value = formatMonth(date)
  loadCalendar()
}

const onSignIn = async () => {
  signing.value = true
  try {
    const data = await doSignIn()
    signResult.value = data
    ElMessage.success(`签到成功，获得 ${formatPoint(data?.pointAward)} 积分`)
    await Promise.all([loadCalendar(), loadStat(), loadAccount(), loadTasks()])
  } catch (e) {
    // 已签到 / 限流等业务失败由拦截器提示
  } finally {
    signing.value = false
  }
}

const onClaim = async (task) => {
  claimingCode.value = task.taskCode
  try {
    const data = await claimTaskReward(task.taskCode)
    ElMessage.success(`领取成功，+${formatPoint(data?.pointAward)} 积分`)
    await Promise.all([loadTasks(), loadAccount()])
  } catch (e) {
    handleError(e, '领取任务奖励')
  } finally {
    claimingCode.value = ''
  }
}

onMounted(loadAll)
</script>

<style scoped>
.task-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 0;
}

.task-item + .task-item {
  border-top: 1px solid var(--cg-border-light);
}

.task-item__main {
  flex: 1 1 auto;
  min-width: 0;
}

.task-item__title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  flex-wrap: wrap;
}

.task-item__desc {
  margin-top: 2px;
  font-size: 12px;
}

.task-item__meta {
  margin-top: 4px;
  font-size: 12px;
}

.task-item__action {
  flex: 0 0 auto;
}

.signin-result {
  margin-top: 10px;
  padding: 8px 12px;
  border-radius: var(--cg-radius-sm);
  background: var(--cg-jade-bg);
  color: var(--cg-jade-deep);
  font-size: 13px;
}

.level-tip {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--cg-border-light);
}
</style>
