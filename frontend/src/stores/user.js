import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as authApi from '@/api/auth'
import { clearToken, getToken, setToken } from '@/utils/request'

/**
 * 用户态：token + UserInfoVO + 角色判断。
 * token 落 localStorage，刷新后仍可复用；userInfo 每次进应用按需拉 /auth/me。
 */
export const useUserStore = defineStore('user', () => {
  const token = ref(getToken())
  const userInfo = ref(null)
  const loading = ref(false)

  const isLogin = computed(() => !!token.value)
  const role = computed(() => userInfo.value?.role || '')
  const isAdmin = computed(() => role.value === 'ADMIN' || role.value === 'OPERATOR')
  const isStudent = computed(() => !role.value || role.value === 'STUDENT')
  const nickname = computed(() => userInfo.value?.nickname || userInfo.value?.username || '未登录')
  const avatarChar = computed(() => nickname.value.slice(0, 1))

  const applyToken = (value) => {
    token.value = value || ''
    setToken(value)
  }

  const setUserInfo = (info) => {
    userInfo.value = info || null
  }

  /** 登录：拿 token + userInfo */
  const login = async (payload) => {
    loading.value = true
    try {
      const data = await authApi.login(payload)
      applyToken(data?.token)
      setUserInfo(data?.userInfo)
      // 部分实现登录只回 token，这里补一次 me，保证角色判断可靠
      if (!data?.userInfo) {
        await fetchMe()
      }
      return userInfo.value
    } finally {
      loading.value = false
    }
  }

  const register = async (payload) => {
    loading.value = true
    try {
      const data = await authApi.register(payload)
      applyToken(data?.token)
      setUserInfo(data?.userInfo)
      if (!data?.userInfo) {
        await fetchMe()
      }
      return userInfo.value
    } finally {
      loading.value = false
    }
  }

  /** 拉取当前用户；失败不抛出，避免守卫里死循环 */
  const fetchMe = async () => {
    if (!token.value) return null
    try {
      const info = await authApi.getMe()
      setUserInfo(info)
      return info
    } catch (e) {
      // 401 已由拦截器清 token 并跳登录
      return null
    }
  }

  /** 只清本地态，不做接口调用（拦截器 401 时用） */
  const resetLocal = () => {
    token.value = ''
    userInfo.value = null
    clearToken()
  }

  const logout = async () => {
    try {
      if (token.value) {
        await authApi.logout()
      }
    } catch (e) {
      // 后端不可用时也要让用户退出，忽略异常
    } finally {
      resetLocal()
    }
  }

  return {
    token,
    userInfo,
    loading,
    isLogin,
    role,
    isAdmin,
    isStudent,
    nickname,
    avatarChar,
    setToken: applyToken,
    setUserInfo,
    login,
    register,
    fetchMe,
    resetLocal,
    logout
  }
})
