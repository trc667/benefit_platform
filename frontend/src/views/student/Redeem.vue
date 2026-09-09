<template>
  <div class="cg-page">
    <PageHeader title="兑换码兑换" description="输入活动兑换码，积分、优惠券或权益会立即到账" />

    <el-row :gutter="16">
      <el-col :xs="24" :sm="24" :md="14" :lg="14">
        <div class="cg-card">
          <div class="cg-card__header">
            <div class="cg-card__title">
              <el-icon><Wallet /></el-icon>
              输入兑换码
            </div>
          </div>
          <div class="cg-card__body">
            <el-form :model="form" label-position="top" @submit.prevent>
              <el-form-item label="兑换码" prop="code">
                <el-input
                  v-model="form.code"
                  size="large"
                  placeholder="请输入兑换码，如 RCB2026090100001..."
                  clearable
                  maxlength="32"
                  :prefix-icon="Ticket"
                  @keyup.enter="onExchange"
                />
              </el-form-item>
              <el-button
                type="primary"
                size="large"
                style="width: 100%"
                :loading="submitting"
                :disabled="!form.code.trim()"
                @click="onExchange"
              >
                立即兑换
              </el-button>
            </el-form>

            <div class="redeem-tip">
              兑换码由运营在管理端生成，一码一次，兑换后不可重复使用。如提示“已使用”，说明该码已被核销。
            </div>
          </div>
        </div>

        <!-- 兑换结果 -->
        <div v-if="result" class="cg-card">
          <div class="cg-card__header">
            <div class="cg-card__title">
              <el-icon class="cg-text-success"><CircleCheck /></el-icon>
              兑换结果
            </div>
            <el-tag size="small" type="success" effect="plain">成功</el-tag>
          </div>
          <div class="cg-card__body">
            <el-result icon="success" :title="result.message || '兑换成功'" :sub-title="resultTitle">
              <template #extra>
                <el-button type="primary" @click="$router.push('/student/points')">查看积分明细</el-button>
                <el-button @click="$router.push('/student/mall')">去兑换权益</el-button>
              </template>
            </el-result>

            <el-descriptions :column="1" border size="small">
              <el-descriptions-item label="批次号">{{ result.batchNo || '-' }}</el-descriptions-item>
              <el-descriptions-item label="奖励类型">
                <el-tag size="small" effect="plain" :type="REDEEM_BIZ_MAP[result.bizType]?.type || 'info'">
                  {{ REDEEM_BIZ_MAP[result.bizType]?.label || result.bizType || '-' }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="奖励值">{{ formatPoint(result.rewardValue) }}</el-descriptions-item>
              <el-descriptions-item label="当前积分余额">
                <span class="cg-point cg-text-primary">{{ formatPoint(result.balance) }}</span>
              </el-descriptions-item>
            </el-descriptions>
          </div>
        </div>
      </el-col>

      <!-- 兑换记录（本次会话内） -->
      <el-col :xs="24" :sm="24" :md="10" :lg="10">
        <div class="cg-card">
          <div class="cg-card__header">
            <div class="cg-card__title">本次兑换记录</div>
            <el-button v-if="history.length" text type="primary" @click="history = []">清空</el-button>
          </div>
          <div class="cg-card__body">
            <EmptyState
              v-if="!history.length"
              compact
              title="还没有兑换记录"
              description="兑换成功后会在这里留痕，方便核对"
              icon="search"
            />
            <div v-else>
              <div v-for="item in history" :key="item.key" class="history-item">
                <div class="history-item__main">
                  <div class="history-item__code cg-ellipsis">{{ item.code }}</div>
                  <div class="cg-text-3 history-item__time">{{ item.time }}</div>
                </div>
                <div class="history-item__reward">
                  <span class="cg-text-success cg-point">+{{ formatPoint(item.rewardValue) }}</span>
                  <div class="cg-text-3">{{ REDEEM_BIZ_MAP[item.bizType]?.label || item.bizType }}</div>
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
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { CircleCheck, Ticket, Wallet } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import { exchangeCode } from '@/api/redeem'
import { getPointAccount } from '@/api/point'
import { REDEEM_BIZ_MAP } from '@/utils/dict'
import { formatDateTime, formatPoint } from '@/utils/format'
import { useUserStore } from '@/stores/user'

/**
 * 兑换码兑换。接口：
 *   POST /api/redeem/exchange  {code}
 *   GET  /api/point/account    （兑换后刷新余额）
 */
const userStore = useUserStore()

const submitting = ref(false)
const result = ref(null)
const history = ref([])
const form = reactive({ code: '' })

const resultTitle = computed(() => {
  if (!result.value) return ''
  const biz = REDEEM_BIZ_MAP[result.value.bizType]?.label || result.value.bizType || ''
  return `已获得 ${biz} ${formatPoint(result.value.rewardValue)}`
})

const onExchange = async () => {
  const code = form.code.trim()
  if (!code) return

  submitting.value = true
  try {
    const data = await exchangeCode(code)
    result.value = data
    history.value.unshift({
      key: `${code}-${Date.now()}`,
      code,
      rewardValue: data?.rewardValue,
      bizType: data?.bizType,
      time: formatDateTime(new Date())
    })
    form.code = ''
    ElMessage.success('兑换成功')

    // 同步余额（顶栏积分 + 本地用户信息）
    try {
      const account = await getPointAccount()
      if (userStore.userInfo) userStore.userInfo.balance = account?.balance ?? userStore.userInfo.balance
    } catch (e) {
      // 余额刷新失败不影响兑换结果展示
    }
  } catch (e) {
    // 码无效/已使用等业务失败由拦截器提示
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.redeem-tip {
  margin-top: 16px;
  padding: 10px 12px;
  background: var(--cg-surface-2);
  border-radius: var(--cg-radius-sm);
  font-size: 12px;
  color: var(--cg-text-3);
  line-height: 1.8;
}

.history-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
}

.history-item + .history-item {
  border-top: 1px solid var(--cg-border-light);
}

.history-item__main {
  min-width: 0;
}

.history-item__code {
  font-size: 13px;
  font-weight: 600;
}

.history-item__time {
  margin-top: 2px;
  font-size: 12px;
}

.history-item__reward {
  flex: 0 0 auto;
  text-align: right;
  font-size: 12px;
}
</style>
