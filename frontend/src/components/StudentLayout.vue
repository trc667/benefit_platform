<template>
  <div class="stu-layout">
    <header class="stu-header">
      <div class="stu-header__brand">
        <div class="app-sider__logo-mark">校</div>
        <span v-if="!appStore.isNarrow">校园成长权益平台</span>
      </div>

      <!-- 宽屏：横向导航；窄屏：抽屉入口 -->
      <nav v-if="!appStore.isNarrow" class="stu-header__nav">
        <router-link
          v-for="item in menus"
          :key="item.path"
          :to="item.path"
          class="stu-nav-link"
          :class="{ 'is-active': isActive(item.path) }"
        >
          {{ item.title }}
        </router-link>
      </nav>
      <div v-else class="stu-header__nav">
        <el-button text :icon="Menu" @click="appStore.openDrawer()">导航</el-button>
      </div>

      <div class="cg-flex-center cg-gap-8">
        <el-tag v-if="!appStore.isNarrow" size="small" effect="plain" type="primary">
          积分 {{ formatPoint(pointBalance) }}
        </el-tag>
        <el-dropdown trigger="click" @command="onCommand">
          <div class="app-header__user">
            <div class="app-header__avatar">{{ userStore.avatarChar }}</div>
            <span v-if="!appStore.isNarrow">{{ userStore.nickname }}</span>
            <el-icon><ArrowDown /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="points" :icon="Coin">积分明细</el-dropdown-item>
              <el-dropdown-item v-if="userStore.isAdmin" command="admin" :icon="Setting">进入管理端</el-dropdown-item>
              <el-dropdown-item command="logout" :icon="SwitchButton" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <main class="stu-main">
      <router-view v-slot="{ Component }">
        <component :is="Component" />
      </router-view>
    </main>

    <!-- 窄屏：导航抽屉 -->
    <el-drawer v-model="appStore.drawerVisible" direction="ltr" :size="240" title="功能导航">
      <div class="stu-drawer-nav">
        <router-link
          v-for="item in menus"
          :key="item.path"
          :to="item.path"
          class="stu-drawer-nav__item"
          :class="{ 'is-active': isActive(item.path) }"
          @click="appStore.closeDrawer()"
        >
          {{ item.title }}
        </router-link>
      </div>
    </el-drawer>

    <!-- 窄屏：底部简洁导航 -->
    <nav v-if="appStore.isNarrow" class="stu-tabbar">
      <router-link
        v-for="item in tabbarMenus"
        :key="item.path"
        :to="item.path"
        class="stu-tabbar__item"
        :class="{ 'is-active': isActive(item.path) }"
      >
        <el-icon :size="18"><component :is="iconOf(item.icon)" /></el-icon>
        <span>{{ item.title }}</span>
      </router-link>
    </nav>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import {
  ArrowDown,
  ChatDotRound,
  Coin,
  Document,
  Goods,
  HomeFilled,
  Menu,
  Setting,
  SwitchButton,
  Ticket,
  Trophy,
  Wallet
} from '@element-plus/icons-vue'
import { STUDENT_MENUS, STUDENT_TABBARS } from '@/router/menus'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { formatPoint } from '@/utils/format'

/**
 * 学生端框架：顶部导航 + 内容区。
 * 窄屏（<=768px）顶部导航折叠为抽屉，底部固定 5 个高频入口。
 */
const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()

const menus = STUDENT_MENUS
const tabbarMenus = computed(() => STUDENT_MENUS.filter((item) => STUDENT_TABBARS.includes(item.path)))

/** 顶栏积分余额（登录后由 /auth/me 带回 balance） */
const pointBalance = computed(() => userStore.userInfo?.balance ?? 0)

const ICONS = {
  HomeFilled,
  Goods,
  Document,
  Ticket,
  Wallet,
  Trophy,
  Coin,
  ChatDotRound
}

const iconOf = (name) => ICONS[name] || Coin

const isActive = (path) => route.path === path || route.path.startsWith(`${path}/`)

const onCommand = async (command) => {
  if (command === 'points') {
    router.push('/student/points')
    return
  }
  if (command === 'admin') {
    router.push('/admin/dashboard')
    return
  }
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确认退出当前账号？', '退出登录', { type: 'warning' })
    } catch (e) {
      return
    }
    await userStore.logout()
    router.replace('/login')
  }
}
</script>

<style scoped>
.stu-layout {
  min-height: 100%;
  display: flex;
  flex-direction: column;
}

.stu-drawer-nav {
  display: flex;
  flex-direction: column;
}

.stu-drawer-nav__item {
  padding: 12px 8px;
  color: var(--cg-text-2);
  border-bottom: 1px solid var(--cg-border-light);
}

.stu-drawer-nav__item.is-active {
  color: var(--cg-primary);
  font-weight: 600;
}
</style>
