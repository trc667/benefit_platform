import { onBeforeUnmount, onMounted, ref } from 'vue'
import { defineStore } from 'pinia'

/** 窄屏断点：与 styles/layout.css 的媒体查询保持一致 */
export const NARROW_WIDTH = 768

/**
 * 应用态：侧边栏折叠 + 是否窄屏。
 * 窄屏由 window.matchMedia 统一驱动，避免各页面各写一套 resize 监听。
 */
export const useAppStore = defineStore('app', () => {
  const collapsed = ref(false)
  const isNarrow = ref(false)
  const drawerVisible = ref(false)

  let mq = null

  const syncNarrow = (matches) => {
    isNarrow.value = matches
    if (matches) {
      // 窄屏不保留展开态，进页面即折叠，侧边栏交给抽屉
      collapsed.value = true
      drawerVisible.value = false
    } else {
      drawerVisible.value = false
    }
  }

  /** 在 App.vue 里调用一次，全局只注册一个监听 */
  const initResponsive = () => {
    if (typeof window === 'undefined' || mq) return
    mq = window.matchMedia(`(max-width: ${NARROW_WIDTH}px)`)
    syncNarrow(mq.matches)
    const handler = (event) => syncNarrow(event.matches)
    mq.addEventListener('change', handler)
    onBeforeUnmount(() => mq?.removeEventListener('change', handler))
  }

  const toggleCollapse = () => {
    if (isNarrow.value) {
      drawerVisible.value = !drawerVisible.value
      return
    }
    collapsed.value = !collapsed.value
  }

  const openDrawer = () => {
    drawerVisible.value = true
  }

  const closeDrawer = () => {
    drawerVisible.value = false
  }

  return { collapsed, isNarrow, drawerVisible, initResponsive, toggleCollapse, openDrawer, closeDrawer }
})

/** 组件内局部响应式（不依赖 store 初始化时机） */
export const useMediaQuery = (query) => {
  const matches = ref(false)
  let mq = null
  onMounted(() => {
    mq = window.matchMedia(query)
    matches.value = mq.matches
    mq.addEventListener('change', (e) => {
      matches.value = e.matches
    })
  })
  onBeforeUnmount(() => mq = null)
  return matches
}
