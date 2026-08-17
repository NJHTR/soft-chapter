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
- fallback 播放器：HLS.js、原生 HLS、HTTP-FLV 在 manifest、autoplay 或首帧失败时销毁实例并清理 `<video>`，避免切换页面后残留媒体管线。
- `LiveCreate`、`LiveWatch` 和首页直播入口已不再使用自定义 WebCodecs 帧作为媒体主路径。
- `/ws/live` 仅处理聊天、点赞、viewer_count；媒体帧、二进制消息和旧 SDP echo 已封口。
- 列表卡片使用封面，不再为每张卡片建立永远等不到媒体帧的控制 WS。
- 后端只向登录用户返回媒体地址；房主才获得 WHIP 地址；stream key 每次开播随机生成。
- viewer/like SQL 增量改为原子更新，直播控制消息有大小、类型和文本边界。

## 必查兼容项

- `src/utils/streaming/webcodecs_sender.ts` 和 player 当前存在 `/api/live/webrtc/offer` 路径，与控制面 `/api/live/engine/webrtc/offer` 不一致；迁移必须删除伪造 offer 路径或明确返回 `provider_not_ready`，不能留下第二条 API。
- legacy WebCodecs `copyTo()`、`getVideoTracks()`、codec await 和空 canvas 缺陷只做风险记录，不能成为新生产链路。

## DoD

- [x] `/ws/live` 只负责控制，媒体不进入 Spring/Kafka/chat WS。
- [x] 旧 `/api/live/engine/webrtc/offer` 返回 410，前端不再导出伪造 offer API。
- [x] 前端 WHEP/HLS/HTTP-FLV 适配器、代理前缀和播放器清理路径已实现。
- [x] WHEP/WHIP 异步竞态、断线回调和页面级有限退避恢复已实现；恢复次数有上限，不把控制 WS 重连误当作媒体恢复。
- [ ] 真实 WHIP ingest、WHEP 首个解码帧、主播重连和 HLS/HTTP-FLV fallback 通过（需要 Docker + HTTPS 浏览器）。
- [ ] viewer presence 以 `(room,user,session)` 幂等，数据库和连接数不双计（当前仍有 REST + WS 双投影风险）。
- [ ] 主播所有权、短期 ingest/play token、SRS callback、房间状态和 viewer 权限有契约测试。
- [ ] provider 重启、异常断开、STARTING/DEGRADED/ENDING 状态恢复通过。
- [ ] 2026-09-30 前完成灰度指标采集，按路线图达成 legacy 退役门槛。

## Review Gate 与剩余风险

1. **媒体授权**：随机 stream key 只是不可预测能力值，不是短期 token；上线前需要 SRS `on_publish/on_play` 或网关签名 URL，并定义撤销和过期错误码。
2. **presence**：当前 `viewerCount` 的 REST join/leave 与内存 WS roster 仍可能分叉；下一任务必须引入 Redis TTL presence 和唯一 session id。
3. **生命周期**：不能用 `update_time` 判断媒体存活；当前已移除会误杀 2 分钟静默直播的清理任务，待 provider heartbeat/reconciliation 接入后再自动结束。
4. **部署**：生产网关必须反代 `/media/srs`、`/media/srs-http`，配置真实 SRS candidate、HTTPS/WSS、Origin allowlist 和 TURN/ICE 策略。
5. **真实性**：类型检查、构建和 HTTP 端口健康不能替代真实浏览器媒体验证；未验证项必须保持未勾选。
