# Douyin 项目总宪法

**版本：** 1.0  
**状态：** 生效  
**生效日期：** 2026-08-14  
**适用范围：** 整个仓库，所有模块，所有开发者和智能体。

本宪法是全项目的长期约束，优先于局部实现偏好和短期目标。  
实时媒体细节另见 `PROJECT_CONSTITUTION.md`（RTC 子宪法），两者不冲突。

---

## 1. 项目定位

仿抖音全功能移动端社区平台，目标能力：

- **视频社区**：短视频上传/编辑/发布/推荐/互动（点赞/评论/收藏/分享）
- **直播**：主播开播推流、观众实时观看、弹幕打赏连麦，低延迟+大规模分发
- **实时通话**：1 对 1 音视频通话、多人群聊音视频，基于 LiveKit SFU
- **即时通讯**：私聊、群聊、通知、红包、消息已读
- **电商**：商品浏览/购物车/订单/多支付渠道/钱包
- **音乐**：音乐库/背景音乐/收藏/排行榜
- **搜索**：综合搜索/联想/历史/热词/别名
- **推荐**：个性化 feed 排序，视频和直播双轨
- **管理后台**：内容审核/用户管理/数据看板/系统配置

---

## 2. 全局不可违反原则

### 2.1 控制面与媒体面分离
Spring Boot 只处理身份、权限、房间、信令、状态和审计，不转发媒体字节。  
媒体由 LiveKit（通话）或 SRS（直播）承担，控制面不冒充媒体服务器。

### 2.2 数据库是状态权威
客户端状态必须可从服务端重建。前端不持有权威状态，只持有视图投影。  
幂等键、outbox 模式和事件账本保证最终一致。

### 2.3 支付和钱包双重验证
支付路径必须：后端签名 + 幂等键 + 状态机 + 审计日志。  
前端不计算金额，不信任本地余额，支付结果必须后端回调确认。

### 2.4 隐私和安全不可后补
Token 短期化、Origin allowlist、接口限流、日志脱敏、录制同意必须在功能上线时同步交付。  
用户隐私字段（手机号/邮件/位置）不能出现在普通日志中。

### 2.5 可观测性是 Definition of Done
新增的每个核心业务路径必须有：结构化日志、关键业务指标（成功率、延迟）、告警阈值。  
未度量的功能不能宣称完成。

### 2.6 禁止临时补丁进主路径
Legacy bridge、TODO stub、硬编码 `|| true` 只能有明确 owner、退役时间和 feature flag。  
已知 blocker 必须在任务文件登记，不得通过调参掩盖。

### 2.7 模块边界不可越界
每个模块只修改自己拥有的目录（见模块所有权表）。  
跨模块依赖只能通过稳定的接口、事件或 OpenAPI 契约，不能直接调用对方内部实现。

---

## 3. 模块所有权

| 模块 ID | 名称 | 主要目录 | 当前状态 |
|---|---|---|---|
| MOD-AUTH | 认证与账户 | `server/.../controller/AuthController` `src/pages/login` | 基本完成 |
| MOD-USER | 用户档案与社交 | `server/.../controller/UserController` `src/pages/me` `src/pages/people` | 基本完成 |
| MOD-VIDEO | 视频内容 | `server/.../controller/VideoController` `src/pages/home` `src/components/slide` | 基本完成 |
| MOD-LIVE | 直播 | `server/.../controller/LiveController` `src/pages/live` `streaming-engine` | 进行中（B-001~B-006） |
| MOD-RTC | 实时通话 | `server/.../rtc` `src/modules/rtc` | 进行中（RTC-004） |
| MOD-IM | 即时通讯 | `server/.../controller/MessageController` `src/pages/message` | 基本完成，待整理 |
| MOD-MUSIC | 音乐 | `server/.../controller/MusicController` `src/pages/home/Music.vue` | 基本完成 |
| MOD-SEARCH | 搜索 | `server/.../controller/SearchController` `src/pages/home/SearchPage.vue` | 基本完成 |
| MOD-SHOP | 电商 | `server/.../controller/ShopController` `src/pages/shop` | 基本完成，支付待收口 |
| MOD-REC | 推荐引擎 | `server/.../service/RecommendationEngine` | 基本完成 |
| MOD-ADMIN | 管理后台 | `server/.../admin` `admin/src` | 进行中 |
| MOD-INFRA | 基础设施 | `server/.../kafka` `server/.../engine` `deploy` `streaming-engine` | 进行中 |

---

## 4. 技术选型锁定（不得无 ADR 擅改）

| 层 | 选型 | 替换条件 |
|---|---|---|
| 前端框架 | Vue 3 + TypeScript + Pinia | 新增 ADR + 全量迁移计划 |
| 后端框架 | Spring Boot 3 + MyBatis-Plus | 新增 ADR |
| 通话 SFU | LiveKit | 新增 ADR-005 |
| 直播 ingest/分发 | SRS（WHIP/WHEP/HLS） | 新增 ADR |
| TURN | coturn | 新增 ADR |
| 消息队列 | Kafka | 新增 ADR |
| 缓存 | Redis | 新增 ADR |
| 对象存储 | MinIO | 新增 ADR |
| 原生编码引擎 | C++ NVENC + SRT（可选） | 不能阻断浏览器主路径 |

---

## 5. 工程门禁（任何任务合并前）

1. 任务文件状态更新，工作日志记录提交哈希。
2. 模块边界和禁止越界目录清晰。
3. 核心路径有单元/契约测试，覆盖正常/边界/权限失败。
4. 安全相关变更（鉴权/支付/隐私）有独立审查记录。
5. 提交只包含本任务文件，无无关格式化、无密钥、无临时产物。
6. 已知 blocker 若未修复，必须在任务文件和 WORK_LOG 登记。

---

## 6. 优先级排序（2026-08-14 基准）

1. **P0 — RTC/Live 质量**：修复 B-001~B-006，推进 RTC-004 到 RTC-010，直播真实 WebRTC 路径
2. **P1 — IM 收口**：群聊消息可靠性、已读、离线推送
3. **P2 — 管理后台完善**：审核工作流、数据看板实时化
4. **P3 — 电商支付收口**：支付状态机、退款、钱包对账
5. **P4 — 推荐与搜索优化**：算法调优、冷启动、A/B
6. **P5 — 性能与发布门禁**：压测、监控、灰度、回滚

---

## 7. 文档体系

| 文档 | 路径 | 用途 |
|---|---|---|
| 本宪法（总） | `docs/project-constitution/DOUYIN_PROJECT_CONSTITUTION.md` | 全局约束 |
| RTC 子宪法 | `docs/project-constitution/PROJECT_CONSTITUTION.md` | 实时媒体细节 |
| 任务索引 | `docs/project-constitution/tasks/TASK_INDEX.md` | 所有任务状态 |
| 项目状态 | `docs/project-constitution/state/PROJECT_STATE.yaml` | 当前指针 |
| 工作日志 | `docs/project-constitution/state/WORK_LOG.md` | 提交记录 |
| 模块拆分 | `docs/project-constitution/MODULE_BREAKDOWN.md` | 12 模块详情 |
| 开发计划 | `docs/project-constitution/FULL_DEVELOPMENT_PLAN.md` | 分阶段路线图 |
| 架构决策 | `docs/adr/` | ADR-001~N |
| 架构图 | `docs/architecture/` | 拓扑/模型/质量 |
| API 契约 | `docs/contracts/` | OpenAPI/schema |
| 测试计划 | `docs/testing/` | 测试矩阵 |

---

## 8. 治理与版本

- 宪法修改需在 PR 中说明原因，并同步更新受影响的任务文件。
- 新增不可逆架构决策必须新增 ADR，不能在实现中悄悄偏离。
- 每个 legacy 路径必须有退役日期；过期未退役必须在任务文件中记录批准的新日期，不得静默延期。
