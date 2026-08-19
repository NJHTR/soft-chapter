# Live 媒体 Provider Reconciliation Contract

状态：`implemented-review-pending`

本文是 RTC-006 的补充契约，专门描述 SRS provider 回调、会话收敛和恢复边界。
它不定义媒体转发路径，也不替代 `docs/contracts/live-media-contract.md` 中的
短期授权和播放约束。

## 1. 适用范围

该契约覆盖以下 provider 侧事件：

- `on_publish`
- `on_play`
- `on_unpublish`
- `on_stop`

这些事件必须只负责控制面收敛，不得转发 SDP、RTP、音频帧、视频帧或录制数据。

## 2. 会话标识

每一个 provider 回调会话必须由以下字段共同定位：

- `room_id`
- `stream_key`
- `server_id` + `client_id`（组合为 provider session generation）
- `purpose`，取值为 `PUBLISH` 或 `PLAY`

服务端把这些字段持久化到关系库；旧 generation 的 stop/unpublish 不能影响新 generation。
当前幂等键为：

```text
provider:<room_id>:<stream_key>:<purpose>:<server_id>:<client_id>
```

## 3. 状态模型

建议的 provider 状态机如下：

```text
STARTING -> ACTIVE -> DEGRADED -> ENDED
```

- `STARTING`：房间已创建，但 provider 还未确认可用。
- `ACTIVE`：收到有效的 publish 回调并通过授权。
- `DEGRADED`：provider 失联或 API 不可达，正在等待恢复窗口。
- `ENDED`：会话终止，不再接受旧 token 或旧回调。

## 4. 收敛规则

1. `on_publish` 和 `on_play` 只要通过授权，就必须可重复调用而不重复创建会话。
2. `on_unpublish` 和 `on_stop` 只要针对同一个会话，重复调用必须幂等返回成功或被动拒绝，不得重复扣减状态。
3. 会话是否存活，不能依赖 `update_time`。
4. 失联后的收敛必须由有界 grace period 或显式 reconciliation job 驱动。
5. 回调日志只能记录脱敏后的 room / stream / purpose / result，不能输出 token、密钥、完整 URL 或媒体内容。

## 5. Heartbeat / Reconciliation

当前仓库已经有 Redis TTL presence 和 provider session/reconciliation 实现；`a54c83d` 将缺流、generation 变化和 grace 到期的 session retirement 与 room CAS 放入同一事务，`99acc27` 以 Redis Lua 原子维护 presence key 生命周期并为 SRS API 设置有界超时，但 RTC-006 仍需真实环境验收：

- `last_seen_at` 由 provider 回调、健康检查或 reconciliation job 更新。
- `grace_period_seconds` 应对短暂重启或网络抖动。
- reconciliation job 只做状态收敛，不做媒体转发。
- 如果 provider API 不可达，只能进入 `DEGRADED`；只有 API 可达且明确无流时，状态才进入有界 grace 并终止或恢复。
- Redis 不可用时 presence 操作 fail-closed，不能退回节点本地计数并宣称全局准确。

## 6. 验收门

在 RTC-006 被标记 `completed` 前，至少应具备以下证据：

- callback token 校验
- stream key / room ownership / token purpose 校验
- 回调幂等
- Redis presence 多实例不重复计数
- provider 重启或异常断开后的有界收敛
- 不使用 `update_time` 判活

## 7. 当前实现与剩余验收

当前工作区已提供 `live_provider_session` 投影、SRS callback 控制器、可达性
reconciliation worker、事务化 generation transition、旧 sentinel 前向迁移和独立 provider
状态字段；但在真实 SRS callback、重启恢复、Redis 多实例和浏览器媒体环境完成前，RTC-006
必须保持 `in_progress`。
