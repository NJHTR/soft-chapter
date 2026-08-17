# 直播媒体契约（RTC-006）

状态：`in_progress`。本文描述当前工作区已经实现的边界，以及上线前仍必须通过的发布门。

## 1. 角色与职责

```text
主播浏览器 -- WHIP/WebRTC --> SRS
观众浏览器 -- WHEP/WebRTC --> SRS
观众浏览器 -- HLS/HTTP-FLV fallback --> SRS HTTP server
主播/观众 -- /ws/live/{roomId} --> Spring Boot 控制面（聊天、点赞、人数）
```

Spring Boot 不接收或转发 SDP、RTP、编码帧和音频帧。`/ws/live` 收到的 `frame`、`media` 和二进制消息必须丢弃；旧 WebCodecs sender/player 只保留为 legacy 记录，不得重新接入生产路径。

## 2. 控制面接口

### `GET /api/live/{roomId}`

- 需要登录才能返回 `media` 地址；未登录只返回房间公开元数据。
- `media.whepUrl`、`media.hlsUrl`、`media.httpFlvUrl` 是观众出口。
- 只有房主身份才返回 `media.whipUrl` 和 `media.rtmpUrl`。
- `media.ingestMode` 为 `browser-whip` 或 `native`，同一房间只能有一个生产者。

### `POST /api/live/{roomId}/join`

- 需要登录，并且房间必须处于 `LIVE`。
- 返回当前计数和观众播放地址。
- 当前实现使用数据库计数加控制 WS 连接投影；`(room,user,session)` 的 Redis 幂等 presence 尚未完成，不能作为多实例发布门。

### `POST /api/live/{roomId}/leave`

- 需要登录；重复调用不得把计数减到零以下。
- 浏览器卸载、网络断开时的自动释放依赖下一项 presence 任务，当前仅由前端卸载钩子尽力调用。

### `POST /api/live/{roomId}/like`

- 需要登录且房间处于 `LIVE`。
- 数据库使用原子增量，控制 WS 只广播展示事件；客户端不得同时将 WS echo 和本地乐观值各加一次。

## 3. SRS 媒体接口

SRS 端点由 `LiveMediaProperties` 生成。开发环境通过 Vite `/media/srs` 和 `/media/srs-http` 代理；生产环境必须由网关配置同名反代或注入绝对公共 URL。

- WHIP：`POST /rtc/v1/whip/?app=live&stream={streamKey}`
- WHEP：`POST /rtc/v1/whep/?app=live&stream={streamKey}`
- HLS：`/live/{streamKey}.m3u8`
- HTTP-FLV：`/live/{streamKey}.flv`
- WHEP/WHIP 会话结束时使用响应 `Location` 执行 `DELETE`；代理前缀必须保留。
- 浏览器端媒体会话必须带 generation/peer identity；组件卸载或新一轮协商使旧操作失效，且在收到 `Location` 后才能登记并清理 provider 会话。

当前 stream key 是每次开播随机生成并仅通过登录后的控制接口返回。短期 ingest token、播放 token、SRS `on_publish/on_play` 回调和撤销语义仍是 RTC-006 发布阻断项；随机 key 不能被当作完整授权系统。

## 4. 编解码与降级

- 浏览器主路径优先 H.264 视频 + Opus 音频；发送端在能力可用时设置 codec preference。
- 默认 720p30 / 2.5 Mbps；主播可选 1080p30、720p30、480p30，设备不支持时使用 ideal constraint 降档。
- 观众先 WHEP；协商或首个解码帧失败后按 HLS.js、原生 HLS、mpegts.js HTTP-FLV 顺序降级。
- WHEP 建立后若连接持续处于 `disconnected`/`failed`，页面按有限指数退避重新协商，达到上限后保持可见错误或 fallback；不能无限创建 PeerConnection。
- 自动播放遵守浏览器策略：播放器默认静音，用户通过音量按钮开启声音。

## 5. 控制 WS 安全边界

- 握手必须带 JWT；主播 role 还必须匹配房主。
- 控制消息最大 8 KiB；聊天文本最多 500 字符，点赞 count 为 1-5。
- 媒体帧和二进制消息不广播。
- 当前 Origin allowlist、一次性 WS ticket、跨实例 presence 和消息限流仍归 RTC-010/后续 RTC-006 review gate。

## 6. 验收矩阵

在 Docker、后端和 HTTPS 浏览器环境可用后，必须记录以下结果才可将 RTC-006 标记 `completed`：

| 场景 | 验收信号 |
|---|---|
| WHIP 发布 | 发布成功、SRS 有流、主播重连不产生第二个 producer |
| WHEP 播放 | 首个 `requestVideoFrameCallback`/解码帧时间，音视频可听可见 |
| fallback | WHEP 失败后 HLS 或 HTTP-FLV 在 10 秒内首帧 |
| 权限 | 非房主不能拿 WHIP；未登录不能拿媒体地址；错误 key 被 SRS 拒绝 |
| presence | 重复 join/leave、刷新、崩溃和多实例不双计数 |
| 控制面 | chat/like 可用，frame/media/binary 不进入广播 |
| provider 故障 | SRS 重启、主播网络断开、观众网络切换均有明确恢复或结束状态 |
