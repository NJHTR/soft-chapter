# 通话领域模型与状态机

## 1. 核心实体

### CallSession

```text
call_id            全局不可变 ID
room_id            provider room ID
scope              direct | group | live-interactive
mode               audio | video
initiator_id       发起者
provider           livekit | p2p-fallback | legacy
state              服务端权威状态
expires_at         ringing/negotiating TTL
connected_at       首次进入 connected 的时间
ended_at           结束时间
end_reason         hangup/rejected/expired/failed/permission/provider
client_request_id  发起幂等键
```

### CallParticipant

```text
call_id, user_id, role
state              invited | ringing | joining | connected | reconnecting |
                   left | rejected | failed
joined_at, left_at, reason
profile_snapshot   用于历史展示，不作为权限来源
```

### CallEvent

```text
event_id, call_id, participant_id, kind, seq, occurred_at, payload, trace_id
```

`event_id` 全局幂等；同一 call/participant 的 `seq` 单调递增。聊天消息 `msg_type=10/11` 保持向后兼容，但只是 CallEvent 的投影。

## 2. CallSession 状态机

```text
CREATED -> RINGING -> ACCEPTED -> NEGOTIATING -> CONNECTED
   |          |          |             |             |
   |          |          +-> REJECTED  |             +-> ENDING
   |          +-> CANCELLED/EXPIRED   +-> FAILED          |
   +-----------------------------------------------> FAILED
                                                        |
                                                        v
                                                     ENDED
```

完整转换表如下；没有列出的转换必须返回 `INVALID_STATE_TRANSITION`，而不是隐式修正状态：

| 当前状态 | 事件/命令 | 目标状态 | 守卫与幂等行为 |
|---|---|---|---|
| `CREATED` | `create` | `RINGING` | `client_request_id` 唯一；重复返回原结果 |
| `CREATED` | `fail` | `FAILED` | 仅系统/超时 worker；记录 provider 或依赖错误 |
| `RINGING` | `accept` | `ACCEPTED` | 目标参与者且未过期 |
| `RINGING` | `reject` | `REJECTED` | 目标参与者；重复返回原结果 |
| `RINGING` | `cancel` | `CANCELLED` | 发起者或系统；重复安全 |
| `RINGING` | `expire` | `EXPIRED` | TTL worker；客户端不能伪造 |
| `ACCEPTED` | `negotiate.start` | `NEGOTIATING` | provider token 有效 |
| `ACCEPTED` | `fail` | `FAILED` | provider/权限/媒体错误 |
| `NEGOTIATING` | `connected` | `CONNECTED` | provider webhook 或可信客户端确认 |
| `NEGOTIATING` | `hangup` | `ENDED` | 尚未接通也要保留结束原因 |
| `NEGOTIATING` | `fail` | `FAILED` | 重试次数和时间预算有限 |
| `CONNECTED` | `hangup` | `ENDING` | 由 owner 或最后参与者触发 |
| `CONNECTED` | `fail` | `FAILED` | provider 丢失或安全阻断 |
| `ENDING` | `ended` | `ENDED` | provider/webhook 收敛；重复安全 |

终态集合为 `REJECTED`、`CANCELLED`、`EXPIRED`、`FAILED`、`ENDED`。终态只接受重复查询或相同 `event_id` 重放；任何新命令返回当前状态，不创建第二条记录。`RINGING` 默认 TTL 30 秒，`NEGOTIATING` 默认 TTL 5 分钟，具体值由配置和指标调整。

## 3. 参与者状态机

```text
INVITED -> RINGING -> JOINING -> CONNECTED -> RECONNECTING -> CONNECTED
    |         |          |          |              |
    +-> REJECTED/CANCELLED  +-> FAILED       +-> LEFT/FAILED
```

成员列表来自服务端群组快照，不能依赖客户端传入的昵称、空 `user_id` 或 `group_members` 文本。群成员变更通过 `participant.joined/left` 事件传播。

## 4. 命令与协商分离

生命周期命令：`create`, `accept`, `reject`, `cancel`, `join`, `leave`, `hangup`。
协商事件：`offer`, `answer`, `ice`, `ice_complete`, `renegotiate`, `ice_restart`。这些事件只有 P2P fallback/legacy adapter 使用；LiveKit 主路径由 provider SDK 内部处理。
媒体控制：`mute`, `camera`, `screen_share`, `subscribe`, `quality_preference`。

生命周期命令走控制面状态机；provider adapter 处理协商细节；媒体控制只能影响已授权参与者的轨道。

## 5. 数据库演进建议

新增 `rtc_call_session`、`rtc_call_participant`、`rtc_call_event`，并为 `event_id`、`client_request_id`、`call_id + user_id` 建唯一约束。三张表和 provider webhook ledger 归 `rtc-persistence` 所有；`chat-persistence` 只写投影。现有 `t_message` 中的 10（音频）和 11（视频）以及 `extra.callState` 0/1/2 保留为兼容投影，新增 `call_id`、`participants`、`ended_at`、`schema_version` 字段时使用向后兼容 JSON。
