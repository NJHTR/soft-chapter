# Douyin RTC 高并发呼叫生命周期、故障韧性与媒体扩容总任务

你是 Douyin 仓库的主开发智能体，负责 RTC、实时通话、直播、LiveKit、TURN、SRS、控制面高并发和媒体扩容。

本任务不是简单增加一个“60 秒自动取消”。

你必须把整个 RTC Call Lifecycle 从：

> 发起呼叫 → 被叫未登录 → 被叫上线 → 响铃 → 接听/拒绝 → CONNECTING → P2P/SFU → CONNECTED → 通话 → 挂断 → 超时 → 异常恢复 → 历史持久化

设计并实现成一套**高并发、幂等、可恢复、可观测、抗故障、事件顺序正确**的生产级架构。

同时继续完成：

- RTC-006 provider recovery / session / reconciliation
- RTC-011 capacity observability
- RTC-012 selective subscription
- RTC-013 LiveKit distributed
- RTC-014 Stage + Audience
- RTC-015 controlled one-to-one P2P

禁止为了完成本任务而绕过已有 Project Constitution、TASK_PROTOCOL、AGENTGIT_PROTOCOL 或已有任务边界。

---

# 一、最高优先级：先理解当前仓库，不允许直接改代码

开始前必须读取：

```text
docs/project-constitution/PROJECT_CONSTITUTION.md
docs/project-constitution/TASK_PROTOCOL.md
docs/project-constitution/AGENTGIT_PROTOCOL.md
docs/project-constitution/state/PROJECT_STATE.yaml
docs/project-constitution/state/WORK_LOG.md
docs/project-constitution/tasks/TASK_INDEX.md

docs/project-constitution/tasks/RTC-006-live-migration.md
docs/project-constitution/tasks/RTC-011-capacity-observability.md
docs/project-constitution/tasks/RTC-012-adaptive-subscription.md
docs/project-constitution/tasks/RTC-013-livekit-distributed.md
docs/project-constitution/tasks/RTC-014-stage-audience.md
docs/project-constitution/tasks/RTC-015-p2p-one-to-one.md

docs/adr/ADR-005-CLIENT-OFFLOAD-AND-SCALE.md
docs/architecture/RTC_SCALE_AND_CLIENT_OFFLOAD.md

src/modules/rtc/adapter/livekitAdapter.ts
src/modules/rtc/adapter/rtcMediaPort.ts
src/modules/rtc/quality/subscriptionPolicy.ts
src/modules/rtc/store/useRtcStore.ts
src/modules/rtc/components/CallPanel.vue

deploy/streaming/
```

然后执行：

```bash
git status --short
git branch --show-current
git log --oneline --decorate -20
git diff
git diff --cached

rg --files src/modules/rtc server/src/main/java/com/douyin/rtc docs deploy/streaming
```

如果存在 RTC-006 provider callback、session、reconciliation 未提交修改：

1. 不覆盖；
2. 不 reset；
3. 不 checkout；
4. 先识别修改边界；
5. 先完成 RTC-006 的测试边界；
6. 再进行本任务。

禁止：

```text
git reset --hard
git checkout --
git clean -fd
git push --force
```

禁止删除、覆盖用户已有未提交修改。

---

# 二、首先重新定义问题

当前系统不能只考虑：

```text
A 呼叫 B
B 60秒没接
TIMEOUT
```

必须考虑以下完整问题集合。

---

# 三、问题 1：被叫没有登录

必须支持：

```text
A 已登录

A → B

B 当前未登录
```

此时不能简单认为：

```text
B offline
→ 创建无意义 WebRTC 房间
```

而应该：

```text
A 发起呼叫
    ↓
查询 B 当前身份/在线状态
    ↓
B 未登录
    ↓
CallSession = RINGING / PENDING
    ↓
保存 expireAt
    ↓
等待 B 在有效窗口内上线
```

但必须明确业务语义：

```text
offline
≠
不存在
≠
拒绝
≠
超时
```

必须区分：

```text
USER_NOT_FOUND
USER_OFFLINE
USER_ONLINE
USER_BUSY
USER_BLOCKED
USER_UNAUTHORIZED
```

---

# 四、问题 2：呼叫过程中被叫登录

必须处理：

```text
01:00 A 呼叫 B
01:00 B 未登录

01:20 B 登录

01:20 B 应该能够发现：
“有人正在呼叫我”
```

登录成功以后必须执行：

```text
Authentication
    ↓
Online Presence
    ↓
Active Call Reconciliation
    ↓
查询/恢复有效 CallSession
    ↓
推送 CALL_RINGING
```

禁止只依赖：

```text
WebSocket 登录时实时收到一条消息
```

因为消息可能在用户登录前已经产生。

因此必须存在：

```text
Call Reconciliation
```

登录后主动恢复当前有效呼叫状态。

---

# 五、问题 3：离线呼叫不能依赖 WebSocket 消息可靠送达

错误架构：

```text
A 呼叫 B
 ↓
Kafka
 ↓
WebSocket
 ↓
B
```

如果 B 当时未登录：

```text
消息发送失败
```

不能导致：

```text
CallSession 消失
```

也不能导致：

```text
B 永远不知道有人呼叫
```

正确模型：

```text
CallSession 是事实
WebSocket 是通知通道
```

所以：

```text
CALL_RINGING
```

必须可以：

```text
实时 push
```

也必须可以：

```text
登录后 reconciliation
```

---

# 六、问题 4：事件延迟导致“明明接通了却已经 TIMEOUT”

必须重点解决。

例如：

```text
服务器时间：
12:00:59.900

A：
显示正在呼叫

B：
点击接听

B → Gateway
```

但因为网络延迟：

```text
12:01:00.300
```

Timeout Worker 已经看到：

```text
expireAt < now
```

然后：

```text
RINGING → TIMEOUT
```

随后 B 的：

```text
ACCEPT
```

才到达。

系统不能简单依赖客户端显示时间。

---

# 七、必须定义权威时间

客户端：

```text
Date.now()
```

不能作为最终状态判断依据。

必须由服务端产生：

```text
createdAt
ringAt
expireAt
serverNow
```

所有状态转换以服务端时间为准。

但必须进一步处理：

```text
网络延迟
请求到达时间
事件产生时间
事件处理时间
客户端显示时间
```

不要假设它们相等。

---

# 八、必须定义“接听”和“超时”的竞争规则

必须明确：

```text
RINGING
```

允许：

```text
ACCEPT
REJECT
CANCEL
TIMEOUT
```

但最终只能有一个成功状态转换。

必须使用：

```text
CAS
版本号
状态版本
eventId
requestId
```

等机制。

例如：

```sql
UPDATE call_session
SET status = 'TIMEOUT'
WHERE call_id = ?
  AND status = 'RINGING'
  AND version = ?;
```

或者等价的原子状态机。

成功后：

```text
affectedRows = 1
```

表示本次状态迁移成功。

失败：

```text
affectedRows = 0
```

表示其他操作已经赢得状态竞争。

禁止：

```text
先 SELECT
再 UPDATE
```

这种非原子状态转换。

---

# 九、必须处理“边界时间接听”

必须测试：

```text
T-5s 接听
T-1s 接听
T-100ms 接听
T+100ms 接听
T+1s 接听
```

并明确业务契约。

不能出现：

```text
前端：
已接听

服务端：
已超时

B：
CONNECTED

A：
TIMEOUT
```

这种双重事实。

最终必须存在：

```text
唯一 authoritative state
```

并通过：

```text
stateVersion
eventVersion
server timestamp
```

让所有客户端最终收敛。

---

# 十、Call State Machine 必须正式定义

建议至少：

```text
INITIATED
RINGING
ACCEPTED
CONNECTING
CONNECTED
REJECTED
CANCELLED
TIMEOUT
CONNECT_FAILED
ENDED
```

必要时增加：

```text
BUSY
EXPIRED
FAILED
RECOVERY_PENDING
```

但不能随意增加状态。

必须在任务/架构文档中明确：

```text
允许状态迁移
非法状态迁移
幂等状态迁移
终态
恢复状态
```

例如：

```text
INITIATED
   ↓
RINGING
   ├── ACCEPTED
   ├── REJECTED
   ├── CANCELLED
   └── TIMEOUT

ACCEPTED
   ↓
CONNECTING
   ├── CONNECTED
   └── CONNECT_FAILED

CONNECTED
   ↓
ENDED
```

终态：

```text
REJECTED
CANCELLED
TIMEOUT
CONNECT_FAILED
ENDED
```

不能再次迁移。

---

# 十一、必须解决 Redis 缓存穿透

考虑恶意请求：

```text
GET call/nonexistent-1
GET call/nonexistent-2
...
```

必须考虑：

```text
参数校验
ID 格式验证
Bloom Filter 或等价机制
Negative Cache
Rate Limit
```

不能让不存在的 CallSession 全部打到 MySQL。

---

# 十二、必须解决 Redis 缓存击穿

热点 CallSession 失效时：

```text
Redis MISS
 ↓
大量请求
 ↓
MySQL
```

必须考虑：

```text
Single Flight
Mutex
热点 Key 保护
TTL Jitter
```

避免瞬间回源。

---

# 十三、必须解决 Redis 缓存雪崩

禁止：

```text
1000万 key
TTL = 60s
```

导致同一时间大规模过期。

必须使用：

```text
TTL Jitter
```

并考虑：

```text
Redis Cluster
副本
故障转移
热点 Key 分片
连接池保护
```

---

# 十四、Redis 不能成为唯一事实来源

必须明确：

```text
Redis = realtime state/index
MySQL = durable fact
Kafka/Outbox = event durability
```

Redis 故障时：

```text
不能导致所有 CONNECTED 通话被强制挂断
```

已建立：

```text
WebRTC
LiveKit
```

应该继续运行。

Redis 恢复后：

```text
reconciliation
```

重建必要状态。

---

# 十五、Call Timeout 不能通过扫描 MySQL 实现

禁止：

```sql
SELECT *
FROM call_session
WHERE status = 'RINGING'
AND expire_at < NOW();
```

每秒全表扫描。

必须设计：

```text
Redis ZSET
或
Delay Queue
或
时间轮
```

并支持分片：

```text
timeout:00
timeout:01
...
timeout:N
```

Timeout Worker 水平扩展。

---

# 十六、Timeout Worker 必须幂等

Worker 可能：

```text
重复执行
重复获取
重复重试
宕机恢复
主从切换
```

所以必须：

```text
CAS
eventId
idempotency key
```

保证：

```text
RINGING → TIMEOUT
```

最多产生一次有效状态迁移。

---

# 十七、必须解决 Timeout Worker 宕机

例如：

```text
Call expireAt = 12:01:00
Worker 在 12:00:59 崩溃
```

系统不能永远保持：

```text
RINGING
```

必须支持：

```text
Worker restart
Redis timeout index recovery
partition rebalance
startup reconciliation
```

并记录：

```text
timeout lag
```

例如：

```text
expireAt = 12:01:00
实际处理 = 12:01:02

timeoutLag = 2s
```

---

# 十八、Kafka 必须考虑消息丢失

禁止：

```text
业务状态改变
 ↓
Kafka send
 ↓
send失败
 ↓
业务认为成功
```

必须考虑：

```text
Transactional Outbox
```

或者等价可靠事件机制。

业务状态和 Outbox 必须保证一致。

---

# 十九、Kafka 必须解决重复消息

必须假设：

```text
CALL_ACCEPTED
CALL_ACCEPTED
CALL_ACCEPTED
```

可能重复到达。

Consumer 必须幂等：

```text
eventId
aggregateId
version
```

结合状态机 CAS。

---

# 二十、Kafka 必须解决乱序

例如：

```text
CALL_ACCEPTED
CALL_TIMEOUT
```

因为不同分区、重试、网络等原因可能乱序。

必须设计：

```text
aggregateId = callId
```

保证同一个 Call 的事件进入同一 partition，或者提供版本校验：

```text
eventVersion
stateVersion
```

旧事件直接丢弃。

---

# 二十一、Kafka Consumer 必须解决 Lag

监控：

```text
consumer lag
partition lag
processing latency
retry count
DLQ count
```

支持：

```text
Consumer Group horizontal scaling
```

---

# 二十二、Kafka Retry 不能无限重试

必须：

```text
Retry Topic
指数退避
最大重试次数
DLQ
人工/自动恢复
```

禁止：

```text
失败
 ↓
立即重试
 ↓
立即重试
 ↓
立即重试
```

形成 Kafka 自身雪崩。

---

# 二十三、MySQL 必须防止慢查询

所有高频查询必须检查：

```text
EXPLAIN
索引
rows examined
锁
执行时间
```

至少重点检查：

```text
call_id
caller_id
callee_id
status
expire_at
created_at
```

禁止：

```text
SELECT *
```

扫描百万/千万 CallSession。

---

# 二十四、MySQL 必须防止锁竞争

避免：

```text
大事务
大批量 UPDATE
长事务
无条件 UPDATE
```

必须：

```text
小批次
明确索引
CAS
短事务
```

---

# 二十五、MySQL 慢的时候不能拖死 RTC

如果 MySQL：

```text
DOWN
SLOW
CONNECTION EXHAUSTED
LOCKED
```

不能导致：

```text
CONNECTING → FAILED
CONNECTED → ENDED
```

除非媒体层自己失败。

实时呼叫状态必须尽量依赖：

```text
Redis / State Machine
```

历史记录：

```text
Kafka / Outbox
```

异步落 MySQL。

---

# 二十六、必须考虑 MySQL 连接池雪崩

必须检查：

```text
HikariCP
connection timeout
idle timeout
max pool
pending requests
```

增加：

```text
Bulkhead
Circuit Breaker
Timeout
```

避免数据库慢导致：

```text
线程耗尽
连接耗尽
Gateway 堆积
整个服务雪崩
```

---

# 二十七、必须处理用户在线状态

呼叫系统不能只判断：

```text
online = true
```

必须定义 Presence：

```text
OFFLINE
CONNECTING
ONLINE
AWAY
BUSY
```

并考虑：

```text
多端登录
手机 + Web
多个 WebSocket
网络断线
重连
心跳丢失
旧连接未清理
```

例如：

```text
B 手机在线
B Web 在线
```

呼叫必须能够决定：

```text
哪个设备响铃
是否多端同时响铃
哪个设备接听后其他设备停止响铃
```

---

# 二十八、必须处理“呼叫过程中上线”

这是本任务重点。

场景：

```text
A → B
B offline

20秒后
B login

B 应该收到有效呼叫
```

登录后：

```text
authenticate
 ↓
register presence
 ↓
reconcile active calls
 ↓
获取所有：
CALLING/RINGING
且
expireAt > serverNow
 ↓
推送 CALL_RINGING
```

必须支持幂等。

如果 B 登录后重复执行：

```text
reconciliation
reconciliation
reconciliation
```

不能出现：

```text
10个响铃 UI
```

---

# 二十九、必须处理“多端登录”

例如：

```text
B 手机
B Web
B 平板
```

A 呼叫 B。

可能：

```text
三个设备同时响
```

B 手机接听。

必须：

```text
手机 → ACCEPT
 ↓
CallSession → CONNECTING
 ↓
Web/平板 → CANCEL_RINGING
```

而不是继续响铃。

---

# 三十、必须处理网络延迟造成的 UI 与服务端状态不一致

例如：

```text
A：
正在呼叫...

B：
已经接听

A：
由于 WebSocket 延迟
仍显示“正在呼叫”
```

或者：

```text
A：
显示已接通

服务端：
已经 TIMEOUT
```

必须设计：

```text
server authoritative state
stateVersion
eventId
serverTimestamp
client reconciliation
```

客户端收到旧事件：

```text
eventVersion < localVersion
```

直接忽略。

必要时主动：

```text
GET /calls/{callId}
```

重新同步。

---

# 三十一、必须处理 WebSocket 消息丢失

WebSocket 是：

```text
notification channel
```

不是唯一事实。

如果：

```text
CALL_ACCEPTED
```

消息丢失：

客户端必须通过：

```text
reconnect
reconciliation
```

重新获取当前状态。

---

# 三十二、必须处理网络重连

例如：

```text
A 呼叫 B
 ↓
A 网络断开
 ↓
A 重新连接
```

必须恢复：

```text
当前 CallSession
当前状态
expireAt
stateVersion
RTC session
```

不能因为 WebSocket 断开就：

```text
自动认为 CALL_ENDED
```

---

# 三十三、必须处理重复点击

用户可能：

```text
连续点击拨号10次
```

必须：

```text
requestId
idempotencyKey
```

最终只有：

```text
一个 CallSession
```

---

# 三十四、必须处理取消与接听竞争

场景：

```text
A 点击取消

同时

B 点击接听
```

最终必须只有一个合法结果。

例如：

```text
RINGING
 ├── CANCELLED
 └── ACCEPTED
```

谁先成功 CAS 谁获胜。

另一个得到：

```text
CALL_ALREADY_ENDED
```

客户端必须重新 reconciliation。

---

# 三十五、必须处理拒绝与超时竞争

同理：

```text
B 点击拒绝
```

和：

```text
Timeout Worker
```

同时发生。

只能产生一个最终状态。

---

# 三十六、必须处理“用户不存在”

必须区分：

```text
USER_NOT_FOUND
```

和：

```text
USER_OFFLINE
```

不存在：

```text
不能创建永久等待的 CallSession
```

可以立即返回：

```text
USER_NOT_FOUND
```

---

# 三十七、必须处理账号权限变化

呼叫过程中：

```text
B 被拉黑
B 注销
A/B 权限发生变化
用户 Token 失效
```

必须通过：

```text
ACL
short-lived RTC token
server authorization
```

重新验证。

不能只依赖第一次检查。

---

# 三十八、RTC 媒体边界不能被破坏

Spring Boot：

```text
鉴权
状态
信令
事件
指标摘要
```

Kafka：

```text
事件
```

WebSocket：

```text
控制面通知
```

禁止：

```text
RTP
音视频帧
完整媒体数据
```

经过：

```text
Spring
Kafka
WebSocket
```

媒体必须：

```text
P2P
LiveKit
TURN
SRS
CDN
```

---

# 三十九、媒体层继续遵循 RTC-SCALE-001

1 对 1：

```text
默认 LiveKit SFU
RTC_P2P_ENABLED=false
```

只有：

```text
恰好2人
双方同意
CONNECTING
P2P允许
```

才尝试 P2P。

失败：

```text
P2P → SFU
```

必须记录：

```text
direct
srflx
relay
sfu
```

和：

```text
fallback reason
RTT
packet loss
CPU
SFU egress
```

---

# 四十、3～8人禁止 Mesh

继续：

```text
LiveKit SFU
```

实现：

```text
simulcast
dynacast
selective subscription
audio priority
active speaker
screen share priority
```

真实调用：

```text
Track subscription
quality layer
track lifecycle
```

不能只：

```text
display:none
```

---

# 四十一、直播必须 Stage + Audience

主播/嘉宾：

```text
LiveKit Stage
```

普通观众：

```text
SRS
WHEP
LL-HLS
HLS
HTTP-FLV
CDN
```

普通观众禁止全部进入：

```text
LiveKit 双向房间
```

---

# 四十二、必须增加完整容量保护

Admission Control 至少考虑：

```text
room count
publisher count
subscriber count
outbound Mbps
RTP pps
CPU
memory
RTT
packet loss
reconnect rate
NACK
PLI
FIR
first frame
freeze
TURN relay
```

达到阈值：

```text
拒绝新房间
或
降低视频质量
或
限制新 Publisher
```

不能等服务器 100% 才处理。

---

# 四十三、必须增加 Call Control Plane 指标

至少：

```text
call_create_qps
call_accept_qps
call_reject_qps
call_cancel_qps
call_timeout_qps

ringing_count
connecting_count
connected_count

call_create_latency
ring_latency
accept_latency
connect_latency
timeout_lag

state_transition_conflict
idempotency_hit
reconciliation_count
reconciliation_failure

websocket_delivery_latency
websocket_delivery_failure

redis_hit_ratio
redis_latency
redis_error
redis_failover

kafka_produce_latency
kafka_consumer_lag
kafka_retry
kafka_dlq

mysql_query_latency
mysql_slow_query
mysql_connection_usage
mysql_lock_wait
mysql_replication_lag
```

---

# 四十四、必须做完整故障测试

必须设计测试矩阵：

## 正常

```text
A → B
B 接听
CONNECTED
挂断
```

## B 未登录

```text
A → B
B offline
60s
TIMEOUT
```

## B 中途上线

```text
A → B
B offline

20s
B login

发现 CALL_RINGING
接听
CONNECTED
```

## 边界接听

```text
59s 接听
59.9s 接听
60s 接听
60.1s 接听
61s 接听
```

## 网络延迟

```text
CALL_ACCEPTED 延迟
CALL_TIMEOUT 延迟
CALL_RINGING 延迟
```

## 消息乱序

```text
ACCEPTED
TIMEOUT
RINGING
```

乱序到达。

## 重复消息

```text
ACCEPTED × 3
TIMEOUT × 3
ENDED × 3
```

## Redis 故障

```text
Redis restart
Redis failover
Redis latency
Redis unavailable
```

## Kafka 故障

```text
Kafka unavailable
Kafka lag
Consumer crash
Duplicate event
Out-of-order event
Retry
DLQ
```

## MySQL 故障

```text
slow query
connection exhaustion
lock contention
primary unavailable
replica lag
```

## WebSocket 故障

```text
disconnect
reconnect
message loss
duplicate delivery
```

## 多端

```text
phone
web
tablet
```

## RTC

```text
P2P success
P2P timeout
TURN relay
SFU fallback
LiveKit reconnect
```

---

# 四十五、必须做并发压测

至少准备：

```text
1000 calls
10000 calls
100000 calls
1000000 logical CallSession
```

如果真实环境允许，再扩大。

测试：

```text
Call create QPS
Accept QPS
Reject QPS
Timeout QPS
Cancel QPS
Reconnect QPS
```

不能伪造数据。

没有真实环境：

```text
明确记录未执行
```

---

# 四十六、必须特别验证“百万/千万未接通 CallSession”

目标不是：

```text
1000万个 WebRTC
```

而是：

```text
1000万个 RINGING / PENDING CallSession
```

验证：

```text
Redis memory
timeout scheduler throughput
timeout lag
Kafka throughput
MySQL write throughput
reconciliation throughput
```

并记录真实结果。

---

# 四十七、最终架构目标

最终形成：

```text
                         Client
                           │
                     CDN / LB / WAF
                           │
                    Call Gateway
                           │
                    Call Service
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
        ▼                  ▼                  ▼
     Redis             State Machine        Outbox
        │                  │                  │
        │                  │                  ▼
        │                  │                Kafka
        │                  │                  │
        │                  │          ┌───────┼───────┐
        │                  │          ▼       ▼       ▼
        │                  │       WebSocket Audit Persistence
        │                  │                         │
        │                  │                         ▼
        │                  │                       MySQL
        │
        ▼
 Timeout Scheduler
        │
        ▼
 Call Reconciliation
        │
        ▼
 RTC Control
        │
        ├──────────────┐
        ▼              ▼
      P2P            LiveKit
                       │
                       ▼
                     TURN
```

直播：

```text
LiveKit Stage
      │
      ▼
     SRS
      │
      ▼
     CDN
      │
      ▼
   Audience
```

---

# 四十八、提交边界

不要把所有事情塞进一个 commit。

建议至少：

```text
feat(RTC-CALL): add high concurrency call state machine

feat(RTC-CALL): add resilient timeout scheduler

feat(RTC-CALL): add presence and login reconciliation

feat(RTC-CALL): add idempotent call event processing

feat(RTC-CALL): add websocket state reconciliation

feat(RTC-011): add call control plane capacity observation

feat(RTC-012): add real selective track subscription

feat(RTC-013): add LiveKit distributed routing contracts

feat(RTC-014): add stage audience topology

feat(RTC-015): add controlled one-to-one p2p fallback

test(RTC-CALL): add concurrency and failure acceptance

test(RTC-SCALE): add browser and load acceptance

docs(RTC): update call lifecycle and resilience runbook
```

---

# 四十九、每个 Agent 必须明确

每个智能体开始工作前输出：

```text
目标：
输入：
输出：
负责目录：
禁止修改目录：
依赖任务：
验证命令：
Definition of Done：
提交哈希：
测试结果：
未执行测试：
遗留风险：
```

禁止一个 Agent 同时修改：

```text
RTC-006
RTC-012
RTC-013
RTC-014
RTC-015
```

多个任务的核心代码。

---

# 五十、Definition of Done

本任务只有同时满足以下条件才可以标记完成：

```text
[ ] Call State Machine 已定义并实现
[ ] 所有状态迁移幂等
[ ] ACCEPT/TIMEOUT/CANCEL/REJECT 竞争安全
[ ] requestId/idempotency 已实现
[ ] server authoritative time 已实现
[ ] stateVersion/eventVersion 已实现
[ ] B 未登录时呼叫仍有明确生命周期
[ ] B 中途登录可以 reconciliation
[ ] 多端登录状态正确
[ ] WebSocket 丢消息后可以恢复
[ ] Redis 穿透已防护
[ ] Redis 击穿已防护
[ ] Redis 雪崩已防护
[ ] Redis 故障有降级/恢复
[ ] Timeout Scheduler 不扫描 MySQL 全表
[ ] Timeout Scheduler 可水平扩展
[ ] Timeout Worker 宕机可恢复
[ ] Kafka Outbox/可靠事件机制已实现
[ ] Kafka 重复事件可安全消费
[ ] Kafka 乱序事件可安全处理
[ ] Kafka Retry/DLQ 已实现
[ ] Kafka Consumer Lag 可观测
[ ] MySQL 慢查询已检查
[ ] MySQL 索引已验证
[ ] MySQL 锁竞争已检查
[ ] MySQL 连接池保护已实现
[ ] MySQL 故障不会直接打断已建立媒体通话
[ ] Call Control Plane 有完整指标
[ ] RTC QoE 指标已接入
[ ] 1v1 P2P/SFU fallback 正确
[ ] 3~8人 SFU selective subscription 正确
[ ] Stage + Audience 正确
[ ] 没有恢复旧 WebSocket Media Bus
[ ] 没有恢复旧 Call.vue Mesh
[ ] 没有使用 Spring/Kafka 转发 RTP
[ ] 没有虚假宣称支持万人
[ ] 真实浏览器测试通过
[ ] Docker 测试通过
[ ] LiveKit 测试通过
[ ] SRS 测试通过
[ ] 压测真实结果已记录
[ ] 未执行测试明确记录原因
[ ] 回滚方案已记录
[ ] PROJECT_STATE.yaml 已写入真实提交哈希
[ ] WORK_LOG.md 已更新
[ ] TASK_INDEX.md 已更新
```

---

# 五十一、最终必须执行

```bash
mvn -f server/pom.xml -DskipTests compile
mvn -f server/pom.xml test

pnpm exec vue-tsc --noEmit --pretty false
pnpm exec eslint src/modules/rtc
pnpm run build-only

docker compose -f docker-compose.streaming.yml \
  --env-file deploy/streaming/.env.example \
  config --quiet

git diff --check
```

如果存在浏览器、Docker、LiveKit、SRS 环境，继续：

```text
双浏览器 1v1
B 未登录 → 中途登录
60秒边界接听
网络延迟
WebSocket 丢失
WebSocket 重连
重复 ACCEPT
ACCEPT/TIMEOUT race
多端登录
100个1v1
100个8人群聊
LiveKit 多节点
Stage + Audience
SRS 重启
Redis 故障
Kafka Consumer 故障
MySQL 慢查询/故障
```

没有环境：

```text
必须明确写：
NOT EXECUTED
原因：
需要什么环境：
如何执行：
```

禁止伪造测试结果。

---

# 五十二、最终报告

最终报告必须包含：

1. 完成的任务
2. 每个任务实际提交哈希
3. 修改文件
4. 模块边界
5. Call State Machine
6. Timeout Scheduler 设计
7. Presence/Reconciliation 设计
8. Redis 防穿透/击穿/雪崩方案
9. Kafka 可靠性方案
10. MySQL 慢查询与锁保护方案
11. WebSocket 重连/状态恢复方案
12. P2P/SFU 方案
13. LiveKit 多节点方案
14. Stage + Audience 方案
15. 测试命令及真实结果
16. 浏览器验收结果
17. Redis/Kafka/MySQL 故障测试结果
18. LiveKit/SRS/Docker 验收结果
19. Call QPS / Accept QPS / Timeout QPS
20. Timeout Lag
21. Kafka Consumer Lag
22. Redis latency/hit ratio
23. MySQL p95/p99/slow query
24. SFU egress
25. TURN relay ratio
26. 客户端 CPU
27. 客户端网络带宽
28. P2P success rate
29. SFU fallback rate
30. 未完成项
31. 未执行测试
32. 阻塞原因
33. 遗留风险
34. 回滚开关
35. 下一阶段依赖

最重要：

**不要把“架构设计完成”写成“生产级高并发已验证”。**

只有真实压测、真实浏览器、真实 Redis/Kafka/MySQL/LiveKit/SRS 环境得到证据后，才能声称对应能力已经验证。

---

# 五十三、开发原则

始终遵守：

```text
Control Plane ≠ Media Plane

Redis ≠ Durable Database

Kafka ≠ Source of Truth

WebSocket ≠ Reliable State

MySQL ≠ Real-time Scheduler

Client Time ≠ Server Time

Notification ≠ State

Timeout ≠ Media Failure

P2P ≠ Universal Solution

LiveKit ≠ Massive Audience CDN

Architecture Document ≠ Production Capacity Evidence
```

最终目标不是单纯“让电话能打通”。

最终目标是：

> **即使大量用户同时发起、接听、拒绝、取消、超时、离线、上线、重连、重复点击，并且 Redis/Kafka/MySQL/WebSocket/LiveKit 某一部分出现延迟、重复、乱序或短暂故障，整个 Call Lifecycle 仍然能够保持状态正确、幂等、最终一致，并且不会因为控制面故障拖垮正在运行的媒体面。**


还有要一定程度上的充分利用客户端也就是用户本身设备分摊压力，不然全部走服务器压力还是很大的

对，这个必须加进去，而且要把“客户端分摊压力”定义成架构原则，而不是简单写一句“支持 P2P”。

因为真正能给服务器减压的地方远不止 1 对 1 P2P。你的整个 Douyin RTC/直播体系应该形成：

                 ┌──────────────────────┐
                 │       控制面          │
                 │ Spring / Redis/Kafka  │
                 │ 鉴权/状态/调度/指标    │
                 └──────────┬───────────┘
                            │
                     只传控制信息
                            │
        ┌───────────────────┼──────────────────┐
        │                   │                  │
        ▼                   ▼                  ▼
   Client A             LiveKit SFU          SRS/CDN
        │                   │                  │
        │                   │                  │
        └────── Client B ───┘                  │
                 ▲                             │
                 │                             │
          客户端承担部分媒体流                  │
                                               │
                                         大规模观众

但不能为了“省服务器”把所有东西都 P2P。客户端减载必须根据拓扑、人数、网络质量、NAT、权限和设备能力动态选择。

我建议你把下面这一段追加到刚才给 Codex 的提示词末尾，甚至把它提升成整个任务的核心原则。

五十四、必须把“Client Offload”作为一级架构目标

本项目不是单纯增加服务器数量来解决 RTC 扩容。

必须在保证：

安全
稳定
权限
媒体质量
可观测性
故障恢复

的前提下，充分利用用户终端设备的 CPU、GPU、网络上行和本地媒体能力分摊服务器压力。

目标：

能由客户端完成的媒体工作，不要无意义地绕服务器。

但是：

客户端只能承担媒体面工作，不能绕过服务端控制面、ACL、Token、状态机和审计。

五十五、明确区分“服务器压力”的几个来源

必须分别优化：

1. RTP 转发压力
2. 上行带宽
3. 下行带宽
4. SFU egress
5. SFU ingress
6. CPU
7. GPU
8. TURN relay
9. 编解码
10. Simulcast 转码/层选择
11. WebSocket 连接
12. Call State
13. Redis
14. Kafka
15. MySQL

不要把所有“服务器压力”都归因于 CPU。

五十六、客户端 Offload 分层

必须设计：

L0：完全服务器媒体
L1：SFU + 客户端选择性订阅
L2：P2P 一对一
L3：受控客户端媒体交换
L4：Stage + CDN Audience

而不是只有：

P2P / SFU
五十七、1 对 1：优先考虑客户端直连，但必须受控

默认：

RTC_P2P_ENABLED=false

打开后：

恰好2人
+
双方明确同意
+
CONNECTING阶段
+
设备能力允许
+
网络质量允许
+
ACL允许

才允许：

Client A
    ↕
Client B

直接传输媒体。

服务器只负责：

Signaling
Authentication
Authorization
ICE configuration
TURN credentials
State
Metrics
Fallback

不转发 RTP。

五十八、不要让 P2P 变成“服务器不知道发生了什么”

即使：

A ↔ B

媒体直接传输。

服务器仍然必须知道：

callId
participantId
topology = p2p
connectionState
ICE type
direct/srflx/relay
RTT
packet loss
jitter
bitrate
codec
CPU
battery/thermal capability（如客户端可安全提供）

并且：

P2P → SFU

回退必须由控制面协调。

五十九、不要让客户端 Relay 成为默认方案

禁止：

A
 ↓
B
 ↓
C

让 B 作为服务器一样转发媒体。

原因：

B 上行带宽爆炸
B CPU/GPU 爆炸
B 手机发热
B 退出导致拓扑崩溃
隐私/安全复杂
网络质量不可控

所以：

客户端 Relay

默认禁止。

除非未来专门设计经过容量、权限、计费和安全验证的协议。

六十、3～8 人：客户端重点承担“订阅减载”

例如：

8人群聊

不要：

每个人
都订阅
7个1080p视频

应该根据：

active speaker
当前主画面
屏幕共享
窗口可见性
用户是否最小化
后台 tab
设备能力
网络能力

决定：

谁订阅
订阅什么质量
是否暂停视频
是否只保留音频

例如：

8人
│
├── Active Speaker
│      1080p
│
├── Screen Share
│      720p/1080p
│
├── 其他人
│      180p/360p
│
└── 隐藏 Tile
       audio only / unsubscribe

这样真正减少：

客户端：
CPU
GPU
解码
内存
网络下载

同时也减少：

SFU：
egress
packet forwarding
layer distribution
六十一、必须真正停止不需要的媒体订阅

禁止只做：

videoElement.style.display = 'none'

因为：

DOM隐藏
≠
停止网络
≠
停止 RTP
≠
停止解码

必须调用 LiveKit 对应的：

Track subscribe
Track unsubscribe
Track quality
Track enable/disable
Track attach/detach

具体 API 以当前 LiveKit SDK 版本为准。

必须有测试证明：

隐藏 Tile
↓
实际订阅下降
↓
网络带宽下降
↓
客户端 CPU/解码压力下降
六十二、后台标签页必须真正降级

例如：

用户切到其他浏览器 Tab

不能继续：

8路1080p视频
全部解码

应该：

Page Visibility API
       ↓
后台
       ↓
降低视频订阅
       ↓
保持音频

恢复：

前台
 ↓
恢复必要视频层

但必须注意：

不要因为浏览器后台策略
导致 CallSession 被误认为离线
六十三、最小化窗口也要触发媒体策略

例如：

视频通话
 ↓
用户最小化 CallPanel

可以：

主视频 → 360p
其他视频 → unsubscribe
音频 → 保持

恢复：

主视频 → 高质量
其他视频 → 按需恢复
六十四、屏幕共享优先

如果：

Camera
+
Screen Share

同时存在。

订阅策略：

Screen Share > Active Speaker Camera > Other Camera

例如：

Screen Share
1080p


Speaker
720p


Others
180/360p

而不是所有轨道统一质量。

六十五、客户端本地媒体处理必须优先

如果浏览器/客户端能够完成：

摄像头采集
麦克风采集
AEC
AGC
Noise Suppression
回声消除
基础美颜
分辨率缩放
帧率调整
本地编码能力

不要把这些无意义地送到服务器处理。

服务器不要承担：

每一个用户的实时美颜
每一个用户的降噪
每一个用户的简单缩放

除非业务确实需要服务器统一处理。

六十六、客户端设备能力必须进入能力协商

Call 建立前客户端上报有限的：

device capability

例如：

camera capability
codec capability
simulcast capability
hardware encoder capability
hardware decoder capability
max resolution
max fps
network type

然后服务器/LiveKit 决定：

high
medium
low
audio-only

禁止所有客户端统一：

1080p 30fps
六十七、弱设备必须主动减载

如果客户端：

CPU > threshold
GPU pressure
memory pressure
thermal throttling
network loss
RTT high

自动：

1080p
 ↓
720p
 ↓
360p
 ↓
audio-only

这实际上是在用：

客户端自己的资源和网络状况，主动避免整个系统进入拥塞。

六十八、弱网必须优先保证音频

必须：

Audio > Screen Share > Active Speaker > Other Video

例如网络恶化：

视频：
1080 → 720 → 360 → off


音频：
继续保持

而不是：

视频卡死
音频也卡死
六十九、必须考虑客户端上传压力

例如多人群聊：

8人

每个人如果：

1080p
30fps

SFU ingress 很大。

可以利用：

Simulcast
编码层
动态帧率
动态分辨率

客户端根据网络情况：

High
Medium
Low

上传多个质量层。

SFU 根据订阅者选择：

只转发需要的 layer

避免服务器做实时转码。

七十、尽量避免 SFU Transcoding

这是一个非常重要的扩容原则。

理想：

Client
  │
  ├── High
  ├── Medium
  └── Low
       │
       ▼
      SFU
       │
       ├── User A → High
       ├── User B → Medium
       └── User C → Low

不要：

Client
 ↓
1080p
 ↓
SFU
 ↓
CPU/GPU Transcoding
 ↓
360p
 ↓
User

否则人数一多：

服务器 GPU/CPU

很快成为瓶颈。

七十一、直播更应该利用 CDN，而不是客户端 P2P

万人直播不要尝试：

主播
 ↓
P2P
 ↓
所有观众

也不要：

LiveKit
 ↓
10000个双向订阅者

正确：

主播
 ↓
LiveKit Stage
 ↓
SRS
 ↓
CDN
 ↓
10000+
Audience

这里真正的“客户端分摊”主要体现为：

客户端负责解码
客户端选择播放质量
客户端缓冲
客户端自适应播放

而：

CDN

承担大规模分发。

七十二、不要为了客户端 Offload 破坏安全

客户端 Offload 永远不能绕过：

ACL
短期 Token
TURN credentials
房间权限
participant identity
server state machine

禁止客户端自己决定：

“我要直接连接任何人”

必须：

Server authorization
 ↓
短期 token
 ↓
ICE credentials
 ↓
允许连接
七十三、必须增加 Client Offload 指标

不能只说：

“客户端承担了一部分压力。”

必须测量。

增加：

p2p_attempt_count
p2p_success_count
p2p_fallback_count


p2p_direct_count
p2p_srflx_count
p2p_relay_count
sfu_count


client_upload_bps
client_download_bps


client_cpu
client_memory
client_gpu
client_decode_time
client_encode_time


video_subscribed_tracks
video_unsubscribed_tracks


video_high_layer
video_medium_layer
video_low_layer


audio_only_count


background_tab_count
minimized_count


client_freeze
client_jitter
client_packet_loss
client_rtt
七十四、必须计算“服务器节省了多少”

最终报告必须回答：

如果10000个用户全部走SFU：


SFU ingress = ?
SFU egress = ?
CPU = ?


如果允许符合条件的1v1 P2P：


SFU egress 减少多少？
TURN egress 增加多少？
客户端 upload/download 增加多少？


如果3~8人开启 selective subscription：


平均 subscriber track 减少多少？
客户端 CPU 减少多少？
客户端带宽减少多少？
SFU egress 减少多少？

没有实际数据：

UNKNOWN

禁止拍脑袋写：

节省70%
节省80%
七十五、必须做“客户端承担能力”的压测

至少测试：

低端设备
中端设备
高端设备

场景：

1v1
2人
4人
8人
屏幕共享
后台 Tab
最小化
弱网
重新连接
摄像头关闭/打开

记录：

CPU
Memory
GPU
FPS
decode time
encode time
network
freeze
RTT
packet loss
七十六、最终的资源分配原则

整个系统必须遵循：

                   谁最适合做什么？
                   
Client
 ├── 采集
 ├── 编码
 ├── 解码
 ├── 本地媒体处理
 ├── 自适应质量
 └── 一定条件下 P2P


LiveKit
 ├── SFU 转发
 ├── 房间媒体路由
 ├── selective forwarding
 └── 少量互动参与者


TURN
 └── NAT traversal / relay fallback


SRS
 └── 直播媒体分发入口


CDN
 └── 大规模 Audience Distribution


Spring
 ├── Authentication
 ├── Authorization
 ├── Call State
 ├── Signaling
 ├── Reconciliation
 └── Control Plane


Redis
 ├── Realtime State
 ├── Presence
 ├── Timeout Index
 └── Routing


Kafka
 ├── Events
 ├── Async Processing
 ├── Audit
 └── Metrics Pipeline


MySQL
 ├── Durable State
 ├── Call History
 ├── Audit
 └── Reporting

核心原则：

不要让服务器做客户端能做的事情。


不要让 SFU 做 CDN 能做的事情。


不要让 MySQL 做 Redis 能做的事情。


不要让 Kafka 做实时状态机。


不要让 Spring 转发媒体。


不要让 CDN 处理互动控制。


不要让 P2P 绕过控制面。
最终验收标准新增

除了原来的 RTC-SCALE-001 验收标准，必须增加：

[ ] Client Offload architecture 已定义
[ ] 1v1 P2P 能真实减少 SFU egress
[ ] 3~8人 selective subscription 能真实减少客户端下行
[ ] 隐藏 Tile 能真实停止/降低媒体订阅
[ ] 后台 Tab 能真实降低视频负载
[ ] 最小化能真实降低视频负载
[ ] Screen Share 有更高订阅优先级
[ ] Active Speaker 有更高订阅质量
[ ] 弱网自动降低视频质量
[ ] 音频在弱网下优先保留
[ ] 客户端设备能力参与质量策略
[ ] Simulcast 优先于服务器转码
[ ] 没有为了扩容把媒体重新塞进 Spring/Kafka/WebSocket
[ ] 客户端 Offload 不绕过 ACL
[ ] 客户端 Offload 不绕过短期 Token
[ ] 客户端 Offload 不绕过 TURN credential
[ ] 客户端 Offload 不绕过 Call State Machine
[ ] 记录 P2P/SFU/TURN 拓扑比例
[ ] 记录客户端 CPU/内存/网络
[ ] 记录 SFU egress 变化
[ ] 记录 selective subscription 带来的实际资源变化
[ ] 没有虚假声称“客户端已经承担 X%”

最终目标：

不是单纯把服务器做得越来越大，而是让 Client、SFU、TURN、SRS、CDN、Redis、Kafka、MySQL 各自承担自己最擅长的工作，并且通过动态策略把媒体负载尽可能分散到正确的位置。

尤其注意：

客户端 Offload ≠ 全部 P2P。

对于你这个 Douyin 架构，最合理的路线实际上是：

1v1
    ↓
条件允许 → P2P
条件不允许 → LiveKit SFU


3~8人
    ↓
LiveKit SFU
    ↓
Simulcast + Dynacast + Selective Subscription
    ↓
客户端主动减载


多人互动直播
    ↓
LiveKit Stage
    ↓
少量互动用户


万人/大规模观众
    ↓
SRS
    ↓
CDN
    ↓
Audience

这样才是真正的**“客户端减载 + SFU 扩容 + CDN 分发 + 控制面高并发”**，而不是单纯堆服务器。

另外，千万条“未接通电话”和千万路正在传输的音视频是完全不同的容量问题：前者主要是 Call State/Redis/Timeout/Kafka/MySQL 控制面问题，后者才是 LiveKit/TURN/SRS/CDN/客户端媒体面问题。两者必须分开做容量模型，否则很容易把架构设计错。