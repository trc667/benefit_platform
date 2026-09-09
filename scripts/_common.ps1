# =====================================================================
# 脚本公共函数：自动探测本机 JDK / MySQL / Redis 位置
#
# 被 start-backend.ps1、start-kafka.ps1、build.ps1、init-db.ps1、start-redis.ps1
# 以 dot-source 方式引入：  . (Join-Path $PSScriptRoot "_common.ps1")
#
# 设计原则：
#   1. 显式参数 > 环境变量 > 常见安装目录自动搜索 > PATH；
#   2. 找不到时给出"去哪装 / 怎么指定"的明确报错，而不是抛一个看不懂的异常；
#   3. 不写死任何个人机器的路径，保证 clone 下来就能跑。
# =====================================================================

# ---------------------------------------------------------------- JDK
function Get-JavaMajorVersion {
    param([Parameter(Mandatory = $true)][string]$JavaHome)
    $release = Join-Path $JavaHome 'release'
    if (Test-Path $release) {
        $m = [regex]::Match((Get-Content $release -Raw -ErrorAction SilentlyContinue), 'JAVA_VERSION="(\d+)')
        if ($m.Success) { return [int]$m.Groups[1].Value }
    }
    return 0
}

function Test-JavaHome {
    param([string]$JavaHome)
    return ($JavaHome -and (Test-Path (Join-Path $JavaHome 'bin\java.exe')))
}

<#
.SYNOPSIS
    解析可用的 JDK 17+ 目录。
.DESCRIPTION
    顺序：-Explicit 参数 → $env:JAVA_HOME → 常见 JDK 安装目录（取版本最高者）。
#>
function Resolve-JavaHome {
    param([string]$Explicit)

    if (Test-JavaHome $Explicit) {
        $major = Get-JavaMajorVersion $Explicit
        if ($major -ge 17) { return $Explicit }
        throw "指定的 JDK 是 $major，SpringBoot3 需要 17+：$Explicit"
    }

    $candidates = @()
    if (Test-JavaHome $env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    foreach ($root in @("$env:USERPROFILE\.jdks", "C:\Program Files\Java", "C:\Program Files\Eclipse Adoptium",
            "C:\Program Files\Microsoft", "C:\Program Files\Amazon Corretto", "C:\Program Files\Zulu")) {
        if (Test-Path $root) {
            $candidates += (Get-ChildItem $root -Directory -ErrorAction SilentlyContinue |
                    Where-Object { Test-JavaHome $_.FullName } | Select-Object -ExpandProperty FullName)
        }
    }

    $best = $null
    $bestMajor = 0
    foreach ($c in $candidates) {
        $major = Get-JavaMajorVersion $c
        if ($major -lt 17) { continue }
        # 优先用 17（pom 里声明的版本，行为最可预期），没有再取版本最高的
        if ($major -eq 17) { return $c }
        if ($major -gt $bestMajor) {
            $best = $c
            $bestMajor = $major
        }
    }
    if ($best) { return $best }

    throw @"
找不到 JDK 17+。
  · 已安装：设置环境变量 JAVA_HOME 指向 JDK 目录
  · 或用参数指定：-JavaHome "C:\path\to\jdk-17"
  · 下载：https://adoptium.net/temurin/releases/?version=17
"@
}

# ---------------------------------------------------------------- MySQL 客户端
function Resolve-MysqlBin {
    param([string]$Explicit)

    if ($Explicit -and (Test-Path $Explicit)) { return $Explicit }

    $patterns = @(
        "C:\Program Files\MySQL\MySQL Server *\bin\mysql.exe",
        "C:\Program Files (x86)\MySQL\MySQL Server *\bin\mysql.exe",
        "C:\ProgramData\chocolatey\bin\mysql.exe",
        "$env:USERPROFILE\scoop\apps\mysql\current\bin\mysql.exe"
    )
    foreach ($p in $patterns) {
        $hit = Get-ChildItem $p -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($hit) { return $hit.FullName }
    }
    $cmd = Get-Command mysql.exe -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }

    throw @"
找不到 mysql 客户端（mysql.exe）。
  · 用参数指定：-MysqlBin "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
  · 或把 mysql.exe 所在目录加入 PATH
"@
}

# ---------------------------------------------------------------- Redis
<#
.SYNOPSIS
    解析 Redis for Windows 的安装目录（需包含 redis-server.exe）。
#>
function Resolve-RedisHome {
    param([string]$Explicit)

    if ($Explicit -and (Test-Path (Join-Path $Explicit 'redis-server.exe'))) { return $Explicit }
    if ($env:REDIS_HOME -and (Test-Path (Join-Path $env:REDIS_HOME 'redis-server.exe'))) { return $env:REDIS_HOME }

    $patterns = @(
        "C:\Program Files\Redis",
        "C:\Redis",
        "$env:USERPROFILE\Redis*",
        "$env:USERPROFILE\Desktop\Redis*",
        "$env:USERPROFILE\Downloads\Redis*"
    )
    foreach ($p in $patterns) {
        $hit = Get-ChildItem $p -Directory -ErrorAction SilentlyContinue |
            Where-Object { Test-Path (Join-Path $_.FullName 'redis-server.exe') } | Select-Object -First 1
        if ($hit) { return $hit.FullName }
    }

    $cmd = Get-Command redis-server.exe -ErrorAction SilentlyContinue
    if ($cmd) { return (Split-Path -Parent $cmd.Source) }

    throw @"
找不到 redis-server.exe。
  · 用参数指定：-RedisHome "C:\path\to\redis"
  · 或设置环境变量 REDIS_HOME
  · 下载（Redis for Windows）：https://github.com/tporadowski/redis/releases
"@
}

# ---------------------------------------------------------------- 端口探测
<#
.SYNOPSIS
    用 TCP 连接判断端口是否在监听。
.DESCRIPTION
    Get-NetTCPConnection 在部分 Windows 环境/权限下查不到监听端口，
    会把"已启动"误判成"没起来"（实测踩过），所以统一用 TCP 探测。
#>
function Test-PortOpen {
    param(
        [int]$Port,
        [string]$TargetHost = '127.0.0.1',
        [int]$TimeoutMs = 1500
    )
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $task = $client.ConnectAsync($TargetHost, $Port)
        if (-not $task.Wait($TimeoutMs)) { return $false }
        return $client.Connected
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}
