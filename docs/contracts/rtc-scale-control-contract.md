# RTC 规模化控制契约

- 契约版本：`douyin.rtc.scale.v1`
- 状态：`draft`
- 关联任务：RTC-SCALE-001、RTC-006、RTC-007、RTC-011、RTC-012、RTC-013、RTC-014、RTC-015
- 生效条件：各子任务通过自己的真实环境门禁后分别启用

本文只定义控制面、配置和审计契约，不证明任何生产容量。LiveKit、coturn、SRS、CDN 和浏览器
仍然承载媒体；Spring Boot、Kafka、数据库和聊天 WebSocket 不得转发 RTP、SDP、音视频帧或
完整高频 WebRTC stats。

## 1. 通用 envelope

新增规模化命令和摘要沿用 `douyin.realtime.v1`，payload 中增加以下公共字段：

| 字段 | 约束 | 说明 |
|---|---|---|
| `event_id` | 必填，全局唯一，8～128 字符 | 幂等键 |
| `trace_id` | 必填，8～128 字符 | 关联控制面、provider 和客户端摘要 |
| `call_id` / `room_id` | 至少一个 | 业务真相源标识，不作为 Prometheus label |
| `generation` | 非负整数 | 拒绝旧 provider、stage 或 topology 事件 |
| `occurred_at` | RFC 3339 UTC | 事件发生时间 |
| `expires_at` | RFC 3339 UTC | 超时后拒绝执行，审计事件仍可保留 |
| `actor_id` | 服务端从凭据解析 | 禁止相信客户端自报身份 |

重复 `event_id` 必须返回第一次提交的结果。相同资源上较旧 `generation` 或已过 `expires_at` 的
命令返回稳定错误，不得隐式修正状态。错误码至少包括 `CAPACITY_UNAVAILABLE`、
`CAPACITY_STALE`、`TOPOLOGY_NOT_ELIGIBLE`、`CONSENT_REQUIRED`、`STAGE_LIMIT_REACHED`、
`GENERATION_STALE` 和 `PROVIDER_UNAVAILABLE`。

## 2. 容量登记与 admission

### 2.1 节点容量快照

LiveKit、coturn、SRS/CDN adapter 每 5～15 秒登记低基数节点快照。快照包含：

```text
schema_version, snapshot_id, node_id, node_epoch, snapshot_seq,
provider, region, zone, version,
observed_at, expires_at, draining, healthy,
rooms, participants, connections, publishers, subscribers, tracks,
egress_mbps, rtp_pps, relay_allocations, relay_ingress_mbps, relay_egress_mbps,
cpu_ratio, memory_ratio, reconnect_rate,
rtt_ms_p95, packet_loss_ratio_p95, nack_rate, pli_rate, fir_rate
```

- `node_id` 只进入容量登记和明细存储，不由应用作为任意 Prometheus label 写入。每节点告警使用
  Prometheus scrape target 生成、受部署清单约束的 `instance`；业务聚合标签只允许
  `provider/region/zone/version/codec/media_kind/reason`。
- `node_epoch` 在 provider 进程重启时变化，`snapshot_seq` 在同一 epoch 内严格递增；
  `snapshot_id=(node_id,node_epoch,snapshot_seq)`。Adapter 在受控明细存储中以同一 `snapshot_id`
  写入 provider resource membership（例如 LiveKit room），但 membership 不进入 Prometheus/Kafka。
- 每个计数或速率同时声明配置上限。没有可验证上限的维度只可告警，不能伪造利用率。
- 快照过期后不得用于新房 placement。Redis/registry 不可用且没有新鲜本地快照时，新房默认
  fail-closed；已有房间继续运行并告警，不得被静默迁移或踢出。
- 原始 RTP 包和逐包事件不进入 Redis、Kafka 或 Spring。provider exporter 只暴露聚合计数。

### 2.2 Admission 请求与结果

Admission 在创建新 call/stage、首次媒体 token 和 stage promotion 前执行。请求字段为：

```text
request_id, call_id/room_id, scope, mode, region,
expected_publishers, expected_subscribers, requested_video_quality,
existing_room, requested_at
```

结果只能为：

| 决策 | 行为 |
|---|---|
| `ALLOW` | 签发正常权限或创建新房 |
| `DEGRADE_VIDEO` | 保持音频，限制视频层/订阅数；返回明确降级策略 |
| `REJECT_NEW_ROOM` | 不创建房间，不签发首次媒体 token；返回可重试时间 |

决策结果包含 `decision_id`、低基数 `reason`、`snapshot_age_ms`、`retry_after_ms`、
`effective_video_quality` 和 `expires_at`。相同 `request_id` 使用 TTL reservation 幂等返回；
reservation 不得因客户端重试重复占用容量。

Reservation 使用以下生命周期；`PENDING` 和 `CONSUMED` 计入尚未被快照吸收的容量，`ABSORBED`
不再额外计数：

```text
PENDING -> CONSUMED -> ABSORBED
PENDING|CONSUMED -> RELEASED
PENDING -> EXPIRED
```

- admission 必须以原子操作同时检查新鲜快照、未被 provider 快照吸收的 reservation 和阈值，再创建
  `PENDING`；同一 `request_id` 返回原记录。记录至少包含 `assigned_node_id/node_epoch` 和 admission
  使用的 `snapshot_seq_floor`。
- provider 接受创建/首次 admission 后记录稳定 `provider_resource_id` 并进入 `CONSUMED`。只有同一
  `node_id/node_epoch` 上 `snapshot_seq > snapshot_seq_floor` 的 membership 明细明确包含该 resource，
  才能 CAS 为 `ABSORBED`；对应 aggregate snapshot 此时接管计数，避免双计或容量空窗。
- provider 拒绝或控制面创建失败必须显式 `RELEASED`。`PENDING` lease 到期只能在控制面和 provider
  均确认资源不存在后进入 `EXPIRED`；不确定时保持占用并告警。
- reconciler 按 `decision_id + call_id/room_id` 收敛孤儿 reservation；若 `PENDING` 对应的 provider
  resource 已存在，必须补写实际 node/epoch/resource id 并进入 `CONSUMED`，再等待明确 membership
  absorption。状态变化使用 CAS，重复 consume/absorb/release 不得重复增减容量。

- 规划阈值：任一可靠维度达到 70% 持续 5 分钟，告警并扩容，目标保留 30% headroom。
- 硬门禁：任一可靠维度达到 80%，停止新房或只允许音频/低层视频。80% 是紧急保护线，
  不是正常容量目标。
- `existing_room=true` 的重连不得因瞬时容量门禁被分配到另一房间；仅可在原房重试、音频优先
  或进入用户可见的失败状态。

## 3. 客户端 QoE 摘要

客户端以 2～5 秒窗口上报聚合摘要。服务端必须校验当前用户是 call/room 成员，限制 body、
轨道数、上报频率和时间偏移。摘要字段为：

```text
schema_version, event_id, trace_id, call_id, room_id,
provider, topology, selected_local_candidate_type, selected_remote_candidate_type,
transport_outcome, sampled_at, window_ms,
connection_state, first_frame_ms, freezes, freeze_duration_ms,
rtt_ms, jitter_ms, packets_lost, packets_received,
bytes_sent, bytes_received, available_outgoing_bitrate,
frames_encoded, frames_decoded, frames_dropped,
frame_width, frame_height, frames_per_second,
nack_count, pli_count, fir_count, reconnect_count,
cpu_ratio, memory_mb, battery_delta
```

`topology` 只取 `p2p|sfu`。selected local/remote candidate type 分别使用 WebRTC 标准枚举
`host|srflx|prflx|relay`；`transport_outcome` 是派生业务分类 `direct|srflx|relay|sfu`：P2P
host/host 为 direct，任一非 relay 的 srflx/prflx 为 srflx，任一 relay 为 relay，SFU 为 sfu。
room/user/call 明细写入受控聚合存储；Kafka 仅允许发送窗口摘要，不允许发送完整
`RTCStatsReport`。Prometheus 不使用 room/user/call/trace 作为 label。

## 4. 选择性订阅与轨道生命周期

### 4.1 Provider-neutral port

Join 必须显式携带 `scope=direct|group|stage`。订阅意图以 `(participant_id, publication_id,
source)` 为键，其中 source 为 `microphone|camera|screen_share|screen_share_audio`。端口提供：

```text
setPublicationSubscription(publication_id, audio/video subscribed)
setPublicationVideoQuality(publication_id, high/medium/low)
setParticipantVisibility(participant_id, visible, focused)
setApplicationVisibility(foreground/background, minimized)
```

业务组件不得引用 LiveKit 类型。Adapter 只有在期望值变化时才调用 provider subscription/quality
API，避免 active speaker 更新造成重复信令。

### 4.2 策略顺序

1. 音频默认保持订阅；极端弱网也先降视频。
2. 屏幕共享优先于摄像头，默认 `HIGH` 且不能因分享者摄像头 tile 隐藏而退订。
3. 主画面和 active speaker 使用 `HIGH`；普通可见 tile 使用 `MEDIUM`，参与者较多或弱网时
   可降为 `LOW`。
4. 隐藏 tile、最小化和后台标签页暂停普通摄像头视频，保持音频；恢复前台后按最新 publication
   registry 重放策略。
5. `direct` 默认保持完整订阅和高质量基线。

Adapter 始终按 `(participant_id, publication_id, source)` 维护 registry，由同一复合键重建
MediaStream。摄像头重开、屏幕共享开始/结束、迟到 mute/unsubscribe 和 participant leave 都不得
让旧 ended track 覆盖新轨道。
Room/local participant/document 监听器必须按 generation 显式解绑。

`adaptiveStream` 在真实 `RemoteTrack.attach()/detach()` 或等价可见性契约、2/4/8 人浏览器矩阵
通过前保持关闭。回滚开关 `RTC_SELECTIVE_SUBSCRIPTION_ENABLED=false` 恢复 LiveKit SFU 全订阅；
群聊 mesh 和客户端 relay 永远不是回滚路径。

## 5. 多节点、Redis 与 TURN 区域池

LiveKit 使用自身支持的 Redis room routing、节点发现和房间粘着。Spring 只执行 admission、token、
ACL 和审计，不实现 RTP 路由。节点登记至少包含 `node_id/region/zone/advertised_ip/version/
draining/capacity/expires_at`。

- 新房只选择健康、非 draining 且低于 admission 硬门禁的节点。
- 已有房间在 node drain 时自然结束；未经 LiveKit 官方迁移能力验证，不做透明跨节点热迁移。
- Redis 失效时禁止创建需要集群路由的新房；已有单节点房间按 provider 能力继续并告警。
- TURN pool 由控制面按 region、健康和优先级返回短期凭据；共享密钥不进入浏览器或仓库。
- TURN UDP 失败后允许 TCP/TLS 区域切换，记录 `from_region/to_region/transport/reason`，不得将
  relay 冒充 direct。

配置模板只证明语法和契约。没有至少两个 LiveKit 节点、共享 Redis、真实 UDP/TCP/TLS TURN 和
故障注入证据时，RTC-013 不能进入 `verified`。

## 6. Stage + Audience 状态机

普通观众保持在 SRS/CDN audience，不创建 LiveKit participant。Stage 首期最多 8 名发布者。

```text
AUDIENCE -> REQUESTED -> PROMOTING -> ON_STAGE -> DEMOTING -> AUDIENCE
                    \-> REVOKING -> REVOKED
```

| 命令 | 权限与效果 |
|---|---|
| `stage.request` | 观众本人申请；重复请求返回同一 request |
| `stage.approve` | 主播/主持人批准；预留名额并进入 PROMOTING |
| `stage.joined` | provider webhook/受控确认；generation 匹配后进入 ON_STAGE |
| `stage.demote` | 本人退出或主持人下麦；进入 DEMOTING，停止补签并撤销 provider 发布权限 |
| `stage.left` | provider 确认或有界超时收敛到 AUDIENCE |
| `stage.revoke` | 权限撤销；立即拒绝补签，进入 REVOKING，provider 确认后进入 REVOKED |

所有命令使用 `event_id + stage_generation` 幂等/CAS。乱序旧 generation 不得复活成员。已签发并
使用的 JWT 不能靠停止补签撤销：DEMOTING/REVOKING 必须调用 LiveKit participant permission API
移除 publish 权限；provider 不支持或调用失败时强制断开该 participant，并以 webhook/查询确认其
不再发布。确认超时由 generation-aware reconciler 重试和告警，不能把成员提前标成已撤销。

Stage 到 SRS 使用 LiveKit Egress provider 控制命令，媒体不经过 Spring。Ingress 只用于把外部源
导入 LiveKit，不属于正常 Stage-to-SRS 路径。Egress 失败时回滚到当前 SRS 单主播 audience，不能
把全体观众迁入 Stage。观众降级和断线恢复只切换 WHEP/LL-HLS/HLS/HTTP-FLV/CDN 出口，不授予
发布权限。

## 7. 受控 1 对 1 P2P

`RTC_P2P_ENABLED=false` 是默认且 fail-closed。只有以下条件同时满足才可产生 P2P attempt：

- `scope=direct`、恰好两名有效参与者；
- 双方针对当前 `call_id + topology_generation` 明确同意且 consent 未过期；
- 业务状态仍为 ACCEPTED/NEGOTIATING，客户端仍显示 CONNECTING；
- 无录制、审核、Stage、群聊或强制 SFU 策略；
- ACL、短期信令 token 和 TURN 临时凭据均有效。

状态机为：

```text
DISABLED -> ELIGIBLE -> CONSENTED -> PROBING
PROBING -> P2P_CONNECTED | FALLING_BACK -> SFU_CONNECTED | FAILED
```

PROBING 预算为 1.5～3 秒。只接受 selected local/remote candidate type 均不是 `relay` 的 ICE
连接：host/host 记为 `direct`，任一 `srflx|prflx` 记为 `srflx`。任一 candidate 为 `relay`、
超时、权限变化或探测质量不达标，立即记录该 outcome，进入 `FALLING_BACK` 并连接原 LiveKit SFU。
在业务已经 `CONNECTED` 后发生质量恶化时，必须先进入用户可见的 `RECONNECTING`，由控制面
记录 generation 和 fallback reason，再重连 SFU；禁止后台静默换房间或拓扑。

每次 attempt 记录 `topology=p2p|sfu`、selected local/remote candidate type、派生的
`direct|srflx|relay|sfu` outcome、RTT、丢包、CPU/电量、SFU egress 和稳定的回退原因。
版本化 P2P offer/answer/ICE 只能承载 SDP/ICE 控制数据，限制大小、成员、顺序和 TTL；不得复用
旧 Call.vue mesh，也不得允许聊天 WS 转发媒体/base64。

## 8. 回滚与审计

| 能力 | 默认/回滚 |
|---|---|
| 容量硬门禁 | 保留告警；紧急时可切换为仅音频，不可绕过 ACL |
| 选择性订阅 | `RTC_SELECTIVE_SUBSCRIPTION_ENABLED=false` 回到 SFU 全订阅 |
| adaptiveStream | 始终关闭，直到独立验收 |
| 多节点 | drain 新节点，DNS/LB 回到已验证单节点；已有房间自然结束 |
| Stage | 禁止新 promotion，撤销 stage token，audience 保持 SRS/CDN |
| P2P | `RTC_P2P_ENABLED=false`，所有新呼叫使用 LiveKit SFU |

审计记录不得包含 JWT、TURN 密码、完整媒体 URL、SDP、ICE 私网地址或用户原始媒体。回滚事件
必须记录 actor、reason、generation、配置版本、开始/结束时间和受影响的聚合数量。

## 9. 验证门禁

配置解析、单元测试、mock provider 和静态构建只能形成前置证据。最终验证仍要求：真实浏览器
1 对 1、2/4/8 人轨道矩阵；TURN UDP/TCP/TLS；LiveKit 多节点/Redis 故障；SRS restart；Stage
+ Audience；带宽、SFU egress、客户端 CPU、P2P 成功/回退/relay 比例。未执行项必须逐项记录，
不得由合成常量或单机数据替代。
