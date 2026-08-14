# 工作日志

## 2026-08-14：RTC-004 重新勘察与契约收敛（进行中）

- 实际基线：分支 `dev/full`，HEAD `4c813c4`；工作区仍包含用户的直播、后台、原生引擎和管理端改动，按宪法“preserve_and_avoid”保留，未执行 reset/checkout/清理。
- 已启用独立审查：`contract_audit`（OpenAPI 与状态文件）、`frontend_rtc_audit`（LiveKit adapter 与旧 Call 隔离）、`backend_rtc_audit`（token、状态机、webhook 生命周期）。审查结论已写入本任务：OpenAPI 缺失、任务 YAML 列表缩进错误、旧 Call 与新面板双主路径、本地轨道聚合和 `srcObject` 绑定不稳定、官方 LiveKit webhook 使用 Authorization JWT。
- 本轮拥有边界：`docs/contracts/`、`docs/project-constitution/`、`src/modules/rtc/`、`src/api/rtc.ts`、RTC 迁移壳以及 `server/.../rtc/webhook/`；不接触用户直播和后台目录。
- 验证约束：类型检查和构建只能证明编译，不得替代双浏览器 SDP/ICE/媒体轨道、TURN relay 和 webhook 端到端验收；RTC-004 在这些验证完成前保持 `in_progress`。

## 2026-08-13：RTC-003 完成（控制面与通话领域，5 提交）

- 波次 A（只读）：architecture-agent + contract-agent 勘察——现状事实、旧 msg_type=10/11 契约、signaling schema 缺口、鉴权/ACL 数据支撑、可复用设施（雪花 ID/Redis 幂等/PaymentStateMachine 模式）。用户未提交改动全部记录并避开（TOP10 重叠清单）。
- 波次 B：domain-agent（`9a9d374`：CallSession/Participant/CallEvent 状态机 + ACL + 事件账本 + migration_034 + 48 契约单测）→ token-agent（`32060db`：LiveKit token API + webhook ledger + migration_035 + SessionFilter 白名单 1 行 + 签名向量测试，77 测试）。
- 主智能体：`940b5d2` docs（signaling schema v1 向后兼容扩展：error/ack/call.expired 等 + token/ttl/replayed 可选字段；livekit-webhook.md 契约；rtc-error-codes.md；API 指南补 10/11；livekit.yaml webhook 段指向 host.docker.internal:9191）。
- 波次 F：integration-review-agent APPROVE_WITH_NOTES（无阻断；2 重要 + 6 建议）→ fix-agent `27cf661`（detail 鉴权、sys:/ttl: event_id 保留前缀隔离 TTL 抢占、并发 create 幂等、ttl_seconds 解析、CONNECTED hangup 投影）→ 主智能体修复 compat callState 语义漂移 `05858d0`（对齐旧前端 0=拒接/1=已接通/2=未接通）。
- 最终验证：`mvn -f server/pom.xml test -Dtest=com.douyin.rtc.**` 91/91 通过，BUILD SUCCESS；`npx --yes @redocly/cli lint docs/contracts/rtc-control.openapi.yaml` 通过且 0 warning；`pnpm exec vue-tsc --noEmit --pretty false` 与 `pnpm run build-only` 通过。
- 交付：com.douyin.rtc 包（domain/repository/service/provider/webhook/controller）、migration_034/035、docs/contracts 三件套、SessionFilter 白名单 1 行。用户未提交改动零接触。
- 已知未验证：端到端 webhook 推送与迁移执行（依赖用户跑 034/035 + 起后端，验收归 RTC-004）。
- 下一任务：RTC-004（1 对 1 LiveKit 适配器，依赖 RTC-002+RTC-003 已满足）——注意其前端改动区域与用户直播改动相邻，开工前需再次确认边界。

## 2026-08-13：RTC-003 认领（控制面与通话领域）

- 推送 RTC-001/RTC-002 全部提交到远程：`f361a12..3acd3e0 dev/full -> origin/dev/full`。
- 按提示词流程选取下一个任务：RTC-003（依赖 RTC-001 已完成；与 RTC-002 可并行，RTC-002 已完结）。
- RTC-003 状态 `planned -> in_progress`，owner=main-dev-agent。
- 边界：负责 `server/.../rtc/`、`server/sql/`、`server/.../websocket/` 的 RTC adapter、`docs/contracts/`；禁止修改媒体服务实现、LiveKit 内部信令、C++ stub。
- 风险记录：用户未提交改动密集覆盖 `server/`（LiveController、LiveRoom、engine/、websocket/DashboardWebSocketHandler、StreamController、migration_033 等），RTC-003 方案将以新增文件为主，与用户改动重叠的文件先协商或绕过；不做 reset/checkout/覆盖。
- 波次 A 启动：architecture-agent + contract-agent 只读勘察（现状、可复用接口、双读契 约、token/webhook 安全面）。

## 2026-08-13：RTC-002 独立审查与修复（smoke 8/8）

- 独立审查智能体结论：APPROVE_WITH_NOTES。核对清单：密钥安全 ✓、边界遵守 ✓、无 stub 宣称 ✓、回滚可执行 ✓、测试证据真实（有瑕疵）。
- 审查发现与修复：
  1. coturn `-n` 为非法参数（镜像无此选项，日志 ERROR "Unknown argument"），且 command 覆盖镜像默认 `--external-ip=$(detect-external-ip)` → 已删 `-n`、显式注入 `--external-ip=${SRS_RTC_CANDIDATE}`。
  2. 真实 TURN allocate 实测暴露：`use-auth-secret` 缺 `lt-cred-mech` 导致 digest 认证 403（首次 uclient 测试失败即铁证）→ conf 补 `lt-cred-mech`，allocate 通过。
  3. smoke 3/6 缺容器 health 断言 → 新增 `docker inspect .State.Health.Status` 检查。
  4. smoke 4/6 仅查日志 → 升级为真实 UDP allocate（turnutils_uclient + REST HMAC 凭据，从 .env 读 secret，不落日志）。
  5. mediamtx UDP 端口映射错（8888/udp 未发布）→ 修正为 8888/udp(WebRTC) + 8888/tcp(RTSP) + 8889/tcp(RTMP)。
  6. 镜像固定 tag：livekit v1.13.5、coturn 4.17.2（monitoring/alternative 未实测镜像版本，README 注明首次启动时固定）。
  7. `GF_ADMIN_PASSWORD` 空值回退 admin/admin → 默认占位值。
- smoke 最终 8/8 ALL PASS：compose config、containers healthy、srs API、livekit 7889/metrics、coturn 3478、srs HTTP-FLV 8080、TURN UDP allocate（REST 凭据）、livekit-cli publish-demo。

## 2026-08-13：RTC-002 provider bootstrap 全绿

### 工作区快照

- 分支：`dev/full`；用户业务改动（admin/、server/、src/、streaming-engine/、docs/runtime/、docs/verification/、migration_033）原样保留，未纳入提交。
- 本任务新增/修改：`deploy/rtc/`、`deploy/streaming/`、`docker-compose.streaming.yml`、`.gitignore`（`deploy/streaming/.env`）、任务/状态/日志文档。

### 交付物

- `deploy/streaming/`：srs.conf、livekit.yaml、mediamtx.yml、prometheus.yml、.env.example、README.md、smoke.ps1（UTF-8 BOM，兼容 PS 5.1）。
- `deploy/rtc/turnserver.conf`：use-auth-secret，TLS 5349 注释推迟到 RTC-003（缺证书）。
- `docker-compose.streaming.yml`：修正原版虚假声明（srs.conf 路径错、无 coturn、密钥硬编码），profiles = srs 默认 / webrtc(livekit+coturn) / alternative(mediamtx) / monitoring(prometheus+grafana)，healthcheck 与端口映射补齐，密钥全部 `${VAR:?}` 环境注入。
- smoke 6/6 ALL PASS：compose config、srs API、livekit 7889/metrics、coturn 3478/tcp、srs HTTP-FLV 8080、livekit-cli `--publish-demo`（真实发布 h264 publication，非 SDP echo）。

### 踩坑记录（供 RTC-003+ 复用）

1. SRS 6 镜像 `./objs` 是二进制目录，挂 volume 会覆盖二进制导致 exec 失败；且无 curl/wget，健康检查用 bash `/dev/tcp`。
2. LiveKit `LIVEKIT_KEYS` 格式是 `"key: secret"`（冒号后必须空格），否则启动即退。
3. LiveKit `/rtc/validate` 需 JWT（401 即存活）；`/metrics` 只在独立 metrics 端口（本部署 7889）提供。
4. coturn `--no-cli` 在 4.17.2 已废弃（ERROR 但继续跑），已移除。
5. PowerShell 5.1：`docker` 原生命令退出码不会抛错，smoke `Check` 必须显式检查 `$LASTEXITCODE`；`$results` 跨函数须用 `$script:` 作用域。
6. docker compose 不自动读取子目录 `.env`，必须显式 `--env-file deploy/streaming/.env`。

### 遗留（诚实声明）

- TURN TLS 5349 未验证（证书缺位，RTC-003 补）。
- 浏览器端真实通话未做（属 RTC-004/005/006）。
- B-006 修复在用户工作区，验证随 RTC-003。

## 2026-08-13：RTC-001 文档基线落地

- 独立智能体完成只读评估（架构、WebRTC、契约、宪法审查），结论一致：当前主要问题是媒体链路与职责边界未成立，而非码率参数。
- 审查修正已合入工作区：宪法 v1.1（§2.4 LiveKit 原生信令与应用控制事件边界、§3 新增 `rtc-persistence` 统一 call ledger 所有权、§7 治理/版本/弃用与 legacy 退役里程碑）、RTC-002~010 任务文件、状态文件与开发计划更新。
- 提交 `85262e2 docs(RTC-001): 实时媒体宪法与架构基线`（34 文件，纯治理/架构文档；`.ai`、`.ai-company` 项目记录一并入库）。
- 修复了会话间遗留的 `admin/design.md` index 空文件问题：用户的 staged 内容（99 行）已恢复，不在本次提交内。
- 用户业务改动（直播、streaming-engine、WebSocket、后台、`docs/runtime/`、`docs/verification/`）原样保留，未纳入提交。
- RTC-001 DoD 全部满足，标记 `completed`；下一任务 RTC-002（provider bootstrap）。

## 2026-08-13：RTC-001 基线勘察

### 工作区快照

- 分支：`dev/full`
- HEAD：`f361a12 feat: 模型训练`
- 跟踪分支：`origin/dev/full`
- 工作区：脏，存在用户已有直播、WebSocket、streaming-engine、后台和文档改动。
- 处理决定：不回滚、不覆盖、不把这些改动误归属于 RTC-001；本轮新增文件限定在设计与治理文档。

### 已执行检查

```text
git status --short
git branch --show-current
git log --oneline --decorate -20
git log --stat -10
git branch -a
git tag --sort=-creatordate
rg --files -g '!node_modules' -g '!dist'
pnpm exec vue-tsc --noEmit --pretty false   # 只读审查记录为通过
pnpm run build-only                         # 只读审查记录为通过
```

后端 Maven、浏览器 WebRTC E2E、TURN/NAT 矩阵和已部署媒体服务本轮未验证，不能宣称通过。

### 现状结论

1. 直播当前是 WebCodecs/自定义 WebSocket 与文本控制混用，前后端二进制链路不闭合；发送器还有 `copyTo()`、`getVideoTracks()` 和空 canvas 问题。
2. WebRTC offer 接口是 SDP echo，C++ WebRTCStreamer 是占位；这不是码率调参问题。
3. 1 对 1/群聊使用浏览器 mesh，只有公共 STUN，没有 TURN、SFU、候选缓存、完整重连和稳定的远端视频绑定。
4. Spring Boot、聊天 WS、数据库和 native engine 的职责边界尚未固化；实时通话记录也缺少独立 call ledger 和幂等事件。
5. Docker compose 声明了多套媒体服务，但配置文件和真实部署验证不完整，不能把 compose 文件当作已运行基础设施。

### 决策

- 通话与互动连麦采用 LiveKit SFU；coturn 作为生产穿透边缘。
- 直播采用 SRS ingest/distribution，WebRTC/WHEP 作为低延迟出口，HLS/HTTP-FLV 作为兼容和规模 fallback。
- Spring Boot 只做控制面、token、权限、业务事件和 webhook；媒体字节不经过业务 WebSocket。
- H.264/VP8 + Opus 为浏览器基线；AV1 是可选增强，H.265 不作为 WebRTC 基线。
- 现有 `/ws/live`、Call.vue mesh、WebCodecs WebSocket 和 C++ WebRTC stub 进入迁移清单，禁止新增生产能力。

### 下一步

完成 RTC-001 文档提交后，按依赖顺序认领 RTC-002：补齐可重复的 LiveKit/coturn/SRS 开发环境、健康检查和最小 token/ingest 验证。
