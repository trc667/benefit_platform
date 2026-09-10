# 接口契约（前后端唯一约定）

- 统一前缀 `/api`，除登录/注册外均需 `Authorization: Bearer <token>`。
- 统一响应体：`{ "code": 0, "message": "ok", "data": ..., "traceId": "..." }`，`code != 0` 即业务失败（前端统一弹窗）。
- 分页响应：`{ "records": [], "total": 0, "page": 1, "size": 10 }`。
- 时间格式统一 `yyyy-MM-dd HH:mm:ss`；金额/积分均为整数。

## 1. 认证 `/auth`

| 方法 | 路径 | 说明 | 入参 | 出参 |
| --- | --- | --- | --- | --- |
| POST | `/api/auth/login` | 登录 | `{username,password}` | `{token,userInfo}` |
| POST | `/api/auth/register` | 学生注册（受准入策略与风控约束） | `{username,password,nickname,studentNo,school,inviteCode?}` | `{token,userInfo}` |
| GET | `/api/auth/register-config` | 注册策略（公开）：前端据此决定是否渲染邀请码/学校下拉 | - | `{mode,needInviteCode,needSchool,schools,studentNoPattern}` |
| POST | `/api/auth/logout` | 退出（清 Redis 白名单） | - | - |
| GET | `/api/auth/me` | 当前用户 | - | `UserInfoVO` |
| PUT | `/api/auth/profile` | 修改资料（补齐学校+学号会触发 `ONCE_PROFILE` 任务） | `{nickname,phone,studentNo,school,avatar}` | `UserInfoVO` |

`UserInfoVO`：`{id,username,nickname,avatar,phone,studentNo,school,role,growthLevel,balance,totalEarned}`

> **登录保护**：接口级限流（默认 5 QPS / IP）+ 账号级失败锁定。
> 同一账号连续失败 5 次（`campus.auth.lock-threshold`）锁定 10 分钟（`lock-minutes`），
> 期间返回 `code=1006`「密码错误次数过多，请 N 分钟后再试」；登录成功即清零。
> 账号不存在与密码错误返回同一个 `1002`，避免账号枚举。

> **注册准入与风控**（防"批量开小号薅权益"）：
>
> | 环节 | 规则 | 返回码 |
> | --- | --- | --- |
> | 准入策略 | `campus.auth.register.mode`：`OPEN`（只校验学号格式）/ `INVITE`（必须带有效邀请码）/ `SCHOOL`（学校须在白名单） | `1007 注册受限` |
> | 学号唯一 | 学号是"一个人一个账号"的等价物，重复学号直接拒绝（DB 唯一索引 `uk_student_no` 兜底） | `1009 该学号已注册过账号` |
> | 学号格式 | 默认 `^[0-9]{6,20}$` | `400 参数校验失败` |
> | 设备/IP 风控 | 单设备每日注册 ≤ `register-per-device`（默认 2）、单 IP ≤ `register-per-ip`（默认 5）、单设备每日**签到账号数** ≤ `signin-accounts-per-device`（默认 3） | `1008 风控拦截` |
>
> 请求头 `X-Device-Id`（前端自动生成并携带）是风控的设备维度；**不传该头不会绕过校验**，
> 服务端会把它归入 `nodev:{ip}` 桶。用 IP 做风控会误伤同一 NAT 出口的整栋宿舍楼，所以判据是设备。

## 2. 签到 `/signin`

| 方法 | 路径 | 说明 | 出参 |
| --- | --- | --- | --- |
| POST | `/api/signin/do` | 执行签到 | `{signDate,continuousDays,pointAward,monthCount,extraAward}` |
| GET | `/api/signin/calendar?month=202609` | 月度日历 | `{month,todaySigned,continuousDays,monthCount,totalCount,days:[{day,signed,future}]}` |
| GET | `/api/signin/stat` | 签到概览 | `{continuousDays,monthCount,yearCount,lastSignDate}` |

## 3. 积分与排行榜 `/point`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/point/account` | `{balance,totalEarned,totalUsed,growthLevel,nextLevelPoint}` |
| GET | `/api/point/records?page=1&size=10&bizType=` | 流水分页 |
| GET | `/api/point/rank?type=TOTAL|MONTH&limit=20` | `[{rank,userId,nickname,avatar,point,isMe}]` |
| GET | `/api/point/rank/me?type=TOTAL` | `{rank,point,total}` |

## 4. 任务 `/task`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/task/list` | `[{taskCode,taskName,taskType,targetValue,progress,status,pointAward,icon,description,periodKey}]`（status：0进行中 1已完成待领 2已领取） |
| POST | `/api/task/progress/report` | `{taskCode,delta}` → `{progress,targetValue,status}` |
| POST | `/api/task/reward/claim` | `{taskCode}` → `{pointAward,balance}` |

## 5. 权益商品 `/benefit`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/benefit/goods/page?page=1&size=12&category=&keyword=&sort=` | 卡片列表（`sort`：`default|hot|priceAsc|priceDesc`） |
| GET | `/api/benefit/goods/{id}` | 详情 |
| GET | `/api/benefit/categories` | `[{code,name,count}]` |

## 6. 优惠券 `/coupon`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/coupon/templates?page=1&size=10` | 可领取券列表（含 `received`、`remainCount`） |
| POST | `/api/coupon/receive` | `{templateId}` → `UserCouponVO` |
| GET | `/api/coupon/mine?status=UNUSED&page=1&size=10` | 我的券 |
| GET | `/api/coupon/available?amount=500` | 结算页可用券（已按门槛过滤） |

## 7. 兑换码 `/redeem`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/redeem/exchange` | `{code}` → `{batchNo,bizType,rewardValue,balance,message}` |

## 8. 订单与售后 `/order`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/order/settle/preview?goodsId=1&quantity=1` | `{goodsTotal,bestPlan:{couponId,couponTitle,discountPoint,payPoint,couponIds[]},candidates:[...],availableCoupons:[...],balance,balanceEnough}` |
| POST | `/api/order/create` | `{goodsId,quantity,couponIds:[],couponId?,receiverName,receiverPhone,receiverAddress,remark}` → `{orderNo,payPoint}`。`couponIds` 为选用的券列表（最优方案可能多张）；`couponId` 为单券兼容字段，二者传其一即可。**优惠金额由服务端重算，前端传的金额一律忽略** |
| POST | `/api/order/pay` | `{orderNo}` → `{orderNo,payPoint,balance}` |
| POST | `/api/order/cancel` | `{orderNo}` |
| POST | `/api/order/finish` | `{orderNo}` 确认完成（PAID → FINISHED） |
| GET | `/api/order/page?status=&page=1&size=10` | 我的订单 |
| GET | `/api/order/{orderNo}` | 订单详情（含明细、券、优惠快照） |
| POST | `/api/order/refund/apply` | `{orderNo,refundType,reason}` → `{refundNo}` |
| GET | `/api/order/refund/page?page=1&size=10` | 我的退换单 |

## 9. AI 助手 `/ai`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/ai/chat` | `{sessionId,message}` → `{sessionId,reply,model,toolCalls:[{name,args,result,success,costMs}],costMs}` |
| GET | `/api/ai/sessions` | 会话列表 |
| GET | `/api/ai/messages?sessionId=` | 消息列表 |
| DELETE | `/api/ai/session/{sessionId}` | 删除会话 |

## 10. 管理端 `/admin/**`（角色 OPERATOR/ADMIN）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/dashboard/overview` | `{userCount,todaySignCount,orderCount,todayOrderCount,pointIssued,couponIssued,goodsCount,refundPending}` |
| GET | `/api/admin/dashboard/signin-trend?days=7` | `[{date,count}]` |
| GET | `/api/admin/dashboard/order-trend?days=7` | `[{date,count,point}]` |
| GET | `/api/admin/user/page?keyword=&role=&page=1&size=10` | 用户管理 |
| POST | `/api/admin/user/status` | `{userId,status}` |
| GET | `/api/admin/benefit/goods/page` | 商品管理（含下架） |
| POST | `/api/admin/benefit/goods/save` | 新增/编辑（`id` 为空即新增） |
| POST | `/api/admin/benefit/goods/status` | `{id,status}` |
| GET | `/api/admin/coupon/template/page` | 券模板管理 |
| POST | `/api/admin/coupon/template/save` | 新增/编辑 |
| POST | `/api/admin/coupon/template/status` | `{id,status}` |
| POST | `/api/admin/coupon/grant` | `{templateId,userIds:[],count}` 定向发放 |
| GET | `/api/admin/coupon/stat` | `[{templateId,title,totalCount,issuedCount,usedCount,useRate}]` |
| GET | `/api/admin/redeem/batch/page` | 批次列表 |
| POST | `/api/admin/redeem/batch/create` | `{title,bizType,refId,rewardValue,totalCount,startTime,endTime,remark}` |
| GET | `/api/admin/redeem/batch/{batchNo}/codes?count=10` | 生成/预览码（从当前进度起） |
| POST | `/api/admin/redeem/batch/status` | `{batchNo,status}` |
| GET | `/api/admin/task/page` | 任务配置列表 |
| POST | `/api/admin/task/save` | 新增/编辑 |
| POST | `/api/admin/task/status` | `{id,status}` |
| GET | `/api/admin/order/page?orderNo=&status=&page=1&size=10` | 订单管理 |
| GET | `/api/admin/order/{orderNo}` | 订单详情（跨用户，运营用） |
| POST | `/api/admin/order/finish` | `{orderNo}` 运营核销（PAID → FINISHED） |
| GET | `/api/admin/order/refund/page?status=&page=1&size=10` | 售后审核 |
| POST | `/api/admin/order/refund/handle` | `{refundNo,approved,handleRemark,refundPoint}` |
| POST | `/api/admin/coupon/expire-now` | 立即执行券过期处理 → `{expiredCount}` |
| POST | `/api/admin/point/rank/rebuild` | 重建排行榜 → `{totalRankSize,monthRankSize}` |
| POST | `/api/admin/point/adjust` | `{userId,changePoint,bizNo?,reason}` 手动调整积分（正加负扣）→ `{applied,bizNo,balance}` |
| GET | `/api/admin/mq/outbox/page?status=&page=1&size=10` | 本地消息表监控 |
| POST | `/api/admin/mq/outbox/retry` | `{id}` 手动补偿 |
| GET | `/api/admin/cache/stats` | 二级缓存命中率/回源次数 |
| GET | `/api/admin/operation/log/page` | 操作日志 |
