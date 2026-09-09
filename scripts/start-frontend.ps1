# 启动前端开发服务器
#
# 用法：.\scripts\start-frontend.ps1
# 说明：vite dev 端口 5173，/api 已代理到 http://127.0.0.1:8090
#       依赖安装走 npmmirror 镜像（国内快）

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$fe = Join-Path $root "frontend"

Push-Location $fe
try {
    if (-not (Test-Path "node_modules")) {
        Write-Host "安装前端依赖 ..." -ForegroundColor Cyan
        & npm install --registry=https://registry.npmmirror.com
        if ($LASTEXITCODE -ne 0) { throw "npm install 失败" }
    }
    Write-Host "启动前端：http://127.0.0.1:5173" -ForegroundColor Green
    & npm run dev
} finally {
    Pop-Location
}
