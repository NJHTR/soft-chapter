# LiveKit Webhook 契约(RTC-003)

## 1. 端点

- 路径：`POST /api/rtc/webhook/livekit`（新端点）
- 鉴权：不走登录态；走签名校验（见 §2）。`SessionFilter` 白名单已放行该前缀。
- 非 200 响应：校验失败返回 `401`；内部错误返回 `500`（LiveKit 会按退避重试）。

## 2. 签名

LiveKit 对每个 webhook 请求计算签名并放入请求头：

```text
LiveKit-Signature: v0=<hex>
```

算法（与 LiveKit 官方实现一致）：

```text
v0 = hex( HMAC-SHA256( secret, rawRequestBodyBytes ) )
```

- `secret` = `LIVEKIT_API_SECRET`（LiveKit 侧签名密钥与其 API key 的 secret 相同；后端通过 `RTC_LIVEKIT_WEBHOOK_SECRET` 注入，必须一致）。
- body 使用**原始字节**，不得先解析 JSON 再签名。
- 比较使用常量时间（`MessageDigest.isEqual`），防时序攻击。
- 若 `RTC_LIVEKIT_WEBHOOK_SECRET` 未配置，端点 `fail-closed`：一律拒绝（不提供弱默认值）。

算法漂移防护：`server/src/test/java/com/douyin/rtc/WebhookSignatureVectorTest.java` 硬编码一条固定向量（secret + body → 期望 hex），任何算法/编码变化都会导致测试失败。

## 3. 幂等与账本

- 每次收到的 webhook 事件先写 `rtc_webhook_ledger`（`event_id` 唯一索引，`INSERT IGNORE`）。
- 已存在（0 行受影响）→ 判定为重放，直接返回 200 幂等响应，不重复执行状态动作。
- 落账本与状态动作非原子：账本先于动作；崩溃窗口内状态由 `CallTtlWorker` 兜底收敛（已登记为已知边界，见任务文件）。

## 4. 事件 → CallState 映射

| LiveKit 事件 | 服务端动作 | 依据 |
|---|---|---|
| `room_created`/`room_started` | `CallService.confirmConnected` | §2 NEGOTIATING→connected→CONNECTED |
| `participant_joined` | `CallService.joinCall` | roster 守卫在服务端 |
| `participant_left` | `CallService.leaveCall` | §3 →LEFT |
| `room_finished` | `hangupCall` + `confirmEnded`(收敛) | §2 CONNECTED→ENDING→ENDED |

- 事件 `identity` 不信任：非成员 join 仅记录，不授予权限。
- `room_finished` 在 CONNECTED 前到达时按媒体面空收敛（可能先于客户端 hangup 命令落账本），语义已注释在实现中。

## 5. 配置注入

```text
RTC_LIVEKIT_API_KEY=devkey
RTC_LIVEKIT_API_SECRET=<同 LIVEKIT_API_SECRET>
RTC_LIVEKIT_WEBHOOK_SECRET=<同 LIVEKIT_API_SECRET>
RTC_TOKEN_TTL_SECONDS=300   # [60,900]
```

LiveKit 侧 webhook 目标地址：`deploy/streaming/livekit.yaml` 的 `webhook.urls`（当前指向 `http://host.docker.internal:9191/api/rtc/webhook/livekit`）。

## 6. 已知未验证

- 端到端 webhook 推送（LiveKit 容器 → 后端）依赖后端起来且 migration_034/035 执行，属联调阶段（RTC-004）验收，不宣称已在 RTC-003 完成。