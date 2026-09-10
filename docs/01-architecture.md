# 校园成长权益平台 · 架构设计说明

> 版本：v1.0 ｜ 定位：**模块化单体（Modular Monolith）**，非微服务
> 一句话：面向高校学生的成长积分 / 任务激励 / 权益兑换平台，学生端 + 运营管理端，重点展示高并发、多级缓存、分布式锁、异步事件驱动与大模型工程化落地。

---

## 1. 定位与边界（先把"不做什么"说清楚）

| 维度 | 决策 | 原因 |
| --- | --- | --- |
| 服务形态 | 单体应用，按业务域分包 | 个人开源项目，团队只有 1 人；拆微服务只会放大运维成本 |
| 注册中心 | **不引入** Nacos / Eureka / Consul | 单体无服务发现问题；Dubbo 走直连 |
| 网关 | **不引入** Spring Cloud Gateway | 前端 nginx / vite proxy 即可 |
| 熔断限流 | **不引入** Sentinel / Hystrix | 用 Guava RateLimiter（单机）+ Redis 计数器（集群）两层自研轻量方案 |
| RPC 调用 | Dubbo3 **只定义接口、只预留实现**，单体阶段 0 远程调用 | 为后续渐进式拆分留缝，但不提前付运维成本 |
| 配置中心 | **不引入** | Spring Boot 多 profile + 环境变量足够 |
| ORM | MyBatis-Plus | 国内企业最主流，代码量最省 |
| 分布式事务 | **不引入** Seata | 单体单库本地事务 + Kafka 最终一致 + 本地消息表，够用 |

> 关键红线（写进代码注释与 CI 检查项）：**单体运行时任何业务代码不得通过 Dubbo 调用远程接口**，Dubbo 默认关闭（`dubbo.enabled=false`），只在 `rpc` profile 下开启。

---

## 2. 部署视图

```
┌──────────────────────────────┐        ┌──────────────────────────────┐
│  Vue3 + Vite + Element Plus  │  HTTP  │  SpringBoot3 单体应用 :8080  │
│  学生端 / 管理端（同一工程）   │ ─────► │  campus-growth-api           │
└──────────────────────────────┘  /api  └───────────┬──────────────────┘
                                                    │
              ┌─────────────────────────────────────┼───────────────────────────┐
              │                                     │                           │
      ┌───────▼────────┐                  ┌─────────▼────────┐          ┌───────▼────────┐
      │  MySQL 5.7/8.0 │                  │ Redis 3.2/5.0+   │          │ Kafka 3.7 KRaft│
      │  业务持久化      │                  │ 缓存/锁/BitMap/   │          │ 异步事件        │
      │  19 张表         │                  │ 排行榜/限流       │          │ 积分/进度/outbox│
      └────────────────┘                  └──────────────────┘          └────────────────┘
                                                     ▲
                                             Caffeine L1（进程内）
```

**本机实测基线（写代码时就是按这套验证的）**

| 组件 | 本机版本 | 说明 |
| --- | --- | --- |
| JDK | 17.0.16 / 21.0.9 | SpringBoot3 要求 17+；本机默认 11，需切 JAVA_HOME |
| Maven | 3.9.10 | 镜像需用 **https** 阿里云源，仓库落在项目内 `.mvnrepo/` |
| MySQL | 5.7.31 @3306 | 表设计已规避 8.0 专有语法（窗口函数/CTE/CHECK/JSON 函数） |
| Redis | 3.2.100 @6379 | 支持 BitMap/BITFIELD/ZSet/Lua，够本项目用；不支持 Stream/GETDEL/UNLINK |
| Kafka | 3.7.1 KRaft 单节点 | 无需 ZooKeeper；Windows 下**不能**用 `bin\windows\*.bat`（类路径超 cmd 8191 字符上限），用 `scripts\start-kafka.ps1` 直接 `java -cp "libs\*" kafka.Kafka` 启动 |

---

## 3. 代码结构（模块化单体的包组织）

单 Maven 工程，**域优先 + 层内聚**：每个业务域自带 controller / service / mapper / entity / dto / vo / rpc，跨域只通过 service 接口调用。

```
campus-growth-api
└── com.campus.growth
    ├── CampusGrowthApplication.java
    ├── common/                     # 全局通用能力，零业务
    │   ├── result/                 # Result / PageResult / ErrorCode
    │   ├── exception/              # BizException / GlobalExceptionHandler
    │   ├── constant/               # RedisKeyConst / MqTopicConst / BizConst
    │   ├── enums/                  # 业务枚举
    │   ├── annotation/             # @RateLimit / @OpLog / @Idempotent
    │   ├── aspect/                 # 限流、操作日志、幂等切面
    │   ├── context/                # UserContext（ThreadLocal）
    │   └── util/                   # Base32Codec / OrderNoGenerator / JsonUtil
    ├── config/                     # 全部 @Configuration
    │   ├── RedisConfig / RedissonConfig / CaffeineCacheConfig
    │   ├── KafkaConfig / KafkaProducerConfig / KafkaConsumerConfig
    │   ├── MybatisPlusConfig / WebMvcConfig / ThreadPoolConfig / AsyncConfig
    │   ├── DubboConfig（仅 rpc profile）/ AiConfig / CorsConfig
    ├── modules/
    │   ├── auth/          # 登录、用户、权限（学生/运营）
    │   ├── signin/        # 每日签到（Redis BitMap + Kafka 事件）
    │   ├── point/         # 积分账户、流水、排行榜
    │   ├── task/          # 任务定义、任务进度（Redis 热数据 + Kafka 异步落库）
    │   ├── benefit/       # 权益商品（Caffeine + Redis 二级缓存）
    │   ├── coupon/        # 优惠券模板/用户券/领取（分布式锁 + 切面顺序修复）
    │   ├── redeem/        # 兑换码（Base32 编码 + BitMap 核销）
    │   ├── order/         # 订单/退换单 + 最优优惠组合（CompletableFuture）
    │   ├── ai/            # AI 助手（Function Calling + 轻量模型直连）
    │   └── mq/            # 本地消息表 outbox + 消费幂等记录
    └── infra/              # 与业务无关的中间件适配
        ├── cache/          # TwoLevelCache（Caffeine + Redis Cache-Aside）
        ├── lock/           # DistributedLockTemplate
        ├── ratelimit/      # LocalRateLimiter + RedisRateLimiter
        └── mq/             # EventPublisher（outbox 落地 + Kafka 发送 + 重试）
```

**域内标准分层**（以 coupon 为例）

```
modules/coupon
├── controller/CouponController.java        # 学生端：领券、我的券
├── controller/CouponAdminController.java   # 管理端：券模板 CRUD、发放
├── rpc/CouponRpcService.java               # Dubbo 接口（@DubboService，仅预留）
├── service/CouponService.java              # 本地业务接口（单体唯一入口）
├── service/impl/CouponServiceImpl.java
├── mapper/CouponTemplateMapper.java
├── entity/CouponTemplate.java
├── dto/CouponReceiveDTO.java
└── vo/UserCouponVO.java
```

> `rpc/` 与 `service/` 的关系：**RPC 实现只做参数校验 + 委派本地 Service**，不写第二套业务逻辑。将来把 coupon 域独立部署时，只需把 `rpc/` 暴露出去，业务代码零改动。

---

## 4. 十条核心技术能力的落点（能力 → 代码 → 验证）

| # | 能力 | 关键实现 | 代码落点 | 怎么验证 |
| --- | --- | --- | --- | --- |
| 1 | 签到 BitMap + Kafka 异步积分 | 月/年两张 BitMap，offset=日期；签到成功发 `signin-success` 事件 | `modules/signin`、`point` 消费者 | `redis-cli bitcount signin:month:202609:{uid}`；查 `point_record` |
| 2 | 任务进度双层存储 | Redis Hash 热数据（秒级可见）+ Kafka 异步批量落库 | `modules/task` | 刷新页面进度不丢；停 Kafka 后 outbox 补发 |
| 3 | Redisson 分布式锁防并发重复写 | `DistributedLockTemplate` 统一封装：看门狗、重试、失败降级 | `infra/lock` | 并发压测脚本，DB 只落 1 条 |
| 4 | Base32 兑换码 + BitMap 核销 | 5bit/字符分段编码，批号+序号+校验位；核销状态位图 | `common/util/Base32Codec`、`modules/redeem` | 单测 20 亿容量边界；重复兑换返回已使用 |
| 5 | 领券超发 + 切面顺序修复 | 锁在外事务在内的失效复现；`@Order` 调整事务/锁切面顺序 | `modules/coupon`、`common/aspect` | 并发领券不超发；`LockOrderDemoTest` 复现旧 bug |
| 6 | 最优优惠组合并行计算 | 可用券组合枚举 + `CompletableFuture` 并行算价 + 策略筛选 | `modules/order/service/impl/OptimalDiscountCalculator` | 结算页返回最优方案 + 全部候选 |
| 7 | AI 助手 Function Calling | 权益查询/下单/取消/进度四个工具；文案生成切轻量模型 HTTP 直连 | `modules/ai` | 对话触发真实下单；日志打印 tool_calls |
| 8 | Caffeine + Redis 二级缓存 | `TwoLevelCache` 封装 Cache-Aside；空值缓存 + 逻辑过期防击穿 + 随机 TTL 防雪崩 | `infra/cache` | 连续请求只打一次 DB（日志计数） |
| 9 | 双层限流 | Guava RateLimiter（单机）→ Redis 计数（集群）可切换 | `infra/ratelimit`、`@RateLimit` | 压测签到/领券接口返回 429 |
| 10 | 本地 Service + Dubbo RPC 双接口 | 订单/优惠券两个域同时提供 `XxxService` 与 `XxxRpcService` | `modules/order/rpc`、`modules/coupon/rpc` | 单体 0 远程调用（Dubbo 关闭）；`rpc` profile 下直连自测 |

---

## 5. 关键链路设计

### 5.1 签到（写多读少 → BitMap + 异步）

```
POST /api/signin/do
 ├─ 1. 本地限流 @RateLimit(limiter=LOCAL, 5/s)  ← Guava
 ├─ 2. 分布式锁 lock:signin:{uid}:{yyyyMM}（防连点）
 ├─ 3. Redis GETBIT 判重 → 已签到直接返回
 ├─ 4. SETBIT 月图 + 年图；BITCOUNT 计算当月天数 / BITFIELD 取连续天数
 ├─ 5. DB 落 sign_in_record（唯一索引兜底）
 └─ 6. EventPublisher 发 signin-success → outbox 表 + Kafka
        └─ 消费端（幂等 by eventId）：加积分 → 更新 point_record / account / ZSet 排行榜
```
- **为什么 BitMap**：一年 365 天只需 46 字节/人，100 万学生 ≈ 44MB；DB 只留流水，避免每天一人一行。
- **一致性**：DB 落库成功才算签到成功；Kafka 事件通过本地消息表保证"业务成功则事件最终必达"。

### 5.2 任务进度（读多写多 → 热缓存 + 异步落库）

```
POST /api/task/progress/report
 ├─ Redis HINCRBY task:progress:{uid}:{periodKey} {taskCode} delta   ← 毫秒级可见，页面刷新不丢
 ├─ 达到目标值 → 学生点「领取奖励」才发积分（claimReward，幂等键 = taskCode:periodKey）
 └─ Kafka task-progress-persist（批量/延迟 5s 聚合）→ 批量 UPDATE user_task_progress
```
> 奖励是**领取制**而不是自动发放：任务完成只改状态，学生主动领取才入账。
> 这样既能让"完成任务"这件事有仪式感，也避免自动发奖把积分账户写热点。
> 因此没有 task-completed 事件——加积分走的是学生请求 + 幂等键，不需要异步。
> 双层存储的取舍：Redis 是"真源"（页面读它），MySQL 是"归档"（报表/对账读它）。Redis 丢数据的风险用"签到/任务事件回放 + 每日对账任务"兜底。

### 5.3 领券（典型超发场景）

```
POST /api/coupon/receive
 ├─ 限流（Redis 计数，按 IP+uid）
 ├─ Redisson 锁 coupon:stock:{templateId}      ← 锁必须在事务外层
 │    ├─ 校验：已发放 < 总量、用户已领 < 单人限领
 │    ├─ Redis 预扣库存（Lua 原子）
 │    └─ 开启事务：user_coupon 插入 + template.issued_count 更新（乐观锁 version）
 └─ 释放锁
```
- **被修复的坑**：`@Transactional` 默认切面 order 最低（`Ordered.LOWEST_PRECEDENCE`），若锁切面 order 更小，则"锁在事务外、提交前释放"，并发下前一个事务未提交，后一个已读到旧值 → 超发。
  修复：显式给锁切面设 `@Order(0)`、事务切面设 `@Order(10)`，**保证锁包住整个事务边界**；并写测试 `LockOrderDemoTest` 复现旧顺序下的超发。

### 5.4 结算页最优优惠组合

```
GET /api/order/settle/preview?goodsId=&qty=
 ├─ 查用户全部可用券（状态 UNUSED 且未过期，二级缓存加速）
 ├─ 过滤：门槛、适用范围（商品/分类）
 ├─ 枚举合法组合（券数量 ≤ 3 时全组合；> 3 时按面额 Top-N 剪枝）
 ├─ CompletableFuture + 自定义线程池并行算每种组合实付金额（含券叠加互斥规则）
 └─ 策略筛选：优先"实付最低"，其次"券面额最大"，返回最优 + 候选列表
```

### 5.5 AI 助手

```
POST /api/ai/chat  {sessionId, message}
 ├─ 组装 System Prompt + 历史（最近 N 轮）+ 工具定义（JSON Schema）
 ├─ 调大模型（OpenAI 兼容 /chat/completions，支持 tool_calls）
 ├─ 若返回 tool_calls → 反射路由到本地工具（权益查询/下单/取消订单/进度查询）
 │    └─ 工具内部走本地 Service，与人工操作完全同一条链路（不绕过校验）
 ├─ 把工具结果回填给模型 → 生成最终自然语言回复
 └─ 需要"订单清单文案"时 → 切轻量模型，RestClient 直连（超时 8s，失败降级为模板文案）
```
- 所有会话与工具调用落 `ai_chat_session` / `ai_chat_message`，便于审计与回归。
- 未配置 API Key 时走 `RuleBasedChatClient`（本地意图识别），**接口与真实链路一致**，仅在文档与日志中明确标注为"离线开发模式"，不做假数据。

---

## 6. 可靠性与一致性设计

| 问题 | 方案 | 落点 |
| --- | --- | --- |
| 消息丢失 | 本地消息表 `mq_event_outbox` + 定时补偿（指数退避重试） | `infra/mq/EventPublisher`、`modules/mq/OutboxCompensateJob` |
| 重复消费 | `mq_consume_record` 唯一索引 + 消费前幂等校验 | `modules/mq/IdempotentConsumerSupport` |
| 缓存击穿 | 逻辑过期 + 互斥重建（Redisson 锁，只放一个线程回源） | `infra/cache/TwoLevelCache` |
| 缓存穿透 | 空值缓存（短 TTL）+ 参数校验 | `infra/cache/TwoLevelCache` |
| 缓存雪崩 | TTL 随机抖动（±10%） | `infra/cache/TwoLevelCache` |
| 缓存与 DB 一致 | Cache-Aside：先更新 DB 再删缓存 + 延迟双删 | `infra/cache/TwoLevelCache` |
| 超卖/超发 | Redis 预扣 + DB 乐观锁（version）+ 唯一索引兜底 | `coupon`、`order` |
| **账户热点行** | **原子增减** SQL（`balance = balance + ?`，余额不足判定也下推 SQL），而不是"读-改-写 + 乐观锁重试"——后者在热点行上会连环冲突，压测实测成功率仅 5% | `modules/point/service/impl/PointServiceImpl#changePoint` |
| **幂等键作用域** | 幂等键统一加 `u:{userId}:` 前缀：幂等是"同一个人别连点"，不是"全站只准提交一次" | `common/aspect/IdempotentAspect` |
| 接口刷量 | 两层限流 + `@Idempotent` 幂等注解 | `infra/ratelimit`、`common/aspect` |

### 6.1 事件清单（每个主题都有真实消费者）

| 主题 | 生产者 | 消费者组 | 消费动作 |
| --- | --- | --- | --- |
| `cg.signin.success` | 签到 | `cg-group-point` | 加积分 + 写 `point_record` + 更新 ZSet 排行榜 |
| `cg.task.progress.persist` | 任务进度上报 | `cg-group-task` | Redis 热数据异步落库到 `user_task_progress` |
| `cg.order.paid` | 订单支付 | `cg-group-order` | 推进「本周兑换一次」任务进度 |
| `cg.dead.letter` | 消费重试耗尽 | 人工 | 管理端排查后重投 |

> 取消订单、售后退款、任务奖励都在本地事务里同步做完了，**不发事件**。
> 曾经声明过 `cg.task.completed` / `cg.order.cancelled` / `cg.refund.finished` / `cg.point.changed`
> 四个主题，但只发不收（或只声明不用），属于"死事件"：白写 outbox、白占分区，
> 还让文档和实现对不上——已删除。加主题成本很低，维护没人消费的事件成本很高。

---

## 7. 线程池与异步边界

| 线程池 | 用途 | 参数 | 拒绝策略 |
| --- | --- | --- | --- |
| `discountCalcExecutor` | 结算页组合并行算价 | 核心 8 / 最大 16 / 队列 200 | CallerRuns（避免结算失败） |
| `pointAsyncExecutor` | 积分异步处理 | 核心 4 / 最大 8 / 队列 500 | CallerRuns |
| `aiTextExecutor` | 轻量模型文案生成 | 核心 4 / 最大 8 / 队列 100 | 丢弃 + 降级模板 |
| Kafka 消费 | 事件处理 | concurrency=2，批量手动提交 | 重试 3 次后进死信 topic |

> 所有池都自定义 `ThreadFactory` 命名 + `ThreadPoolTaskExecutor` 监控（active/queue/完成数），避免"裸 new ThreadPoolExecutor 出事故"。

---

## 8. 接口与鉴权

- 统一前缀 `/api`，返回体 `Result<T>{code,message,data,traceId}`。
- 登录：JWT（`Authorization: Bearer`），Redis 存 token 白名单支持主动踢下线。
- 密钥与凭据：`CAMPUS_JWT_SECRET` / `MYSQL_PASSWORD` / `DEEPSEEK_API_KEY` 全部走环境变量或
  被 gitignore 的 `application-local.yml`；`SecurityStartupCheck` 在 prod 环境仍是默认密钥时拒绝启动。
- 防爆破：接口限流（5 QPS / IP）+ 账号级失败计数（连续 5 次锁 10 分钟，Redis `cg:auth:fail:{username}`），
  账号不存在与密码错误返回同一提示，避免账号枚举。
- 角色：`STUDENT` / `OPERATOR` / `ADMIN`，**两层校验**（`AuthInterceptor`）：
  1. 路径兜底：`/api/admin/**` 一律要求 OPERATOR/ADMIN——新增管理端接口忘加注解也不会漏防护；
  2. 注解声明：`@RequireRole` 用于非 admin 路径的特定角色要求。
  不引 Spring Security（个人项目没必要，但接口位置保留）。
- 跨域：白名单 `campus.cors.allowed-origins`，不使用 `*` + `allowCredentials`。
- 监控端点：默认只暴露 `health`（`/actuator/**` 不在 `/api/**` 下，拦截器覆盖不到，因此不能靠拦截器兜底）。
- 全局异常：`BizException`（业务可预期）与系统异常分开，前端统一弹窗。

---

## 10. 订单状态机与业务闭环

```
CREATED ──支付──► PAID ──学生确认完成 / 运营核销──► FINISHED
   │               │
   │               └──售后退货（审核通过）──► REFUNDED（退积分 + 回滚库存 + 释放优惠券）
   │               └──售后换货（审核通过）──► FINISHED（回滚库存，不退积分/券）
   └──取消 / 超时未支付（15 分钟）──► CANCELLED（回滚库存 + 释放券）
```

- **退货终态是 `REFUNDED` 而不是 `FINISHED`**：语义必须区分，否则"已完成订单数""消耗积分"统计口径会错。
- **核销入口有两个**：学生端 `POST /api/order/finish`（确认收货）、管理端 `POST /api/admin/order/finish`（运营核销）。
- **售后与券**：退货会把订单占用的券释放回用户（`unlockCoupon`），换货不回退（商品本身没退钱）。

### 任务闭环（第二轮修复的重点）

早期版本任务模块是"孤岛"——只有前端主动上报，签到/下单不会驱动任务进度。现在改为**后端业务成功后本地调用**（符合"单体内部一律本地方法调用"的原则）：

| 业务行为 | 触发的任务 | 落点 |
| --- | --- | --- |
| 签到成功 | `DAILY_SIGN_IN` | `SignInServiceImpl#signIn` |
| 订单支付成功 | `WEEKLY_ORDER` | `OrderServiceImpl#pay` |
| 补齐学校 + 学号 | `ONCE_PROFILE` | `AuthServiceImpl#updateProfile` |
| 浏览商品 / 分享 | `DAILY_BROWSE` / `DAILY_SHARE` | 前端上报（本身就是 UI 行为） |

所有联动都走 `TaskService#reportProgressQuietly`：**任务被停用或删除时只记日志，绝不影响主业务**。

### MQ 发送不阻塞主链路

`EventPublisher` 在事务 `afterCommit` 后把发送任务提交到独立线程池 `mqSendExecutor`，HTTP 线程立即返回；producer 侧 `max.block.ms=10s`、`delivery.timeout.ms=15s`，避免 Kafka 抖动拖死业务线程。
实测：Kafka 未启动时，22 项端到端验证从 **约 10 分钟降到 4 秒**（修复前每个事件都会在业务线程上等 Kafka 元数据超时）。

### 派生数据一致性

排行榜是 Redis 派生数据，真源是 `user_point_account` / `point_record`。两条修复路径：
1. 启动自检 `RankBackfillRunner`：总榜为空时自动重建（正常启动几乎零开销）；
2. 运维接口 `POST /api/admin/point/rank/rebuild`：随时手动重建。

---

## 11. 已知取舍（面试时最容易被追问的点）

1. **为什么不用 Seata？** 单体单库，本地事务足够；跨"库"只有 Redis 与 MySQL，用最终一致 + 对账即可，引 Seata 反而增加故障面。
2. **Redis 3.2 的局限**：不支持 Stream / `GETDEL` / `UNLINK`，所以不用 Redis 做消息队列（用 Kafka），删除大 key 用 `DEL` 并控制 value 体积。
3. **MySQL 5.7 的局限**：无窗口函数/CTE，排行榜放 Redis ZSet；分页用 `LIMIT` 而非 `OFFSET` 深翻（列表页限制最大页）。
4. **Dubbo 预留的代价**：多一个依赖、多一层接口，但换来"将来拆服务不改业务代码"；因此接口只暴露粗粒度、幂等、无分布式事务的用例。
5. **本地缓存一致性**：Caffeine L1 在多实例下存在窗口期不一致，故只用于"读多写少且容忍秒级不一致"的数据（商品、券模板），下单校验一律读 DB 或 Redis。
