# RTC-006：直播 WHIP/WHEP 迁移

## 状态与边界

- 状态：`in_progress`
- 负责人：`main-dev-agent`
- 开始时间：`2026-08-17`
- 依赖：RTC-002、RTC-003
- 负责目录：`src/pages/live/`、`src/modules/live/`、`server/.../live/`、SRS 配置和直播契约
- 禁止修改：把 `/ws/live` 二进制/文本继续当生产媒体总线、继续维护 SDP echo、引入第二套默认媒体服务

## 目标

主播使用 WHIP（SRT/RTMP 兼容），观众优先 WHEP/WebRTC，按规模和浏览器 fallback 到 LL-HLS/HLS/HTTP-FLV；聊天/点赞/presence 单独走控制 WS。

## 当前已交付（工作区）

- `SrsWhipPublisher`：浏览器轨道直接向 SRS WHIP 发布，默认 H.264/Opus、720p30、2.5 Mbps，并按设备能力使用 ideal constraints。
- `SrsWhepPlayer`：WHEP 收流，等待视频解码首帧；会话有 generation/peer identity 保护，停止或卸载时取消未完成协商并释放远端轨道；连接进入失败/断开状态时由页面有限退避重建。
- 主播 WHIP 连接进入 `disconnected`/`failed` 后按 1s/2s/4s/8s/16s 有界退避重建，最多 5 次；卸载、结束直播或新一代会话会取消恢复任务。
- WHIP/WHEP 协商在收发两端都偏好 H.264 + Opus；WHEP recvonly transceiver 不再因为没有 sender track 而跳过 codec preference。
- fallback 播放器：HLS.js、原生 HLS、HTTP-FLV 在 manifest、autoplay 或首帧失败时销毁实例并清理 `<video>`，避免切换页面后残留媒体管线。
- `LiveCreate`、`LiveWatch` 和首页直播入口已不再使用自定义 WebCodecs 帧作为媒体主路径。
- `/ws/live` 仅处理聊天、点赞、viewer_count；媒体帧、二进制消息和旧 SDP echo 已封口。
- 列表卡片使用封面，不再为每张卡片建立永远等不到媒体帧的控制 WS。
- 后端只向登录用户返回媒体地址；房主才获得 WHIP 地址；stream key 每次开播随机生成。
- viewer/like SQL 增量改为原子更新，直播控制消息有大小、类型和文本边界。
- SRS `on_publish/on_play/on_unpublish/on_stop` callback 已拆分，具备 callback secret、短期 token、stream key、房间/主播 ACL、provider session 幂等和 bounded reconciliation；实现提交为 `25e440a`。
- 新增 `server/sql/migration_038_live_provider_session.sql`，036/037 保留给 AI-003/AI-004，不重编号。
- `3bc701b`、`02fa7b8`、`876eb57` 增加回调事务房间锁、SRS provider generation CAS 和 `on_unpublish/on_stop` 的签名媒体 token 校验；`94d349d` 拒绝缺失/非法 presence session；`9c70437`、`ec3f286` 补齐 generation、分页和 grace 单测。
- `2c2ef00` 兼容旧房间的空字符串 provider generation，并新增 `migration_039_normalize_provider_session_id.sql`；`a54c83d` 将缺流、代际变化和 grace 到期的 retirement/CAS 收敛放入同一事务，避免 stale reconciliation 阻塞同 SRS server 重连；`99acc27` 使用 Redis Lua 原子维护 presence TTL/key 生命周期，给 SRS API 增加有界 connect/read timeout，并让畸形 callback URL 参数 fail-closed。
- `597c041` 将浏览器媒体 smoke 的 SRS session `Location` 改为布尔结果，避免在验收输出中泄露临时 token；修复后以 Chrome/Playwright 重跑 WHIP、WHEP 首帧、HLS 和 HTTP-FLV 全部通过。

## 必查兼容项

- `src/utils/streaming/webcodecs_sender.ts` 和 player 当前存在 `/api/live/webrtc/offer` 路径，与控制面 `/api/live/engine/webrtc/offer` 不一致；迁移必须删除伪造 offer 路径或明确返回 `provider_not_ready`，不能留下第二条 API。
- legacy WebCodecs `copyTo()`、`getVideoTracks()`、codec await 和空 canvas 缺陷只做风险记录，不能成为新生产链路。
- WebCodecs sender/player 的旧 `webrtc` 模式现在在入口处 fail-closed，不再创建 PeerConnection 或请求 `/api/live/webrtc/offer`；直播生产路径继续使用 WHIP/WHEP。

## DoD

- [x] `/ws/live` 只负责控制，媒体不进入 Spring/Kafka/chat WS。
- [x] 旧 `/api/live/engine/webrtc/offer` 返回 410，前端不再导出伪造 offer API。
- [x] 前端 WHEP/HLS/HTTP-FLV 适配器、代理前缀和播放器清理路径已实现。
- [x] WHEP/WHIP 异步竞态、断线回调和页面级有限退避恢复已实现；恢复次数有上限，不把控制 WS 重连误当作媒体恢复。
- [x] 本机 Docker 媒体验收通过：`SRS_RTC_CANDIDATE=172.21.160.1` 时 WHIP `connected`、WHEP 首帧 `640x480`、HLS master/media playlist 与 TS 片段、HTTP-FLV 数据均可读；TS 经 `ffprobe` 确认为 H.264/AAC。2026-08-20 在 `597c041` 后使用 Chrome/Playwright 重跑通过且输出不含 session URL/token。
- [ ] HTTPS/公网 candidate 下的主播重连、跨网络 ICE/TURN 和浏览器矩阵仍待发布环境验收；前端已具备有界恢复逻辑。
- [x] viewer presence 已改为 `(room,user,session)` Redis TTL 成员；REST join/leave 与控制 WS 共用幂等 session，避免数据库和连接数双计。仍需在真实 Redis 多实例和异常断开环境复测。
- [x] 主播所有权、短期 ingest/play token、SRS callback、房间状态和 viewer 权限的 Java/MockMvc 契约测试已覆盖；当前 Maven 全套 `148/148` 通过。
- [ ] 真实 SRS callback HTTP 运行验收（包括 provider 返回非 2xx、缺失/错误媒体 token 时拒绝媒体会话）。
- [x] 控制面已增加可开关的 HMAC 短期媒体 token、SRS callback 校验端点和 token 单元测试；默认开发 profile 仍关闭，生产需挂载 `deploy/streaming/srs-auth.conf.example` 的 callback 段并完成真实 provider 验收。
- [x] provider session projection、heartbeat/reconciliation、bounded grace 和异常 generation 防护已实现并有单测。
- [x] provider 缺流/代际变化的精确 retirement 与 room CAS 已事务化；旧 generation sentinel 已有前向迁移，presence Lua 生命周期和 SRS API timeout 已有单测及本机 Redis 单实例验证。
- [ ] 真实 SRS 重启、主播异常断开、STARTING/DEGRADED/ENDING 状态恢复验收。
- [ ] 2026-09-30 前完成灰度指标采集，按路线图达成 legacy 退役门槛。

## Review Gate 与剩余风险

1. **媒体授权**：callback secret 与短期 ingest/play token 已实现，启用媒体鉴权时 token secret 和 callback secret 均要求至少 32 字符；生产 callback 配置仍需渲染、内网限制和真实 SRS 验收。
2. **presence**：已引入 Redis TTL presence 和唯一 session id；Lua touch/leave/count 为原子操作且 Redis 不可用时 fail-closed，单实例已实测，但仍需真实 Redis 多实例、异常断开和数据库重启场景验证。
3. **生命周期**：不能用 `update_time` 判断媒体存活；provider heartbeat/reconciliation、generation retirement 和 bounded grace 已实现，真实 SRS restart/断开收敛仍待验收。
4. **部署**：生产网关必须反代 `/media/srs`、`/media/srs-http`，配置真实 SRS candidate、HTTPS/WSS、Origin allowlist 和 TURN/ICE 策略。
5. **真实性**：类型检查、构建和 HTTP 端口健康不能替代真实浏览器媒体验证；未验证项必须保持未勾选。
6. **收敛契约**：`docs/contracts/live-media-reconciliation.md` 与 provider session/reconciliation worker 已随 `25e440a` 提交；文档/单测不替代真实 provider runtime 验收。
