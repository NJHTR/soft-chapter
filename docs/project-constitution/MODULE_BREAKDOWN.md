# Douyin 模块拆分详情

**版本：** 1.0 | **日期：** 2026-08-14

每个模块定义：职责、主要目录、对外契约、当前状态、已知问题、下一步任务。

---

## MOD-AUTH 认证与账户

**职责：** 注册、登录（密码/验证码/第三方）、JWT 颁发、设备管理、会话过期、找回密码  
**主要目录：**
- `server/.../controller/AuthController.java`
- `server/.../controller/EmailController.java`
- `server/.../controller/LoginHistoryController.java`
- `server/.../service/SessionService.java`
- `src/pages/login/`

**对外契约：** JWT Bearer token，`/api/auth/**`  
**状态：** 基本完成  
**待收口：** Origin allowlist（B-007）、短期 token 刷新策略、第三方登录安全审计

---

## MOD-USER 用户档案与社交

**职责：** 用户资料编辑、头像、关注/粉丝、访客记录、名片、面对面加友、扫码  
**主要目录：**
- `server/.../controller/UserController.java`
- `server/.../service/UserService.java` / `UserProfileService.java`
- `server/.../mapper/FollowMapper.java` / `FriendMapper.java` / `VisitorMapper.java`
- `src/pages/me/` / `src/pages/people/`

**对外契约：** `/api/user/**`、`/api/follow/**`  
**状态：** 基本完成  
**待收口：** 用户隐私字段脱敏日志

---

## MOD-VIDEO 视频内容

**职责：** 上传分片、封面提取、编码、feed 流（竖刷/横刷/社区）、视频详情、评论、点赞、收藏、举报、编辑器  
**主要目录：**
- `server/.../controller/VideoController.java` / `ChunkUploadController.java`
- `server/.../service/VideoService.java` / `VideoMergeService.java` / `CoverService.java`
- `server/.../kafka/VideoEventConsumer.java` / `CoverExtractConsumer.java`
- `src/pages/home/` / `src/components/slide/` / `src/pages/home/Publish.vue`

**对外契约：** `/api/video/**`、Kafka topic `video.events`  
**状态：** 基本完成  
**待收口：** 分片上传断点续传可靠性、封面提取 Kafka 消费幂等

---

## MOD-LIVE 直播

**职责：** 开播/下播生命周期、推流参数管理（SRT/WHIP）、观看 URL 分发（WHEP/HLS）、弹幕、打赏、连麦、美颜/画质、主播统计  
**主要目录：**
- `server/.../controller/LiveController.java` / `StreamController.java`
- `server/.../service/LiveService.java` / `impl/LiveServiceImpl.java`
- `server/.../engine/StreamingEngine.java` / `StreamingSessionManager.java`
- `server/.../websocket/LiveStreamHandler.java`（legacy bridge）
- `src/pages/live/LiveCreate.vue` / `LiveWatch.vue`
- `src/utils/streaming/webcodecs_sender.ts` / `webcodecs_player.ts`
- `streaming-engine/`（C++ 原生引擎）
- `deploy/streaming/`

**对外契约：** `/api/live/**`、`/api/live/engine/**`、`/ws/live/**`（迁移期）  
**状态：** 进行中 — 架构已定，实现有 blocker  
**已知 blocker：**
- B-001 SDP echo，非真实协商 → RTC-006
- B-002 C++ WebRTC stub → RTC-009
- B-003 WS handler 与二进制协议不匹配 → RTC-006
- B-004 WebCodecs payload 硬错误 → RTC-006
- B-006 JNI `|| true` 可能崩溃 → RTC-002

---

## MOD-RTC 实时通话

**职责：** 1 对 1 音视频通话、群聊音视频（≤8 人）、LiveKit SFU 接入、ICE/TURN、通话记录、连麦  
**主要目录：**
- `server/.../rtc/` 全部（controller/service/domain/repository/webhook）
- `src/modules/rtc/`（store、adapter、signaling、UI）
- `deploy/rtc/`

**对外契约：** `/api/rtc/**`、`douyin.realtime.v1` 信令、LiveKit webhook  
**状态：** RTC-003 完成，RTC-004 进行中  
**任务序列：** RTC-004 → RTC-005 → RTC-007 → RTC-009 → RTC-010

---

## MOD-IM 即时通讯

**职责：** 私聊消息（文字/图片/语音/红包）、群聊（创建/成员/消息/已读游标）、系统通知、粉丝消息、互动通知  
**主要目录：**
- `server/.../controller/MessageController.java` / `GroupChatController.java` / `SystemNoticeController.java`
- `server/.../service/MessageService.java` / `GroupChatService.java`
- `server/.../websocket/ChatWebSocketHandler.java`
- `server/.../kafka/DirectMessagePublisher.java` / `KafkaMessageConsumer.java`
- `src/pages/message/`

**对外契约：** `/api/message/**`、`/ws/chat`、Kafka topic `im.messages`  
**状态：** 基本完成，消息可靠性和已读需补测  
**待收口：** 离线推送、消息幂等消费验证、群聊已读游标一致性

---

## MOD-MUSIC 音乐

**职责：** 音乐库、搜索音乐、背景音乐绑定视频、收藏、排行榜、猜你喜欢  
**主要目录：**
- `server/.../controller/MusicController.java`
- `server/.../service/MusicService.java`
- `src/pages/home/Music.vue` / `MusicRankList.vue`
- `src/pages/me/MyMusic.vue` / `collect/MusicCollect.vue`

**对外契约：** `/api/music/**`  
**状态：** 基本完成

---

## MOD-SEARCH 搜索

**职责：** 综合搜索（视频/用户/音乐）、联想词、搜索历史、热词、别名管理  
**主要目录：**
- `server/.../controller/SearchController.java` / `SearchHistoryController.java`
- `server/.../service/SearchService.java` / `SearchSuggestionService.java`
- `server/.../admin/controller/SearchConfigController.java`
- `src/pages/home/SearchPage.vue`

**对外契约：** `/api/search/**`  
**状态：** 基本完成  
**待收口：** 全文索引（当前 MySQL LIKE，规划 Elasticsearch）

---

## MOD-SHOP 电商

**职责：** 商品展示/详情、购物车、订单、多支付渠道（支付宝/微信/银行卡/钱包）、退款、钱包余额/流水  
**主要目录：**
- `server/.../controller/ShopController.java` / `WalletController.java`
- `server/.../service/GoodsService.java` / `OrderService.java` / `WalletService.java`
- `server/.../service/payment/`（策略模式：4 种渠道 + 状态机 + 幂等管理）
- `src/pages/shop/`

**对外契约：** `/api/shop/**`、`/api/wallet/**`、外部支付回调  
**状态：** 基本完成，支付链路需安全审计  
**待收口：** 支付回调签名验证测试、退款状态机、对账报表

---

## MOD-REC 推荐引擎

**职责：** 视频 feed 个性化排序、直播 feed 排序、用户兴趣建模、曝光/点击记录  
**主要目录：**
- `server/.../service/RecommendationEngine.java` / `ScoringFunctions.java` / `RecommendationConfig.java`
- `server/.../service/ContentFeatureService.java`
- `server/.../entity/UserContentProfile.java` / `VideoExposure.java`

**对外契约：** 内部服务调用，不对外暴露独立端点  
**状态：** 基本完成  
**待收口：** A/B 框架、冷启动策略、推荐解释性日志

---

## MOD-ADMIN 管理后台

**职责：** 内容审核（视频/直播）、用户管理（封禁/解封）、数据看板（实时 DAU/收益/地图）、系统配置、标签管理  
**主要目录：**
- `server/.../admin/controller/` 全部
- `server/.../admin/service/MetricsQueryService.java`
- `server/.../websocket/DashboardWebSocketHandler.java`
- `admin/src/`

**对外契约：** `/api/admin/**`、`/ws/dashboard/stream`  
**状态：** 进行中  
**待收口：** 审核工作流状态机、实时看板 WS 稳定性、权限分级（超管/运营/客服）

---

## MOD-INFRA 基础设施

**职责：** Kafka 消息总线、Redis 缓存、MinIO 对象存储、WebSocket 配置与安全、JNI 引擎桥、Docker Compose 部署  
**主要目录：**
- `server/.../kafka/` 全部
- `server/.../config/` 全部
- `server/.../engine/` （JNI 桥）
- `streaming-engine/`（C++ 原生）
- `deploy/`

**对外契约：** 内部基础设施，不对外暴露业务端点  
**状态：** 基本完成，JNI 有 blocker B-006  
**待收口：** WebSocket Origin allowlist、Kafka 消费幂等补测、native DLL 存在性检查
