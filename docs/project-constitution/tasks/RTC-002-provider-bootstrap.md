# RTC-002：媒体 provider bootstrap 环境

## 状态与边界

- 状态：`review`
- 依赖：RTC-001
- 负责目录：`deploy/rtc/`、`deploy/streaming/`、`docker-compose.streaming.yml` 及本任务测试文档
- 禁止修改：`src/`、`server/` 业务控制面、`Call.vue`、WebCodecs 和 native engine

## 目标

建立可重复启动的 LiveKit、coturn、SRS provider 环境，补齐实际存在的配置文件、端口、健康检查和 provider CLI smoke test。该任务不依赖 Spring 业务 token API。

## 关键契约

- LiveKit：HTTP/WS、UDP 媒体端口、签名 webhook。
- coturn：3478 UDP/TCP、5349/443 TLS、REST 临时凭据。
- SRS：WHIP/WHEP、SRT/RTMP 兼容端口、HTTP callback 或 stream key。
- 每个 profile 只启用一套直播媒体服务，不能端口冲突。

## 验证与 DoD

- [x] compose 引用的每个配置文件真实存在且通过配置检查（`docker compose config --quiet`）。
- [x] LiveKit、coturn、SRS 健康检查和端口检查通过（smoke 3/6-4/6 全绿，容器 `healthy`）。
- [x] provider CLI/最小浏览器测试能完成真实媒体路径，不使用 SDP echo（livekit-cli `--publish-demo` 实际发布 h264 分层媒体，日志确认 publication）。
- [~] 记录 TURN UDP/TCP/TLS relay 结果和失败原因：UDP/TCP 3478 与 relay 初始化通过；TLS 5349 因证书未签发推迟到 RTC-003（TURN ICING），不宣称 TLS 已验证。
- [x] 密钥来自环境注入（`.env.example` 模板 + `.env` 本地注入 + compose `${VAR:?}` 强制），日志无 token/credential。
- [x] 失败可停止、清理和重新启动（`docker compose down` + `up -d --force-recreate` 验证过），回滚只切换 profile/镜像版本。

## 已知边界（诚实声明）

1. smoke 验证的是 provider CLI/端口/健康检查层级，不含浏览器端真实通话；浏览器 WebRTC E2E 属 RTC-004/005/006。
2. SRS 镜像（`registry.cn-hangzhou.aliyuncs.com/ossrs/srs:6`）无 curl/wget，镜像内健康检查改用 bash `/dev/tcp` 探测端口；HTTP API 检查由 smoke 从宿主机执行。
3. LiveKit `/rtc/validate` 需鉴权（401 即存活），健康检查用 7889/metrics；指标端口独立于信令 7880。
4. B-006（JNI native availability 恒 true）已在用户工作区修复（`nativeLoaded` 静态标记），验证随 RTC-003 后端联调确认。
5. `.env` 不落库；本地使用 `SRS_RTC_CANDIDATE=127.0.0.1`，LAN/公网联调需按 README 更换候选地址。

