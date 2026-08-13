# RTC-003：RTC 控制面和通话领域

## 状态与边界

- 状态：`completed`（独立集成审查 APPROVE_WITH_NOTES：无阻断，2 重要 + 6 建议全部修复并复测 88/88）
- 依赖：RTC-001
- 可与 RTC-002 并行；provider smoke 使用 CLI 或测试密钥
- 负责目录：`server/.../rtc/`、`server/sql/`、`server/.../websocket/` 的 RTC adapter、`docs/contracts/`
- 禁止修改：媒体服务实现、LiveKit SDK 内部信令、C++ WebRTC stub

## 目标

实现 CallSession/Participant/CallEvent 权威状态机、群成员 ACL、幂等命令、TTL worker、LiveKit token API、webhook ledger 和旧消息投影。`rtc-persistence` 写真相，`chat-persistence` 只投影。

## DoD

- [x] 完整 transition table 和错误码有契约测试（TransitionTableTest 7 + 全量 88/88 含权限矩阵/TTL 边界/签名向量）。
- [x] `event_id`、`client_request_id`、`call_id + user_id` 唯一约束生效（migration_034 uk 索引 + INSERT IGNORE/回查幂等，并发 create 已修）。
- [x] 未登录、非成员、过期、重复、乱序命令被拒绝或安全返回当前状态（OutOfOrderTest/IdempotencyTest；detail 端点鉴权已补）。
- [x] token TTL 60-900 秒，发布/订阅权限来自服务端，不信任客户端 roster（TokenServiceTest 15）。
- [x] LiveKit webhook 签名校验、event id 去重和重放测试通过（WebhookSignatureTest/VectorTest/DedupTest；客户端 event_id 保留前缀 `sys:`/`ttl:` 已隔离）。
- [x] 旧 `msg_type=10/11` 记录保留兼容字段并带 `call_id` 投影（CompatCallProjection，callState 0=拒接/1=已接通/2=未接通 对齐旧前端语义）。

## 提交记录

- `9a9d374` feat：领域状态机/ACL/事件账本 + migration_034 + 契约单测
- `32060db` feat：token API + webhook ledger + migration_035 + SessionFilter 白名单 1 行
- `940b5d2` docs：webhook 契约、错误码、signaling schema v1 扩展（向后兼容）
- `27cf661` fix：detail 鉴权、sys/ttl event_id 保留前缀、并发 create 幂等、ttl_seconds 解析、CONNECTED hangup 投影
- `05858d0` fix：compat callState 对齐旧语义

## 已知边界（诚实声明）

1. 端到端（后端 + LiveKit webhook 推送 + migration 执行）未验证：依赖用户执行 migration_034/035 + 启动后端，验收归属 RTC-004 联调。
2. `RECONNECTING` 状态无服务层入口，重连逻辑留 RTC-004。
3. 限流 fail-open 是 RedisCacheService 既有行为（异常时放行），本次未改，记录待 RTC-010。
4. `(call_id, event_id)` 复合唯一未做（保留全局唯一），配合前缀隔离后剩余影响有限，记录。

