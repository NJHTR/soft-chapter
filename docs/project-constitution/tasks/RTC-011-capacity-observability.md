# RTC-011：SFU/TURN/客户端容量观测、Admission 与压测

## 状态与边界

- 状态：`planned`
- 负责人：未认领
- 分支/开始时间：未分配
- 波次：B
- 依赖：RTC-007
- 负责目录：`deploy/streaming/`、监控配置、`src/modules/rtc/quality/`、
  `server/.../rtc/observability/`、`server/.../rtc/capacity/`、压测工具和 `docs/runbooks/`
- 允许的共享改动：`server/pom.xml`、`application.example.yml`、`CallService` 的新房 admission hook，
  由主智能体逐文件审查
- 禁止修改：把原始 RTP/高频 stats 写入 Kafka 或业务聊天 WS；用单机开发数据宣称生产容量；
  用 room/user/call/trace 作为 Prometheus label

## 目标与非目标

建立 LiveKit、coturn、客户端和控制面的统一容量基线，按 CPU、内存、Mbps、pps、连接数、轨道数
和 relay bytes 做 room admission。只对新房、首次媒体 admission 或明确 stage promotion 做决策，
不得因瞬时阈值静默迁移或结束已有房间。

非目标：不由 Spring 采集或转发媒体包；不在本任务实现 LiveKit room routing；不以 mock 数值替代
真实压测。

## 契约

- 容量快照、QoE 摘要、Admission 请求/决策：
  `docs/contracts/rtc-scale-control-contract.md` 第 2～3 节。
- 70% 持续 5 分钟是扩容/规划告警，正常目标保留 30% headroom；80% 是紧急硬门禁。
- 快照 stale、registry/Redis 不可用且没有新鲜缓存时，新房 fail-closed；已有房间继续并告警。
- 相同 `request_id/call_id` reservation 幂等，按 `PENDING -> CONSUMED -> ABSORBED` 或
  `RELEASED/EXPIRED` 以 CAS 收敛；按 reserved/observed/remaining capacity vector 逐维吸收，只有
  同 binding generation、node/epoch 的 post-bind membership 可更新 observed。node/epoch 改变必须
  rebind、重置 observed/remaining 和 seq floor；不得靠 aggregate 增量或 room 单一存在性猜测全量吸收。
- 决策仅为 `ALLOW|DEGRADE_VIDEO|REJECT_NEW_ROOM`，reason 使用固定低基数枚举。

## 实现输出

1. LiveKit、coturn、客户端 QoE 和控制面容量指标与低基数 recording rules。
2. 新房和首次媒体 token 的 admission service、逐维容量 reservation、provider membership
   contribution/absorption 明细、node/epoch rebind 与降级策略。
3. 认证、限流、ACL 校验的 2～5 秒客户端 QoE 摘要 API 和聚合存储端口。
4. Prometheus rules、固定版本监控配置和可重复的 load/acceptance harness。
5. 容量报告模板只引用真实运行产物，未运行矩阵保持 `not_run`。

## 验证命令

```text
mvn -f server/pom.xml -DskipTests compile
mvn -f server/pom.xml -Dtest=com.douyin.rtc.capacity.**,com.douyin.rtc.observability.** test
mvn -f server/pom.xml test
pnpm exec vue-tsc --noEmit --pretty false
pnpm exec eslint src/modules/rtc/quality
docker compose -f docker-compose.streaming.yml --env-file deploy/streaming/.env.example config --quiet
promtool check config deploy/streaming/prometheus.yml
promtool check rules deploy/streaming/rules/*.yml
git diff --check
```

负载命令由 harness 输出环境、配置、种子和报告路径；没有真实集群时只执行配置/契约检查。

## Definition of Done

- [ ] 客户端每 2～5 秒聚合 QoE，服务端能关联 `trace_id/room_id/call_id` 并校验成员 ACL。
- [ ] Prometheus 只使用低基数标签，room/user/call 明细写入受控聚合存储。
- [ ] LiveKit、TURN、客户端和控制面覆盖房间、pub/sub、egress Mbps、RTP pps、CPU、内存、RTT、
  丢包、重连、NACK/PLI/FIR、首帧、卡顿和 relay 指标。
- [ ] stale/missing snapshot、Redis 故障、重复 reservation、consume/absorb/release/expire/reconcile、
  partial vector absorption、provider node/epoch rebind、ambiguous create outcome、PENDING 对应既有 room、
  不可归因维度、已有房间、音频降级和 70/80% 边界测试通过。
- [ ] 完成 100x2、100x8、1000x2 房间和 stage + audience 压测矩阵并附真实原始证据。
- [ ] 受部署清单约束的 Prometheus `instance` 能识别单个热节点；节点连续 5 分钟超过 70% 告警，
  任一可靠关键资源超过 80% 阻止新房或明确降级视频。
- [ ] 独立审查确认没有高基数指标、假容量、媒体越界或 ACL 绕过。

## 回滚与风险

- `RTC_CAPACITY_ADMISSION_ENABLED=false` 关闭硬决策，保留指标/告警；已有房间不变。
- registry 数据不可信时停止新房，不通过调大阈值恢复。
- 风险：provider 指标名称随版本变化、room reservation 漂移、客户端摘要伪造、Kafka/存储积压。

## 交付记录

- 提交哈希：未实现
- 测试结果：未执行
- 遗留风险：RTC-007 未完成，真实 LiveKit/coturn/浏览器负载环境未提供
