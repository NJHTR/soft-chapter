# RTC-013 bounded-start validator:隔离启动 coturn 服务,确认存活且日志无配置错误,再有界停止。
# coturn 4.17.2 没有 dry-run/--check-config,因此必须真实启动验证。
# 用法:
#   powershell -File deploy/rtc/validate-coturn-startup.ps1 -ComposeFile docker-compose.rtc-cluster.yml -Service turn-region-a
# 参数: -EnvFile 默认 deploy/rtc/cluster/.env; -LogTail 日志检查行数; -TimeoutSec 有界等待。
param(
    [string]$ComposeFile = "docker-compose.rtc-cluster.yml",
    [string]$Service = "turn-region-a",
    [string]$EnvFile = "",
    [int]$LogTail = 120,
    [int]$TimeoutSec = 90
)

$ErrorActionPreference = "Continue"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$compose = Join-Path $root $ComposeFile
if (-not (Test-Path $compose)) { Write-Error "compose 文件不存在: $compose"; exit 2 }
if ($EnvFile -eq "") { $EnvFile = Join-Path $root "deploy\rtc\cluster\.env" }
if (-not (Test-Path $EnvFile)) { Write-Error "env 文件不存在: $EnvFile"; exit 2 }

$docker = Get-Command docker -ErrorAction SilentlyContinue
if (-not $docker) { Write-Error "未找到 docker"; exit 2 }

Write-Host "[validate-coturn] 校验 $Service (compose=$ComposeFile)"
Set-Location $root

Write-Host "[validate-coturn] 启动前先停止同名服务,确保干净环境"
& docker compose -f $compose --env-file $EnvFile stop $Service 2>$null | Out-Null

Write-Host "[validate-coturn] 启动 $Service ..."
& docker compose -f $compose --env-file $EnvFile up -d $Service 2>&1 | Out-String | Write-Host
if ($LASTEXITCODE -ne 0) { Write-Error "启动命令失败"; exit 1 }

$deadline = (Get-Date).AddSeconds($TimeoutSec)
$healthy = $false
$logText = ""
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 2
    try {
        $state = (docker inspect --format "{{.State.Health.Status}}" "douyin-$Service" 2>$null)
    } catch { $state = "" }
    $logText = (docker compose -f $compose --env-file $EnvFile logs --tail $LogTail $Service 2>$null | Out-String)
    if ($logText -match "ERROR|fatal|failed to|configuration error|invalid config") {
        Write-Host "[validate-coturn] 日志出现配置错误模式:"
        $logText -split "`n" | Select-Object -Last 12 | ForEach-Object { Write-Host "  $_" }
        & docker compose -f $compose --env-file $EnvFile stop $Service 2>$null | Out-Null
        exit 1
    }
    if ($state -eq "healthy") { $healthy = $true; break }
    if ($state -ne "starting" -and $state -ne "healthy") {
        $logText = (docker compose -f $compose --env-file $EnvFile logs --tail $LogTail $Service 2>$null | Out-String)
        if ($logText -match "use-auth-secret|listening|Relay|TURN Server") { $healthy = $true; break }
    }
}
if (-not $healthy -and -not $logText) {
    $logText = (docker compose -f $compose --env-file $EnvFile logs --tail $LogTail $Service 2>$null | Out-String)
}

$ok = $false
if ($logText -match "use-auth-secret|listening-port|TURN Server" -and $logText -notmatch "ERROR|fatal|failed to|invalid config") {
    $ok = $true
}
if ($ok) {
    Write-Host "[validate-coturn] OK: $Service 存活且日志无配置错误(样例):"
    $logText -split "`n" | Select-Object -Last 10 | ForEach-Object { Write-Host "  $_" }
} else {
    Write-Host "[validate-coturn] FAIL: 未观察到健康启动输出:"
    $logText -split "`n" | Select-Object -Last 20 | ForEach-Object { Write-Host "  $_" }
}

Write-Host "[validate-coturn] 有界停止 $Service"
& docker compose -f $compose --env-file $EnvFile stop $Service 2>$null | Out-Null

if ($ok) { exit 0 } else { exit 1 }