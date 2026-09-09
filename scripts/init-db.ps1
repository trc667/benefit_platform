# 初始化数据库：建库 + 建表 + 导入演示数据
#
# 用法：.\scripts\init-db.ps1 -Password 你的MySQL密码
# 说明：mysql 客户端路径自动探测（参数 → 常见安装目录 → PATH），不写死个人机器路径。
#       脚本只做"执行 SQL 文件"，不会修改任何 MySQL 服务配置。
#       重复执行是安全的（schema.sql 里每张表都是 DROP IF EXISTS 后重建）。

param(
    [string]$User = "root",
    [Parameter(Mandatory = $true)][string]$Password,
    [string]$Host = "127.0.0.1",
    [int]$Port = 3306,
    [string]$MysqlBin = ""
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot "_common.ps1")
$sqlDir = Join-Path $root "sql"

$MysqlBin = Resolve-MysqlBin -Explicit $MysqlBin
Write-Host "使用 mysql 客户端：$MysqlBin" -ForegroundColor DarkGray

Write-Host "执行 schema.sql ..." -ForegroundColor Cyan
& $MysqlBin "-h$Host" "-P$Port" "-u$User" "-p$Password" -e "source $($sqlDir -replace '\\','/')/schema.sql"
if ($LASTEXITCODE -ne 0) { throw "建表失败" }

Write-Host "执行 data.sql ..." -ForegroundColor Cyan
& $MysqlBin "-h$Host" "-P$Port" "-u$User" "-p$Password" -e "source $($sqlDir -replace '\\','/')/data.sql"
if ($LASTEXITCODE -ne 0) { throw "导入演示数据失败" }

Write-Host "`n数据库初始化完成：campus_growth" -ForegroundColor Green
Write-Host "演示账号：admin / operator / student01 ~ student06，密码均为 123456"
