<template>
  <div class="login-page">
    <div class="login-shell">
      <!-- 左侧品牌区：墨色底 + 三条真实能力，不放插画和渐变 -->
      <aside class="login-brand">
        <div class="login-brand__mark">校</div>
        <div>
          <h2 class="login-brand__title">校园成长权益平台</h2>
          <p class="login-brand__sub">签到 · 积分 · 任务 · 权益兑换</p>
        </div>
        <ul class="login-brand__list">
          <li>Redis BitMap 签到，一年只占 46 字节</li>
          <li>Kafka 异步入账，签到接口 138ms 返回</li>
          <li>领券防超发 + 最优优惠组合算价</li>
        </ul>
        <div class="login-brand__foot">模块化单体 · SpringBoot 3 + Vue 3</div>
      </aside>

      <section class="login-form">
        <h1 class="login-form__title">{{ mode === 'login' ? '登录' : '学生注册' }}</h1>
        <p class="login-form__sub">登录后按角色进入学生端或运营管理端</p>

        <el-tabs v-model="mode" stretch>
          <el-tab-pane label="登录" name="login" />
          <el-tab-pane label="学生注册" name="register" />
        </el-tabs>

        <!-- 登录 -->
        <el-form
          v-if="mode === 'login'"
          ref="loginFormRef"
          :model="loginForm"
          :rules="loginRules"
          label-position="top"
          @submit.prevent
        >
          <el-form-item label="账号" prop="username">
            <el-input v-model="loginForm.username" placeholder="请输入账号" clearable :prefix-icon="User" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="loginForm.password"
              type="password"
              placeholder="请输入密码"
              show-password
              :prefix-icon="Lock"
              @keyup.enter="onLogin"
            />
          </el-form-item>
          <el-button type="primary" class="login-form__submit" :loading="loading" @click="onLogin">
            登录
          </el-button>
        </el-form>

        <!-- 注册（/auth/register） -->
        <el-form
          v-else
          ref="registerFormRef"
          :model="registerForm"
          :rules="registerRules"
          label-position="top"
          @submit.prevent
        >
          <el-form-item label="账号" prop="username">
            <el-input v-model="registerForm.username" placeholder="4-20 位字母或数字" clearable />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="registerForm.password" type="password" placeholder="至少 6 位" show-password />
          </el-form-item>
          <el-form-item label="昵称" prop="nickname">
            <el-input v-model="registerForm.nickname" placeholder="展示在排行榜上的名字" clearable />
          </el-form-item>
          <el-row :gutter="12">
            <el-col :xs="24" :sm="12">
              <el-form-item label="学号" prop="studentNo">
                <el-input v-model="registerForm.studentNo" placeholder="如 2023010101" clearable />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12">
              <el-form-item label="学校" prop="school">
                <el-input v-model="registerForm.school" placeholder="如 示范大学" clearable />
              </el-form-item>
            </el-col>
          </el-row>
          <el-button type="primary" class="login-form__submit" :loading="loading" @click="onRegister">
            注册并登录
          </el-button>
        </el-form>

        <div class="login-tip">
          <div class="login-tip__head">演示账号（密码均为 123456）</div>
          <div class="login-tip__row">
            <span class="login-tip__key">运营 / 管理员</span>
            <span class="login-tip__val">operator / admin</span>
          </div>
          <div class="login-tip__row">
            <span class="login-tip__key">学生</span>
            <span class="login-tip__val">student01 ~ student06</span>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

/**
 * 登录 / 注册页。对应接口：
 *   POST /api/auth/login
 *   POST /api/auth/register
 * 登录成功后按角色分流：学生 → /student/home，运营/管理员 → /admin/dashboard。
 */
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const mode = ref('login')
const loading = ref(false)

const loginFormRef = ref(null)
const registerFormRef = ref(null)

const loginForm = reactive({ username: '', password: '' })
const registerForm = reactive({ username: '', password: '', nickname: '', studentNo: '', school: '' })

const loginRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const registerRules = {
  username: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { min: 4, max: 20, message: '长度 4-20 位', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' }
  ],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  studentNo: [{ required: true, message: '请输入学号', trigger: 'blur' }],
  school: [{ required: true, message: '请输入学校', trigger: 'blur' }]
}

/** 登录后跳转：优先回跳来源页，否则按角色分流 */
const redirectAfterLogin = () => {
  const redirect = route.query.redirect
  if (redirect) {
    router.replace(String(redirect))
    return
  }
  router.replace(userStore.isAdmin ? '/admin/dashboard' : '/student/home')
}

const onLogin = async () => {
  const valid = await loginFormRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await userStore.login({ ...loginForm })
    ElMessage.success('登录成功')
    redirectAfterLogin()
  } catch (e) {
    // 失败提示已由 request 拦截器统一弹出
  } finally {
    loading.value = false
  }
}

const onRegister = async () => {
  const valid = await registerFormRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await userStore.register({ ...registerForm })
    ElMessage.success('注册成功')
    router.replace('/student/home')
  } catch (e) {
    // 同上
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-form__submit {
  width: 100%;
  margin-top: 4px;
}

:deep(.el-tabs__header) {
  margin-bottom: 18px;
}

/* 标签页做成下划线式，去掉整块高亮 */
:deep(.el-tabs__nav-wrap::after) {
  height: 1px;
  background-color: var(--cg-border-light);
}

:deep(.el-tabs__item) {
  font-size: 13.5px;
}

:deep(.el-form-item__label) {
  font-size: 13px;
  color: var(--cg-text-2);
}
</style>
