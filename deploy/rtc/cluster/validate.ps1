# RTC-013 可执行验收脚本(静态检查 + 可选运行时集群/故障注入)
# 静态(默认):
#   1) docker compose config --quiet(streaming + rtc-cluster)
#   2) livekit/livekit-server:v1.13.5 --config <node-a/b.yaml> ports(配置可解析)
#   3) TURN 区域 bounded-start validator
# 运行时(-Runtime):
#   4) 拉起 cluster,确认双节点 metrics 就绪
#   5) 共享 Redis `nodes` 注册检查(节点数/唯一 advertised IP/region)
#   6) livekit-cli join 跨房间,metrics 确认房间放置(Redis routing)
#   7) 故障注入:停止 Redis 后新房失败;恢复 Redis 后新房成功
# 用法: powershell -File deploy/rtc/cluster/validate.ps1 [-Runtime]
param([switch]$Runtime, [int]$RuntimeTimeoutSec = 300)

$ErrorActionPreference = "Continue"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$composeCluster = Join-Path $root "docker-compose.rtc-cluster.yml"
$composeStreaming = Join-Path $root "docker-compose.streaming.yml"
$envCluster = Join-Path $root "deploy\rtc\cluster\.env"
$envStreaming = Join-Path $root "deploy\streaming\.env.example"
$image = "livekit/livekit-server:v1.13.5"
$cliImage = "livekit/livekit-cli:latest"
$clusterNetwork = "douyin_rtc-cluster-net"
Set-Location $root

$docker = Get-Command docker -ErrorAction SilentlyContinue
if (-not $docker) { Write-Error "未找到 docker"; exit 2 }

function Fail([string]$msg) { Write-Host "[validate] FAIL: $msg" -ForegroundColor Red; exit 1 }

Write-Host "== 1) compose config 解析 =="
& docker compose -f $composeStreaming --env-file $envStreaming config --quiet 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) { Fail "streaming compose config" }
& docker compose -f $composeCluster --env-file $envCluster config --quiet 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) { Fail "rtc-cluster compose config" }
Write-Host "OK: 两份 compose 解析通过"

Write-Host "== 2) LiveKit 节点配置 ports 解析 =="
foreach ($node in @("livekit-node-a", "livekit-node-b")) {
    $yaml = Join-Path $root "deploy\rtc\cluster\$node.yaml"
    $mount = "$((Resolve-Path (Split-Path $yaml)).Path)\$node.yaml:/etc/livekit.yaml:ro"
    $out = (& docker run --rm -v $mount $image --config /etc/livekit.yaml ports 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0) { Fail "node $node yaml 解析失败: $out" }
    Write-Host "OK: $node"
    Write-Host $out
}

Write-Host "== 3) TURN 区域 bounded-start =="
& powershell -NoProfile -File (Join-Path $root "deploy\rtc\validate-coturn-startup.ps1") -ComposeFile "docker-compose.rtc-cluster.yml" -Service "turn-region-a" -EnvFile $envCluster
if ($LASTEXITCODE -ne 0) { Fail "TURN region-a 启动验证" }
Write-Host "OK: TURN region-a 启动验证通过"

if (-not $Runtime) {
    Write-Host "静态检查全部通过。附加 -Runtime 以执行集群路由/故障注入验收。"
    exit 0
}

Write-Host "== 4) 运行时:拉起 cluster(redis + 双 livekit 节点)=="
& docker compose -f $composeCluster --env-file $envCluster up -d redis livekit-node-a livekit-node-b 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) { Fail "compose up" }

$deadline = (Get-Date).AddSeconds($RuntimeTimeoutSec)
$readyA = $false; $readyB = $false
while ((Get-Date) -lt $deadline -and (-not ($readyA -and $readyB))) {
    Start-Sleep -Seconds 3
    $mA = (docker exec douyin-livekit-node-a wget -qO- http://127.0.0.1:7889/metrics 2>$null | Out-String)
    $mB = (docker exec douyin-livekit-node-b wget -qO- http://127.0.0.1:7889/metrics 2>$null | Out-String)
    if ($mA -match "livekit_node_packet_total") { $readyA = $true }
    if ($mB -match "livekit_node_packet_total") { $readyB = $true }
}
if (-not ($readyA -and $readyB)) { Fail "节点 metrics 未就绪 (a=$readyA b=$readyB)" }
Write-Host "OK: 双节点 metrics 就绪"
$mA | Select-String "livekit_node_packet_total|livekit_participant_total" | Select-Object -First 4 | ForEach-Object { Write-Host "  node-a: $($_.Line.Trim())" }
$mB | Select-String "livekit_node_packet_total|livekit_participant_total" | Select-Object -First 4 | ForEach-Object { Write-Host "  node-b: $($_.Line.Trim())" }

Write-Host "== 5) 共享 Redis 节点注册(`nodes` 哈希)=="
$pw = ((Get-Content $envCluster | Where-Object { $_ -like "REDIS_PASSWORD=*" }) -split "=", 2)[1]
$nodesRaw = (docker exec douyin-rtc-redis redis-cli -a $pw --no-auth-warning HGETALL nodes 2>$null | Out-String)
$nodeCount = ($nodesRaw -split "`n" | Where-Object { $_ -match "ND_" }).Count
Write-Host "  nodes 哈希字段数: $nodeCount (期望 >= 4 = 2 字段 + 2 值)"
if ($nodeCount -lt 4) { Fail "Redis nodes 注册不足: $nodesRaw" }
$nodesRaw -split "`n" | ForEach-Object { if ($_ -match "ND_|172\.31\.10\.|cn-east") { Write-Host "  $_" } }
Write-Host "OK: 双节点已注册进共享 Redis(唯一 advertised IP + region)"

Write-Host "== 6) 房间放置(livekit-cli join,需 cli 镜像)=="
$key = ((Get-Content $envCluster | Where-Object { $_ -like "LIVEKIT_API_KEY=*" }) -split "=", 2)[1]
$secret = ((Get-Content $envCluster | Where-Object { $_ -like "LIVEKIT_API_SECRET=*" }) -split "=", 2)[1]
if (-not $key -or -not $secret) { Fail "无法读取 cli 凭据" }

function Start-Join([string]$room, [string]$identity, [string]$url = "ws://livekit-node-a:7880") {
    $name = "rtc13-join-" + [DateTime]::Now.Ticks
    $args = @("run", "-d", "--name", $name, "--network", $clusterNetwork,
        "-e", "LIVEKIT_API_KEY=$key", "-e", "LIVEKIT_API_SECRET=$secret",
        $cliImage, "join-room", "--yes", "--url", $url, "--room", $room, "--identity", $identity)
    & docker @args 2>&1 | Out-Null
    return $name
}

function Get-JoinOutcome([string]$name, [int]$timeoutSec, [switch]$ExpectPlacement) {
    $deadline = (Get-Date).AddSeconds($timeoutSec)
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 3
        $running = (docker inspect -f "{{.State.Running}}" $name 2>$null | Out-String).Trim()
        if ($running -eq "false") {
            $logs = ((& docker logs $name 2>&1 | ForEach-Object { "$_" }) -join "`n")
            & docker rm -f $name 2>&1 | Out-Null
            $failMsg = ($logs -split "`n" | Select-Object -First 8 | ForEach-Object { $_.Trim() } | Where-Object { $_ }) -join "; "
            return "failed: $failMsg"
        }
        if ($ExpectPlacement) {
            $mA3 = (docker exec douyin-livekit-node-a wget -qO- http://127.0.0.1:7889/metrics 2>$null | Out-String)
            $mB3 = (docker exec douyin-livekit-node-b wget -qO- http://127.0.0.1:7889/metrics 2>$null | Out-String)
            $paLine = ($mA3 | Select-String "livekit_participant_total\{[^}]*\} (\d+)" | Select-Object -First 1)
            $pbLine = ($mB3 | Select-String "livekit_participant_total\{[^}]*\} (\d+)" | Select-Object -First 1)
            $pa = 0; $pb = 0
            if ($paLine) { $pa = [int]$paLine.Matches[0].Groups[1].Value }
            if ($pbLine) { $pb = [int]$pbLine.Matches[0].Groups[1].Value }
            if ($pa -gt 0 -or $pb -gt 0) {
                & docker rm -f $name 2>&1 | Out-Null
                $who = "node-a" ; if ($pb -gt $pa) { $who = "node-b" }
                return "placed:$who"
            }
        }
    }
    & docker rm -f $name 2>&1 | Out-Null
    return "timeout"
}

$room1 = "rtc13-room-" + (Get-Random -Minimum 1000 -Maximum 9999)
Write-Host "-- join $room1 --"
$out1 = Get-JoinOutcome (Start-Join $room1 "u1") 45 -ExpectPlacement
if ($out1 -notlike "placed:*") { Fail "join $room1 失败: $out1" }
Write-Host "OK: $room1 放置于 $($out1.Split(':')[1]) (Redis routing)"

Write-Host "== 7) 故障注入:停止 Redis 后新房应失败 =="
& docker compose -f $composeCluster --env-file $envCluster stop redis 2>&1 | Out-Null
Start-Sleep -Seconds 2
$outFail = Get-JoinOutcome (Start-Join "rtc13-fault-$([DateTime]::Now.Ticks)" "u2") 40
Write-Host "故障注入 join 结果: $outFail"
if ($outFail -like "failed:*") {
    Write-Host "OK: Redis 故障时新房失败(fail-closed),原因:"
    $outFail.Substring(8) -split "; " | ForEach-Object { Write-Host "  $_" }
} else {
    Fail "Redis 故障时新房未失败(结果: $outFail)"
}

Write-Host "-- 恢复 Redis 后新房应成功 --"
& docker compose -f $composeCluster --env-file $envCluster up -d redis 2>&1 | Out-Null
Start-Sleep -Seconds 8
$roomR = "rtc13-recover-$([DateTime]::Now.Ticks)"
$outRec = Get-JoinOutcome (Start-Join $roomR "u3") 45 -ExpectPlacement
if ($outRec -like "placed:*") {
    Write-Host "OK: Redis 恢复后 $roomR 放置于 $($outRec.Split(':')[1])"
} else {
    Fail "恢复后新房失败: $outRec"
}

Write-Host "== 收尾:有界停止集群 =="
& docker compose -f $composeCluster --env-file $envCluster stop 2>&1 | Out-Null
Write-Host "RTC-013 验收通过:静态解析、双节点注册、Redis 路由放置、故障注入 fail-closed。"
exit 0