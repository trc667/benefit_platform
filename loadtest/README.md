# 压测工具（零依赖，Node 18+）

不装 k6 / JMeter / Gatling，`node loadtest/run.mjs` 直接跑。原因：
1. 这些工具要么装不上、要么几十 MB 二进制，别人 clone 下来跑不动；
2. 本项目要压的接口不多，Node 内置 `http` + keep-alive 连接池已经能打出 1800+ RPS（远超本机后端容量）；
3. **可以把「并发正确性断言」和压力一起做** —— 领券是不是恰好发出 100 张、同一用户并发签到会不会重复加分，这些用 k6 做要绕一大圈。

---

## 快速开始

```powershell
# 1) 压测前关掉限流与风控（否则压到的是限流器/风控，不是数据库）
#    - 限流：登录按 IP 5 QPS，压测机最先被挡
#    - 风控：单设备每日注册上限 2，压测要造 200 个账号，必然被挡
$env:CAMPUS_LIMIT_ENABLED='false'; $env:CAMPUS_RISK_ENABLED='false'; .\scripts\start-backend.ps1
#    注意：Redis / MySQL / Kafka 都要在跑

# 2) 全场景跑一遍，产出报告
node loadtest/run.mjs --all --users 200 --stock 100
#    报告输出到 docs/loadtest-report.md，原始 JSON 在 loadtest/results/
```

单场景：

```powershell
node loadtest/run.mjs --scenario signin --users 100 --fresh   # 签到高峰 + 并发重复签到断言
node loadtest/run.mjs --scenario coupon --users 300 --stock 100
node loadtest/run.mjs --scenario settle --vus 100 --duration 15
node loadtest/run.mjs --scenario hotspot --codes 60           # 同一账户并发写（乐观锁热点）
node loadtest/run.mjs --scenario order --users 60
node loadtest/run.mjs --scenario stage --stages 50,100,200,400,800 --per-stage 8
```

退出码：`0` 全部断言通过；`2` 有断言失败（可以直接挂到 CI 上）。

---

## 场景说明（以及每个场景在验证什么）

| 场景 | 打什么 | 关键断言 |
| --- | --- | --- |
| `signin` | N 个用户**各并发打 2 次**签到 | 每用户最多成功 1 次；重复请求被 429（分布式锁）/2001（今日已签到）明确拒绝；积分流水只有 1 条、余额增量等于奖励；顺带量出**异步入账延迟** |
| `coupon` | N 个用户并发抢一张库存 100 的券 | **成功数恰好 = 100，零超发**；`issued_count` 一致；单人限领不被突破 |
| `settle` | 持续压结算页（CompletableFuture 并行算价，幂等只读） | 吞吐 / P95 / P99；无 5xx |
| `hotspot` | **同一个用户**并发核销 60 个兑换码（都写同一个积分账户） | 不出现系统错误码 500；成功数 × 奖励 == 余额增量（不静默丢分） |
| `order` | N 个用户并发「下单 + 支付」 | 没有任何账户被扣成负数；扣减总额 == 成功订单 × 单价；库存扣减 == 成功订单数 |
| `stage` | 阶梯加压（50→800 并发） | 找吞吐拐点，观察错误率与 P99 随并发的变化 |

夹具（用户 / 券模板 / 兑换码批次 / 商品）全部通过**真实接口**创建，不用 SQL 直插 —— 压的就是完整业务链路。
压测账号会缓存到 `loadtest/.users.json`（已 gitignore，含 token），第一次准备 200 个账号约 1-2 分钟。

---

## 为什么这些断言比"QPS 好看"更重要

压测第一次跑出来就抓到两个**只有并发才暴露**的真实缺陷：

1. **积分账户热点行**：同一账户 60 并发写，只有 3 次成功（5%），其余全是 `500 积分更新冲突过于频繁`。
   原因是"读-改-写 + 乐观锁 + 重试 3 次"在热点行上必然连环冲突 → 改成 `UPDATE ... SET balance = balance + ?`
   原子增减后，**60/60 全部成功**。
2. **幂等键缺用户维度**：`@Idempotent(key = "'order:create:' + #dto.goodsId + ':' + #dto.quantity")`
   不含用户 ID，60 个不同用户同时下单同一商品，59 个被"请勿重复提交"挡掉 → 在切面里统一加 `u:{userId}:` 前缀后 **60/60 下单成功**。

数字会骗人，断言不会。

---

## 已知局限（写清楚，避免过度解读）

1. 压测客户端与后端**同机回环**，且是单 Node 进程 —— 200 并发以上吞吐停在 ~1800 RPS，这个数字是**下限**，不代表服务端真实容量；要测真实容量需要在另一台机器上跑客户端；
2. 场景都是**单接口**压力，没有做全链路混合流量（真实高峰是签到/商城/结算混着来）；
3. 没有做全链路压测的流量染色与影子库（[影子库 vs 影子表](https://developer.aliyun.com/article/982802)）—— 本项目的做法是压测试账号，不碰真实数据；
4. Kafka 消费者并发固定 2，压测里没有单独拉高它来观察 lag。

---

## 结果怎么读

- **RPS 上不去但错误率为 0** → 服务端吞吐饱和（看 CPU / 连接池 / Redis 单线程），不是应用 bug；
- **错误率突然抬升** → 找拐点，对照 `Hikari` 等待数、Tomcat 线程数、Redis 慢日志；
- **断言失败** → 才是真正的问题（并发正确性、数据一致性），优先级永远高于性能数字。
