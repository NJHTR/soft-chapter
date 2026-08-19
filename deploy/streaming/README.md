# Media provider bootstrap(RTC-002)运维说明

## 前置

1. 在 `deploy/streaming/` 复制 `.env.example` 为 `.env` 并填写:
   - `SRS_RTC_CANDIDATE`:浏览器可直达的宿主机 IP(局域网/公网),填错则 WHIP/WHEP 协商到容器内网 IP,表现为"收不到流"。Windows Docker Desktop 本机验收可使用当前 WSL host 地址(例如 `172.21.160.1`)，不要将该地址用于公网部署
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

### IDEA 本地后端启动变量

后端从 IDEA Run/Debug Configuration 的 Environment variables 读取变量，Docker 子目录 `.env` 不会自动注入到 IDEA 进程：

```text
RTC_LIVEKIT_API_KEY=devkey
RTC_LIVEKIT_API_SECRET=<deploy/streaming/.env 中的 LIVEKIT_API_SECRET>
RTC_LIVEKIT_WEBHOOK_SECRET=<同一个 LIVEKIT_API_SECRET>
RTC_TOKEN_TTL_SECONDS=300
DOUYIN_KAFKA_ENABLED=true
KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:9092
```

如果 `Test-NetConnection 127.0.0.1 -Port 9092` 失败，Kafka 虽然可能已启动，但没有暴露到 Windows 的该地址；请改成实际 advertised listener/端口后重启 IDEA 后端。后端在 Docker 中运行时才使用容器网络名（例如 `kafka:9092`）。

## RTC-006 直播迁移提示

- 浏览器直播主路径是 SRS WHIP/WHEP；`/ws/live` 只承载聊天、点赞和人数控制，不能发送媒体帧。
- 开发环境的 `/media/srs`、`/media/srs-http` 代理由 Vite 提供；生产必须在网关配置等价的 HTTPS 反代或注入 `SRS_RTC_PUBLIC_BASE` / `SRS_HTTP_PUBLIC_BASE` 绝对地址。
- `SRS_RTC_CANDIDATE` 必须是浏览器可达地址。端口健康不代表 WHIP/WHEP 已经发布或首帧已解码。
- 当前 RTC-006 已通过本机 WHIP→WHEP、HLS/HTTP-FLV fallback 和 Docker provider smoke；公网 HTTPS/TURN、provider 重启、SRS callback 授权和多实例 presence 故障验收仍未完成，详见 `docs/contracts/live-media-contract.md` 和任务文件。
- stream key 目前是随机能力值，不等同短期 ingest/play token。生产启用前必须接入 SRS callback/网关 ACL 和撤销策略，不能直接把 HTTP smoke 当安全验收。
- 生产媒体授权：设置 `LIVE_MEDIA_AUTH_ENABLED=true`、长度至少 32 的 `LIVE_MEDIA_TOKEN_SECRET` 和长度至少 32 的 `SRS_CALLBACK_TOKEN`，将 `srs-auth.conf.example` 渲染为不含占位符的私有配置后，再合并进 SRS vhost。未配置 callback 时不要宣称短期令牌已保护 provider。
- `SRS_CALLBACK_TOKEN` 只能使用 URL-safe 随机字符（`A-Z`、`a-z`、`0-9`、`-`、`_`），避免 `&`、`?`、`#` 改写 SRS callback 查询串。反向代理、SRS 和 Spring access log 必须脱敏 `callback_token`、媒体 token 和完整媒体 URL。
- Windows Docker Desktop 中 SRS 容器回调 IDEA 后端通常使用 `host.docker.internal:9191`；Linux Docker 需要配置 `host-gateway` 或使用同一容器网络内的服务名。`spring-control:9191` 并不属于当前 compose，不能直接使用。
- 启用 reconciliation 时还要配置 `LIVE_MEDIA_RECONCILIATION_ENABLED=true` 和 `SRS_API_BASE`。SRS API 不可达只会使直播进入 `DEGRADED`，不会自动关播；API 恢复且明确无流后才开始有界 grace period。

### RTC-006 callback 运行验收

先以登录主播创建并开始一个测试直播间，从详情接口取得该房间的短期 `whipUrl` 和以另一登录用户取得的 `whepUrl`，仅从 URL 的 `token` 查询参数提取临时 token。不要将完整 URL 或 token 粘贴到日志、终端历史或提交中。

渲染私有 `http_hooks` 配置、启动 Spring 和 SRS 后，可用以下脚本检查 Spring callback 契约。脚本只输出通过/失败和 HTTP 状态，不回显任何 secret、token 或响应体：

```powershell
powershell -File deploy/streaming/provider-callback-smoke.ps1 `
  -CallbackBase http://localhost:9191/api/live/provider/srs `
  -StreamKey <stream-key>
```

脚本会用隐藏输入提示 ingest token、play token 和 callback token；自动化环境可以用同名参数传入，但不得将它们写进 CI 输出、命令日志或提交。

随后必须做真实 provider 验收：用 WHIP 发布、用 WHEP 等待首帧、检查 HLS 与 HTTP-FLV fallback；重启 SRS 或中断发布者，确认房间在 grace window 内转为 `DEGRADED`，有效新 `on_publish` 才能恢复，重复旧 callback 不能复活旧 generation。不同 Redis/Spring 实例同时 join/leave 同一 `sessionId` 时，viewer count 必须只变化一次。
