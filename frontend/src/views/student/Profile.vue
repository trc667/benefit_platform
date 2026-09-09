<template>
  <div class="cg-page">
    <PageHeader title="个人中心" description="完善学校与学号可完成「完善个人资料」任务，获得一次性积分奖励">
      <el-button :icon="Refresh" :loading="loading" @click="loadAll">刷新</el-button>
    </PageHeader>

    <el-row :gutter="16">
      <!-- 左：资料表单 -->
      <el-col :xs="24" :sm="24" :md="14" :lg="14">
        <div class="cg-card">
          <div class="cg-card__header">
            <div class="cg-card__title">
              <el-icon><User /></el-icon>
              基本资料
            </div>
          </div>
          <div class="cg-card__body">
            <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" label-position="top">
              <el-row :gutter="12">
                <el-col :xs="24" :sm="12">
                  <el-form-item label="登录账号">
                    <el-input :model-value="userInfo.username" disabled />
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12">
                  <el-form-item label="昵称" prop="nickname">
                    <el-input v-model="form.nickname" maxlength="20" show-word-limit placeholder="请输入昵称" />
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12">
                  <el-form-item label="手机号" prop="phone">
                    <el-input v-model="form.phone" maxlength="11" placeholder="用于接收权益通知" />
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12">
                  <el-form-item label="学号" prop="studentNo">
                    <el-input v-model="form.studentNo" maxlength="32" placeholder="例如 2023010101" />
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12">
                  <el-form-item label="学校" prop="school">
                    <el-input v-model="form.school" maxlength="64" placeholder="例如 示范大学" />
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12">
                  <el-form-item label="头像地址" prop="avatar">
                    <el-input v-model="form.avatar" maxlength="255" placeholder="可选，图片 URL" />
                  </el-form-item>
                </el-col>
              </el-row>
              <div class="profile-actions">
                <el-button type="primary" :loading="saving" @click="onSubmit">保存资料</el-button>
                <el-button @click="resetForm">重置</el-button>
              </div>
            </el-form>
          </div>
        </div>
      </el-col>

      <!-- 右：账户概览 -->
      <el-col :xs="24" :sm="24" :md="10" :lg="10">
        <div class="cg-card">
          <div class="cg-card__header">
            <div class="cg-card__title">
              <el-icon><Coin /></el-icon>
              账户概览
            </div>
          </div>
          <div class="cg-card__body">
            <el-row :gutter="12">
              <el-col :span="12">
                <StatCard label="可用积分" :value="userInfo.balance" unit="分" icon="coin" />
              </el-col>
              <el-col :span="12">
                <StatCard label="累计获得" :value="userInfo.totalEarned" unit="分" icon="trend" />
              </el-col>
            </el-row>
            <el-descriptions :column="1" border size="small" class="cg-mt-16">
              <el-descriptions-item label="角色">
                <el-tag size="small" effect="plain">{{ roleLabel }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="成长等级">Lv{{ userInfo.growthLevel || 1 }}</el-descriptions-item>
              <el-descriptions-item label="学校">{{ userInfo.school || '未填写' }}</el-descriptions-item>
              <el-descriptions-item label="学号">{{ userInfo.studentNo || '未填写' }}</el-descriptions-item>
            </el-descriptions>
            <el-alert
              v-if="!userInfo.school || !userInfo.studentNo"
              class="cg-mt-16"
              type="info"
              :closable="false"
              show-icon
              title="补齐学校与学号可完成一次性任务"
              description="完成后到首页任务列表领取积分奖励。"
            />
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Coin, Refresh, User } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import { getMe, updateProfile } from '@/api/auth'
import { handleError } from '@/utils/errorHandler'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const loading = ref(false)
const saving = ref(false)
const formRef = ref(null)
const userInfo = ref({})

const form = reactive({
  nickname: '',
  phone: '',
  studentNo: '',
  school: '',
  avatar: ''
})

const rules = {
  nickname: [{ max: 20, message: '昵称不能超过 20 个字', trigger: 'blur' }],
  phone: [{ pattern: /^$|^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }],
  studentNo: [{ max: 32, message: '学号不能超过 32 位', trigger: 'blur' }],
  school: [{ max: 64, message: '学校名称不能超过 64 个字', trigger: 'blur' }]
}

const roleLabel = computed(() => {
  const map = { STUDENT: '学生', OPERATOR: '运营', ADMIN: '管理员' }
  return map[userInfo.value.role] || userInfo.value.role || '-'
})

const fillForm = (data) => {
  form.nickname = data?.nickname || ''
  form.phone = data?.phone || ''
  form.studentNo = data?.studentNo || ''
  form.school = data?.school || ''
  form.avatar = data?.avatar || ''
}

const loadAll = async () => {
  loading.value = true
  try {
    const data = await getMe()
    userInfo.value = data || {}
    fillForm(data)
  } catch (e) {
    handleError(e, '加载个人资料')
  } finally {
    loading.value = false
  }
}

const resetForm = () => fillForm(userInfo.value)

const onSubmit = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const data = await updateProfile({ ...form })
    userInfo.value = data || {}
    fillForm(data)
    // 同步全局用户信息，顶栏昵称立即更新
    userStore.setUserInfo?.(data)
    ElMessage.success('资料已保存')
  } catch (e) {
    handleError(e, '保存资料')
  } finally {
    saving.value = false
  }
}

onMounted(loadAll)
</script>

<style scoped>
.profile-actions {
  display: flex;
  gap: 12px;
  margin-top: 8px;
}
</style>
