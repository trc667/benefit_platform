/**
 * 管理端菜单：侧边栏渲染 + 路由 meta.title 共用，避免两处维护。
 */
export const ADMIN_MENUS = [
  { path: '/admin/dashboard', title: '仪表盘', icon: 'dashboard' },
  { path: '/admin/goods', title: '商品管理', icon: 'goods' },
  { path: '/admin/coupons', title: '券模板管理', icon: 'coupon' },
  { path: '/admin/redeem', title: '兑换码批次', icon: 'redeem' },
  { path: '/admin/tasks', title: '任务配置', icon: 'task' },
  { path: '/admin/orders', title: '订单管理', icon: 'order' },
  { path: '/admin/refunds', title: '售后审核', icon: 'refund' },
  { path: '/admin/users', title: '用户管理', icon: 'user' },
  { path: '/admin/mq', title: '本地消息表', icon: 'mq' },
  { path: '/admin/cache', title: '二级缓存', icon: 'cache' },
  { path: '/admin/logs', title: '操作日志', icon: 'log' }
]

/** 学生端顶部导航（窄屏折叠进抽屉，底部另有 5 个高频入口） */
export const STUDENT_MENUS = [
  { path: '/student/home', title: '首页', icon: 'HomeFilled' },
  { path: '/student/mall', title: '权益商城', icon: 'Goods' },
  { path: '/student/orders', title: '我的订单', icon: 'Document' },
  { path: '/student/coupons', title: '优惠券', icon: 'Ticket' },
  { path: '/student/redeem', title: '兑换码', icon: 'Wallet' },
  { path: '/student/rank', title: '排行榜', icon: 'Trophy' },
  { path: '/student/points', title: '积分明细', icon: 'Coin' },
  { path: '/student/ai', title: 'AI 助手', icon: 'ChatDotRound' },
  { path: '/student/profile', title: '个人中心', icon: 'User' }
]

/** 窄屏底部导航只放 5 个高频入口 */
export const STUDENT_TABBARS = ['/student/home', '/student/mall', '/student/orders', '/student/coupons', '/student/ai']
