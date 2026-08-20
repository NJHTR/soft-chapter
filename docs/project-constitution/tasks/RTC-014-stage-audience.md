# RTC-014：Stage + Audience 大规模直播分层

## 状态与边界

- 状态：`planned`
- 负责人：未认领
- 分支/开始时间：未分配
- 波次：E
- 依赖：RTC-006、RTC-013
- 负责目录：`server` 直播/stage 控制面、stage 数据迁移、`deploy/streaming/`、直播前端和 CDN 运维文档
- 允许的共享改动：LiveKit token policy、provider control port、SRS callback generation，由主智能体审查
- 禁止修改：让普通观众加入双向 LiveKit 房间；用单一万人 SFU 房间代替分层；让 Spring/Kafka
  处理合成媒体或音视频帧；破坏 RTC-006 provider generation/reconciliation

## 目标与非目标

少量主播/嘉宾进入 LiveKit Stage，普通观众继续使用 SRS WHEP、LL-HLS/HLS、HTTP-FLV 或 CDN。
观众申请且获批时才进入受控互动房间或 breakout room；上麦、下麦、权限撤销、断线恢复和观众
降级状态机均幂等可审计。

非目标：不把观众 presence 当 LiveKit participant；不在本任务自制媒体 mixer；不以 10k HTTP
请求代替真实媒体/CDN 验收。

## 状态机与权限契约

- 统一契约：`docs/contracts/rtc-scale-control-contract.md` 第 6 节。
- 成员状态：`AUDIENCE -> REQUESTED -> PROMOTING -> ON_STAGE -> DEMOTING -> AUDIENCE`，
  任一受控状态可因权限撤销进入 `REVOKED`。
- 每个命令使用 `event_id + stage_generation` 幂等/CAS；重复返回第一次结果，旧 generation 拒绝。
- `stage.request` 仅本人；`approve/demote/revoke` 仅主播/主持人；stage token 只授予当前有效成员。
- 首期发布者硬上限 8 人。扩到 16 人必须另有容量、首帧、CPU/egress 和 admission 证据。
- Egress/Ingress 到 SRS 由 provider control port 发命令，Spring 只记录状态、generation 和审计。
- audience delivery 降级只在 WHEP/LL-HLS/HLS/FLV/CDN 间切换，不获得 LiveKit 发布权限。

## 实现输出

1. Stage session/member/request/audit 数据模型、唯一键、版本/CAS 和迁移。
2. Stage controller/service/state machine/token policy 与主持人/成员 ACL。
3. LiveKit Egress/Ingress 到 SRS 的 provider port、generation reconciliation 和失败回滚。
4. SRS origin/edge/CDN audience policy、首帧/延迟/CDN 命中/降级指标。
5. 直播 WS presence 广播节流/合并和多实例控制事件；媒体不进入业务 WS。
6. 重复、乱序、过期、断线、权限撤销、8 人上限、旧 callback 和回滚测试。

## 验证命令

```text
mvn -f server/pom.xml -DskipTests compile
mvn -f server/pom.xml -Dtest=com.douyin.live.stage.** test
mvn -f server/pom.xml test
pnpm exec vue-tsc --noEmit --pretty false
pnpm exec eslint <stage-frontend-files>
docker compose -f <stage-compose> --env-file <private-env> config --quiet
git diff --check
```

真实验收必须查询 LiveKit participant 数、SRS/CDN session、egress generation 和观众路径，不能只
看控制 API 200。

## Definition of Done

- [ ] Stage 发布者首期不超过 8 人，扩展到 16 人前有压测和 admission。
- [ ] 普通观众不能获得 Stage LiveKit token，观看链路有独立首帧、延迟和 CDN 命中率指标。
- [ ] 申请、批准、上麦、下麦、撤销、降级、断线恢复的重复/乱序/过期测试通过。
- [ ] 主持人权限、人数、地域和房间 ACL 由控制面校验并进入脱敏审计。
- [ ] Egress/SRS 失败回滚到现有 audience 路径，旧 generation 不终止新会话。
- [ ] 1 stage + 10k audience 的合成流/真实流验收完成，LiveKit participant 始终仅为 Stage 成员。
- [ ] 媒体字节不经过 Spring/Kafka/聊天 WS，独立审查和回滚演练通过。

## 回滚与风险

- `RTC_STAGE_ENABLED=false` 停止新 promotion，撤销 stage token；audience 保持现有 SRS/CDN。
- 已在台成员进入 DEMOTING 并按 generation 收敛，不把观众迁入 LiveKit。
- 风险：RTC-006 callback/reconciliation 未闭环、LiveKit Egress 未部署、直播 WS 节点内 map/
  presence 广播无法直接支撑多实例或 10k、CDN 当前不存在。

## 交付记录

- 提交哈希：未实现
- 测试结果：未执行
- 遗留风险：RTC-006、RTC-013 未完成；当前仅存在单主播 SRS audience 路径
