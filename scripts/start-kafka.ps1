# 启动 Kafka 3.7.1（KRaft 单节点，无 ZooKeeper、无 Docker、无 WSL）
#
# 前置：把 Kafka 解压到 .tools\kafka_2.13-3.7.1\（或任意位置，用 -KafkaHome 指定）
#       国内可用下载源（实测：阿里云 403、清华/USTC/南大只保留最新版，只有华为云长期留档）：
#       https://mirrors.huaweicloud.com/apache/kafka/3.7.1/kafka_2.13-3.7.1.tgz   (114 MB)
#       官方归档：https://archive.apache.org/dist/kafka/3.7.1/kafka_2.13-3.7.1.tgz
#       Windows 10+ 自带 tar：tar -xzf kafka_2.13-3.7.1.tgz -C .tools
#
# 用法：.\scripts\start-kafka.ps1
#
# 两个踩过的坑，这里都绕开了：
#   1) 首次运行自动 format 存储目录（随机 cluster id），之后直接启动。
#   2) 不走 bin\windows\*.bat。Kafka 3.7 的 libs 下有 119 个 jar，批处理把完整
#      类路径拼进 java 命令行，长度超过 cmd.exe 的 8191 字符上限，会直接报
#      "The input line is too long."。这里改成 java -cp "libs\*" 启动，
#      通配符由 JVM 自己展开，路径再长也不会爆。
#   3) 主题不用手动建：后端启动时 KafkaConfig 里的 NewTopic Bean 会自动创建。

param(
    [string]$KafkaHome = "",
    [string]$JavaHome = "",
    [int]$HeapMb = 1024,
    [switch]$Foreground
)

$ErrorActionPreference = "Continue"
$root = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot "_common.ps1")

if ([string]::IsNullOrWhiteSpace($KafkaHome)) {
    $candidate = Get-ChildItem (Join-Path $root ".tools") -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like "kafka_*" } | Select-Object -First 1
    if ($candidate) { $KafkaHome = $candidate.FullName }
}
if ([string]::IsNullOrWhiteSpace($KafkaHome) -or -not (Test-Path $KafkaHome)) {
    throw @"
找不到 Kafka 安装目录。
请下载并解压到 .tools\ 下（目录名形如 kafka_2.13-3.7.1），或用 -KafkaHome 指定：
  https://mirrors.huaweicloud.com/apache/kafka/3.7.1/kafka_2.13-3.7.1.tgz
"@
}

$JavaHome = Resolve-JavaHome -Explicit $JavaHome
$java = Join-Path $JavaHome "bin\java.exe"

$config = Join-Path $root ".tools\kafka\server.properties"
$dataDir = Join-Path $root ".tools\kafka-data"
$logDir = Join-Path $root ".tools\kafka-logs"
$libs = Join-Path $KafkaHome "libs\*"
$serverLog4j = (Join-Path $KafkaHome "config\log4j.properties") -replace '\\', '/'
$toolsLog4j = (Join-Path $KafkaHome "config\tools-log4j.properties") -replace '\\', '/'

if (-not (Test-Path $config)) { throw "找不到 broker 配置：$config" }

if (Test-PortOpen -Port 9092) {
    Write-Host "Kafka 9092 已在监听，无需重复启动。" -ForegroundColor Yellow
    exit 0
}

New-Item -ItemType Directory -Force -Path $dataDir, $logDir | Out-Null
$common = @(
    "-Dlog4j.configuration=file:$toolsLog4j",
    "-Dkafka.logs.dir=$logDir",
    "-cp", $libs, "kafka.tools.StorageTool"
)

if (-not (Test-Path (Join-Path $dataDir "meta.properties"))) {
    Write-Host "首次启动：格式化 KRaft 存储目录 ..." -ForegroundColor Cyan
    $uuid = (& $java @common random-uuid 2>> (Join-Path $logDir "storage.err.log") | Where-Object { $_ -match '^[A-Za-z0-9_\-]{20,}$' } | Select-Object -Last 1)
    if (-not $uuid) { throw "获取 cluster id 失败，请手动执行：java -cp `"$libs`" kafka.tools.StorageTool random-uuid" }
    $uuid = $uuid.Trim()
    Write-Host "  cluster id = $uuid"
    & $java @common format -t $uuid -c $config | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "Kafka 存储目录格式化失败" }
}

Write-Host "启动 Kafka (KRaft, ${HeapMb}MB heap) ..." -ForegroundColor Cyan
$outLog = Join-Path $logDir "server.out.log"
$errLog = Join-Path $logDir "server.err.log"
$serverArgs = @(
    "-Xms${HeapMb}m", "-Xmx${HeapMb}m", "-XX:+UseG1GC",
    "-Dlog4j.configuration=file:$serverLog4j",
    "-Dkafka.logs.dir=$logDir",
    "-cp", $libs,
    "kafka.Kafka", $config
)
# 启动方式：
#   默认          -> 拉起一个独立进程后立即返回，适合本地手动启动。
#   -Foreground   -> 前台阻塞运行，适合交给任务调度/CI/IDE 托管（进程随调用者生命周期）。
if ($Foreground) {
    Write-Host "启动 Kafka (前台模式, KRaft, ${HeapMb}MB heap)，Ctrl+C 停止 ..." -ForegroundColor Cyan
    & $java @serverArgs
    exit $LASTEXITCODE
}

# 用 Win32_Process.Create 拉起一个完全脱离当前终端的进程。
# 为什么不用 Start-Process：它会继承调用者的 stdout 句柄，脚本被管道或自动化脚本
# 调用时，即使脚本已经退出，管道也不会关闭，外层看起来像"卡住不返回"。
$inner = '"{0}" -Xms{1}m -Xmx{1}m -XX:+UseG1GC -Dlog4j.configuration=file:{2} -Dkafka.logs.dir={3} -cp "{4}" kafka.Kafka "{5}" > "{6}" 2>&1' -f `
    $java, $HeapMb, $serverLog4j, $logDir, $libs, $config, $outLog
try {
    $res = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{ CommandLine = "cmd.exe /c $inner" } -ErrorAction Stop
    if ($res.ReturnValue -ne 0) { throw "Win32_Process.Create 返回 $($res.ReturnValue)" }
} catch {
    # CIM 被安全策略禁用时回退（交互式终端下 Start-Process 同样能正常工作）
    Start-Process -FilePath $java -ArgumentList $serverArgs -WindowStyle Hidden `
        -RedirectStandardOutput $outLog -RedirectStandardError $errLog | Out-Null
}

Write-Host "等待 broker 就绪（最多 60 秒）..."
for ($i = 1; $i -le 30; $i++) {
    Start-Sleep -Seconds 2
    if (Test-PortOpen -Port 9092) {
        Write-Host "Kafka 已就绪：127.0.0.1:9092" -ForegroundColor Green
        exit 0
    }
}
Write-Warning "60 秒内未检测到 9092，下面是日志尾部："
if (Test-Path $errLog) { Get-Content $errLog -Tail 20 }
if (Test-Path $outLog) { Get-Content $outLog -Tail 20 }
exit 1
