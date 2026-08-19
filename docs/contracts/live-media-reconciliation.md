# Live 媒体 Provider Reconciliation Contract

状态：`draft`

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

每一个 provider 回调会话至少应由以下字段之一唯一定位：

- `room_id`
- `stream_key`
- `publisher_identity`
- `purpose`，取值为 `ingest` 或 `play`

服务端可以根据部署环境选择把这些字段存入关系库、Redis 或事件账本，但必须保留幂等键。
幂等键建议为：

```text
provider:<room_id>:<stream_key>:<purpose>:<event_id>
```

## 3. 状态模型

建议的 provider 状态机如下：

```text
PROVISIONED -> ACTIVE -> GRACE -> ENDED
```

- `PROVISIONED`：房间已创建，但 provider 还未确认可用。
- `ACTIVE`：收到有效的 publish/play 回调并通过授权。
- `GRACE`：provider 失联或异常断开，正在等待恢复窗口。
- `ENDED`：会话终止，不再接受旧 token 或旧回调。

## 4. 收敛规则

1. `on_publish` 和 `on_play` 只要通过授权，就必须可重复调用而不重复创建会话。
2. `on_unpublish` 和 `on_stop` 只要针对同一个会话，重复调用必须幂等返回成功或被动拒绝，不得重复扣减状态。
3. 会话是否存活，不能依赖 `update_time`。
4. 失联后的收敛必须由有界 grace period 或显式 reconciliation job 驱动。
5. 回调日志只能记录脱敏后的 room / stream / purpose / result，不能输出 token、密钥、完整 URL 或媒体内容。

## 5. Heartbeat / Reconciliation

当前仓库已经有 Redis TTL presence，但 provider 会话恢复仍需要一个明确契约：

- `last_seen_at` 由 provider 回调、健康检查或 reconciliation job 更新。
- `grace_period_seconds` 应对短暂重启或网络抖动。
- reconciliation job 只做状态收敛，不做媒体转发。
- 如果 provider 重启后不能找到活跃会话，状态应从 `ACTIVE` 进入 `GRACE`，再按业务规则终止或恢复。

## 6. 验收门

在 RTC-006 被标记 `completed` 前，至少应具备以下证据：

- callback token 校验
- stream key / room ownership / token purpose 校验
- 回调幂等
- Redis presence 多实例不重复计数
- provider 重启或异常断开后的有界收敛
- 不使用 `update_time` 判活

## 7. 仍待实现

本契约只是补齐约束，不代表当前代码已经提供完整 provider session 表或 reconciliation worker。
实现阶段应由后续任务把这份契约落成数据表、定时器或事件账本。
