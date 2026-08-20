# RTC-013：LiveKit 多节点、Redis 路由与 TURN 区域池

## 状态与边界

- 状态：`planned`
- 负责人：未认领
- 分支/开始时间：未分配
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

- 提交哈希：未实现
- 测试结果：当前只有单节点 compose config 历史证据；多节点/故障注入未执行
- 遗留风险：RTC-011 尚未实现；当前 LiveKit 无 Redis、coturn 单区域且 TLS 未验收
