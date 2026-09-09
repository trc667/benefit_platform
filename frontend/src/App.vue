<template>
  <router-view />
</template>

<script setup>
import { useAppStore } from '@/stores/app'

/**
 * 根组件：只负责挂载路由出口 + 初始化全局响应式监听（窄屏判定只注册一次）。
 */
const appStore = useAppStore()
appStore.initResponsive()
</script>
