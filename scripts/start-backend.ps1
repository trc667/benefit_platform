# 启动后端（开发模式）
#
# 用法：.\scripts\start-backend.ps1 [-Profile dev|prod|rpc] [-Port 8090] [-JavaHome <JDK目录>]
# 前置：MySQL 已启动且已执行 scripts\init-db.ps1；Redis 已启动（scripts\start-redis.ps1）
# Kafka 可选：未启动时应用照常运行，事件会先落 mq_event_outbox，
#             Kafka 恢复后由补偿任务自动补发（这就是本地消息表的价值）。
#
# 数据库密码：放在 backend\src\main\resources\application-local.yml（已被 .gitignore 忽略），
#             或设置环境变量 MYSQL_PASSWORD。

param(
    [string]$Profile = "dev",
    [int]$Port = 8090,
    [string]$JavaHome = ""
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot "_common.ps1")

$env:JAVA_HOME = Resolve-JavaHome -Explicit $JavaHome
$settings = Join-Path $root ".mvn\settings.xml"

# 优先用项目自带的 Maven Wrapper（backend\mvnw.cmd），没装 Maven 也能跑
$mvn = Join-Path $root "backend\mvnw.cmd"
if (-not (Test-Path $mvn)) { $mvn = "mvn" }

Write-Host "使用 JDK：$env:JAVA_HOME" -ForegroundColor DarkGray
Write-Host "启动 campus-growth-api :$Port (profile=$Profile) ..." -ForegroundColor Cyan
Write-Host "健康检查：http://127.0.0.1:$Port/actuator/health`n"

& $mvn -B -s $settings -f (Join-Path $root "backend\pom.xml") `
    spring-boot:run "-Dspring-boot.run.profiles=$Profile" "-Dspring-boot.run.arguments=--server.port=$Port"
