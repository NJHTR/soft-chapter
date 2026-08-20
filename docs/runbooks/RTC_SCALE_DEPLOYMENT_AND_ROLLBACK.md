# RTC 规模化部署、验收与回滚手册

- 状态：`draft / not executed`
- 适用任务：RTC-SCALE-001、RTC-011～RTC-015
- 基线：开发 compose 为单 LiveKit、单 coturn、单 SRS，不代表生产集群

## 1. 发布前总门禁

1. RTC-006 的 SRS callback、provider restart、Redis 多实例 presence 和浏览器恢复证据已完成。
2. RTC-007 的 QoE 聚合、ABR、弱网和恢复矩阵已完成。
3. 本次发布的任务文件已经处于 `review` 或更后状态，提交哈希、配置版本和回滚点明确。
4. 所有 secret 通过环境/secret manager 注入；渲染结果不进入 Git、日志或验收产物。
5. Spring、Kafka 和聊天 WS 的 payload 检查确认没有 RTP、媒体帧、base64 媒体或完整 stats。
6. 先在隔离环境执行浏览器/provider/故障验收，再进入小流量 canary；禁止直接把模板用于生产。

## 2. 开关与默认值

| 开关 | 默认 | 作用 | 回滚结果 |
|---|---|---|---|
| `RTC_CAPACITY_ADMISSION_ENABLED` | `false` | 新房容量门禁 | 关闭硬拒绝，保留指标和告警 |
| `RTC_SELECTIVE_SUBSCRIPTION_ENABLED` | `false` | 远端视频订阅/质量层策略 | 回到 LiveKit SFU 全订阅 |
| `RTC_ADAPTIVE_STREAM_ENABLED` | `false` | LiveKit adaptiveStream | 必须保持关闭到 attach/detach 验收 |
| `RTC_DISTRIBUTED_ENABLED` | `false` | 多节点 Redis routing profile | drain 新节点，入口回到已验证节点 |
| `RTC_STAGE_ENABLED` | `false` | Stage promotion/LiveKit egress | 停止上麦，audience 保持 SRS/CDN |
| `RTC_P2P_ENABLED` | `false` | 受控 1 对 1 P2P attempt | 新呼叫全部使用 LiveKit SFU |

开关不得绕过 ACL、token、TURN 临时凭据、room state 或审计。关闭开关只影响新 attempt；已有
房间按其 generation 收敛，禁止静默热切换。

## 3. RTC-011 容量观测与 admission

### 部署顺序

1. 先只读部署 exporter、Micrometer endpoint、Prometheus rules 和容量快照登记。
2. 验证低基数标签、快照时效、阈值和告警，再开启 `shadow` admission，只记录决策不拒绝。
3. 对 70% 规划阈值和 80% 硬门禁做故障注入；确认已有房间不被踢出。
4. 最后小流量开启硬门禁，先允许 `DEGRADE_VIDEO`，再启用 `REJECT_NEW_ROOM`。

### 回滚

- 关闭 admission 开关；保留 exporter 和告警以便复盘。
- 删除未消费的 reservation，不修改已创建 room 的 placement。
- 若容量 registry 不一致，停止新房并人工确认；不要通过放大上限掩盖坏数据。

### 证据

记录每次矩阵的工具版本、浏览器、codec、region、节点配置、原始 Prometheus 时间段和报告哈希。
未执行的 `100x2`、`100x8`、`1000x2` 和 stage/audience 行必须显式为 `not_run`。

## 4. RTC-012 选择性订阅

### 部署顺序

1. 保持 adaptiveStream 关闭，先验证 publication registry 和摄像头/屏幕共享生命周期。
2. shadow 计算策略，比较期望订阅与 provider 实际订阅，不调用 `setSubscribed`。
3. 依次 canary：2 人群聊、4 人群聊、8 人群聊；覆盖隐藏 tile、最小化、后台标签页。
4. 记录全订阅与选择性订阅的浏览器下行、解码 CPU、SFU egress、首帧和冻结率。

### 回滚

- 关闭选择性订阅，重新订阅所有授权音频/视频并恢复 provider 默认质量。
- 清空当前房间的策略覆盖，但保留 publication registry，避免回滚时黑屏。
- 黑屏、重复音频、摄像头重开失败或 active speaker 抖动任一超过基线即停止扩大 canary。

## 5. RTC-013 多节点与 TURN 区域池

### 部署顺序

1. 独立部署共享 Redis、两个 LiveKit canary 节点和负载入口；每个节点使用唯一 node id、region、
   advertised IP 和不冲突的 UDP 范围。
2. 只给新测试房间使用集群入口，验证同房粘着、不同房分布和跨房隔离。
3. 依次注入 Redis 延迟/断开、LiveKit drain/restart、TURN UDP/TCP/TLS 区域故障。
4. 所有已有房间自然结束后，才扩大入口权重。

### 回滚

- 标记新节点 draining，停止新房；不把活跃房间透明迁移到另一节点。
- LB/DNS 权重回到已验证节点；等待旧房结束，再停止故障节点。
- TURN pool 按健康/优先级撤掉故障 region，短期凭据自然过期；不下发长期静态凭据。
- Redis 故障时停止需要集群 routing 的新房，已有房间按 LiveKit 官方行为继续或重连失败可见。

## 6. RTC-014 Stage + Audience

### 部署顺序

1. 保持 audience 现有 SRS WHEP/HLS/HTTP-FLV 路径，先上线只读 stage 状态和审计。
2. 测试主播 + 1 嘉宾，验证 LiveKit Egress/Ingress 到 SRS，确认媒体不经过 Spring/Kafka。
3. 验证 8 人硬上限、重复/乱序请求、断线恢复、权限撤销和 egress 失败回滚。
4. audience 负载逐级放大；每个观众只创建 SRS/CDN 会话，不创建 LiveKit participant。

### 回滚

- 关闭新 promotion，撤销 stage token，已在台成员进入 DEMOTING。
- audience 始终保持或回到现有单主播 SRS 路径；不得将 audience 回滚进 LiveKit 房间。
- Egress generation 失败时只退休匹配 generation，旧 callback 不得终止新 egress。

## 7. RTC-015 P2P

### 部署顺序

1. 开关保持关闭，仅验证 eligibility、consent、TTL、候选分类和审计。
2. 内部测试账号开启 1 对 1 direct；群聊、Stage、录制、审核强制拒绝。
3. 依次执行 host、srflx、relay、IPv6、UDP 禁用、TURN 不可用、超时和权限撤销。
4. 只有直连成功率、SFU fallback 成功率、TURN relay 比例、CPU/电量和通话失败率满足门禁后，
   才扩大灰度。

### 回滚

- 立即关闭新 P2P attempt。CONNECTING 中的 attempt 记录 `feature_disabled` 并连接原 LiveKit SFU。
- 已 P2P CONNECTED 的房间不静默换拓扑；质量/权限触发时进入可见 RECONNECTING，记录审计后
  才使用 SFU fallback。

## 8. 最小验收矩阵

| 场景 | 必须记录 | 当前基线 |
|---|---|---|
| 双浏览器 1 对 1 SFU/P2P fallback | candidate、RTT、loss、CPU、fallback reason | `not_run` |
| 2/4/8 人选择性订阅 | subscribed publications、层、egress、CPU、首帧、冻结 | `not_run` |
| 摄像头关闭/重开、屏幕共享 | trackSid/source/generation、黑屏/重复音频 | `not_run` |
| 最小化、后台、恢复 | video subscribed、audio continuity、恢复首帧 | `not_run` |
| 100x2、100x8、1000x2 | rooms、pub/sub、Mbps、pps、CPU、内存、SLO | `not_run` |
| 多节点/Redis/TURN failover | placement、drain、fault timeline、recovery | `not_run` |
| 1 stage + 10k audience | LiveKit participants、SRS/CDN sessions、首帧、hit ratio | `not_run` |
| SRS restart/异常断开 | provider generation、DEGRADED/grace/recovery | RTC-006 `not_run` |

`not_run` 不能改成 `passed`，除非相应命令、环境、原始数据位置、时间和提交哈希都已记录。

## 9. 本地静态检查

```text
mvn -f server/pom.xml -DskipTests compile
mvn -f server/pom.xml test
pnpm exec vue-tsc --noEmit --pretty false
pnpm exec eslint src/modules/rtc
pnpm run build-only
docker compose -f docker-compose.streaming.yml --env-file deploy/streaming/.env.example config --quiet
git diff --check
```

这些命令通过只说明代码和单节点模板可构建，不能替代本手册第 8 节的媒体/容量验收。
