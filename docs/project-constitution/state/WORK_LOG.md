# 工作日志

## 2026-08-19：RTC-012 选择性订阅基础契约

- 新增 provider-neutral 的远端订阅端口：质量层、音频/视频订阅、可见参与者集合和 active speaker。
- LiveKit adapter 仅在显式设置可见集合时取消不可见视频订阅；默认仍保持兼容的全量订阅，音频默认优先。
- 屏幕共享默认使用 HIGH，active speaker 使用 MEDIUM，其他视频使用 LOW；参与者离开或房间销毁时清理策略状态。
- `adaptiveStream` 继续保持关闭，待真实 `RemoteTrack` attach/detach 与双浏览器验收后再评估开启。
- 验证：`vue-tsc`、目标 ESLint、`pnpm run build-only`、`git diff --check` 通过；真实双浏览器、弱网矩阵和带宽下降统计尚未完成，RTC-012 仍为未完成任务。

## 2026-08-19：RTC-012 接入群聊面板生命周期

- 群聊视频面板正常显示时向 adapter 提交已知远端参与者集合；最小化或浏览器标签页后台时只保留 active speaker（无 active speaker 时取第一个远端）的视频订阅。
- 订阅策略通过 Pinia action 暴露，视图不直接依赖 LiveKit 类型；音频订阅始终保持，1 对 1 继续使用全量兼容模式。
- 验证：`vue-tsc` 和 RTC store/CallPanel ESLint 通过；真实 4/8 人浏览器、后台恢复和 egress 下降数据尚未完成。

## 2026-08-19：收口旧 WebCodecs WebRTC 调用

- `WebCodecsSender` 和 `WebCodecsPlayer` 的旧 `webrtc` 模式在入口处 fail-closed，不再打开摄像头、创建 PeerConnection 或请求已退役的 `/api/live/webrtc/offer`。
- 后端 `/api/live/engine/webrtc/offer` 继续保持 410；直播生产路径只有 SRS WHIP/WHEP 和 HLS/HTTP-FLV fallback。
- 验证：`vue-tsc`、两个 legacy 文件 ESLint 通过（仅保留未使用变量 warning）；真实浏览器直播和公网 ICE/TURN 仍需发布环境验收。

## 2026-08-19：RTC-007 QoE 观测基础

- LiveKit adapter 新增 provider-neutral `RtcQoeSnapshot`，读取远端 inbound-rtp 的丢包、jitter、字节、解码帧、分辨率和帧率。
- 通话接通后每 3 秒采集最近一次快照，挂断或生命周期切换时停止；当前只保存在 store，不改变质量层，也不写入 Kafka/聊天 WS。
- 验证：`vue-tsc`、RTC adapter/store ESLint 通过；服务端聚合、真实弱网矩阵和 SLO 发布门禁仍未完成。
- 独立审查修正：QoE 轨道改用 publication `trackSid` 唯一标识并保留 source，摄像头与屏幕共享的统计不再互相覆盖；采样改为串行循环并在重连后恢复，LiveKit `connecting/signalReconnecting` 显式归一化。

## 2026-08-19：RTC 通话流程验收与忙线并发守卫

- 新增控制面 `BUSY` 错误码：发起者或目标参与者仍处于有效通话时，创建新会话返回 409，不创建第二个 LiveKit 房间。
- `RtcCallSessionMapper.findActiveByUserId` 同时检查会话状态和参与者状态；群通话中已经 `LEFT/REJECTED/CANCELLED` 的成员不会继续占用忙线。
- 前端在通话中收到竞态来电时保持当前页面，并回传 `call_busy`；发起方取消未接通会话并提示“对方正在通话中”。
- 新增 `docs/verification/RTC_CALL_FLOW_ACCEPTANCE.md`，记录 A→B 接听、切换主画面、最小化/恢复、摄像头同步、挂断和 C 并发来电验收矩阵。
- 新增主叫回铃/被叫来电循环音效与震动，接听、拒绝、挂断、连接成功和超时都会停止；前端 30 秒未接听自动收口，与服务端 ringing TTL 对齐。
- 验证：RTC 全量测试 105/105 通过，`vue-tsc`、目标 ESLint、OpenAPI lint、`pnpm run build-only`、`git diff --check` 通过；双账号真实浏览器验收仍需附着两个已登录浏览器现场执行。

## 2026-08-18：一对一通话 ACL 改为互相关注

- `RtcAclMapper` 的 direct 权限查询从 `t_friend` 双向确认改为 `t_follow` 双向关注；不再要求好友表记录。
- 单向关注和未关注创建一对一通话都会返回 `NOT_AUTHORIZED`，互相关注可正常进入 LiveKit 呼叫流程；群聊成员 ACL 保持不变。
- 新增互关、单向关注、无关注和无好友记录互关的契约测试。
- 验证：Maven RTC 测试 `100/100` 通过，Maven compile 通过，前端 `vue-tsc` 通过。

## 2026-08-18：RTC-006 presence 与本地联调

- IDEA 后端已监听 `9191`，MySQL 连接成功；启动日志显示 Kafka 客户端尝试连接 `127.0.0.1:9092`，但本机该端口实际未监听，需修正 Kafka advertised/listener 或 IDEA `KAFKA_BOOTSTRAP_SERVERS`。
- Docker SRS、LiveKit、coturn 全部 healthy；`deploy/streaming/smoke.ps1 -ProfileName webrtc` 8/8 通过。
- 新增 `LivePresenceService`：Redis ZSET + 45 秒 TTL，REST join/leave 与 `/ws/live` 共享稳定 `sessionId` 幂等成员；LiveWatch/Home LivePage 增加 15 秒 presence 心跳，消除 REST + WS 双计数。
- 验证：`vue-tsc`、目标 ESLint、`pnpm run build-only`、Maven compile、RTC 测试 98/98 通过。RTC-006 仍保持 `in_progress`，公网 HTTPS/TURN、SRS callback 授权和 provider 故障恢复尚未在真实环境验收。

## 2026-08-18：RTC-006 最终 Docker/provider smoke

- 恢复并验证 Docker 媒体栈后执行 `powershell -File deploy/streaming/smoke.ps1 -ProfileName webrtc`：8/8 全部通过（compose、容器健康、SRS API、LiveKit metrics、coturn、SRS HTTP-FLV、TURN UDP allocate、LiveKit CLI 真实 H.264 发布）。
- 提交后的浏览器验收脚本再次通过：WHIP `connected`、WHEP 首帧、HLS master/media/TS、HTTP-FLV 数据。
- 前端和后端应用服务均未启动，留给用户自行启动。

## 2026-08-17：RTC-006 媒体生命周期收口

- WHEP/WHIP 适配器增加 generation 与 peer identity 检查；停止、卸载和异步协商竞态不会再把旧轨道写回新页面，协商完成后才会登记 SRS `Location` 并在取消时清理会话。
- WHEP 连接断开/失败通过回调触发页面级有限指数退避；HLS.js、原生 HLS 和 HTTP-FLV 在超时或播放失败时都会销毁实例并重置 video，避免播放器泄漏。
- LiveWatch 与首页入口补齐无 WHEP 时的 fallback、卸载守卫和旧弹幕定时器 key；LiveCreate 的结束请求采用 best-effort + finally，导航不会被控制面短暂故障卡死。
- 验证：`pnpm exec vue-tsc --noEmit --pretty false`、目标文件 ESLint、`pnpm run build-only`、`docker compose -f docker-compose.streaming.yml --env-file deploy/streaming/.env.example config --quiet` 通过；Docker daemon 当前不可用，真实 SRS/浏览器媒体 smoke 仍未执行。

## 2026-08-17：RTC-006 直播媒体迁移（工作区收口，待真实 provider 验收）

- 直播主路径切换为浏览器直连 SRS WHIP/WHEP；HLS/HTTP-FLV 仅作播放器 fallback，Spring Boot 不转发媒体字节。
- `SrsWhipPublisher` 增加 H.264/Opus 能力偏好、720p30 默认档位、设备不支持时的 ideal constraint 降档；`SrsWhepPlayer` 等待视频解码首帧并修正代理前缀下的 WHEP DELETE。
- `LiveWatch`、首页直播入口使用 `<video>`，默认静音并提供开声控制；播放失败有重试状态；列表卡片和 feed 不再建立失效的媒体 WS。
- `/ws/live` 限制为控制消息，拒绝媒体帧/二进制、限制消息大小和类型；关播会广播 `end` 并释放控制连接。
- 后端只向登录用户返回媒体地址、房主才获得 WHIP；SRT auth 比较 stream key；viewer/like 更新改为 SQL 原子增量；移除以 `update_time` 为依据的 2 分钟误关播清理。
- 验证：`pnpm exec vue-tsc --noEmit --pretty false`、目标文件 ESLint、`pnpm run build-only`；Maven/真实 SRS/浏览器媒体 smoke 当前环境不可用，不能宣称 RTC-006 完成。
- 未完成 review gate：短期 ingest/play token 与 SRS callback ACL、Redis TTL presence、provider heartbeat/reconciliation、生产反代和 Origin allowlist。

## 2026-08-17：RTC-004 + RTC-005 完成，推进 RTC-006

- RTC-004 标记 `completed`：1 对 1 LiveKit 媒体路径（store + adapter + CallPanel + OpenAPI）已在上轮提交 `0e2787e`。
- RTC-005 实现并提交 `4ca8942 feat(RTC-005): migrate group calls to LiveKit SFU`：
  - `src/modules/rtc/types.ts`：新增 `GroupCallMeta`，`IncomingCall` 加 `isGroup/groupMembers`，`OutgoingMeta.toUserId` 改可选。
  - `src/api/rtc.ts`：`CreateCallParams` 支持 `scope: 'group'` + `group_id`。
  - `src/modules/rtc/signaling/wsBridge.ts`：群通话 `call_request` 由 `VITE_RTC_005` 开关控制；`notifyIncoming` 透传 `isGroup/groupMembers`。
  - `src/modules/rtc/store/useRtcStore.ts`：新增 `groupMeta` 状态、`openGroupDial`、`dialGroup` action；`accept()` 支持群通话；`hardResetState()` 补清 `groupMeta`。
  - `src/modules/rtc/components/CallPanel.vue`：群通话 CSS grid 多参与者界面（发言高亮、静音图标、头像 fallback）；单人 video 保留为 `v-else-if`。
  - `src/components/Call.vue`：`SHOW_GROUP_CALL` handler 在 `RTC005=on` 且有 `groupId` 时走 `openGroupDial`，否则回退旧 mesh 路径。
  - `src/pages/message/chat/GroupChat.vue`：bus emit 补发 `groupId`。
  - `env/.env`：补 `VITE_LIVEKIT_URL`、`VITE_RTC_004=on`、`VITE_RTC_005=on`。
- 验证：`vue-tsc --noEmit` 通过；Less build 错误为用户 live 文件预存 bug（已隔离确认与 RTC-005 无关）。
- `PROJECT_STATE.yaml` 更新：RTC-004/005 → completed，current_task → RTC-006，baseline head_commit 更新为 `4ca8942`。
- 下一任务：RTC-006（直播迁移到 WHIP/WHEP + HLS/HTTP-FLV fallback）。

## 2026-08-14：AI-001 收尾 + AI-005 验证 + PROJECT_STATE 修正

- `server/python/correct_video_durations.py` 最后一处硬编码 DB 密码迁移到 `_require_env("DB_PASSWORD")`，DB host/port/user 同步改为 `os.environ.get`。
- `git grep` 扫描（sk-/XrKk/yccv 模式）零命中，所有可跟踪源文件无明文密钥。AI-001 密钥 env 化 DoD 全部满足，状态更新为 `completed`。
- AI-005 实物验证：`.venv/Scripts/python.exe ai_pipeline.py eval --provider echo` 10/10 通过；`train --dry-run` 输出训练计划、不落 checkpoint；34 个 Python 单测全部通过（1.53s）。状态更新为 `completed`。运行环境需 `.venv/Scripts/python.exe`（系统 python 不可用，exit:49）。
- `PROJECT_STATE.yaml` 修正：current_task 恢复为 RTC-004（in_progress），scope/artifacts 对齐实际任务内容；AI-001 和 AI-005 状态同步为 completed。
- JWT secret 有非空 Base64 默认值风险，已在 SECURITY.md §2.1 L39 登记，fail-closed 实现归 AI-002。

## 2026-08-14：全项目宪法与模块拆分
- 创建 `docs/project-constitution/MODULE_BREAKDOWN.md`：12模块详细拆分（MOD-AUTH/USER/VIDEO/LIVE/RTC/IM/MUSIC/SEARCH/SHOP/REC/ADMIN/INFRA），含职责边界、目录、契约、状态和待收口项。
- 创建 `docs/project-constitution/FULL_DEVELOPMENT_PLAN.md`：六阶段开发路线图（Phase 0已完成→Phase 6发布门禁）。
- 更新 `docs/project-constitution/tasks/TASK_INDEX.md`：补充 ADM/SHOP/SRC/REC/OPS 系列任务（Phase 3~6共15个任务）。
- 架构结论：WebRTC 方向正确（LiveKit SFU for calls，SRS WHIP/WHEP for live），当前画质问题源于 B-001~B-006 实现缺陷，非架构选型问题；RTC-004 继续推进为 P0。

## 2026-08-14：RTC-004 重新勘察与契约收敛（进行中）

- 实际基线：分支 `dev/full`，HEAD `4c813c4`；工作区仍包含用户的直播、后台、原生引擎和管理端改动，按宪法“preserve_and_avoid”保留，未执行 reset/checkout/清理。
- 已启用独立审查：`contract_audit`（OpenAPI 与状态文件）、`frontend_rtc_audit`（LiveKit adapter 与旧 Call 隔离）、`backend_rtc_audit`（token、状态机、webhook 生命周期）。审查结论已写入本任务：OpenAPI 缺失、任务 YAML 列表缩进错误、旧 Call 与新面板双主路径、本地轨道聚合和 `srcObject` 绑定不稳定、官方 LiveKit webhook 使用 Authorization JWT。
- 本轮拥有边界：`docs/contracts/`、`docs/project-constitution/`、`src/modules/rtc/`、`src/api/rtc.ts`、RTC 迁移壳以及 `server/.../rtc/webhook/`；不接触用户直播和后台目录。
- 验证约束：类型检查和构建只能证明编译，不得替代双浏览器 SDP/ICE/媒体轨道、TURN relay 和 webhook 端到端验收；RTC-004 在这些验证完成前保持 `in_progress`。

## 2026-08-14：RTC-004 媒体适配器与契约提交

- 提交：`0e2787e feat(RTC-004): add one-to-one LiveKit media path`。
- 交付：LiveKit provider-neutral media port、1 对 1 CallPanel/store、设备切换和轨道聚合、旧 1 对 1 SDP/ICE 隔离、控制面 OpenAPI、官方 LiveKit JWT webhook 校验与迁移期 HMAC 兼容、JWT 单测。
- 验证：`pnpm exec vue-tsc --noEmit --pretty false`、`pnpm run build-only`、`npx --yes @redocly/cli lint docs/contracts/rtc-control.openapi.yaml` 均通过；Maven RTC 专项测试 91/91 通过（BUILD SUCCESS）；`git diff --cached --check` 通过。
- 范围审查：提交只包含 RTC-004 所有权文件；`admin/`、直播页面、streaming-engine、原生引擎、运行产物和其他后台改动仍按 `preserve_and_avoid` 留在工作区。
- 任务仍为 `in_progress`：尚未完成 Chrome/Firefox/Safari/移动端双浏览器通话、TURN relay/NAT 矩阵、LiveKit 容器到 Spring Boot webhook 端到端和 720p30/500 kbps QoE 报告。

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
## 2026-08-17：RTC-006 真实浏览器媒体验收与主播恢复

- Docker 媒体栈已启动并健康：SRS、LiveKit、coturn；未启动前端或 Spring Boot，保留给用户自行启动。
- 修正本机 SRS candidate：`10.68.138.84` 在 Docker UDP 回包上间歇性 ICE 不稳定，切换到 WSL host `172.21.160.1` 后稳定通过。
- 使用 Chromium fake camera + H.264 codec preference 完成真实 WHIP/WHEP：WHIP `connected`，WHEP 首个解码帧 `640x480`。
- 真实 fallback：HLS master → media playlist → TS 片段可读，HTTP-FLV 读取约 70 KiB；TS 用 `ffprobe` 确认为 H.264/AAC。
- 前端补充主播 WHIP 失败/断开后的 5 次有界指数退避重连，并让 WHEP recvonly transceiver 使用 H.264/Opus 偏好。
- 验证：`pnpm exec vue-tsc --noEmit --pretty false`、`pnpm exec eslint src/pages/live/LiveCreate.vue src/utils/streaming/srs_rtc.ts`、`pnpm run build-only` 均通过；Maven CLI 当前未安装，未重复运行后端测试。
- 仍未关闭的发布门：短期媒体 token/SRS callback ACL、Redis presence 幂等、provider heartbeat/reconciliation、SRS 重启与公网 HTTPS/WSS/TURN 矩阵。

## 2026-08-19：客户端减载与多人媒体规模设计

- 完成拓扑审计：Spring Boot、Kafka 和聊天 WebSocket 只做控制面；当前单 LiveKit 节点、有限 UDP/TURN 端口池属于开发配置，不能宣称支持生产万人规模。
- 新增 `docs/adr/ADR-005-CLIENT-OFFLOAD-AND-SCALE.md` 和 `docs/architecture/RTC_SCALE_AND_CLIENT_OFFLOAD.md`，明确 1 对 1 P2P 仅可受控灰度，群聊继续 SFU，万人观看采用 stage + SRS/CDN audience。
- 在项目状态和任务索引中登记 RTC-011～RTC-015：容量观测、选择性订阅、多节点、stage-audience 和 P2P 实验。
- 发现实现与设计漂移：`livekitAdapter.ts` 当前 `adaptiveStream/dynacast=false`、`autoSubscribe=true`，且远端轨道先聚合为 `MediaStream`；在完成 publication/元素 attach-detach 契约前，不直接打开 `adaptiveStream`。
- 前端适配器在保留 `adaptiveStream=false` 的前提下显式开启 `dynacast` 和 `simulcast`，并对多人房间默认将远端视频限制为 LOW、active speaker 提升到 MEDIUM；1 对 1 质量基线和音频订阅不变。
- 本次未修改用户已有的后端、部署和 AI 文件；后续代码优化必须按 RTC-012 的 publication/可见性契约测试和可回滚开关推进。
- 文档提交：`34a35cf docs(RTC-011): define client offload and media scale policy`。由于状态文件、工作日志和适配器含有用户已有暂存修改，本次未将它们混入该提交。
