# 端到端验证脚本：对着运行中的后端跑一遍核心链路，逐项打印结果
#
# 用法：
#   .\scripts\verify-e2e.ps1                 # 默认 http://127.0.0.1:8090
#   .\scripts\verify-e2e.ps1 -Base http://127.0.0.1:8090
#
# 前置：数据库已初始化（scripts\init-db.ps1）、Redis 已启动、后端已启动。
# Kafka 可选：未启动时签到积分不会立即到账，但能看到 mq_event_outbox 里的待补偿事件。

param(
    [string]$Base = "http://127.0.0.1:8090",
    [string]$User = "student01",
    [string]$Password = "123456"
)

$ErrorActionPreference = "Stop"
# 保证中文在控制台与 HTTP 响应里都按 UTF-8 处理
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
$pass = 0
$fail = 0

function PostJson($uri, $obj, $extraHeaders = $null) {
    # 注意：PowerShell 传字符串 body 会按本地编码发送，中文会乱码，必须显式转 UTF-8 字节
    $json = $obj | ConvertTo-Json -Depth 6
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
    $h = if ($extraHeaders) { $extraHeaders } else { $headers }
    return Invoke-RestMethod -Method Post -Uri $uri -Headers $h -ContentType 'application/json; charset=utf-8' -Body $bytes
}

function Step($name, [scriptblock]$body) {
    Write-Host "`n=== $name ===" -ForegroundColor Cyan
    try {
        & $body
        $script:pass++
        Write-Host "  [PASS]" -ForegroundColor Green
    } catch {
        $script:fail++
        Write-Host "  [FAIL] $($_.Exception.Message)" -ForegroundColor Red
    }
}

# ---------------------------------------------------------------- 登录
$token = $null
$headers = $null
$adminHeaders = $null
Step "1. 登录（学生 + 管理员）" {
    $r = PostJson "$Base/api/auth/login" @{ username = $User; password = $Password }
    if ($r.code -ne 0) { throw "登录失败：$($r.message)" }
    $script:token = $r.data.token
    $script:headers = @{ Authorization = "Bearer $($r.data.token)" }
    Write-Host "  学生：$($r.data.userInfo.nickname) 角色=$($r.data.userInfo.role) 积分=$($r.data.userInfo.balance)"

    $admin = PostJson "$Base/api/auth/login" @{ username = "admin"; password = "123456" }
    if ($admin.code -ne 0) { throw "管理员登录失败：$($admin.message)" }
    $script:adminHeaders = @{ Authorization = "Bearer $($admin.data.token)" }
    Write-Host "  管理员：$($admin.data.userInfo.nickname) 角色=$($admin.data.userInfo.role)"

    # 脚本里的下单/退款用例会真实花掉积分，跑第二遍就可能余额不足。
    # 这里用运营手动调整接口补足（走流水 + 幂等键），保证脚本可重复执行。
    $need = 1000
    $balance = [int]$r.data.userInfo.balance
    if ($balance -lt $need) {
        $adj = PostJson "$Base/api/admin/point/adjust" @{
            userId      = $r.data.userInfo.id
            changePoint = ($need - $balance)
            bizNo       = "e2e-topup-" + (Get-Date -Format 'yyyyMMddHHmmssfff')
            reason      = "端到端验证前置充值"
        } $script:adminHeaders
        if ($adj.code -ne 0) { throw "运营积分调整失败：$($adj.message)" }
        Write-Host "  积分不足（$balance），已用运营调整接口补到 $($adj.data.balance)（bizNo=$($adj.data.bizNo)）"
    }
}

# ---------------------------------------------------------------- 签到
Step "2. 每日签到（Redis BitMap + Kafka 事件）" {
    $r = Invoke-RestMethod -Method Post -Uri "$Base/api/signin/do?source=APP" -Headers $headers
    if ($r.code -ne 0) {
        # 今天已签到属于正常情况（脚本可重复执行）
        Write-Host "  $($r.message)（若为重复签到属正常）"
    } else {
        Write-Host "  签到日期=$($r.data.signDate) 连续=$($r.data.continuousDays) 奖励=$($r.data.pointAward) 本月=$($r.data.monthCount)"
    }
}

Step "3. 签到日历（直接读 BitMap）" {
    $r = Invoke-RestMethod -Uri "$Base/api/signin/calendar" -Headers $headers
    if ($r.code -ne 0) { throw $r.message }
    # 注意：PowerShell 对"只匹配一项"的结果会退化成标量，.Count 取不到值，必须用 @() 强制成数组
    $signed = @($r.data.days | Where-Object { $_.signed }).Count
    Write-Host "  月份=$($r.data.month) 已签=$signed 天 连续=$($r.data.continuousDays) 今天已签=$($r.data.todaySigned)"
}

# ---------------------------------------------------------------- 积分
Step "4. 积分账户" {
    $r = Invoke-RestMethod -Uri "$Base/api/point/account" -Headers $headers
    if ($r.code -ne 0) { throw $r.message }
    Write-Host "  余额=$($r.data.balance) 累计获得=$($r.data.totalEarned) 等级=Lv$($r.data.growthLevel) 距下一级=$($r.data.nextLevelPoint)"
}

Step "5. 积分流水（签到事件消费后会出现 SIGNIN 记录）" {
    $r = Invoke-RestMethod -Uri "$Base/api/point/records?page=1&size=5" -Headers $headers
    if ($r.code -ne 0) { throw $r.message }
    if ($r.data.total -eq 0) {
        Write-Host "  暂无流水：说明 Kafka 未启动，事件还在 mq_event_outbox 等待补偿（这是设计预期）"
    } else {
        $r.data.records | ForEach-Object { Write-Host "  $($_.bizType) $($_.changePoint) -> 余额 $($_.balanceAfter)  $($_.remark)" }
    }
}

Step "6. 积分排行榜" {
    $r = Invoke-RestMethod -Uri "$Base/api/point/rank?type=TOTAL&limit=5" -Headers $headers
    if ($r.code -ne 0) { throw $r.message }
    if ($r.data.Count -eq 0) { Write-Host "  榜单为空（还没有积分入账）" }
    $r.data | ForEach-Object { Write-Host "  #$($_.rank) $($_.nickname) $($_.point)" }
}

# ---------------------------------------------------------------- 任务
Step "7. 任务列表与进度上报" {
    $r = Invoke-RestMethod -Uri "$Base/api/task/list" -Headers $headers
    if ($r.code -ne 0) { throw $r.message }
    $r.data | Select-Object -First 3 | ForEach-Object {
        Write-Host "  $($_.taskName) 进度 $($_.progress)/$($_.targetValue) 状态=$($_.status) 奖励=$($_.pointAward)"
    }
    $r2 = Invoke-RestMethod -Method Post -Uri "$Base/api/task/progress/report?taskCode=DAILY_BROWSE&delta=1" -Headers $headers
    Write-Host "  上报后进度=$($r2.data.progress)/$($r2.data.targetValue)"
}

# ---------------------------------------------------------------- 领券
Step "8. 领取优惠券（分布式锁 + Redis 预扣 + DB 条件更新）" {
    $r = PostJson "$Base/api/coupon/receive" @{ templateId = 2 }
    if ($r.code -ne 0) { Write-Host "  $($r.message)" } else {
        Write-Host "  领到：$($r.data.couponTitle) [$($r.data.valueDesc)] 有效期至 $($r.data.expireTime)"
    }
}

Step "9. 锁顺序对比实验（复现锁在事务内导致的超发）" {
    $r = Invoke-RestMethod -Method Post -Uri "$Base/api/admin/coupon/demo/lock-order?stock=10&threads=50" -Headers $adminHeaders
    if ($r.code -ne 0) { throw $r.message }
    Write-Host "  锁在事务内 : 成功 $($r.data.wrongOrder.successCount) 次，超发 $($r.data.wrongOrder.oversold) 张"
    Write-Host "  锁包住事务 : 成功 $($r.data.rightOrder.successCount) 次，超发 $($r.data.rightOrder.oversold) 张"
    Write-Host "  $($r.data.conclusion)"
}

# ---------------------------------------------------------------- 兑换码
Step "10. 兑换码：生成 + 核销（Base32 分段编码 + BitMap 防重复）" {
    $codes = Invoke-RestMethod -Uri "$Base/api/admin/redeem/batch/RCB2026090100001/codes?count=3" -Headers $adminHeaders
    if ($codes.code -ne 0) { throw $codes.message }
    Write-Host "  生成的码：$($codes.data -join ', ')"
    $code = $codes.data[0]
    $r = Invoke-RestMethod -Method Post -Uri "$Base/api/redeem/exchange?code=$code" -Headers $headers
    Write-Host "  首次兑换：$($r.message)（余额 $($r.data.balance)）"
    # 接口有 3 QPS 的令牌桶限流，稍等一下再测重复兑换，避免测到限流而非重复校验
    Start-Sleep -Milliseconds 600
    $again = Invoke-RestMethod -Method Post -Uri "$Base/api/redeem/exchange?code=$code" -Headers $headers
    Write-Host "  重复兑换：$($again.message)"
    if ($again.code -eq 0) { throw "重复兑换没有被拦截！" }
    if ($again.code -ne 7002) { throw "重复兑换返回了非预期错误码 $($again.code)（期望 7002 兑换码已使用）" }
}

# ---------------------------------------------------------------- 结算与下单
Step "11. 结算页最优优惠组合（CompletableFuture 并行算价）" {
    $r = Invoke-RestMethod -Uri "$Base/api/order/settle/preview?goodsId=1&quantity=1" -Headers $headers
    if ($r.code -ne 0) { throw $r.message }
    Write-Host "  商品：$($r.data.goodsTitle) 单价=$($r.data.unitPoint) 总额=$($r.data.goodsTotal)"
    Write-Host "  券候选数=$($r.data.couponCount) 组合数=$($r.data.combinationCount) 算价耗时=$($r.data.totalCostMs)ms"
    if ($r.data.bestPlan) {
        Write-Host "  最优方案：$($r.data.bestPlan.desc) 优惠=$($r.data.bestPlan.discountPoint) 实付=$($r.data.bestPlan.payPoint)"
    }
    if ($r.data.candidates) {
        $r.data.candidates | Select-Object -First 3 | ForEach-Object {
            Write-Host "    候选：$($_.desc) 实付=$($_.payPoint)"
        }
    }
}

Step "12. 下单 + 支付" {
    # 用 300 积分的商品，才能看出优惠券抵扣的效果
    $preview = Invoke-RestMethod -Uri "$Base/api/order/settle/preview?goodsId=1&quantity=1" -Headers $headers
    $couponIds = @()
    if ($preview.data.bestPlan -and $preview.data.bestPlan.couponIds) { $couponIds = $preview.data.bestPlan.couponIds }
    $created = PostJson "$Base/api/order/create" @{
        goodsId         = 1
        quantity        = 1
        couponIds       = $couponIds
        receiverName    = "林清和"
        receiverPhone   = "13900000001"
        receiverAddress = "示范大学 3 号宿舍楼 501"
        remark          = "端到端验证脚本下单"
    }
    if ($created.code -ne 0) { throw "下单失败：$($created.message)" }
    Write-Host "  下单成功 orderNo=$($created.data.orderNo) 优惠=$($created.data.discountPoint) 实付=$($created.data.payPoint)"

    $paid = PostJson "$Base/api/order/pay" @{ orderNo = $created.data.orderNo }
    if ($paid.code -ne 0) { throw "支付失败：$($paid.message)" }
    Write-Host "  支付成功 状态=$($paid.data.statusDesc)"

    $summary = Invoke-RestMethod -Uri "$Base/api/ai/order-summary?orderNo=$($created.data.orderNo)" -Headers $headers
    Write-Host "  订单文案：$($summary.data.summary)"
}

# ---------------------------------------------------------------- 缓存
Step "13. 二级缓存命中率（Caffeine L1 + Redis L2）" {
    1..20 | ForEach-Object { Invoke-RestMethod -Uri "$Base/api/benefit/goods/1" -Headers $headers | Out-Null }
    $r = Invoke-RestMethod -Uri "$Base/api/admin/cache/stats" -Headers $adminHeaders
    if ($r.code -ne 0) { throw $r.message }
    $s = $r.data.stats
    Write-Host "  L1命中=$($s.l1Hit) L2命中=$($s.l2Hit) 回源DB=$($s.dbLoad) 命中率=$($s.hitRate)% 本地缓存条目=$($r.data.localCacheSize)"
}

# ---------------------------------------------------------------- AI
Step "14. AI 助手（Function Calling）" {
    $r = PostJson "$Base/api/ai/chat" @{ message = "帮我看看有什么可以兑换的" }
    if ($r.code -ne 0) { throw $r.message }
    Write-Host "  模型=$($r.data.model) 真实模型=$($r.data.realModel) 耗时=$($r.data.costMs)ms"
    Write-Host "  回复：$($r.data.reply)"
    $r.data.toolCalls | ForEach-Object { Write-Host "  工具调用：$($_.label)($($_.args)) 成功=$($_.success) 耗时=$($_.costMs)ms" }
}

# ---------------------------------------------------------------- 管理端
Step "15. 管理端仪表盘" {
    $r = Invoke-RestMethod -Uri "$Base/api/admin/dashboard/overview" -Headers $adminHeaders
    if ($r.code -ne 0) { throw "管理端仪表盘失败：$($r.message)" }
    Write-Host "  学生数=$($r.data.userCount) 今日签到=$($r.data.todaySignCount) 订单=$($r.data.orderCount) 今日订单=$($r.data.todayOrderCount)"
    Write-Host "  消耗积分=$($r.data.pointUsed) 已发券=$($r.data.couponIssued) 在售商品=$($r.data.goodsCount) 待审售后=$($r.data.refundPending)"
}

# ---------------------------------------------------------------- 安全与闭环回归（本轮修复项）
Step "16. 权限校验：学生 token 调管理端接口应被拒绝" {
    $blocked = Invoke-RestMethod -Uri "$Base/api/admin/dashboard/overview" -Headers $headers
    if ($blocked.code -ne 403) { throw "学生访问管理端接口未被拦截！返回 code=$($blocked.code) $($blocked.message)" }
    Write-Host "  学生访问 /api/admin/** → code=$($blocked.code)（$($blocked.message)）"

    $allowed = Invoke-RestMethod -Uri "$Base/api/admin/dashboard/overview" -Headers $adminHeaders
    if ($allowed.code -ne 0) { throw "管理员访问管理端接口失败：$($allowed.message)" }
    Write-Host "  管理员访问 /api/admin/** → code=0（正常放行）"
}

Step "17. 签到自动驱动「每日签到」任务进度" {
    # 用一个今天还没签到的账号，才能真正验证"签到 → 任务进度"的联动
    $fresh = PostJson "$Base/api/auth/login" @{ username = "student05"; password = "123456" }
    if ($fresh.code -ne 0) { throw "student05 登录失败：$($fresh.message)" }
    $freshHeaders = @{ Authorization = "Bearer $($fresh.data.token)" }

    $sign = Invoke-RestMethod -Method Post -Uri "$Base/api/signin/do?source=APP" -Headers $freshHeaders
    if ($sign.code -eq 0) {
        Write-Host "  student05 签到成功：连续 $($sign.data.continuousDays) 天，奖励 $($sign.data.pointAward) 积分"
    } else {
        Write-Host "  student05 今日已签到（$($sign.message)），直接校验任务进度"
    }

    $tasks = Invoke-RestMethod -Uri "$Base/api/task/list" -Headers $freshHeaders
    if ($tasks.code -ne 0) { throw $tasks.message }
    $signTask = $tasks.data | Where-Object { $_.taskCode -eq 'DAILY_SIGN_IN' }
    if (-not $signTask) { throw "未找到 DAILY_SIGN_IN 任务定义" }
    Write-Host "  每日签到任务：进度 $($signTask.progress)/$($signTask.targetValue) 状态=$($signTask.status)"
    if ($signTask.progress -lt 1) { throw "签到后任务进度仍为 0，联动未生效" }
    Write-Host "  已由签到自动推进（无需前端上报）"
}

Step "18. 优惠券过期处理" {
    $r = Invoke-RestMethod -Method Post -Uri "$Base/api/admin/coupon/expire-now" -Headers $adminHeaders
    if ($r.code -ne 0) { throw $r.message }
    Write-Host "  本次置为 EXPIRED 的券：$($r.data.expiredCount) 张"
    $expired = Invoke-RestMethod -Uri "$Base/api/coupon/mine?status=EXPIRED&page=1&size=5" -Headers $headers
    Write-Host "  我的过期券查询 → total=$($expired.data.total)"
}

Step "19. 订单闭环：创建 → 支付 → 确认完成" {
    $preview = Invoke-RestMethod -Uri "$Base/api/order/settle/preview?goodsId=3&quantity=1" -Headers $headers
    $created = PostJson "$Base/api/order/create" @{
        goodsId = 3; quantity = 1
        receiverName = "林清和"; receiverPhone = "13900000001"
        receiverAddress = "示范大学 3 号楼 501"; remark = "闭环回归"
    }
    if ($created.code -ne 0) { throw "下单失败：$($created.message)" }
    $paid = PostJson "$Base/api/order/pay" @{ orderNo = $created.data.orderNo }
    if ($paid.code -ne 0) { throw "支付失败：$($paid.message)" }
    $finished = PostJson "$Base/api/order/finish" @{ orderNo = $created.data.orderNo }
    if ($finished.code -ne 0) { throw "确认完成失败：$($finished.message)" }
    $detail = Invoke-RestMethod -Uri "$Base/api/order/$($created.data.orderNo)" -Headers $headers
    Write-Host "  orderNo=$($created.data.orderNo) 最终状态=$($detail.data.statusDesc)"
    if ($detail.data.status -ne 'FINISHED') { throw "订单未进入 FINISHED，实际=$($detail.data.status)" }
}

Step "20. 管理端订单详情 + 运营核销" {
    # 单独建一笔订单来验证核销，避免复用到上一步已经 FINISHED 的订单
    $newOrder = PostJson "$Base/api/order/create" @{
        goodsId = 4; quantity = 1
        receiverName = "林清和"; receiverPhone = "13900000001"
        receiverAddress = "示范大学 3 号楼 501"; remark = "管理端核销回归"
    }
    if ($newOrder.code -ne 0) { throw "下单失败：$($newOrder.message)" }
    $paidOrder = PostJson "$Base/api/order/pay" @{ orderNo = $newOrder.data.orderNo }
    if ($paidOrder.code -ne 0) { throw "支付失败：$($paidOrder.message)" }

    $detail = Invoke-RestMethod -Uri "$Base/api/admin/order/$($newOrder.data.orderNo)" -Headers $adminHeaders
    if ($detail.code -ne 0) { throw "管理端详情失败：$($detail.message)" }
    Write-Host "  管理端详情：订单 $($detail.data.orderNo) 用户=$($detail.data.nickname) 状态=$($detail.data.statusDesc)"

    $adminFinish = Invoke-RestMethod -Method Post -Uri "$Base/api/admin/order/finish" -Headers $adminHeaders `
        -ContentType 'application/json; charset=utf-8' `
        -Body ([System.Text.Encoding]::UTF8.GetBytes((@{ orderNo = $newOrder.data.orderNo } | ConvertTo-Json)))
    if ($adminFinish.code -ne 0) { throw "运营核销失败：$($adminFinish.message)" }
    $after = Invoke-RestMethod -Uri "$Base/api/admin/order/$($newOrder.data.orderNo)" -Headers $adminHeaders
    Write-Host "  运营核销成功 → 状态=$($after.data.statusDesc)"
    if ($after.data.status -ne 'FINISHED') { throw "核销后状态不是 FINISHED，实际=$($after.data.status)" }
}

Step "21. 排行榜重建（账户与榜单一致性）" {
    $before = Invoke-RestMethod -Uri "$Base/api/point/rank?type=TOTAL&limit=3" -Headers $headers
    Write-Host "  重建前榜单人数=$($before.data.Count)"
    $rebuild = Invoke-RestMethod -Method Post -Uri "$Base/api/admin/point/rank/rebuild" -Headers $adminHeaders
    if ($rebuild.code -ne 0) { throw $rebuild.message }
    Write-Host "  重建完成：总榜 $($rebuild.data.totalRankSize) 人，月榜 $($rebuild.data.monthRankSize) 人"
    $after = Invoke-RestMethod -Uri "$Base/api/point/rank?type=TOTAL&limit=3" -Headers $headers
    $after.data | ForEach-Object { Write-Host "  #$($_.rank) $($_.nickname) $($_.point)" }
    if ($rebuild.data.totalRankSize -lt 1) { throw "重建后总榜仍为空" }
}

Step "22. 个人资料保存（联动「完善个人资料」一次性任务）" {
    $profile = Invoke-RestMethod -Method Put -Uri "$Base/api/auth/profile" -Headers $headers `
        -ContentType 'application/json; charset=utf-8' `
        -Body ([System.Text.Encoding]::UTF8.GetBytes((@{
                    nickname = "林清和"; phone = "13900000001"
                    studentNo = "2023010101"; school = "示范大学"
                } | ConvertTo-Json)))
    if ($profile.code -ne 0) { throw "保存资料失败：$($profile.message)" }
    Write-Host "  保存成功：$($profile.data.nickname) / $($profile.data.school) / $($profile.data.studentNo)"
    $tasks = Invoke-RestMethod -Uri "$Base/api/task/list" -Headers $headers
    $profileTask = $tasks.data | Where-Object { $_.taskCode -eq 'ONCE_PROFILE' }
    Write-Host "  完善资料任务：进度 $($profileTask.progress)/$($profileTask.targetValue) 状态=$($profileTask.status)"
}

# ---------------------------------------------------------------- 汇总
Write-Host "`n============================================" -ForegroundColor Yellow
Write-Host "  端到端验证完成：PASS=$pass FAIL=$fail" -ForegroundColor Yellow
Write-Host "============================================" -ForegroundColor Yellow
if ($fail -gt 0) { exit 1 }
