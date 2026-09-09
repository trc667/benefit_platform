# 校园成长权益平台 · 前端

Vue 3 + Vite 5 + Pinia 2 + Vue Router 4 + Element Plus 2.7 的单工程双端应用：
**学生端**（签到 / 积分 / 任务 / 权益商城 / 优惠券 / 兑换码 / 订单售后 / AI 助手 / 排行榜）
与 **运营管理端**（仪表盘 / 商品 / 券 / 兑换码 / 任务 / 订单 / 售后 / 用户 / 消息表 / 缓存 / 日志）。

接口契约见 `../docs/03-api.md`，本工程所有页面只调用契约中真实存在的接口，**不含任何 mock 或假数据**。

---

## 1. 启动

```bash
cd frontend

# 国内镜像（首次）
npm config set registry https://registry.npmmirror.com

npm install
npm run dev          # http://127.0.0.1:5173
```

开发服务器把 `/api` 代理到后端单体应用 `http://127.0.0.1:8090`（见 `vite.config.js`），
因此后端未启动时页面会走「请求失败 → 空状态 + 错误提示」的降级路径，不会白屏。

### 构建

```bash
npm run build        # vite build → dist/
npm run preview      # 本地预览构建产物
```

### 演示账号（来自 `sql/data.sql`，密码统一 `123456`）

| 端 | 账号 | 角色 |
| --- | --- | --- |
| 管理端 | `admin` / `operator` | ADMIN / OPERATOR |
| 学生端 | `student01` ~ `student06` | STUDENT |

---

## 2. 目录结构

```
frontend/
├── index.html
├── vite.config.js              # dev 端口 5173，/api → 127.0.0.1:8090
├── package.json
├── scripts/
│   ├── verify.mjs              # 离线静态校验（模板/脚本/样式/import 路径）
│   └── build-rollup.mjs        # 备用生产构建（不依赖 esbuild，见第 6 节）
└── src/
    ├── main.js                 # 挂载 Pinia / Router / Element Plus / 全局异常处理
    ├── App.vue                 # 路由出口 + 全局响应式监听初始化
    ├── api/                    # 按域拆分的接口函数（唯一出处，页面不直接写 axios）
    │   ├── auth.js             #   /auth
    │   ├── signin.js           #   /signin
    │   ├── point.js            #   /point
    │   ├── task.js             #   /task
    │   ├── benefit.js          #   /benefit
    │   ├── coupon.js           #   /coupon
    │   ├── redeem.js           #   /redeem
    │   ├── order.js            #   /order
    │   ├── ai.js               #   /ai
    │   └── admin.js            #   /admin/**
    ├── components/             # 公共组件
    │   ├── AppLayout.vue       # 管理端框架：折叠侧边栏 + 顶栏面包屑/用户下拉 + 内容区
    │   ├── AppSider.vue        # 管理端侧边栏（宽屏 el-aside / 窄屏 el-drawer 复用）
    │   ├── StudentLayout.vue   # 学生端框架：顶部导航 + 窄屏抽屉 + 底部导航
    │   ├── PageHeader.vue      # 标题 + 描述 + 右侧操作插槽
    │   ├── SearchBar.vue       # 查询区，窄屏自动换行
    │   ├── DataTable.vue       # el-table + 分页 + loading + 空状态
    │   ├── FormDialog.vue      # el-dialog + el-form + 提交/取消，宽度响应式
    │   ├── StatCard.vue        # 指标卡片
    │   ├── EmptyState.vue      # 空状态 / 失败降级
    │   ├── GoodsCard.vue       # 学生端商品卡片
    │   ├── CouponCard.vue      # 券卡片（左面额右信息，过期灰化）
    │   ├── CoverPlaceholder.vue# 封面占位（渐变 + 分类图标 / 首字）
    │   └── SigninCalendar.vue  # 签到日历（BitMap 可视化）
    ├── stores/
    │   ├── user.js             # token / userInfo / isAdmin / isStudent / login / logout / me
    │   └── app.js              # 侧边栏折叠 + 窄屏判定（matchMedia 全局单例）
    ├── router/
    │   ├── index.js            # 路由表 + 守卫（token + 角色）+ 404
    │   └── menus.js            # 管理端/学生端菜单单一来源
    ├── utils/
    │   ├── request.js          # axios 封装：注入 token / 统一 code!=0 / 401 / 超时 / traceId
    │   ├── errorHandler.js     # 全局异常兜底（app.config.errorHandler）
    │   ├── format.js           # 时间、积分、脱敏、占位字符等格式化
    │   └── dict.js             # 枚举字典（角色/订单/券/任务/积分/售后/消息表）
    ├── styles/
    │   ├── index.css           # 全局基础样式、企业简洁后台风格变量
    │   └── layout.css          # 布局与组件局部样式
    └── views/
        ├── Login.vue           # 登录 / 学生注册
        ├── NotFound.vue        # 404
        ├── student/            # 学生端 10 个页面
        └── admin/              # 管理端 11 个页面
```

---

## 3. 页面清单与接口对齐

### 学生端

| 页面 | 路由 | 调用接口 |
| --- | --- | --- |
| 首页 | `/student/home` | `GET /signin/calendar?month=yyyyMM`、`GET /signin/stat`、`POST /signin/do`、`GET /point/account`、`GET /task/list`、`POST /task/reward/claim` |
| 权益商城 | `/student/mall` | `GET /benefit/categories`、`GET /benefit/goods/page`、`POST /task/progress/report`（浏览任务） |
| 商品详情 | `/student/goods/:id` | `GET /benefit/goods/{id}`、`POST /task/progress/report` |
| 订单结算 | `/student/settle/:goodsId` | `GET /benefit/goods/{id}`、`GET /order/settle/preview`、`POST /order/create`、`POST /order/pay`、`POST /task/progress/report` |
| 我的订单 | `/student/orders` | `GET /order/page`、`GET /order/{orderNo}`、`POST /order/pay`、`POST /order/cancel`、`POST /order/refund/apply` |
| 我的优惠券 | `/student/coupons` | `GET /coupon/mine`、`GET /coupon/templates`、`POST /coupon/receive` |
| 兑换码 | `/student/redeem` | `POST /redeem/exchange`、`GET /point/account` |
| 积分排行榜 | `/student/rank` | `GET /point/rank`、`GET /point/rank/me` |
| 积分明细 | `/student/points` | `GET /point/account`、`GET /point/records` |
| AI 助手 | `/student/ai` | `GET /ai/sessions`、`GET /ai/messages`、`POST /ai/chat`、`DELETE /ai/session/{sessionId}` |

### 管理端

| 页面 | 路由 | 调用接口 |
| --- | --- | --- |
| 登录 | `/login` | `POST /auth/login`、`POST /auth/register` |
| 仪表盘 | `/admin/dashboard` | `GET /admin/dashboard/overview`、`GET /admin/dashboard/signin-trend`、`GET /admin/dashboard/order-trend` |
| 商品管理 | `/admin/goods` | `GET /admin/benefit/goods/page`、`POST /admin/benefit/goods/save`、`POST /admin/benefit/goods/status` |
| 券模板管理 | `/admin/coupons` | `GET /admin/coupon/template/page`、`POST /admin/coupon/template/save`、`POST /admin/coupon/template/status`、`POST /admin/coupon/grant`、`GET /admin/coupon/stat` |
| 兑换码批次 | `/admin/redeem` | `GET /admin/redeem/batch/page`、`POST /admin/redeem/batch/create`、`GET /admin/redeem/batch/{batchNo}/codes`、`POST /admin/redeem/batch/status` |
| 任务配置 | `/admin/tasks` | `GET /admin/task/page`、`POST /admin/task/save`、`POST /admin/task/status` |
| 订单管理 | `/admin/orders` | `GET /admin/order/page`、`GET /order/{orderNo}` |
| 售后审核 | `/admin/refunds` | `GET /admin/order/refund/page`、`POST /admin/order/refund/handle` |
| 用户管理 | `/admin/users` | `GET /admin/user/page`、`POST /admin/user/status` |
| 本地消息表 | `/admin/mq` | `GET /admin/mq/outbox/page`、`POST /admin/mq/outbox/retry` |
| 二级缓存 | `/admin/cache` | `GET /admin/cache/stats` |
| 操作日志 | `/admin/logs` | `GET /admin/operation/log/page` |

---

## 4. 关键实现约定

**统一请求封装**（`src/utils/request.js`）
- 请求拦截注入 `Authorization: Bearer <token>`
- 响应拦截统一处理 `code != 0` → `ElMessage.error(message)` 并 reject
- `401` → 清 token 并跳登录（带 `redirect` 回跳参数，并发请求只跳一次）
- 网络异常 / 超时 / 403 / 429 / 5xx 分类提示
- `traceId` 通过 `console.debug` 输出，便于按链路排查

**全局异常兜底**（`src/utils/errorHandler.js`）
- `app.config.errorHandler` + `unhandledrejection`，异常以弹窗提示并节流（5s 内同一错误只弹一次），不白屏
- 请求类异常已由拦截器提示，不重复弹窗

**状态管理**
- `useUserStore`：`token`、`userInfo`、`isAdmin`/`isStudent`、`login`/`logout`/`fetchMe`
- `useAppStore`：`collapsed`、`isNarrow`、`drawerVisible`，窄屏判定由 `matchMedia` 全局单例驱动

**路由守卫**（`src/router/index.js`）
- 未登录 → `/login?redirect=...`
- 刷新后 `userInfo` 为空先补 `GET /auth/me` 再判角色
- 学生访问 `/admin/**` → 提示并跳回 `/student/home`
- 未知路径 → `/404`；`document.title` 随路由变化

**响应式布局**
- 断点 768px：管理端侧边栏 `el-aside` → `el-drawer`，学生端顶部导航 → 抽屉 + 底部导航
- 栅格：商品卡片 `:xs="24" :sm="12" :md="8" :lg="6"`；指标卡片 `:xs="12" :sm="12" :md="6" :lg="6"`
- 弹窗宽度：窄屏 92%，宽屏 640 / 720px；表格操作列 `fixed="right"`，列设 `min-width` 保证窄屏横向滚动而非挤压

**图片占位**
- 后端 `cover_url` 指向 `/img/goods/*.png`，静态资源未提供，统一用 `CoverPlaceholder.vue`
  （分类渐变底色 + 分类图标 / 标题首字）兜底，同时 `el-image` 配 `error` 插槽，不出现破图

**风格**
- 白底、浅灰分割线（`--cg-border-light`）、圆角统一 6–8px、主色为 Element Plus 默认蓝
- 不使用过渡动画；`el-menu` 关闭 `collapse-transition`

---

## 5. 校验与构建

```bash
npm run verify       # 离线静态校验：37 个 SFC + 19 个 JS + 291 条 import
```

校验内容：SFC 三段式能否编译、`<script setup>` 编译结果是否合法 ESM、模板表达式是否报错、
所有 import（`@/` 别名、相对路径、npm 包）是否解析到真实文件。

---

## 6. 关于 `npm run build` 的环境限制

标准构建命令是 `npm run build`（`vite build`）。Vite 加载配置与转译模块时依赖 esbuild，
而 esbuild 以**子进程服务**方式运行、需要管道 stdio。

在本项目当前的受限沙箱中，Node 以 `stdio: 'pipe'` 方式 spawn 任何子进程都会被系统拒绝：

```
Error: spawn EPERM
    at ensureServiceIsRunning (.../esbuild/lib/main.js:1975:29)
```

因此 `npm run build` 在该沙箱内无法执行（错误发生在加载 `vite.config.js` 阶段，与业务代码无关）。
在正常开发机上它可正常使用。

为了在本环境仍能**真实打包并验证产物**，工程提供了等价的 Rollup 构建：

```bash
npm run build:rollup
```

它用 `@vitejs/plugin-vue` 直接驱动 Rollup（全程不调用 esbuild），产出结构与 `vite build` 一致：
`index.html` + 入口 chunk + 路由级动态分包 + 第三方分包（vue / element / vendor）+ 合并后的单文件 CSS。

最近一次产物（`npm run build:rollup`，退出码 0，无告警）：

| 文件 | 大小 |
| --- | --- |
| `assets/element-*.js` | 1,932.18 kB |
| `assets/vendor-*.js` | 1,345.00 kB |
| `assets/main-*.js` | 50.16 kB |
| `assets/index.css` | 23.58 kB |
| 其余 34 个路由/组件分包 | 0.5–36 kB |

共 38 个文件、3,784.28 kB，打包耗时 4.8s（含启动约 6s）。
