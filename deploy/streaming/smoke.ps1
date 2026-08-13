# RTC-002 provider smoke test(Windows PowerShell)
# 前提:docker daemon 已启动;配置见 deploy/streaming/.env.example
# 用法:powershell -File deploy/streaming/smoke.ps1 [-ProfileName webrtc]

param(
    [string]$ProfileName = "webrtc"
)

$ErrorActionPreference = "Stop"
$compose = "docker-compose.streaming.yml"
$envFile = "deploy/streaming/.env"
$results = @()

function Check([string]$name, [scriptblock]$body) {
    try {
        & $body | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "exit code $LASTEXITCODE" }
        $script:results += "PASS $name"
        Write-Host "PASS: $name" -ForegroundColor Green
    } catch {
        $script:results += "FAIL $name : $($_.Exception.Message)"
        Write-Host "FAIL: $name : $($_.Exception.Message)" -ForegroundColor Red
    }
}

Write-Host "==> 1/6 compose 配置校验" -ForegroundColor Cyan
Check "docker compose config" { docker compose -f $compose --env-file $envFile config --quiet }

Write-Host "==> 2/6 启动服务(profile=$ProfileName)" -ForegroundColor Cyan
if ($ProfileName -eq "none") {
    docker compose -f $compose --env-file $envFile up -d srs
} else {
    docker compose -f $compose --env-file $envFile --profile $ProfileName up -d
}
Start-Sleep -Seconds 12

Write-Host "==> 3/6 健康状态" -ForegroundColor Cyan
Check "containers healthy(docker inspect)" {
    $names = if ($ProfileName -eq "none") { @("douyin-srs") } else { @("douyin-srs", "douyin-livekit", "douyin-coturn") }
    foreach ($n in $names) {
        $h = docker inspect --format "{{.State.Health.Status}}" $n 2>&1
        if ($LASTEXITCODE -ne 0 -or $h -ne "healthy") { throw "$n health=[$h]" }
    }
}
Check "srs healthy(1985 /api/v1/versions)" {
    $r = Invoke-RestMethod http://localhost:1985/api/v1/versions -TimeoutSec 5
    if ($r.code -ne 0) { throw "srs api code=$($r.code)" }
}
Check "livekit metrics(7889 /metrics)" {
    $r = Invoke-WebRequest http://localhost:7889/metrics -UseBasicParsing -TimeoutSec 5
    if ($r.StatusCode -ne 200) { throw "http $($r.StatusCode)" }
}
Check "coturn port 3478/tcp" {
    $t = Test-NetConnection localhost -Port 3478 -WarningAction SilentlyContinue
    if (-not $t.TcpTestSucceeded) { throw "3478 不可达" }
}
Check "srs HTTP-FLV 端口 8080" {
    $t = Test-NetConnection localhost -Port 8080 -WarningAction SilentlyContinue
    if (-not $t.TcpTestSucceeded) { throw "8080 不可达" }
}

Write-Host "==> 4/6 TURN 真实 allocate(use-auth-secret REST 凭据)" -ForegroundColor Cyan
docker logs douyin-coturn 2>&1 | Select-Object -First 6
Check "TURN UDP allocate(REST 临时凭据)" {
    $secret = ""
    if (Test-Path $envFile) {
        $secret = ((Get-Content $envFile | Where-Object { $_ -match '^TURN_SHARED_SECRET=' } | Select-Object -First 1) -replace '^TURN_SHARED_SECRET=', '').Trim()
    }
    if (-not $secret) { throw "未配置 TURN_SHARED_SECRET" }
    $exp = [DateTimeOffset]::UtcNow.AddHours(1).ToUnixTimeSeconds()
    $user = "$exp`:$PID"
    $hmac = New-Object System.Security.Cryptography.HMACSHA1
    $hmac.Key = [Text.Encoding]::ASCII.GetBytes($secret)
    $pass = [Convert]::ToBase64String($hmac.ComputeHash([Text.Encoding]::ASCII.GetBytes($user)))
    $out = docker exec douyin-coturn turnutils_uclient -y -u $user -w $pass -z 3 -n 1 127.0.0.1 2>&1 | Out-String
    $tail = ($out -split "`n" | Where-Object { $_.Trim() } | Select-Object -Last 3) -join " | "
    if ($out -match '(?i)403|forbidden|error|refused|auth.*fail') { throw "allocate 认证/连接失败: $tail" }
    if ($out -match '(?i)start_mclient|new allocation|Total success') { return }
    throw "allocate 未成功: $tail"
}

Write-Host "==> 5/6 LiveKit 真实媒体打通(livekit-cli --publish-demo)" -ForegroundColor Cyan
$key = "devkey"
$secret = ""
if (Test-Path $envFile) {
    $secret = ((Get-Content $envFile | Where-Object { $_ -match '^LIVEKIT_API_SECRET=' } | Select-Object -First 1) -replace '^LIVEKIT_API_SECRET=', '').Trim()
}
if (-not $secret) { $secret = $env:LIVEKIT_API_SECRET }
if ($secret) {
    Check "livekit-cli join-room publish-demo" {
        $global:LASTEXITCODE = 0
        $log = Join-Path $env:TEMP "smoke_lk_cli_$PID.log"
        $logErr = Join-Path $env:TEMP "smoke_lk_cli_$PID.err"
        $p = Start-Process docker -ArgumentList "run --rm --network host livekit/livekit-cli room join --url ws://localhost:7880 --api-key $key --api-secret $secret --publish-demo --identity smoke_bot rtc_smoke_$PID" -PassThru -NoNewWindow -RedirectStandardOutput $log -RedirectStandardError $logErr
        Start-Sleep -Seconds 45
        if (-not $p.HasExited) { Stop-Process -Id $p.Id -Force }
        Start-Sleep -Seconds 3
        docker ps -a -q --filter ancestor=livekit/livekit-cli | ForEach-Object { docker rm -f $_ } | Out-Null
        $text = (Get-Content $log -Raw -ErrorAction SilentlyContinue) + (Get-Content $logErr -Raw -ErrorAction SilentlyContinue)
        if (-not $text) { throw "CLI 无输出" }
        $tail = ($text -split "`n" | Where-Object { $_ } | Select-Object -Last 2) -join " | "
        if ($text -match '(?i)error|refused|failed') { throw "CLI 报错: $tail" }
        if ($text -match '(?i)joined|publishing|publication') { return }
        throw "未确认加入房间: $tail"
    }
} else {
    Write-Host "SKIP: 未配置 LIVEKIT_API_SECRET,跳过 livekit-cli 媒体打通" -ForegroundColor Yellow
}

Write-Host "==> 6/6 摘要" -ForegroundColor Cyan
$results | ForEach-Object { Write-Host $_ }
if ($results | Where-Object { $_ -like 'FAIL*' }) { exit 1 } else { Write-Host "ALL PASS" -ForegroundColor Green }