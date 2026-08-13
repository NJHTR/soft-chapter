# Media provider bootstrap(RTC-002)运维说明

## 前置

1. 在 `deploy/streaming/` 复制 `.env.example` 为 `.env` 并填写:
   - `SRS_RTC_CANDIDATE`:浏览器可直达的宿主机 IP(局域网/公网),填错则 WHIP/WHEP 协商到容器内网 IP,表现为"收不到流"
   - `LIVEKIT_API_SECRET` / `TURN_SHARED_SECRET`:随机 hex(如 `openssl rand -hex 32`)
2. 启动 Docker Desktop(Windows)或 docker daemon。

## 启动

```bash
# 直播栈(默认 profile)
docker compose -f docker-compose.streaming.yml up -d srs

# 通话栈(LiveKit + coturn)
docker compose -f docker-compose.streaming.yml --profile webrtc up -d

# 替代直播栈(与 srs 互斥,本机先执行 down srs)
docker compose -f docker-compose.streaming.yml --profile alternative up -d

# 观测
docker compose -f docker-compose.streaming.yml --profile monitoring up -d
```

## 验证

```bash
# 一键健康检查 + smoke(含 livekit-cli 真实媒体打通,需要 --profile webrtc 已启动)
powershell -File deploy/streaming/smoke.ps1
```

人工检查:

- SRS 健康:`curl http://localhost:1985/api/v1/versions`
- LiveKit 指标:`curl http://localhost:7889/metrics`(信令 7880;`/rtc/validate` 需 JWT,401 即存活)
- coturn 端口:`Test-NetConnection localhost -Port 3478`
- TURN relay 结果:见 `smoke.ps1` 输出与 `docker logs douyin-coturn`

## 端口一览(仅对应 profile 启动时占用)

| 端口 | 服务 | 协议/用途 |
|---|---|---|
| 1935 | srs | RTMP |
| 1985 | srs | HTTP API / WHIP/WHEP 信令 |
| 8080 | srs | HTTP-FLV / HLS 出口 |
| 9000 | srs | SRT |
| 8000/udp | srs | WebRTC RTP |
| 7880 | livekit | HTTP/WS 信令 |
| 7881 | livekit | WebRTC over TCP |
| 7889 | livekit | Prometheus metrics |
| 50000-50050/udp | livekit | WebRTC RTP |
| 3478 | coturn | TURN UDP/TCP |
| 49160-49180/udp | coturn | TURN relay |
| 8888/8889 | mediamtx(alternative) | WebRTC/API、RTMP |
| 9091 / 3003 | prometheus / grafana(monitoring) | 指标 |

## 停止与回滚

```bash
docker compose -f docker-compose.streaming.yml down          # 停止并清理
docker compose -f docker-compose.streaming.yml down -v       # 连卷一起清理
```

回滚 = 切换 profile 或固定镜像 tag;不支持原地改配置掩盖问题。

## 已知未验证项(RTC-002 DoD 记录)

- coturn TLS 5349:需证书(PEM),归属证书签发/部署阶段,与 RTC-003 控制面 token 一起落地;bootstrap 只验证 UDP/TCP 3478。
- LiveKit 与浏览器 WebRTC E2E:livekit-cli `--publish-demo` 已覆盖真实 SDP/ICE/DTLS;浏览器双端通话是 RTC-004 的验收。
- SRS WHIP ingest 媒体实测:无 stub/echo,由 RTC-006 迁移任务验收真实推拉流。
- LiveKit webhook → 后端(`host.docker.internal:9191/api/rtc/webhook/livekit`):签名契约见 `docs/contracts/livekit-webhook.md`;端到端推送验证依赖后端起来 + migration_034/035 执行,归属 RTC-004 联调(RTC-003 已用签名向量/幂等单测覆盖,不宣称端到端已验证)。
- 后端环境变量注入:`RTC_LIVEKIT_API_KEY`、`RTC_LIVEKIT_API_SECRET`、`RTC_LIVEKIT_WEBHOOK_SECRET`(= LIVEKIT_API_SECRET)、`RTC_TOKEN_TTL_SECONDS`(60-900,默认 300);未配置则 token 签发/webhook 校验 fail-closed。