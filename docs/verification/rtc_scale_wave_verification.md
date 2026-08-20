# RTC-SCALE-001 实现波次 B~F 验证报告

日期：2026-08-20；分支：`dev/full`；状态 owner：/root。
本报告记录实现波次的真实提交、测试证据、未执行门禁与回滚开关；任何未执行项不得被合成数据替代。

## 1. 波次与提交

| 波次 | 任务 | 提交 | 内容 |
|---|---|---|---|
| B | RTC-011 容量观测/Admission | `826333a` | CapacityRegistry/AdmissionService/Redis 预留/QoE 摘要/micrometer |
| C | RTC-012 选择性订阅 | `43a87c5`+`dd7d4b6` | subscriptionPolicy、livekitAdapter 复合键 registry、mediaAttention |
| D | RTC-013 多节点/Redis 路由/TURN 区域池 | `a934d8f` | 双节点共享 Redis、join 放置、fail-closed、bounded-start coturn 校验 |
| E | RTC-014 Stage+Audience | `49b5692`(后端)+`b3566d1`(前端) | Stage 状态机/成员/审计/egress port + 前端镜像 |
| F | RTC-015 受控 1 对 1 P2P | `3d6d592` | P2P 拓扑/双 consent/HMAC 信令/relay 回退 + 前端镜像 |
| docs | 各波次状态回填 | `4f00b84`/`8764d4c`/`46d2b9d` | PROJECT_STATE/TASK_INDEX/WORK_LOG/任务文件 |

## 2. 已执行验证命令（真实结果）

| 命令 | 结果 |
|---|---|
| `mvn -f server/pom.xml test`（Wave E 尾） | 244/244 通过 |
| `mvn -f server/pom.xml test`（Wave F 尾） | 282/282 通过 |
| `mvn "-Dtest=com.douyin.rtc.stage.**" test` | 27/27（StageStateMachine 8 / StageService 10 / StageController 5 / Egress 4） |
| `mvn "-Dtest=com.douyin.rtc.p2p.**" test` | 38/38（状态机 7 / policy 4 / 候选分类 5 / service 17 / controller 5） |
| `pnpm exec vitest run src/modules/live/stage` | 8/8 |
| `pnpm exec vitest run src/modules/rtc/p2p` | 8/8 |
| `pnpm exec eslint src/modules/live/stage` 与 `src/modules/rtc/p2p`；`vue-tsc`（对应模块） | 通过 |
| `powershell -File deploy/rtc/cluster/validate.ps1 -Runtime`（RTC-013） | 全绿：双节点注册、join 放置、Redis 故障 fail-closed、恢复 |
| `docker compose -f docker-compose.rtc-cluster.yml config` / coturn bounded-start / livekit ports | 通过 |

## 3. 关键契约证据（单测覆盖）

- RTC-014：`event_id + stage_generation` 幂等/CAS、重放返回首次、旧 generation 拒绝、REQUEST 仅本人 /
  approve/demote/revoke 仅主持人、APPROVE 超 8 人 `STAGE_LIMIT_REACHED`、DEMOTING/REVOKING 未确认前
  不提前标已撤、审计 redact(SHA-256 前 16 hex)、egress 500→`StageProviderException`。
- RTC-015：feature flag 默认关闭 fail-closed、eligibility 全条件矩阵、双 consent TTL（重放不延长）、
  PROBING 撤销→FALLING_BACK(PERMISSION_REVOKED)、consent 过期→CONSENT_EXPIRED、selected pair 任一
  relay→RELAY_REQUIRED、信令 HMAC/size/seq/event_id/速率/候选数越界拒绝、邮箱 TTL 瞬态不落库、
  审计无 SDP 原文。

## 4. 未执行门禁（not_run，不得标记 completed）

- 真实浏览器 stage 上麦/下麦/撤销矩阵；LiveKit Egress 真服务出流到 SRS。
- 真实双浏览器 1 对 1 / NAT / IPv6 / 企业网 / TURN 不可用矩阵；真实 P2P 成功率/回退率/relay 比例；
  CPU/电量/SFU egress 实测。
- 1 stage + 10k audience 合成/真实流验收；CDN 命中率指标。
- 多实例 stage store / P2P store 与共享信令密钥（当前为单实例内存）。
- permission API 实际撤销 provider 调用（`revokePublishPermission` 当期返回 false 留 providerPending）。
- 100×2 / 100×8 / 1000×2 房间负载；TURN TLS(5349)/节点 drain/DNS-LB 回滚。

## 5. 回滚开关（契约第 8 节）

| 能力 | 默认 | 回滚 |
|---|---|---|
| 容量硬门禁 | 保留告警 | 仅音频；不可绕过 ACL |
| 选择性订阅 | 开 | `RTC_SELECTIVE_SUBSCRIPTION_ENABLED=false` 全订阅 |
| adaptiveStream | 始终关闭 | — |
| 多节点 | 模板 | 不部署 `docker-compose.rtc-cluster.yml`，退回已验证单节点 |
| Stage | `RTC_STAGE_ENABLED=false` | 禁新 promotion；撤销 stage token；audience 保持 SRS/CDN |
| P2P | `RTC_P2P_ENABLED=false` | 新呼叫全部使用 LiveKit SFU |

## 6. 结论

RTC-011~015 实现与单元/静态/运行时(仅 RTC-013)证据已提交；RTC-014/015 未满足真实浏览器与负载
DoD，状态保持 `in_progress`；生产开关必须保持 fail-closed 默认值，直至双浏览器/NAT/TURN/负载验收
补齐。