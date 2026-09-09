# 数据库与缓存设计

DDL 见 `sql/schema.sql`（19 张表），初始化数据见 `sql/data.sql`。
**兼容 MySQL 5.7.31（本机实测）与 8.0.x**：不使用窗口函数、CTE、`CHECK`、JSON 函数，字符集统一 `utf8mb4_general_ci`。

---

## 1. 表清单与职责

| 域 | 表 | 职责 | 关键索引 / 约束 |
| --- | --- | --- | --- |
| 用户 | `sys_user` | 账号、角色、成长等级 | `uk_username`、`idx_student_no` |
| 积分 | `user_point_account` | 积分余额（与流水分离） | `uk_user_id`，`version` 乐观锁 |
| 积分 | `point_record` | 积分流水 | **`uk_user_biz(user_id,biz_type,biz_no)` → 天然幂等** |
| 签到 | `sign_in_record` | 签到流水（真源是 Redis BitMap） | `uk_user_date` 兜底防重 |
| 任务 | `task_definition` | 任务配置 | `uk_task_code` |
| 任务 | `user_task_progress` | 进度归档（真源是 Redis Hash） | `uk_user_task_period(user_id,task_code,period_key)` |
| 权益 | `benefit_goods` | 商品、库存、上下架 | `uk_goods_code`、`idx_status_sort`、`version` |
| 优惠券 | `coupon_template` | 券模板、发行量、限领 | `uk_template_code`、`version` |
| 优惠券 | `user_coupon` | 用户券（含快照字段） | `uk_coupon_code`、`idx_user_status` |
| 兑换码 | `redeem_code_batch` | 批次、总量、已核销数 | `uk_batch_no` |
| 兑换码 | `redeem_code` | 核销记录（**不预生成**） | `uk_code`、`idx_batch_seq` |
| 订单 | `order_main` | 订单主表、优惠快照 | `uk_order_no`、`idx_user_status`、`version` |
| 订单 | `order_item` | 明细（字段级快照） | `idx_order_id` |
| 售后 | `order_refund` | 退换单与审核 | `uk_refund_no`、`idx_status_time` |
| AI | `ai_chat_session` / `ai_chat_message` | 会话与工具调用审计 | `uk_session_id`、`idx_session` |
| 可靠性 | `mq_event_outbox` | 本地消息表（事件最终必达） | `uk_event_id`、`idx_status_retry` |
| 可靠性 | `mq_consume_record` | 消费幂等记录 | **`uk_event_consumer(event_id,consumer_group)`** |
| 审计 | `sys_operation_log` | `@OpLog` 切面自动落库 | `idx_user_time`、`idx_module_time` |

## 2. 几个设计取舍

1. **账户与流水分离**：`user_point_account.balance` 是热点行，用 `version` 乐观锁 + 重试；`point_record` 只追加，`uk_user_biz` 保证同一业务单号只入账一次（Kafka 重复消费时靠它兜底）。
2. **券快照**：`user_coupon` 冗余券的名称/面额/门槛/适用范围，避免模板被运营改动后历史券语义漂移；`order_main.discount_snapshot` 存下单时的最优优惠方案 JSON 文本，用于售后核对。
3. **兑换码不预生成**：码 = `batchNo` + 分段序号 + 校验位，通过 `Base32Codec` 双向可解。`redeem_code` 只在核销时插入，20 亿容量下不会产生 20 亿行数据；核销状态真源是 Redis BitMap。
4. **任务进度双层**：Redis Hash 是"真源"（页面秒级可见），MySQL 是归档。Kafka 消费者按 `(userId, periodKey)` 批量 upsert，靠唯一索引保证不重复。
5. **无外键**：全部逻辑外键 + 应用层校验，方便后续按域拆库。
6. **时间列**：`DATETIME` + `DEFAULT CURRENT_TIMESTAMP`，5.7 下允许多列同时使用（`TIMESTAMP` 有数量限制）。

## 3. Redis 键设计

| 键 | 类型 | TTL | 用途 |
| --- | --- | --- | --- |
| `cg:signin:month:{yyyyMM}` | BitMap（按用户分片 hash tag） | 90 天 | 月度签到，offset = day-1 |
| `cg:signin:month:{yyyyMM}:{userId}` | BitMap | 90 天 | 单用户月度签到位图 |
| `cg:signin:year:{yyyy}:{userId}` | BitMap | 400 天 | 年度签到位图，offset = dayOfYear-1 |
| `cg:signin:continuous:{userId}` | String | 400 天 | 连续签到天数（带月度重置逻辑） |
| `cg:point:rank:total` | ZSet | 永久 | 总积分排行榜 |
| `cg:point:rank:month:{yyyyMM}` | ZSet | 40 天 | 月榜 |
| `cg:task:progress:{userId}:{periodKey}` | Hash | 周期末 +1 天 | 任务进度热数据，field = taskCode |
| `cg:coupon:stock:{templateId}` | String（Lua 预扣） | 券过期 | 领券库存，DB 乐观锁兜底 |
| `cg:coupon:user:{userId}:{templateId}` | String 计数 | 券过期 | 单人限领计数 |
| `cg:redeem:used:{batchNo}` | BitMap | 批次结束后 180 天 | 核销位图，offset = seqNo |
| `cg:cache:goods:{id}` | String(JSON) | 30min±10% | 二级缓存 L2 |
| `cg:cache:goods:null:{id}` | String | 60s | 缓存穿透空值占位 |
| `cg:lock:{bizKey}` | Redisson 锁 | 看门狗 30s | 分布式锁 |
| `cg:limit:local:{key}` / `cg:limit:redis:{key}:{window}` | String 计数 | 窗口期 | 双层限流 |
| `cg:idem:{eventId}` | String | 24h | 消费/接口幂等 |
| `cg:token:{userId}:{jti}` | String | 与 JWT 同步 | 登录态白名单（支持踢下线） |

> 统一前缀 `cg:` 便于按前缀清理与监控；`cache` 与 `biz` 分离，避免误 `FLUSHDB` 打掉锁。

## 4. 容量估算（面试常问）

| 数据 | 方案 | 100 万学生占用 |
| --- | --- | --- |
| 月度签到 | BitMap 31 bit/人 | ≈ 3.7 MB |
| 年度签到 | BitMap 366 bit/人 | ≈ 44 MB |
| 若用 MySQL 行存储 | 1 行/人/天 | 一年 ≈ 3.65 亿行（不可接受） |
| 兑换码 | 不预生成 | 仅核销行 |
