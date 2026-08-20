# RTC-013 LiveKit 多节点集群与 TURN 区域池

- 集群:两节点 LiveKit 共享 Redis room routing/节点发现(官方能力),Spring 不转发 RTP
- TURN:双区域 coturn(UDP/TCP),REST short-term credentials;TLS 待证书未验收
- 验证:配置解析、bounded-start coturn validator、Redis 路由放置与故障注入均在本仓库可执行

## 快速开始

```powershell
copy deploy\rtc\cluster\.env.example deploy\rtc\cluster\.env   # 填写随机密钥
docker compose -f docker-compose.rtc-cluster.yml --env-file deploy\rtc\cluster\.env up -d
powershell -File deploy\rtc\cluster\validate.ps1                # 静态验收
powershell -File deploy\rtc\cluster\validate.ps1 -Runtime       # + 集群放置/故障注入
powershell -File deploy\rtc\validate-coturn-startup.ps1 -Service turn-region-a
```

## 拓扑

| 服务 | 容器 | 宿主端口 | 说明 |
|---|---|---|---|
| redis | douyin-rtc-redis | 16379 | 共享路由(AUTH;TLS 6380 待证书) |
| livekit-node-a | douyin-livekit-node-a | 17880/17881/17889, UDP 51000-51050 | node-a, region=cn-east-1 |
| livekit-node-b | douyin-livekit-node-b | 17882/17883/17899, UDP 51100-51150 | node-b, 与 A 端口范围唯一 |
| turn-region-a | douyin-turn-region-a | 13478, UDP 41000-41020 | realm rtc-a.local |
| turn-region-b | douyin-turn-region-b | 13479, UDP 41100-41120 | realm rtc-b.local |
| lk-cli | douyin-lk-cli | - | 验收工具(tooling profile) |

宿主端口统一错开单节点栈(7880/3478/50000-50050 等),两套可同时运行。
节点 id/region/advertised IP(静态 172.31.10.x)/UDP 端口范围均唯一;密钥只走环境注入。
客户端经负载入口(nginx 模板 `nginx-livekit.conf`)先命中任意节点,join 响应由 Redis routing
指示房间所在节点;入口只做连接级 sticky,不做业务 room 路由。

## 验收范围(真实证据)

静态:两份 compose `config --quiet`;双节点 yaml 经 `livekit-server ports` 解析;TURN
bounded-start 启动检查。
运行时(`-Runtime`):双节点 metrics 身份、Redis 注册 key、跨房间放置计数、Redis 停止后新房
fail-closed、Redis 恢复后新房成功。
未提供环境时如实标记 `not_run`:跨区 RTT、TURN TLS、UDP/TCP 真实穿透、100/1000 房间并发 SLO。