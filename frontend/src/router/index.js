import { createRouter, createWebHashHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppLayout from '@/components/AppLayout.vue'
import StudentLayout from '@/components/StudentLayout.vue'
import { useUserStore } from '@/stores/user'

/**
 * 路由表。
 * - /login 公开
 * - /student/** 学生端（任何已登录用户都能进，管理员也可预览）
 * - /admin/**  管理端（meta.requireRole = OPERATOR|ADMIN）
 */
const routes = [
  { path: '/', redirect: '/student/home' },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录', public: true }
  },

  /* ---------------- 学生端 ---------------- */
  {
    path: '/student',
    component: StudentLayout,
    redirect: '/student/home',
    meta: { title: '学生端' },
    children: [
      {
        path: 'home',
        name: 'StudentHome',
        component: () => import('@/views/student/Home.vue'),
        meta: { title: '首页' }
      },
      {
        path: 'mall',
        name: 'StudentMall',
        component: () => import('@/views/student/Mall.vue'),
        meta: { title: '权益商城' }
      },
      {
        path: 'goods/:id',
        name: 'StudentGoodsDetail',
        component: () => import('@/views/student/GoodsDetail.vue'),
        meta: { title: '商品详情', activeMenu: '/student/mall' }
      },
      {
        path: 'settle/:goodsId',
        name: 'StudentSettle',
        component: () => import('@/views/student/Settle.vue'),
        meta: { title: '订单结算', activeMenu: '/student/mall' }
      },
      {
        path: 'orders',
        name: 'StudentOrders',
        component: () => import('@/views/student/Orders.vue'),
        meta: { title: '我的订单' }
      },
      {
        path: 'coupons',
        name: 'StudentCoupons',
        component: () => import('@/views/student/Coupons.vue'),
        meta: { title: '我的优惠券' }
      },
      {
        path: 'redeem',
        name: 'StudentRedeem',
        component: () => import('@/views/student/Redeem.vue'),
        meta: { title: '兑换码' }
      },
      {
        path: 'rank',
        name: 'StudentRank',
        component: () => import('@/views/student/Rank.vue'),
        meta: { title: '积分排行榜' }
      },
      {
        path: 'points',
        name: 'StudentPoints',
        component: () => import('@/views/student/Points.vue'),
        meta: { title: '积分明细' }
      },
      {
        path: 'ai',
        name: 'StudentAi',
        component: () => import('@/views/student/Ai.vue'),
        meta: { title: 'AI 助手' }
      },
      {
        path: 'profile',
        name: 'StudentProfile',
        component: () => import('@/views/student/Profile.vue'),
        meta: { title: '个人中心' }
      }
    ]
  },

  /* ---------------- 管理端 ---------------- */
  {
    path: '/admin',
    component: AppLayout,
    redirect: '/admin/dashboard',
    meta: { title: '管理端', requireRole: ['OPERATOR', 'ADMIN'] },
    children: [
      {
        path: 'dashboard',
        name: 'AdminDashboard',
        component: () => import('@/views/admin/Dashboard.vue'),
        meta: { title: '仪表盘' }
      },
      {
        path: 'goods',
        name: 'AdminGoods',
        component: () => import('@/views/admin/Goods.vue'),
        meta: { title: '商品管理' }
      },
      {
        path: 'coupons',
        name: 'AdminCoupons',
        component: () => import('@/views/admin/Coupons.vue'),
        meta: { title: '券模板管理' }
      },
      {
        path: 'redeem',
        name: 'AdminRedeem',
        component: () => import('@/views/admin/Redeem.vue'),
        meta: { title: '兑换码批次' }
      },
      {
        path: 'tasks',
        name: 'AdminTasks',
        component: () => import('@/views/admin/Tasks.vue'),
        meta: { title: '任务配置' }
      },
      {
        path: 'orders',
        name: 'AdminOrders',
        component: () => import('@/views/admin/Orders.vue'),
        meta: { title: '订单管理' }
      },
      {
        path: 'refunds',
        name: 'AdminRefunds',
        component: () => import('@/views/admin/Refunds.vue'),
        meta: { title: '售后审核' }
      },
      {
        path: 'users',
        name: 'AdminUsers',
        component: () => import('@/views/admin/Users.vue'),
        meta: { title: '用户管理' }
      },
      {
        path: 'mq',
        name: 'AdminMq',
        component: () => import('@/views/admin/Mq.vue'),
        meta: { title: '本地消息表' }
      },
      {
        path: 'cache',
        name: 'AdminCache',
        component: () => import('@/views/admin/Cache.vue'),
        meta: { title: '二级缓存' }
      },
      {
        path: 'logs',
        name: 'AdminLogs',
        component: () => import('@/views/admin/Logs.vue'),
        meta: { title: '操作日志' }
      }
    ]
  },

  /* ---------------- 404 ---------------- */
  {
    path: '/404',
    name: 'NotFound',
    component: () => import('@/views/NotFound.vue'),
    meta: { title: '页面不存在', public: true }
  },
  { path: '/:pathMatch(.*)*', redirect: '/404' }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach(async (to) => {
  const userStore = useUserStore()

  if (to.meta?.public) {
    // 已登录再访问登录页，直接回首页
    if (to.path === '/login' && userStore.isLogin) {
      return userStore.isAdmin ? '/admin/dashboard' : '/student/home'
    }
    return true
  }

  if (!userStore.isLogin) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  // 刷新后 userInfo 为空，先补一次 /auth/me 再做角色判断
  if (!userStore.userInfo) {
    await userStore.fetchMe()
    if (!userStore.isLogin) {
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }

  const requireRole = to.matched.find((record) => record.meta?.requireRole)?.meta.requireRole
  if (requireRole && !requireRole.includes(userStore.role)) {
    ElMessage.warning('当前账号没有管理端权限，已返回学生端')
    return '/student/home'
  }

  return true
})

router.afterEach((to) => {
  const base = '校园成长权益平台'
  document.title = to.meta?.title ? `${to.meta.title} · ${base}` : base
})

export default router
