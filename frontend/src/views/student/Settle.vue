<template>
  <div class="cg-page">
    <PageHeader title="订单结算" description="系统已为你并行试算全部可用券组合，并标出最优方案">
      <el-button :icon="ArrowLeft" @click="$router.push('/student/mall')">返回商城</el-button>
    </PageHeader>

    <el-skeleton v-if="loading" :rows="8" animated />

    <el-row v-else :gutter="16">
      <!-- 左：商品 + 优惠方案 -->
      <el-col :xs="24" :sm="24" :md="14" :lg="15">
        <!-- 商品概要 -->
        <div class="cg-card">
          <div class="cg-card__header">
            <div class="cg-card__title">兑换商品</div>
          </div>
          <div class="cg-card__body">
            <div v-if="!goods" class="cg-text-3">商品信息加载失败，请返回商城重新选择</div>
            <div v-else class="settle-goods">
              <div class="settle-goods__cover">
                <el-image v-if="goods.coverUrl && !coverFailed" :src="goods.coverUrl" fit="cover" @error="coverFailed = true">
                  <template #error><CoverPlaceholder :goods="goods" /></template>
                </el-image>
                <CoverPlaceholder v-else :goods="goods" />
              </div>
              <div class="settle-goods__info">
                <div class="settle-goods__title cg-ellipsis-2">{{ goods.title }}</div>
                <div class="cg-text-3 settle-goods__sub">{{ goods.subTitle || '—' }}</div>
                <div class="settle-goods__meta">
                  <span class="cg-text-2">{{ formatPoint(goods.pricePoint) }} 积分 × {{ quantity }}</span>
                  <el-button text type="primary" @click="changeQuantity">修改数量</el-button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 最优优惠组合 -->
        <div class="cg-card">
          <div class="cg-card__header">
            <div class="cg-card__title">
              <el-icon><MagicStick /></el-icon>
              最优优惠组合
            </div>
            <el-tag v-if="preview" size="small" effect="plain" type="info">
              共试算 {{ candidates.length }} 种组合
            </el-tag>
          </div>
          <div class="cg-card__body">
            <div v-if="!preview" class="cg-text-3">暂未获取到算价结果</div>
            <template v-else>
              <div class="best-plan">
                <div class="cg-flex-between cg-mb-12">
                  <span class="best-plan__tag">
                    <el-icon><Star /></el-icon>
                    最优方案
                  </span>
                  <span class="cg-text-2">已省 {{ formatPoint(bestPlan.discountPoint) }} 积分</span>
                </div>
                <div class="plan-row">
                  <span class="cg-text-2">使用券</span>
                  <span class="cg-nowrap">{{ bestCouponText }}</span>
                </div>
                <div class="plan-row">
                  <span class="cg-text-2">商品总额</span>
                  <span class="cg-point">{{ formatPoint(preview.goodsTotal) }}</span>
                </div>
                <div class="plan-row">
                  <span class="cg-text-2">优惠抵扣</span>
                  <span class="cg-text-success cg-point">-{{ formatPoint(bestPlan.discountPoint) }}</span>
                </div>
                <div class="plan-row">
                  <span class="cg-text-2">实付积分</span>
                  <span class="cg-point cg-text-danger" style="font-size: 18px">
                    {{ formatPoint(bestPlan.payPoint) }}
                  </span>
                </div>
              </div>

              <!-- 候选组合 -->
              <div v-if="candidates.length" class="cg-mt-16">
                <div class="cg-text-2 cg-mb-12">全部候选组合（点击可切换）</div>
                <div
                  v-for="(plan, index) in candidates"
                  :key="index"
                  class="candidate-row"
                  :class="{ 'is-selected': isSelected(plan), 'is-best': index === 0 }"
                  @click="selectPlan(plan)"
                >
                  <div class="candidate-row__main">
                    <div class="candidate-row__title">
                      {{ planTitle(plan) }}
                      <el-tag v-if="index === 0" size="small" type="success" effect="plain">最优</el-tag>
                    </div>
                    <div class="cg-text-3 candidate-row__sub">抵扣 {{ formatPoint(plan.discountPoint) }} 积分</div>
                  </div>
                  <div class="candidate-row__pay cg-point">{{ formatPoint(plan.payPoint) }}</div>
                </div>
              </div>
            </template>
          </div>
        </div>

        <!-- 可用券 -->
        <div class="cg-card">
          <div class="cg-card__header">
            <div class="cg-card__title">可用优惠券</div>
            <el-tag size="small" effect="plain" type="info">{{ availableCoupons.length }} 张</el-tag>
          </div>
          <div class="cg-card__body">
            <el-radio-group v-model="selectedCouponId" class="coupon-picker" @change="onCouponChange">
              <el-radio :value="null" class="coupon-picker__item">
                <span>不使用优惠券</span>
              </el-radio>
              <el-radio v-for="coupon in availableCoupons" :key="coupon.id" :value="coupon.id" class="coupon-picker__item">
                <span class="cg-flex-between coupon-picker__label">
                  <span>
                    <b>{{ coupon.couponTitle || coupon.title }}</b>
                    <span class="cg-text-3">（{{ formatThreshold(coupon) }}）</span>
                  </span>
                  <span class="cg-text-success">-{{ formatPoint(faceOf(coupon)) }}</span>
                </span>
              </el-radio>
            </el-radio-group>

            <div v-if="!availableCoupons.length" class="cg-text-3">
              当前订单没有满足门槛的可用券，可去
              <el-link type="primary" :underline="false" @click="$router.push('/student/coupons')">领券中心</el-link>
              看看
            </div>
          </div>
        </div>
      </el-col>

      <!-- 右：收货信息 + 提交 -->
      <el-col :xs="24" :sm="24" :md="10" :lg="9">
        <div class="cg-card">
          <div class="cg-card__header">
            <div class="cg-card__title">收货信息</div>
          </div>
          <div class="cg-card__body">
            <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
              <el-form-item label="收货人" prop="receiverName">
                <el-input v-model="form.receiverName" placeholder="请输入收货人姓名" maxlength="32" clearable />
              </el-form-item>
              <el-form-item label="手机号" prop="receiverPhone">
                <el-input v-model="form.receiverPhone" placeholder="11 位手机号" maxlength="11" clearable />
              </el-form-item>
              <el-form-item label="收货地址" prop="receiverAddress">
                <el-input
                  v-model="form.receiverAddress"
                  type="textarea"
                  :rows="2"
                  maxlength="120"
                  show-word-limit
                  placeholder="宿舍楼 / 教学楼 + 详细门牌"
                />
              </el-form-item>
              <el-form-item label="备注">
                <el-input v-model="form.remark" placeholder="选填，如兑换时间要求" maxlength="80" clearable />
              </el-form-item>
            </el-form>
          </div>
        </div>

        <div class="cg-card">
          <div class="cg-card__header">
            <div class="cg-card__title">支付确认</div>
          </div>
          <div class="cg-card__body">
            <div class="plan-row">
              <span class="cg-text-2">商品总额</span>
              <span class="cg-point">{{ formatPoint(preview?.goodsTotal) }}</span>
            </div>
            <div class="plan-row">
              <span class="cg-text-2">优惠抵扣</span>
              <span class="cg-text-success cg-point">-{{ formatPoint(currentDiscount) }}</span>
            </div>
            <div class="plan-row">
              <span class="cg-text-2">应付积分</span>
              <span class="cg-point cg-text-danger">{{ formatPoint(currentPay) }}</span>
            </div>
            <div class="plan-row">
              <span class="cg-text-2">我的余额</span>
              <span class="cg-point">{{ formatPoint(preview?.balance) }}</span>
            </div>

            <el-alert
              v-if="preview && preview.balanceEnough === false"
              class="cg-mt-12"
              type="warning"
              :closable="false"
              show-icon
              title="积分余额不足"
              description="可先完成签到与任务攒积分，或减少兑换数量"
            />

            <el-button
              class="cg-mt-16"
              type="primary"
              size="large"
              style="width: 100%"
              :loading="submitting"
              :disabled="!goods || preview?.balanceEnough === false"
              @click="onSubmit"
            >
              提交订单（{{ formatPoint(currentPay) }} 积分）
            </el-button>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, MagicStick, Star } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import CoverPlaceholder from '@/components/CoverPlaceholder.vue'
import { getGoodsDetail } from '@/api/benefit'
import { previewSettle, createOrder, payOrder } from '@/api/order'
import { formatPoint, formatThreshold, num } from '@/utils/format'
import { handleError } from '@/utils/errorHandler'

/**
 * 结算页。接口：
 *   GET  /api/benefit/goods/{id}
 *   GET  /api/order/settle/preview?goodsId=&quantity=
 *   POST /api/order/create
 *   POST /api/order/pay
 * bestPlan 为后端并行算出的最优组合（可能是多张券），candidates 为全部候选。
 */
const route = useRoute()
const router = useRouter()

const loading = ref(false)
const submitting = ref(false)
const coverFailed = ref(false)

const goods = ref(null)
const preview = ref(null)
const selectedCouponId = ref(null)
const formRef = ref(null)

const quantity = computed(() => Math.max(1, num(route.query.quantity, 1)))

const form = reactive({
  receiverName: '',
  receiverPhone: '',
  receiverAddress: '',
  remark: ''
})

const rules = {
  receiverName: [{ required: true, message: '请输入收货人', trigger: 'blur' }],
  receiverPhone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  receiverAddress: [{ required: true, message: '请输入收货地址', trigger: 'blur' }]
}

const bestPlan = computed(() => preview.value?.bestPlan || {})
const availableCoupons = computed(() => (Array.isArray(preview.value?.availableCoupons) ? preview.value.availableCoupons : []))

/** 候选组合：后端顺序通常已按最优排序，这里再按实付升序兜底 */
const candidates = computed(() => {
  const list = Array.isArray(preview.value?.candidates) ? [...preview.value.candidates] : []
  return list.sort((a, b) => num(a?.payPoint) - num(b?.payPoint))
})

/** bestPlan 里的券标题可能为空，用 id 反查可用券 */
const bestCouponText = computed(() => {
  const plan = bestPlan.value
  if (plan?.couponTitle) return plan.couponTitle
  const ids = Array.isArray(plan?.couponIds) ? plan.couponIds : plan?.couponId ? [plan.couponId] : []
  if (!ids.length) return '不使用优惠券'
  return ids
    .map((id) => {
      const coupon = availableCoupons.value.find((item) => item.id === id)
      return coupon?.couponTitle || coupon?.title || `券 #${id}`
    })
    .join(' + ')
})

const planTitle = (plan) => plan?.couponTitle || bestCouponText.value || '不使用优惠券'

const currentDiscount = computed(() => {
  if (selectedCouponId.value) {
    const coupon = availableCoupons.value.find((item) => item.id === selectedCouponId.value)
    return coupon ? faceOf(coupon) : 0
  }
  return num(bestPlan.value?.discountPoint)
})

const currentPay = computed(() => Math.max(0, num(preview.value?.goodsTotal) - currentDiscount.value))

/** 券的可抵扣面额：折扣券按比例估算（后端最终以算价为准） */
const faceOf = (coupon) => {
  const type = coupon?.couponType || coupon?.type
  const total = num(preview.value?.goodsTotal)
  if (type === 'DISCOUNT') {
    const rate = num(coupon?.discountRate, 100)
    let discount = Math.floor((total * (100 - rate)) / 100)
    const maxDiscount = num(coupon?.maxDiscount)
    if (maxDiscount > 0) discount = Math.min(discount, maxDiscount)
    return discount
  }
  return num(coupon?.faceValue)
}

const isSelected = (plan) => {
  if (selectedCouponId.value) {
    const ids = Array.isArray(plan?.couponIds) ? plan.couponIds : plan?.couponId ? [plan.couponId] : []
    return ids.length === 1 && ids[0] === selectedCouponId.value
  }
  return num(plan?.payPoint) === num(bestPlan.value?.payPoint)
}

const loadPreview = async () => {
  try {
    preview.value = await previewSettle({ goodsId: route.params.goodsId, quantity: quantity.value })
    selectedCouponId.value = null
  } catch (e) {
    preview.value = null
  }
}

const loadGoods = async () => {
  try {
    goods.value = await getGoodsDetail(route.params.goodsId)
  } catch (e) {
    goods.value = null
  }
}

const loadAll = async () => {
  loading.value = true
  await Promise.all([loadGoods(), loadPreview()])
  loading.value = false
}

/** 切换单张券：重新向后端要一次算价（后端会按门槛与适用范围过滤） */
const onCouponChange = async () => {
  try {
    preview.value = await previewSettle({
      goodsId: route.params.goodsId,
      quantity: quantity.value,
      couponId: selectedCouponId.value ?? undefined
    })
  } catch (e) {
    // 保留上一次结果，用户可继续操作
  }
}

const selectPlan = (plan) => {
  const ids = Array.isArray(plan?.couponIds) ? plan.couponIds : plan?.couponId ? [plan.couponId] : []
  selectedCouponId.value = ids.length === 1 ? ids[0] : null
  if (ids.length !== 1) {
    ElMessage.info('该方案使用多张券，提交时按最优方案执行')
  }
}

const changeQuantity = async () => {
  try {
    const { value } = await ElMessageBox.prompt('请输入兑换数量', '修改数量', {
      inputValue: String(quantity.value),
      inputPattern: /^[1-9]\d{0,2}$/,
      inputErrorMessage: '请输入 1-999 的整数',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })
    router.replace({ query: { ...route.query, quantity: value } })
    await loadPreview()
  } catch (e) {
    // 取消不做处理
  }
}

const onSubmit = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  // 用哪几张券：手动选了就用那一张；没手动选就按后端算出的最优组合（可能多张）提交。
  // 早期版本这里只传单个 couponId，导致最优方案是多张券时优惠被丢掉。
  const planCouponIds = selectedCouponId.value
    ? [selectedCouponId.value]
    : (Array.isArray(bestPlan.value?.couponIds) ? [...bestPlan.value.couponIds] : [])

  submitting.value = true
  try {
    const created = await createOrder({
      goodsId: num(route.params.goodsId),
      quantity: quantity.value,
      couponIds: planCouponIds,
      receiverName: form.receiverName,
      receiverPhone: form.receiverPhone,
      receiverAddress: form.receiverAddress,
      remark: form.remark
    })

    // 下单成功后立即尝试支付，支付失败则提示去订单列表手动支付
    try {
      await payOrder(created.orderNo)
      ElMessage.success(`下单并支付成功，扣减 ${formatPoint(created.payPoint)} 积分`)
    } catch (payError) {
      ElMessage.warning('订单已创建，支付未完成，请到「我的订单」继续支付')
    }

    // 注意：订单类任务进度由后端在支付成功时上报（WEEKLY_ORDER），
    // 前端不再重复上报，避免同一行为被计两次。

    router.replace('/student/orders')
  } catch (e) {
    handleError(e, '提交订单')
  } finally {
    submitting.value = false
  }
}

onMounted(loadAll)
</script>

<style scoped>
.settle-goods {
  display: flex;
  gap: 12px;
}

.settle-goods__cover {
  flex: 0 0 88px;
  width: 88px;
  height: 88px;
  border-radius: var(--cg-radius-sm);
  overflow: hidden;
  background: var(--cg-surface-2);
}

.settle-goods__info {
  flex: 1 1 auto;
  min-width: 0;
}

.settle-goods__title {
  font-size: 14px;
  font-weight: 600;
  line-height: 1.5;
}

.settle-goods__sub {
  margin-top: 4px;
  font-size: 12px;
}

.settle-goods__meta {
  margin-top: 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 13px;
}

.candidate-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--cg-border-light);
  border-radius: var(--cg-radius-sm);
  cursor: pointer;
}

.candidate-row + .candidate-row {
  margin-top: 8px;
}

.candidate-row:hover {
  border-color: var(--cg-border-strong);
}

.candidate-row.is-selected {
  border-color: var(--cg-jade-line);
  background: var(--cg-jade-bg);
}

.candidate-row__title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
}

.candidate-row__sub {
  margin-top: 2px;
  font-size: 12px;
}

.candidate-row__pay {
  color: var(--cg-jade);
  font-size: 16px;
}

.coupon-picker {
  display: block;
  width: 100%;
}

.coupon-picker__item {
  display: flex;
  align-items: center;
  width: 100%;
  height: auto;
  padding: 10px 12px;
  margin: 0 0 8px;
  border: 1px solid var(--cg-border-light);
  border-radius: var(--cg-radius-sm);
}

.coupon-picker__item :deep(.el-radio__label) {
  flex: 1 1 auto;
  min-width: 0;
}

.coupon-picker__label {
  gap: 12px;
  font-size: 13px;
}

@media (max-width: 768px) {
  .cg-card + .cg-card {
    margin-top: 12px;
  }
}
</style>
