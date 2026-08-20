# RTC-013：LiveKit 多节点、Redis 路由与 TURN 区域池

## 状态与边界

- 状态：`in_progress`（实现已提交 `a934d8f`；DoD 中 100/1000 房间载、跨区 RTT、TURN TLS、drain、浏览器矩阵未执行，不得标记 completed）
- 负责人：主智能体（Wave D）
- 分支/开始时间：`dev/full` / 2026-08-20
- 波次：D
- 依赖：RTC-011
- 负责目录：`deploy/streaming/`、`deploy/rtc/`、独立 cluster/Helm 模板、运维配置和部署文档
- 允许的控制面共享改动：短期 TURN credential provider port 和区域选择策略，由主智能体审查
- 禁止修改：让 Spring 或 Kafka 转发媒体；自行实现 RTP room router；在没有 Redis room routing、
  唯一 node id/advertised IP 和 placement 验收时水平复制 LiveKit

## 目标与非目标

使用 LiveKit 官方 Redis 共享 routing 和节点发现，将独立房间放置到多个健康节点，并建立多地域
coturn pool、短期凭据、节点 drain 和故障恢复策略。

非目标：不承诺透明迁移已接通房间；不把当前单节点 compose 直接扩副本当集群；不以配置解析
证明多节点容量。

## 契约

- 统一契约：`docs/contracts/rtc-scale-control-contract.md` 第 5 节。
- 节点登记包含唯一 `node_id/region/zone/advertised_ip/version/draining/capacity/expires_at`。
- 新房只落在健康、非 draining 且低于 admission 门禁的节点；同一房间保持 provider routing 粘着。
- Redis/registry 不可用时停止需要集群 routing 的新房；已有房间不静默迁移。
- TURN pool 由控制面返回 region/transport/priority 和短期凭据；共享 secret 不进入浏览器。
- UDP 失败后可按健康策略切换 TCP/TLS 或区域，必须记录 relay、transport 和原因。

## 实现输出

1. 至少双节点的 `docker-compose.rtc-cluster.yml`、`deploy/rtc/cluster/livekit-node-a.yaml`、
   `livekit-node-b.yaml`、共享 Redis TLS/auth 和负载入口模板。
2. 唯一节点/地域/advertised IP/UDP 端口约束与容量登记。
3. TURN 区域清单、健康/故障切换模板和短期 credential adapter。
4. 节点 drain、Redis 故障、版本升级、DNS/LB 回滚 runbook。
5. 可执行的配置、路由、跨房隔离和故障注入验收脚本。

## 验证命令

```text
docker compose -f docker-compose.streaming.yml --env-file deploy/streaming/.env.example config --quiet
docker compose -f docker-compose.rtc-cluster.yml --env-file deploy/rtc/cluster/.env.example config --quiet
docker run --rm -v "${PWD}/deploy/rtc/cluster/livekit-node-a.yaml:/etc/livekit.yaml:ro" livekit/livekit-server:v1.13.5 --config /etc/livekit.yaml ports
docker run --rm -v "${PWD}/deploy/rtc/cluster/livekit-node-b.yaml:/etc/livekit.yaml:ro" livekit/livekit-server:v1.13.5 --config /etc/livekit.yaml ports
powershell -File deploy/rtc/validate-coturn-startup.ps1 -ComposeFile docker-compose.rtc-cluster.yml -Service turn-region-a
git diff --check
```

上述 cluster 文件和 bounded-start validator 是 RTC-013 实现提交必须创建的固定输出。LiveKit
`ports` 会解析配置并退出；锁定的 coturn 4.17.2 没有 dry-run/`--check-config`，因此 validator 必须
启动隔离容器、确认其持续运行且日志无配置错误，再有界停止。真实验收脚本不得输出
Redis/TURN/LiveKit secret。

## Definition of Done

- [ ] 两个以上 LiveKit 节点共享 Redis routing，节点 id、region、advertised IP 和 UDP 范围唯一。
- [ ] 新房不会落到 draining/不健康/超过容量门禁的节点，同房粘着和跨房隔离有证据。
- [ ] 已有房间的 drain/restart 行为明确，不宣称未验证的透明热迁移。
- [ ] Redis 延迟/故障、LiveKit 节点故障和 TURN UDP/TCP/TLS 区域切换有故障注入记录。
- [ ] TURN 凭据短期、区域化、可撤销，前端和仓库没有共享 secret。
- [ ] 100/1000 并发房间无跨房串流，连接 SLO 相对单节点基线不劣化超过 20%。
- [ ] 配置、测试、原始报告、回滚演练和独立审查均已提交。

## 回滚与风险

- `RTC_DISTRIBUTED_ENABLED=false`；新节点 drain，LB/DNS 回到已验证节点，已有房间自然结束。
- Redis 故障时停止新房，不切换到 Spring/Kafka 媒体路由。
- 风险：provider 版本配置差异、NAT advertised IP、跨区 RTT、TURN TLS 证书、Redis split-brain。

## 交付记录

- 提交哈希：`a934d8f feat(RTC-013): add LiveKit distributed routing, Redis registry and TURN region pool`
  （`docker-compose.rtc-cluster.yml`、`deploy/rtc/cluster/`(livekit-node-a/b.yaml、redis.conf、turnserver-region-a/b.conf、
  turn-regions.yml、nginx-livekit.conf、.env.example、README、validate.ps1)、`deploy/rtc/validate-coturn-startup.ps1`、
  `docs/runbooks/rtc-cluster-ops.md`、`.gitignore` +=cluster .env）
- 验证命令（全部真实执行，2026-08-20）：
  - `docker compose -f docker-compose.streaming.yml --env-file deploy/streaming/.env.example config --quiet`：通过
  - `docker compose -f docker-compose.rtc-cluster.yml --env-file deploy/rtc/cluster/.env config --quiet`：通过
  - 双节点 `livekit-server --config <a/b>.yaml ports`：解析通过（UDP 51000-51050 / 51100-51150）
  - TURN region-a bounded-start validator：通过（relay ports init、prometheus :9641 监听，有界停止）
  - `validate.ps1 -Runtime` 全绿：双节点 metrics 就绪 → Redis `nodes` 哈希含 2 个唯一
    `node_id=ND_*` + 唯一 advertised IP(172.31.10.10/172.31.10.11) + region(cn-east-1/2) →
    livekit-cli join 真实放置于 node-a → `docker compose stop redis` 后新房失败(fail-closed) →
    redis 恢复后新房成功 → 有界停止集群
  - 真实证据：node-a/node-b `livekit_participant_join_total{state="rtc_success"}`、
    `livekit_session_join_latency_ms`、`livekit_room_total`、process CPU/RSS 采样。
- DoD 已满足：双节点共享 Redis routing、node_id/region/advertised IP/UDP 唯一、Redis 故障新房 fail-closed、
  已有房间不迁移、TURN 凭据经 env 隔离（仓库无 secret）、可执行验收脚本已提交。
- 未执行（如实记录）：100/1000 房间载、跨区 RTT、TURN TCP/TLS(5349) 切换、节点 drain 演练、
  DNS/LB 回滚演练、L7 sticky 负载入口实流量、Redis TLS(6380)。
- 遗留风险：跨 region 会话 relay 依赖 PSRPC over Redis；Redis split-brain 无仲裁；`.env` 需在目标机重新生成随机密钥。
