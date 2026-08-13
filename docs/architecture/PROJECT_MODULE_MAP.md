# 项目模块地图

这份地图把现有仓库按业务边界和长期可维护性重新分组。目录是实现位置的线索，不等于模块可以互相任意调用；跨模块交互必须通过 API、领域端口、事件或 provider adapter。

## 1. 业务模块

| 模块 | 用户能力 | 当前入口 | 长期拥有边界 | 主要依赖 |
|---|---|---|---|---|
| 身份与账号 | 登录、注册、用户资料、设备、权限 | `src/pages/login/`、`src/pages/me/`、`server/.../controller/AuthController.java`、`UserController.java` | 用户身份、JWT、设备会话、隐私设置 | 数据库、邮件、文件 |
| 内容与互动 | 短视频、图文、音乐、点赞、评论、收藏、关注 | `src/pages/home/`、`src/pages/people/`、`server/.../controller/VideoController.java`、`PostController.java` | 内容生命周期、互动关系、审核输入 | 身份、文件、搜索、推荐 |
| 消息与群组 | 私聊、群聊、已读、通知、分享 | `src/pages/message/`、`src/api/message.ts`、`server/.../MessageController.java`、`GroupChatController.java` | 消息 schema、群成员、兼容投影、outbox、去重 | 身份、Kafka/WS、RTC roster |
| RTC 通话 | 1 对 1 音视频、群通话、屏幕共享、通话记录 | `src/components/Call.vue`、`server/.../websocket/ChatWebSocketHandler.java` | CallSession、Participant、CallEvent、provider token、状态机 | 身份、群组、LiveKit、coturn |
| 直播 | 开播、观看、聊天、点赞、连麦、回放 | `src/pages/live/`、`server/.../LiveController.java`、`StreamController.java` | LiveRoom、主播生命周期、ingest/play token、presence、审核 | 身份、SRS、CDN、录制 |
| 商城与交易 | 商品、店铺消息、收藏/购物车、钱包 | `src/pages/shop/`、`server/.../controller/ShopController.java`、`WalletController.java` | 商品和交易状态、订单/钱包审计 | 身份、文件、消息 |
| 管理与审核 | 用户/作品/直播审核、运营指标、后台大屏 | `admin/`、`server/.../admin/` | 审核工作流、运营视图、管理员权限 | 所有业务读模型、审计 |

## 2. 平台和媒体模块

| 模块 | 责任 | 当前目录 | 目标架构 |
|---|---|---|---|
| `rtc-signaling` | 信令 envelope、路由、顺序、幂等、ACL、重连 | `src/utils/socket.ts`、`server/.../websocket/` | 控制事件专用 WS；不传媒体 |
| `rtc-media-adapter` | LiveKit 房间、轨道、token、webhook | 尚未形成独立目录；当前散在 `Call.vue`/engine | `src/modules/rtc` + `server/.../rtc/provider` |
| `ice-edge` | STUN/TURN、临时凭据、NAT 诊断 | 未落地；compose 有草案 | coturn pool，UDP/TCP/TLS 443 |
| `live-ingest` | WHIP/SRT/RTMP 主播推流和鉴权 | `LiveCreate.vue`、`streaming-engine/network` | SRS ingest adapter + room binding |
| `live-distribution` | WHEP/WebRTC、LL-HLS/HLS/HTTP-FLV、CDN | `LiveWatch.vue`、`LiveStreamHandler` | SRS edge + CDN fallback |
| `media-quality` | 设备能力、编解码、simulcast/ABR、QoE | `src/utils/streaming/`、session stats | 独立质量策略和指标模块 |
| `recording-transcode` | Egress/DVR、FFmpeg 转码、缩略图、VOD | `server/python/`、部分 native engine | 异步 worker + 对象存储 |
| `native-engine` | 可选采集、滤镜、编码、推流 adapter | `streaming-engine/`、`server/.../engine/` | 可独立发布的 native artifact，不能阻断浏览器主路径 |
| `observability-security` | QoE、日志、告警、审计、限流、隐私 | `admin/`、后端日志、`docs/verification/` | 统一指标/trace、安全基线和 runbook |

## 3. 依赖方向

```text
身份与账号
    ├── 内容与互动 ─── 搜索/推荐/AI
    ├── 消息与群组 ─── RTC roster / call projection
    ├── RTC 通话 ─── LiveKit + coturn
    ├── 直播 ─── SRS + CDN + recording
    ├── 商城与交易 ─── 消息/文件/钱包
    └── 管理与审核 ─── 各业务只读模型 + 审计

所有媒体业务 ─── media-quality / observability-security
所有异步写入 ─── outbox / idempotency / event ledger
```

依赖只允许从上游领域端口进入，不允许 `Call.vue` 直接操作群组数据库，也不允许 `LiveStreamHandler` 直接修改媒体服务以外的业务状态。

## 4. 当前实现到目标实现的拆分

### RTC 通话

当前 `Call.vue` 同时包含 UI、状态、设备、PeerConnection、信令和统计，后续拆为：

```text
rtc-store / call-state-machine
rtc-signaling-client
rtc-device-manager
rtc-media-adapter (LiveKit)
rtc-participant-grid
rtc-call-controls
rtc-qoe-reporter
```

### 直播

当前 `LiveCreate.vue`、`LiveWatch.vue`、`WebCodecsSender/Player` 和 `LiveStreamHandler` 共同承担控制与媒体，后续拆为：

```text
live-room-store
live-control-client (chat/like/presence)
live-publisher (WHIP/SRT/RTMP adapter)
live-player (WHEP/WebRTC -> LL-HLS/HLS/HTTP-FLV)
live-quality-policy
live-qoe-reporter
legacy-live-bridge (temporary)
```

## 5. 模块开发顺序

1. 工程治理与契约：宪法、状态机、schema、测试门禁。
2. 媒体基础设施：LiveKit、coturn、SRS、健康检查和 token。
3. RTC 控制面：CallSession、Participant、ACL、幂等事件和记录。
4. 1 对 1 provider adapter，再迁移群聊 SFU。
5. 直播 WHIP/WHEP 与 fallback，控制/媒体连接分离。
6. QoE、ABR、弱网恢复、录制转码和运营观察面。
7. Legacy 退役、native 收口、安全/负载/故障发布门禁。

## 6. 模块 Definition of Done

模块完成不等于页面能显示：

- 有明确的输入、输出、错误、状态和幂等契约；
- 有权限、超时、重连、恢复和回滚方案；
- 有单元/契约/集成测试及真实运行环境验证；
- 有指标和日志关联键；
- 有拥有目录和禁止越界边界；
- 有迁移期间的旧路径退出日期。
