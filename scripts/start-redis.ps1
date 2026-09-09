# 启动本地 Redis（使用你机器上已有的 Redis for Windows）
#
# 用法：.\scripts\start-redis.ps1 [-RedisHome <Redis目录>] [-Port 6379]
# 说明：Redis 目录自动探测（参数 → REDIS_HOME 环境变量 → 常见安装目录 → PATH）；
#       数据目录落在项目内 .tools/redis-data，不会污染系统目录；
#       如果端口已在监听，脚本会直接提示并退出。

param(
    [string]$RedisHome = "",
    [int]$Port = 6379
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot "_common.ps1")

if (Test-PortOpen -Port $Port) {
    Write-Host "Redis $Port 已在监听，无需重复启动。" -ForegroundColor Yellow
    exit 0
}

$RedisHome = Resolve-RedisHome -Explicit $RedisHome
$exe = Join-Path $RedisHome "redis-server.exe"
New-Item -ItemType Directory -Force -Path (Join-Path $root ".tools\redis-data") | Out-Null

Write-Host "使用 Redis 目录：$RedisHome" -ForegroundColor DarkGray
Write-Host "启动 Redis :$Port ..." -ForegroundColor Cyan
Start-Process -FilePath $exe -ArgumentList (Join-Path $root ".tools\redis\redis-6379.conf") -WindowStyle Minimized

for ($i = 1; $i -le 15; $i++) {
    Start-Sleep -Seconds 1
    if (Test-PortOpen -Port $Port) {
        Write-Host "Redis 已就绪：127.0.0.1:$Port" -ForegroundColor Green
        exit 0
    }
}
Write-Warning "15 秒内未检测到 $Port，请检查 Redis 启动窗口的日志"
exit 1
