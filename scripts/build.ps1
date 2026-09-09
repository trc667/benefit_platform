# 项目统一构建/启动脚本（Windows PowerShell）
#
# 用法：
#   .\scripts\build.ps1            编译 + 跑单元测试 + 打 jar
#   .\scripts\build.ps1 -SkipTests 跳过测试
#
# 说明：JDK 目录自动探测（显式参数 → JAVA_HOME → 常见安装目录），
#       不写死任何个人机器路径；SpringBoot3 需要 JDK 17+，探测不到会给出明确提示。

param(
    [switch]$SkipTests,
    [string]$JavaHome = ""
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot "_common.ps1")

$env:JAVA_HOME = Resolve-JavaHome -Explicit $JavaHome
$settings = Join-Path $root ".mvn\settings.xml"
Write-Host "使用 JDK：$env:JAVA_HOME" -ForegroundColor DarkGray

# 优先用项目自带的 Maven Wrapper（backend\mvnw.cmd），没装 Maven 也能构建
$mvn = Join-Path $root "backend\mvnw.cmd"
if (-not (Test-Path $mvn)) { $mvn = "mvn" }

Write-Host "[1/2] 构建后端 campus-growth-api ..." -ForegroundColor Cyan
$args = @("-B", "-s", $settings, "-f", (Join-Path $root "backend\pom.xml"))
if ($SkipTests) { $args += "-DskipTests" }
$args += "clean", "package"
& $mvn @args
if ($LASTEXITCODE -ne 0) { throw "后端构建失败" }

Write-Host "[2/2] 构建前端 ..." -ForegroundColor Cyan
Push-Location (Join-Path $root "frontend")
try {
    if (-not (Test-Path "node_modules")) {
        Write-Host "  安装依赖（npmmirror 镜像）..."
        & npm install --registry=https://registry.npmmirror.com
    }
    & npm run build
    if ($LASTEXITCODE -ne 0) { throw "前端构建失败" }
} finally {
    Pop-Location
}

Write-Host "`n构建完成：" -ForegroundColor Green
Write-Host "  后端产物：backend\target\campus-growth-api.jar"
Write-Host "  前端产物：frontend\dist\"
