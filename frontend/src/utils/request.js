import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * 统一请求封装。
 * 契约见 docs/03-api.md：
 *   - 前缀 /api，除登录/注册外都带 Authorization: Bearer <token>
 *   - 响应体统一 Result{code,message,data,traceId}，code != 0 即业务失败
 *   - 分页响应 data 为 PageResult{records,total,page,size}
 */
const TOKEN_KEY = 'cg_token'

export const getToken = () => localStorage.getItem(TOKEN_KEY) || ''

export const setToken = (token) => {
  if (token) localStorage.setItem(TOKEN_KEY, token)
}

export const clearToken = () => localStorage.removeItem(TOKEN_KEY)

const service = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json;charset=utf-8' }
})

/** 401 只跳一次登录，避免并发请求弹出多个提示 */
let redirecting = false

const redirectToLogin = () => {
  if (redirecting) return
  redirecting = true
  clearToken()
  const current = window.location.hash || ''
  // 保留来源地址，登录后可回跳
  const redirect = encodeURIComponent(current.replace(/^#/, '') || '/student/home')
  window.location.hash = `#/login?redirect=${redirect}`
  setTimeout(() => {
    redirecting = false
  }, 1000)
}

service.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

service.interceptors.response.use(
  (response) => {
    const body = response.data

    // 非 Result 结构（例如 nginx 直接返回的静态内容）原样返回，交由调用方判断
    if (!body || typeof body !== 'object' || !('code' in body)) {
      return body
    }

    const { code, message, data, traceId } = body

    // traceId 全链路排查用，只在 debug 级别输出，不污染生产控制台
    if (traceId) {
      console.debug(`[api] ${response.config.method?.toUpperCase()} ${response.config.url} traceId=${traceId}`)
    }

    if (code === 0) {
      return data
    }

    if (code === 401) {
      ElMessage.error(message || '登录已过期，请重新登录')
      redirectToLogin()
      return Promise.reject(Object.assign(new Error(message || '未登录'), { code, traceId }))
    }

    ElMessage.error(message || '请求失败')
    return Promise.reject(Object.assign(new Error(message || '请求失败'), { code, traceId }))
  },
  (error) => {
    // HTTP 层异常：401 走登录，其余按网络/超时/服务端错误分类提示
    if (error.response) {
      const { status, data } = error.response
      const msg = data?.message || ''
      if (status === 401) {
        ElMessage.error(msg || '登录已过期，请重新登录')
        redirectToLogin()
      } else if (status === 403) {
        ElMessage.error(msg || '没有权限访问该资源')
      } else if (status === 429) {
        ElMessage.error(msg || '操作过于频繁，请稍后再试')
      } else if (status >= 500) {
        ElMessage.error(msg || `服务端异常（${status}）`)
      } else {
        ElMessage.error(msg || `请求失败（${status}）`)
      }
    } else if (error.code === 'ECONNABORTED' || /timeout/i.test(error.message || '')) {
      ElMessage.error('请求超时，请检查网络或稍后重试')
    } else {
      ElMessage.error('网络异常，请检查后端服务是否已启动')
    }

    console.debug('[api] request failed:', error.config?.url, error.message)
    return Promise.reject(error)
  }
)

export default service
