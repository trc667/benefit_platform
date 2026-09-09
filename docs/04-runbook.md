# 本地运行手册

> 目标：**不装 Docker**，用你机器上已有的 MySQL / Redis，加上一个便携 Kafka，把整套系统跑起来。

## 0. 环境基线（本项目实际验证过的版本）

| 组件 | 版本 | 备注 |
| --- | --- | --- |
| JDK | **17+**（本机用 17.0.16） | SpringBoot3 硬性要求；脚本会自动探测（参数 → `JAVA_HOME` → 常见安装目录），优先选 17 |
| Maven | 3.9+（或用 `backend\mvnw.cmd`） | 镜像走 **https** 阿里云源；本地仓库用各自默认的 `~/.m2` 即可 |
| MySQL | 5.7.31 / 8.0.x 均可 | 表设计规避了 8.0 专有语法 |
| Redis | 3.2.100+ | 需要 BitMap / ZSet / Lua / `SETNX`；已实测 3.2.100 可用 |
| Kafka | 3.7.1（KRaft 单节点） | 不需要 ZooKeeper |
| Node | 18.18+（本机 24） | 前端构建 |

## 1. 一次性准备

```powershell
# 1) 建库 + 建表 + 演示数据（会 DROP 重建 campus_growth 库，注意别指向生产库）
.\scripts\init-db.ps1 -Password 你的MySQL密码

# 2) 启动 Redis（复用你已有的 Redis for Windows）
.\scripts\start-redis.ps1

# 3) 下载并解压 Kafka 到 .tools\ 下，然后启动
#    下载（实测可用：华为云长期留档旧版本；清华/阿里/USTC 只保留最新版，会 404）
#      https://mirrors.huaweicloud.com/apache/kafka/3.7.1/kafka_2.13-3.7.1.tgz
#    解压（Windows 10+ 自带 tar）
#      tar -xzf kafka_2.13-3.7.1.tgz -C .tools\
.\scripts\start-kafka.ps1
```

> **Windows 上启动 Kafka 的两个坑**（`scripts\start-kafka.ps1` 里已经绕开，无需手动处理）：
> 1. **不要用 `bin\windows\*.bat`**：Kafka 3.7 的 `libs\` 下有 119 个 jar，批处理把完整类路径
>    拼进 java 命令行后超过 cmd.exe 的 8191 字符上限，直接报 `The input line is too long.`。
>    脚本改为 `java -cp "libs\*" kafka.Kafka`，由 JVM 展开通配符。
> 2. **不要用 `Get-NetTCPConnection` 判断是否启动**：部分 Windows 环境下该 cmdlet 查不到监听端口，
>    会把已经启动的 broker 误判为未启动。脚本改用 TCP 连接探测。
>
> 另外 `start-kafka.ps1` 默认后台拉起进程；交给 CI / IDE 托管时用 `-Foreground` 前台阻塞运行。

> **Kafka 可以先不启动**：应用照常运行，事件会先写入 `mq_event_outbox`（本地消息表），
> Kafka 恢复后由补偿任务自动补发。这正是本项目"消息不丢"的设计，可以拿来做演示。

## 2. 启动应用

```powershell
# 后端（默认 8090）
.\scripts\start-backend.ps1

# 前端（默认 5173，/api 已代理到 8080）
.\scripts\start-frontend.ps1
```

浏览器打开 <http://127.0.0.1:5173>，用演示账号登录：

| 账号 | 密码 | 角色 | 入口 |
| --- | --- | --- | --- |
| `student01` ~ `student06` | `123456` | 学生 | 学生端 |
| `operator` | `123456` | 运营 | 管理端 |
| `admin` | `123456` | 管理员 | 管理端 |

## 3. 配置说明

配置文件按环境拆分，敏感信息一律不进仓库：

| 文件 | 作用 |
| --- | --- |
| `application.yml` | 公共配置 + 环境变量占位符（端口、连接池、业务参数、Dubbo 开关） |
| `application-dev.yml` | 本地开发：打印 SQL、放开 `metrics` 端点 |
| `application-prod.yml` | 生产基线：不打印 SQL、只暴露 `health`、日志级别收敛 |
| `application-rpc.yml` | 仅在验证预留的 Dubbo 接口时启用（`--spring.profiles.active=rpc`） |
| `application-local.yml` | **本地私有**：数据库密码、AI Key，已被 `.gitignore` 忽略 |

### 3.1 环境变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_DATABASE` | `127.0.0.1` / `3306` / `campus_growth` | 数据库连接 |
| `MYSQL_USER` / `MYSQL_PASSWORD` | `root` / 空 | **密码必须配**，否则连不上；本地可写进 `application-local.yml` |
| `REDIS_HOST` / `REDIS_PORT` | `127.0.0.1` / `6379` | Redis 连接（有密码加 `spring.data.redis.password`） |
| `KAFKA_BOOTSTRAP` | `127.0.0.1:9092` | Kafka 地址 |
| `CAMPUS_JWT_SECRET` | 开发默认值 | **生产必须覆盖**；prod 环境仍用默认值会拒绝启动 |
| `CAMPUS_CORS_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173` | 允许跨域的前端来源（逗号分隔） |
| `DEEPSEEK_API_KEY` / `DEEPSEEK_LIGHT_API_KEY` | 空 | 留空时 AI 助手走本地规则引擎（离线模式，接口/链路完全一致） |

### 3.2 业务开关（`campus.*`）

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `campus.limit.strategy` | `LOCAL` | 改 `REDIS` 即切换为集群分布式限流 |
| `campus.auth.lock-threshold` / `lock-minutes` | `5` / `10` | 登录失败锁定阈值与锁定时长 |
| `campus.mq.enabled` / `campus.mq.outbox.enabled` | `true` | 关掉后事件只落本地消息表、不发送 |
| `dubbo.enabled` | `false` | **单体部署必须保持 false**；置 true 才会暴露 RPC 接口 |

环境变量方式（推荐）：

```powershell
$env:MYSQL_PASSWORD = "你的MySQL密码"
$env:CAMPUS_JWT_SECRET = "换成足够长的随机串"
$env:DEEPSEEK_API_KEY = "sk-xxx"        # 可选，主模型
.\scripts\start-backend.ps1
```

## 4. 端到端验证清单

启动后按顺序执行，每一步都能看到真实结果：

### 4.1 签到 → Kafka → 积分 → 排行榜（核心异步链路）

```powershell
# 1) 登录拿 token
$login = Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8090/api/auth/login `
  -ContentType 'application/json' -Body '{"username":"student01","password":"123456"}'
$token = $login.data.token
$h = @{ Authorization = "Bearer $token" }

# 2) 签到
Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8090/api/signin/do -Headers $h

# 3) 看 Redis 位图（把 userId=3 换成你的用户 ID）
redis-cli bitcount "cg:signin:month:202609:3"
redis-cli getbit   "cg:signin:month:202609:3" 8

# 4) 看积分是否到账（Kafka 消费成功后才会出现 SIGNIN 流水）
Invoke-RestMethod -Uri "http://127.0.0.1:8090/api/point/records?page=1&size=5" -Headers $h

# 5) 排行榜
Invoke-RestMethod -Uri "http://127.0.0.1:8090/api/point/rank?type=TOTAL&limit=10" -Headers $h
```

**Kafka 没启动时的预期表现**：第 2 步成功、第 4 步暂无积分流水，
`mq_event_outbox` 里有一条 `status=FAILED` 的记录；启动 Kafka 后等 30 秒（补偿任务周期），
该记录变 `SENT`，积分到账。这个对比本身就是"本地消息表"最好的演示。

### 4.2 领券防超发

```powershell
# 并发 200 次领同一张券（stock=100 的模板），观察成功数与 issued_count
1..200 | ForEach-Object -Parallel {
    Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8090/api/coupon/receive `
      -Headers $using:h -ContentType 'application/json' -Body '{"templateId":6}'
} -ThrottleLimit 50
```

再查 `coupon_template.issued_count` 与 `total_count`：**永远不会超过发行量**。
单人限领会挡住同一用户重复领，所以真实压测建议用多个账号。

### 4.3 锁顺序对比实验（复现"锁在事务内导致超发"）

```powershell
Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:8090/api/admin/coupon/demo/lock-order?stock=10&threads=50" -Headers $h
```

返回里会同时给出 `wrongOrder`（锁在事务内 → 成功 50 次、超发 40）与
`rightOrder`（锁包住事务 → 成功 10 次、超发 0）。

### 4.4 兑换码

```powershell
# 管理端生成 5 个码
Invoke-RestMethod -Uri "http://127.0.0.1:8090/api/admin/redeem/batch/RCB2026090100001/codes?count=5" -Headers $h

# 学生端兑换（把返回的码填进去，注意去掉连字符也行）
Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:8090/api/redeem/exchange?code=XXXX-XXXX-XXXX" -Headers $h

# 再次兑换同一个码 → 返回"该兑换码已被使用"
```

### 4.5 最优优惠组合

```powershell
# 用 student01（预置了 3 张券）
Invoke-RestMethod -Uri "http://127.0.0.1:8090/api/order/settle/preview?goodsId=1&quantity=1" -Headers $h
```

返回 `bestPlan`（最优组合，`best=true`）与 `candidates`（全部候选方案，按实付升序），
以及 `totalCostMs`（并行算价耗时）。

### 4.6 二级缓存

```powershell
# 连续请求商品详情 20 次，然后看命中率
1..20 | ForEach-Object { Invoke-RestMethod -Uri http://127.0.0.1:8090/api/benefit/goods/1 -Headers $h | Out-Null }
Invoke-RestMethod -Uri http://127.0.0.1:8090/api/admin/cache/stats -Headers $h
```

`stats.l1Hit` 会显著增长，`dbLoad` 只增加 1 次。

### 4.7 AI 助手

```powershell
# 未配置 API Key 时（离线模式，返回 realModel=false）
Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8090/api/ai/chat -Headers $h `
  -ContentType 'application/json' -Body '{"message":"看看有什么可以兑换的"}'
```

返回里的 `toolCalls` 会展示真实的工具调用（名称、入参、结果、耗时）。

### 4.8 权限与业务闭环回归（第二轮评审修复项）

```powershell
# 1) 权限：学生 token 调管理端接口必须被拒绝
Invoke-RestMethod -Uri http://127.0.0.1:8090/api/admin/dashboard/overview -Headers $学生header   # 期望 code=403
Invoke-RestMethod -Uri http://127.0.0.1:8090/api/admin/dashboard/overview -Headers $管理员header # 期望 code=0

# 2) 签到驱动任务：换一个今天没签到的账号签到，再查任务列表，DAILY_SIGN_IN 应为 1/1
# 3) 券过期：手动触发一轮
Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8090/api/admin/coupon/expire-now -Headers $管理员header

# 4) 订单闭环：创建 → 支付 → POST /api/order/finish → 状态 FINISHED
# 5) 管理端核销：POST /api/admin/order/finish（运营 token）
# 6) 排行榜重建：
Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8090/api/admin/point/rank/rebuild -Headers $管理员header

# 7) 运营手动调整积分（幂等：同一个 bizNo 重复提交只入账一次）
Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8090/api/admin/point/adjust -Headers $管理员header `
  -ContentType 'application/json' -Body '{"userId":3,"changePoint":100,"reason":"活动漏发补偿"}'

# 8) 订单支付事件被真实消费（Kafka 链路）：
#    下完单支付后查 mq_consume_record，应出现 consumer_group='cg-group-order' 的记录

# 9) 登录失败锁定：对同一账号连续错 5 次（每次间隔 >1s，避开接口限流），第 6 次返回 code=1006
$body = '{"username":"locktest","password":"wrong"}'
1..5 | ForEach-Object { Start-Sleep -Milliseconds 1600
  Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8090/api/auth/login -ContentType 'application/json' -Body $body }
# 解锁（或等 10 分钟）：redis-cli del cg:auth:fail:locktest
```

> 以上 8 项已包含在 `scripts/verify-e2e.ps1` 里，直接跑脚本即可（当前 22/22 全绿）。
> 脚本会在开头检查测试账号余额，不足时自动用 `/api/admin/point/adjust` 补到 1000，
> 所以**可以反复跑**（下单/退款用例会真实消耗积分）。

## 5. 常见问题

| 现象 | 原因 / 解决 |
| --- | --- |
| 启动报 `UnsupportedClassVersionError` | 用了 JDK 11 编译/运行，设置 `JAVA_HOME` 到 17+ |
| Maven 拉依赖失败 / 卡住 | 用 `-s .mvn/settings.xml`（脚本已内置），镜像必须是 **https** |
| `Access denied for user 'root'` | 改 `application.yml` 里的数据库密码 |
| 签到成功但积分没加 | Kafka 没起 → 看 `mq_event_outbox`，起 Kafka 后自动补偿 |
| Kafka 启动报 `The input line is too long.` | 别用 `bin\windows\*.bat`（119 个 jar 拼类路径超 cmd 8191 字符上限），用 `scripts\start-kafka.ps1` |
| Kafka 删 topic 后 broker 起不来 | Windows 上删 topic 的目录重命名会 `AccessDeniedException`，日志目录被判 offline → broker 退出。停掉 Kafka，手动删掉 `kafka-data\` 下对应的残留分区目录再启动（本项目已不再依赖删 topic） |
| Redis 报 `NOAUTH` | 你的 Redis 有密码，补 `spring.data.redis.password` |
| 前端 `npm run build` 报 esbuild 错 | 用 `npm run dev` 即可；构建用 `npm run build:rollup` 或换 Node 18/20 |
| 端口被占用 | `start-backend.ps1 -Port 8081`，同时改前端 `vite.config.js` 的 proxy 目标 |

## 6. 生产部署提示（写在这里避免踩坑）

1. `campus.jwt.secret`、数据库密码、AI Key 一律走环境变量，不要提交到 Git；
2. `campus.limit.strategy` 改 `REDIS`（多实例共享配额）；
3. `dubbo.enabled` 保持 `false`，除非你真的拆了服务；
4. 前端 `npm run build` 后由 nginx 托管，并把 `/api` 反向代理到后端，同时开启 gzip；
5. MySQL 建议 8.0 + 独立账号（不要用 root），并给 `point_record` / `sign_in_record` 加按月的归档策略。
