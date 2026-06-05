# 抖音前后端接口与交互文档

> 适用分支: `dev/full` (全功能) / `master` (不含推荐系统)
> 最后更新: 2026-06-06

---

## 一、系统架构总览

```
┌─────────────────────────────────────────────────────┐
│                    前端 (Vue 3)                      │
│  Vite 5 + Pinia + Vue Router + TypeScript           │
│  54 个路由页面，7 大模块                              │
├─────────────────────────────────────────────────────┤
│  HTTP REST API (/api/*) │ WebSocket (/ws/chat)       │
├─────────────────────────────────────────────────────┤
│              后端 (Spring Boot Java)                 │
│  MyBatis-Plus + MySQL + Kafka(可选) + MinIO(可选)     │
│  12 个 Controller，60+ API 端点                       │
└─────────────────────────────────────────────────────┘
```

### 关键配置项 (`application.yml`)

| 配置项 | 说明 |
|---|---|
| `douyin.kafka.enabled` | true=Kafka异步消息，false=同步直发 |
| `minio.enabled` | true=文件上传到MinIO，false=上传功能不可用 |

---

## 二、后端 API 接口总览

### 2.1 认证模块 — `AuthController`

| 方法 | 路径 | 功能 |
|---|---|---|
| POST | `/api/login` | 密码登录，返回 JWT token + userId |
| POST | `/api/register` | 邮箱验证码注册 |

### 2.2 邮箱模块 — `EmailController`

| 方法 | 路径 | 功能 |
|---|---|---|
| POST | `/api/email/send-code` | 发送验证码到邮箱 |
| POST | `/api/email/login` | 验证码登录（自动注册新用户） |

### 2.3 用户模块 — `UserController`

| 方法 | 路径 | 功能 |
|---|---|---|
| GET | `/api/user/panel` | 用户面板信息（资料+关注/粉丝数） |
| GET | `/api/user/userinfo` | 用户详细信息 |
| GET | `/api/user/friends` | 好友列表 |
| GET | `/api/user/video_list` | 用户视频列表（分页） |
| GET | `/api/user/collect` | 收藏视频列表 |
| PUT | `/api/user/profile` | 修改个人资料 |
| PUT | `/api/user/avatar` | 修改头像 |
| PUT | `/api/user/cover` | 修改封面图 |
| GET | `/api/user/followings` | 关注列表 |
| GET | `/api/user/followers` | 粉丝列表 |
| POST | `/api/user/follow/{userId}` | 关注/取关用户 |
| GET | `/api/user/search` | 搜索用户 |
| GET | `/api/user/recent-authors` | 最近常看作者（最多6个） |
| POST | `/api/user/visitors/{userId}` | 记录主页访问 |
| GET | `/api/user/visitors` | 查看谁访问过我 |

### 2.4 视频模块 — `VideoController`

| 方法 | 路径 | 功能 |
|---|---|---|
| GET | `/api/video/recommended` | 推荐视频流 |
| GET | `/api/video/long/recommended` | 推荐长视频（≥60秒） |
| GET | `/api/video/following` | 关注的人的视频 |
| GET | `/api/video/trending` | 热门视频 |
| GET | `/api/video/comments` | 视频评论列表 |
| GET | `/api/video/comment/replies/{commentId}` | 评论的回复列表 |
| POST | `/api/video/comment/like/{commentId}` | 点赞/取消点赞评论 |
| GET | `/api/video/my` | 我的视频 |
| GET | `/api/video/like` | 我点赞的视频 |
| GET | `/api/video/private` | 私密视频 |
| GET | `/api/video/history` | 观看历史 |
| GET | `/api/video/historyOther` | 其他浏览历史 |
| POST | `/api/video` | 发布视频 |
| POST | `/api/video/like/{videoId}` | 点赞/取消点赞视频 |
| POST | `/api/video/share/{videoId}` | 记录分享（分享数+1） |
| POST | `/api/video/collect/{videoId}` | 收藏/取消收藏 |
| POST | `/api/video/comments` | 发表评论（支持@提及） |
| POST | `/api/video/watch/{videoId}` | 记录观看历史 |
| GET | `/api/video/search` | 搜索视频 |

### 2.5 图文模块 — `PostController`

| 方法 | 路径 | 功能 |
|---|---|---|
| GET | `/api/post/recommended` | 推荐图文帖子 |

### 2.6 电商模块 — `ShopController`

| 方法 | 路径 | 功能 |
|---|---|---|
| GET | `/api/shop/recommended` | 推荐商品 |

### 2.7 音乐模块 — `MusicController`

| 方法 | 路径 | 功能 |
|---|---|---|
| GET | `/api/music/search` | 搜索音乐 |
| GET | `/api/music/{id}` | 音乐详情 |
| GET | `/api/music/list` | 音乐列表（分页） |
| GET | `/api/music/hot` | 热门音乐 |
| POST | `/api/music/upload` | 上传音乐文件 |

### 2.8 消息模块 — `MessageController`

| 方法 | 路径 | 功能 |
|---|---|---|
| POST | `/api/message/send` | 发送私聊消息（通过WebSocket推送） |
| GET | `/api/message/history` | 聊天历史（游标分页，beforeId） |
| GET | `/api/message/conversations` | 会话列表 |
| POST | `/api/message/read/{fromUserId}` | 标记某用户消息已读 |
| GET | `/api/message/unread/count` | 未读消息总数 |
| GET | `/api/message/notifications` | 通知列表（按类型筛选） |
| GET | `/api/message/notifications/unread` | 各类型通知未读数 |
| POST | `/api/message/notifications/read` | 标记通知已读 |
| GET | `/api/message/search` | 搜索聊天记录 |
| GET | `/api/message/notifications/search` | 搜索通知 |

### 2.9 其他模块

**SystemNoticeController** — 系统通知
- `GET /api/system-notice` — 通知列表
- `PUT /api/system-notice/{id}/read` — 标记单条已读
- `PUT /api/system-notice/read-all` — 全部已读

**SearchHistoryController** — 搜索历史
- `GET /api/search-history` — 获取历史
- `POST /api/search-history` — 保存关键词
- `DELETE /api/search-history` — 清空历史
- `DELETE /api/search-history/keyword` — 删除单条

**LoginHistoryController** — 登录设备管理
- `GET /api/user/login-history` — 设备列表

**UploadController** (需 `minio.enabled=true`)
- `POST /api/upload/video` — 上传视频
- `POST /api/upload/image` — 上传图片
- `POST /api/upload/voice` — 上传音频

---

## 三、WebSocket 实时通信

### 3.1 连接方式

```
ws://<host>/ws/chat?token=<jwt_token>
```

- 前端 `src/utils/socket.ts` 管理连接
- 自动断线重连（3秒间隔）
- 通过全局 bus 分发消息事件

### 3.2 消息流转（Kafka 模式）

```
用户A发送 → WebSocket → Kafka(topic:chat-messages)
  → Consumer 写入 MySQL(t_message)
  → SessionManager.pushBoth() 推送给 A 和 B
```

### 3.3 消息类型（msg_type）

| msg_type | 含义 | 前端渲染组件 |
|---|---|---|
| 1 | 文本消息 | `chat-text` |
| 2 | 图片消息 | `image` |
| 3 | 语音消息 | `audio` |
| 4 | 视频消息 | `douyin_video`（旧格式） |
| 5 | 红包 | `red_packet` |
| 9 | 抖音视频卡片 | `douyin_video`（分享卡片） |

### 3.4 通知类型

| type | 含义 |
|---|---|
| 1 | 点赞通知 |
| 2 | 评论通知 |
| 3 | 回复通知 |
| 4 | @提及通知 |
| 5 | 关注通知 |

### 3.5 已读回执

- 消息 `is_read` 字段标记已读状态
- 已读后通过 WebSocket 推送 `read_receipt` 事件
- 前端在对方最后一条已读消息下显示"已读"小头像

---

## 四、前端架构

### 4.1 目录结构

```
src/
├── api/              # API 请求模块
│   ├── auth.ts       # 登录/注册
│   ├── user.ts       # 用户/好友/访客
│   ├── videos.ts     # 视频/评论
│   ├── message.ts    # 聊天/通知
│   └── music.ts      # 音乐
├── components/       # 通用组件
│   ├── Share.vue     # 分享弹窗（核心）
│   ├── Comment.vue   # 评论区（核心）
│   ├── slide/        # 滑动组件
│   │   ├── BaseVideo.vue   # 视频播放
│   │   ├── ImageSlide.vue  # 图文作品
│   │   └── TextSlide.vue   # 文本作品
│   └── ...
├── pages/
│   ├── home/         # 首页（视频流）
│   ├── message/      # 消息/聊天
│   │   ├── chat/Chat.vue              # 聊天页
│   │   └── components/ChatMessage.vue # 消息气泡
│   ├── login/        # 登录/注册
│   ├── me/           # 个人中心
│   ├── people/       # 用户主页
│   └── other/        # 视频详情页
├── router/           # 路由配置（54个路由）
├── store/pinia.ts    # 全局状态
└── utils/
    ├── socket.ts     # WebSocket 管理
    └── bus.ts        # 事件总线
```

### 4.2 Pinia Store 核心状态

| 字段 | 说明 |
|---|---|
| `token` | JWT（持久化到localStorage） |
| `userinfo` | 当前用户完整信息 |
| `friends.all` | 好友列表 |
| `routeData` | 路由传递的临时数据（视频列表+索引） |

---

## 五、核心交互流程详解

### 5.1 视频分享到私聊

**触发路径：** 首页视频 → 分享按钮 → 选择好友 → 发送视频卡片

**Share.vue 数据构建流程：**

```
item (VideoVO)
  ├── item.video.cover.url_list[0]    ← 封面第一优先级
  ├── item.video.poster               ← 兜底
  ├── item.cover_url?.[0].url_list[0]  ← 兜底
  ├── item.cover                      ← 兜底
  ├── item.origin_cover               ← 兜底
  ├── item.video.origin_cover.url_list[0] ← 兜底
  └── item.video.play_addr.url_list[0]   ← 最终兜底（.mp4 URL）
```

**发送的 videoData JSON 结构：**

```json
{
  "poster": "封面图URL",
  "title": "视频描述",
  "author": {
    "avatar": "作者头像URL",
    "name": "作者昵称"
  },
  "aweme_id": "视频ID",
  "video_url": "播放地址",
  "type": "视频类型 (recommend-video/image/text)",
  "duration": 视频时长,
  "image_urls": ["图文作品图片列表"],
  "statistics": {
    "digg_count": 点赞数,
    "comment_count": 评论数,
    "share_count": 分享数,
    "collect_count": 收藏数,
    "play_count": 播放数
  }
}
```

**发送流程：**

1. 用户选择好友 + 可选文字 → 点击发送
2. 先发送文字消息（如有）：`msg_type=1, content=文字`
3. 再发送视频卡片：`msg_type=9, content=videoData JSON`
4. 后端写入 `t_message` 表（content 列为 TEXT 类型）
5. Kafka → Consumer → WebSocket 推送给接收方

**接收端渲染（ChatMessage.vue）：**

- `msg_type=9` → 渲染 `.douyin_video` 卡片
- 封面图 + 渐变遮罩 + 播放图标 + 标题 + 作者头像昵称
- 封面加载失败 → 显示 `.poster-placeholder` 渐变背景

**点击卡片跳转（Chat.vue clickItem）：**

1. 检测 `e.type === MESSAGE_TYPE.DOUYIN_VIDEO` 且有 `aweme_id`
2. 构建 VideoVO 兼容对象（含 video、author、statistics、music 等字段）
3. 写入 `store.routeData = { list: [videoItem], index: 0 }`
4. 路由跳转 `/video-detail`，VideoDetail 页面从 store.routeData 读取数据渲染

### 5.2 图文/文本作品分享

与视频分享逻辑相同，`videoData.type` 字段区分：
- `"image"` → 图文作品
- `"text"` → 文本作品
- `"recommend-video"` → 普通视频

**关键：** `image_urls` 和 `statistics` 字段确保分享后点击卡片进入详情页数据完整。

### 5.3 首页视频流交互

**数据加载：**

- Slide0（推荐流）：调用 `trendingVideos()` API
- VideoVO 通过 `VideoVO.from()` 转换，设置 `video.cover.url_list[0]` 和 `video.poster`

**交互组件层级：**

```
SlideVerticalInfinite (垂直滑动)
  └── BaseVideo / ImageSlide / TextSlide (根据 item.type 分发)
        └── .float (绝对定位覆盖层)
              ├── .toolbar (点赞/评论/分享按钮)
              └── .item-desc (视频描述)
```

**pointer-events 修复（重要）：**
- ImageSlide 和 TextSlide 的 `.float` 层设置了 `pointer-events: none`
- 通过 `:deep(.toolbar)` 和 `:deep(.item-desc)` 恢复 `pointer-events: auto`
- 确保图文/文本作品的点赞、评论、分享按钮可点击

### 5.4 评论互动

**评论区（Comment.vue）：**

- 弹出式底部面板
- 评论列表 + 回复子列表
- 支持发表评论、回复评论、点赞评论
- **点击头像/昵名跳转用户主页：**
  - 主评论：`<img @click.stop="goUserHome(item)">` 和 `<div @click.stop="goUserHome(item)">`
  - 子回复：同样绑定
  - `goUserHome()` 方法：`this.$router.push('/people/user-home/' + uid)`

**通知联动：**
- 发表评论时解析 @提及 → 发送通知
- 点赞评论 → 发送通知给评论作者
- 通知通过 Kafka → WebSocket 实时推送

### 5.5 聊天消息系统

**会话列表（AllMessage.vue）：**

- 调用 `getConversations()` 获取所有会话
- 显示最后一条消息预览、时间、未读标记

**聊天页（Chat.vue）：**

路由参数：
```
/message/chat?user_id=<对方UID>&name=<对方昵称>&avatar=<对方头像>
```

**消息发送：**
1. 文本：`sendMessage({ to_user_id, content })` → msg_type=1
2. 图片：上传到 `/api/upload/image` → 拿到URL → `sendMessage({ to_user_id, content: url, msg_type: 2 })`
3. 语音：MediaRecorder 录音 → 上传 → `sendMessage({ to_user_id, content: url, msg_type: 3, extra: duration })`
4. 视频卡片：`msg_type=9`（由分享流程触发）

**消息接收：**
1. `onSocketMsg('chat', handleWsMessage)` 监听 WebSocket
2. 检查 `from_user_id` 是否当前对话对象
3. 防重复：`addMessageWithDedup()` 通过 `backendId` 去重
4. 自动标记已读：`markRead(targetUserId)`

**时间分隔线：**
- 两条消息间隔 > 5分钟 → 插入时间标签
- 格式：今天显示 HH:mm，昨天显示"昨天 HH:mm"，本周显示"周X HH:mm"

**已读回执：**
- 我发的最后一条已读消息下方显示对方头像 + "已读"
- 对方标记已读后通过 WebSocket 推送 `read_receipt`
- 前端更新消息 `isRead` 状态

### 5.6 音视频通话

前端 UI 保留通话入口，显示通话状态：
- `CALL_STATE.RESOLVE` → 通话时长
- `CALL_STATE.REJECT` → "对方已拒绝"
- `CALL_STATE.NONE` → "对方未接通"

> ⚠️ 实际通话功能（WebRTC信令）尚未实现完整后端

---

## 六、数据库表结构（与消息/分享相关）

### t_message — 消息表

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键（雪花ID） |
| from_user_id | BIGINT | 发送者 |
| to_user_id | BIGINT | 接收者 |
| content | TEXT | 消息内容（JSON字符串或文本） |
| msg_type | INT | 消息类型（1-9） |
| extra | VARCHAR(255) | 附加信息（如语音时长） |
| is_read | TINYINT | 0=未读, 1=已读 |
| create_time | DATETIME | 发送时间 |

> **注意：** content 列已从 VARCHAR(1000) 迁移为 TEXT（migration_015），以支持包含长签名URL的视频卡片JSON。

### t_notification — 通知表

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键 |
| type | INT | 通知类型（1-5） |
| from_user_id | BIGINT | 触发者 |
| to_user_id | BIGINT | 接收者 |
| content | VARCHAR(500) | 通知内容 |
| source_id | VARCHAR(64) | 来源ID（视频/评论ID） |
| is_read | TINYINT | 0=未读, 1=已读 |
| create_time | DATETIME | 创建时间 |

---

## 七、好友系统

### API

| 接口 | 说明 |
|---|---|
| 发送好友申请 | `POST /api/user/friend-request` |
| 接受好友申请 | `POST /api/user/friend-request/accept` |
| 拒绝好友申请 | `POST /api/user/friend-request/reject` |
| 好友列表 | `GET /api/user/friends` |
| 好友申请列表 | `GET /api/user/friend-requests` |
| 共同好友 | `GET /api/user/friends-mutual` |

### 前端页面

- `/message/friend-requests` — 好友申请列表
- `/message/fans` — 粉丝列表
- `/people/follow-and-fans` — 关注和粉丝
- `/address-list` — 通讯录

---

## 八、音乐系统

### 后端

- MusicController 提供搜索、列表、热门、详情、上传功能
- Music 实体存储在 `t_music` 表
- 音乐文件通过 MinIO 存储

### 前端

- `/home/music` — 音乐播放页
- `/home/music-rank-list` — 音乐排行榜
- `/me/my-music` — 我的音乐
- `/me/collect/music-collect` — 音乐收藏
- Share 组件支持 `mode="music"` 和 `mode="my-music"` 分享模式

---

## 九、推荐系统（仅 dev/full 分支）

### 涉及文件

```
server/python/                              # Python推荐脚本
server/sql/migration_007_recommendation.sql  # 推荐相关表
server/sql/migration_008_user_profile.sql    # 用户画像表
```

**Java 文件：**

| 文件 | 功能 |
|---|---|
| `RecommendationEngine.java` | 推荐引擎核心 |
| `ContentFeatureService.java` | 内容特征提取 |
| `UserProfileService.java` | 用户画像服务 |
| `UserContentProfile.java` | 用户内容偏好实体 |
| `VideoContent.java` | 视频内容特征实体 |
| `VideoExposure.java` | 视频曝光记录实体 |
| 对应 Mapper 文件 | 数据访问层 |

> ⚠️ **master 分支不含以上文件**，相关 Controller/Service 中的推荐代码也已回退。

---

## 十、开发部署注意事项

### 前端

```bash
cd douyin
npm install
npm run dev        # 开发模式
npm run build      # 生产构建
```

### 后端

```bash
cd server
mvn spring-boot:run
```

### 环境要求

| 组件 | 必需/可选 | 说明 |
|---|---|---|
| MySQL | 必需 | 运行所有 migration SQL |
| Kafka | 可选 | `douyin.kafka.enabled=false` 可关闭 |
| MinIO | 可选 | `minio.enabled=false` 可关闭上传功能 |
| Python | 仅 dev/full | 推荐系统脚本需要 |

### 数据库初始化

按序号执行 `server/sql/` 下的 migration 文件：
- migration_001 ~ migration_015（master分支）
- 额外 migration_007, 008（dev/full分支）

### 分支策略

| 分支 | 内容 |
|---|---|
| `master` | 不含推荐系统，朋友可正常运行 |
| `dev/full` | 含推荐系统完整代码 |
