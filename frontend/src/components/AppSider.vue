<template>
  <div class="app-sider">
    <div class="app-sider__logo">
      <div class="app-sider__logo-mark">校</div>
      <span v-if="!collapse" class="app-sider__logo-text">校园成长权益平台</span>
    </div>

    <el-menu
      :default-active="activePath"
      :collapse="collapse"
      :collapse-transition="false"
      unique-opened
      router
      :default-openeds="defaultOpeneds"
    >
      <el-menu-item v-for="item in flatMenus" :key="item.path" :index="item.path">
        <el-icon><component :is="item.icon" /></el-icon>
        <template #title>{{ item.title }}</template>
      </el-menu-item>
    </el-menu>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import {
  Coin,
  DataLine,
  Document,
  Goods,
  List,
  Refresh,
  Search,
  Ticket,
  User,
  Wallet
} from '@element-plus/icons-vue'
import { ADMIN_MENUS } from '@/router/menus'

/**
 * 管理端侧边栏（管理端菜单单一来源：src/router/menus.js）。
 * 宽屏渲染在 el-aside 内，窄屏渲染在 el-drawer 内，展开/折叠状态由父级传入。
 */
defineProps({
  collapse: { type: Boolean, default: false }
})

const route = useRoute()

const ICONS = {
  dashboard: DataLine,
  goods: Goods,
  coupon: Ticket,
  redeem: Wallet,
  task: List,
  order: Document,
  refund: Refresh,
  user: User,
  mq: Coin,
  cache: Search,
  log: Document
}

const flatMenus = computed(() =>
  ADMIN_MENUS.map((item) => ({ path: item.path, title: item.title, icon: ICONS[item.icon] || List }))
)

const defaultOpeneds = computed(() => flatMenus.value.map((item) => item.path))

/** 详情页等高亮父级菜单：取匹配到的最后一级菜单路径 */
const activePath = computed(() => route.meta?.activeMenu || route.path)
</script>
