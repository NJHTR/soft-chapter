# LiveKit Webhook 契约（RTC-004）

## 1. 端点

- 路径：`POST /api/rtc/webhook/livekit`。
- 不使用用户登录态；`SessionFilter` 仅对白名单放行，控制器必须完成 provider 签名校验。
- 签名失败返回 `401`；JSON 无法解析返回 `400`；首次处理和重复投递都返回 `200`，便于 LiveKit 重试。

## 2. 官方签名（生产主路径）

LiveKit Server 发送：

```text
Authorization: Bearer <LiveKit AccessToken JWT>
```

JWT 使用 LiveKit API secret 签名，`iss` 必须等于 LiveKit API key，`exp` 必须有效，`sha256` claim 是原始请求体 SHA-256 的 Base64 摘要。服务端必须：

1. 使用 `RTC_LIVEKIT_WEBHOOK_SECRET` 验证 JWT（该值必须与 LiveKit API secret 相同）。
2. 使用 `RTC_LIVEKIT_API_KEY` 校验 `iss`。
3. 对未经 JSON 解析的原始 body 计算 SHA-256，并用常量时间比较 `sha256`。

## 3. 迁移兼容签名（有关闭日期）

历史测试和旧 bridge 可发送：

```text
LiveKit-Signature: v0=<lowercase-hex>
```

其中 `hex = HMAC-SHA256(RTC_LIVEKIT_WEBHOOK_SECRET, rawRequestBodyBytes)`。当 `Authorization` header 存在时，服务端不得回退到该兼容 header；这样可以避免无效官方 token 借兼容路径绕过校验。HMAC 入口必须单独监控，并在 legacy 退役任务中关闭。

## 4. 幂等与账本

- 事件 `id` 是 `rtc_webhook_ledger.event_id` 的唯一键。
- 首次请求先落账，再执行状态收敛；重复投递直接返回 `duplicate=true`，不重复推进状态。
- 落账和状态动作不是同一事务；异常窗口由 TTL worker 与后续 reconciliation 任务兜底，不能把 `200` 当成状态已完成的证明。

## 5. 事件映射

| LiveKit 事件 | 服务端动作 |
|---|---|
| `room_started` / `room_created` | `CallService.confirmConnected` |
| `participant_joined` | `CallService.joinCall`（只允许服务端 roster 成员） |
| `participant_left` | `CallService.leaveCall` |
| `room_finished` | `hangupCall` + `confirmEnded` 收敛 |
| `track_*`、`egress_*`、其他事件 | 只落账审计，不隐式修改通话状态 |

事件中的 participant identity 只是关联线索，不授予权限。房间名由服务端 token 决定，未知房间只记录审计。

## 6. 配置

```text
RTC_LIVEKIT_API_KEY=devkey
RTC_LIVEKIT_API_SECRET=<至少 32 字节>
RTC_LIVEKIT_WEBHOOK_SECRET=<与 API secret 相同>
RTC_TOKEN_TTL_SECONDS=300
```

LiveKit 目标地址由 `deploy/streaming/livekit.yaml` 的 `webhook.urls` 配置。官方 JWT header 的真实容器到后端回调、事件顺序和数据库迁移必须在 RTC-004 联调中验证；单元签名向量不能替代端到端验收。
