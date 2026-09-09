<template>
  <el-container class="app-layout">
    <!-- 宽屏：固定侧边栏（可折叠） -->
    <el-aside
      v-if="!appStore.isNarrow"
      class="app-layout__aside"
      :width="appStore.collapsed ? '64px' : '210px'"
    >
      <AppSider :collapse="appStore.collapsed" />
    </el-aside>

    <!-- 窄屏：侧边栏转抽屉 -->
    <el-drawer
      v-else
      v-model="appStore.drawerVisible"
      direction="ltr"
      :size="220"
      :with-header="false"
      class="app-sider__drawer-body"
    >
      <AppSider :collapse="false" />
    </el-drawer>

    <el-container class="app-layout__body">
      <el-header height="56px" class="app-header">
        <div class="app-header__trigger" @click="appStore.toggleCollapse()">
          <el-icon>
            <Expand v-if="appStore.collapsed || appStore.isNarrow" />
            <Fold v-else />
          </el-icon>
        </div>

        <div class="app-header__crumb">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/admin/dashboard' }">管理端</el-breadcrumb-item>
            <el-breadcrumb-item v-for="(crumb, index) in breadcrumbs" :key="index">
              {{ crumb }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <el-dropdown trigger="click" @command="onCommand">
          <div class="app-header__user">
            <div class="app-header__avatar">{{ userStore.avatarChar }}</div>
            <span v-if="!appStore.isNarrow">{{ userStore.nickname }}</span>
            <el-icon><ArrowDown /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile" :icon="User">{{ roleText }}</el-dropdown-item>
              <el-dropdown-item command="student" :icon="Goods">切换到学生端</el-dropdown-item>
              <el-dropdown-item command="logout" :icon="SwitchButton" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>

      <el-main class="app-main">
        <router-view v-slot="{ Component }">
          <component :is="Component" />
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { ArrowDown, Expand, Fold, Goods, SwitchButton, User } from '@element-plus/icons-vue'
import AppSider from '@/components/AppSider.vue'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { ROLE_MAP } from '@/utils/dict'

/**
 * 管理端整体框架：可折叠侧边栏 + 顶栏（面包屑 + 用户下拉）+ 内容区。
 * 窄屏（<=768px）侧边栏自动转为 el-drawer，顶栏只留图标与头像。
 */
const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()

const roleText = computed(() => ROLE_MAP[userStore.role]?.label || '未分配角色')

/** 面包屑取当前路由的 matched meta.title */
const breadcrumbs = computed(() =>
  route.matched.filter((item) => item.meta?.title).map((item) => item.meta.title)
)

const onCommand = async (command) => {
  if (command === 'student') {
    router.push('/student/home')
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
.app-layout {
  height: 100%;
}

.app-layout__aside {
  background: #fff;
  height: 100%;
  overflow: hidden;
}

.app-layout__body {
  min-width: 0;
  height: 100%;
}

@media (max-width: 768px) {
  .app-main {
    padding: 12px;
  }
}
</style>
