# RTC-013 集群运维 Runbook

- 适用:RTC-013 双节点 LiveKit + 共享 Redis + TURN 区域池
- 原则:已有房间不静默迁移;Redis/registry 不可用时停止需要集群路由的新房(fail-closed);
  媒体永不经过 Spring/Kafka/聊天 WS(契约 §5)
- 证据纪律:未执行的操作不得写成已验证;本文件操作步骤均需在真实环境演练后才可列为验收证据

## 0. 前置

```powershell
copy deploy\rtc\cluster\.env.example deploy\rtc\cluster\.env   # 随机密钥,不入库
$env:COMPOSE = "docker compose -f docker-compose.rtc-cluster.yml --env-file deploy\rtc\cluster\.env"
```

## 1. 节点 drain

LiveKit 节点 drain 应为“停止接受新房,已有房间自然结束”;不做透明热迁移。

1. 控制面容量登记把节点 `draining=true`(RTC-011 节点快照字段),prometheus 规则停止提示扩到该节点。
2. 负载入口从 upstream 摘除该节点(nginx-livekit.conf 注释对应 server 行),新 join 不再落到该节点。
3. 等该节点 rooms/participants 归零:`curl http://127.0.0.1:7889/metrics | grep -E "rooms|participants"`。
4. `docker compose ... stop livekit-node-<x>`;观察共享 Redis 中节点注册 key 消失。
5. 恢复:`docker compose ... up -d livekit-node-<x>`,入口恢复 upstream 行,控制面 `draining=false`。

已有房间若在 drain 期间自然结束,是符合契约的行为,不算故障。

## 2. Redis 故障 / 恢复

契约:Redis 失效时禁止创建需要集群路由的新房;已有单节点房间按 provider 能力继续并告警。

1. 确认告警:`RtcCapacityNoFreshSnapshot` / 节点 registry 心跳超时(见
   deploy/streaming/rules/rtc-scale-rules.yml)。
2. 停止新房:控制面 admission 对需要集群 routing 的 scope 返回 fail-closed 错误,不做模糊重试。
3. 诊断:`docker compose logs redis`、`redis-cli -a $REDIS_PASSWORD ping`、检查宿主机端口 6379。
4. 恢复:`docker compose ... up -d redis`;healthcheck 通过后节点自动重连共享路由,新房可用。
5. 验收脚本 `deploy/rtc/cluster/validate.ps1 -Runtime` 第 6 步固化该故障注入用例。
6. 禁止:把 Redis 路由临时切到 Spring/Kafka/聊天 WS;跨节点迁移已有房间“救活”。

## 3. 版本升级(LiveKit)

版本差异 split-brain 风险最高:一次只升一个节点。

1. 摘除节点 B(见 §1),确认 `douyin-livekit-node-b` 无存量房间。
2. 改 `docker-compose.rtc-cluster.yml` 中 node-b 的 image tag;`docker compose ... pull livekit-node-b`。
3. `docker compose ... up -d livekit-node-b`;通过 7899/metrics 与 Redis 注册确认新版本生效。
4. 观察 5 分钟无告警后再升级 node-a(同样先摘除)。
5. 回滚:把 image tag 指回旧版本并重复上述过程;旧版本与新版本同 Redis 时节点必须全部就绪后
   再放量,不允许长时间混跑未验收版本。
6. 升级期间不做既有房间热迁移,老房间在旧节点自然结束。

## 4. DNS/LB 回滚

1. DNS:把 livekit 解析切回已验证节点(或 LB 的 A 记录指向单节点);
   TTL 调小(60s)以加速收敛。
2. LB(nginx-livekit.conf):注释掉新节点 upstream 行,`nginx -s reload`。
3. 新节点按 §1 drain 后下线,已有房间自然结束。
4. TURN:若区域池某区域故障,先从 `turn-regions.yml` 标记不可用并重发凭据(短期凭据天然可废弃),
   再恢复节点;浏览器拿到的旧 TURN 凭据到期自动失效。

## 5. 记录要求

故障注入执行时记录:时间、操作、节点/Redis 状态、日志样例、受影响房间数量、回滚动作。
审计不得包含 JWT、TURN 密码、完整媒体 URL、SDP、ICE 私网地址(契约 §8)。