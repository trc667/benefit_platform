import { ElMessage, ElMessageBox } from 'element-plus'

/**
 * 全局异常兜底。
 * 挂到 app.config.errorHandler，避免任何组件渲染/异步异常导致白屏。
 * 同一个错误在 5s 内只弹一次，防止循环报错把屏幕刷满。
 */

const lastShown = new Map()
const THROTTLE_MS = 5000

const shouldShow = (key) => {
  const now = Date.now()
  const last = lastShown.get(key) || 0
  if (now - last < THROTTLE_MS) return false
  lastShown.set(key, now)
  return true
}

/** 把任意抛出物转成可读文本 */
export const normalizeError = (err) => {
  if (!err) return '未知错误'
  if (typeof err === 'string') return err
  if (err instanceof Error) return err.message || err.name
  if (typeof err === 'object' && err.message) return String(err.message)
  try {
    return JSON.stringify(err)
  } catch (e) {
    return String(err)
  }
}

/**
 * 组件内可主动调用的异常处理。
 * @param {*} error 捕获到的错误
 * @param {string} scene 场景描述，便于定位（如 "加载商品列表"）
 */
export const handleError = (error, scene = '') => {
  const detail = normalizeError(error)
  const key = `${scene}|${detail}`
  console.error(`[error]${scene ? ` ${scene}` : ''}`, error)

  if (!shouldShow(key)) return

  // 业务错误 request.js 已经弹过 ElMessage，这里只处理未标记的异常
  if (error && (error.code !== undefined || error.isAxiosError)) return

  ElMessage.error(scene ? `${scene}失败：${detail}` : `操作失败：${detail}`)
}

/** 未捕获的 Promise 异常也兜一层 */
export const installGlobalErrorHandler = (app) => {
  app.config.errorHandler = (err, instance, info) => {
    const detail = normalizeError(err)
    const key = `vue|${detail}`
    console.error('[vue errorHandler]', info, err)

    if (!shouldShow(key)) return

    ElMessageBox.alert(
      `<div style="line-height:1.8">
        <div>页面出现未预期的异常，已拦截，功能可继续使用。</div>
        <div style="color:#909399;font-size:12px;margin-top:6px">${escapeHtml(detail)}</div>
        <div style="color:#c0c4cc;font-size:12px">位置：${escapeHtml(String(info || ''))}</div>
      </div>`,
      '页面异常',
      { dangerouslyUseHTMLString: true, confirmButtonText: '知道了', type: 'warning' }
    ).catch(() => {})
  }

  app.config.warnHandler = (msg, instance, trace) => {
    if (import.meta.env.DEV) {
      console.warn('[vue warn]', msg, trace)
    }
  }

  window.addEventListener('unhandledrejection', (event) => {
    const reason = event.reason
    console.error('[unhandledrejection]', reason)
    // 请求类异常已在拦截器处理过，这里不重复弹窗
    if (reason && (reason.isAxiosError || reason.code !== undefined)) return
    const key = `promise|${normalizeError(reason)}`
    if (shouldShow(key)) {
      ElMessage.error(`操作未完成：${normalizeError(reason)}`)
    }
  })
}

const escapeHtml = (str) =>
  String(str).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c])
