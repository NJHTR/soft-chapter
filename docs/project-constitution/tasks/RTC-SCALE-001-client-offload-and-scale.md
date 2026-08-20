# RTC-SCALE-001：客户端减载、多人通话扩容与万人直播分层

## 状态与边界

- 状态：`in_progress`（仅 Wave A 契约与审计；实现子任务仍按依赖保持 `planned`）
- 负责人：`/root`
- 分支：`dev/full`
- 开始时间：`2026-08-20`
- 输入快照：`415bae2`，工作区含用户 `CallPanel.vue` 修改和未跟踪任务提示文档，均保留并避让
- 依赖完成门：RTC-006、RTC-007、RTC-011、RTC-012、RTC-013、RTC-014、RTC-015
- 负责目录：架构/契约/任务/工作日志；各子任务只拥有各自任务文件声明的目录
- 禁止修改：旧 Call.vue mesh、WebSocket 媒体总线、C++ WebRTC stub；不得把用户未提交修改纳入任务提交

## 目标

1. 1 对 1 默认 LiveKit SFU，P2P 默认关闭且只允许双方同意、CONNECTING 阶段、ICE 非 relay
   直连，失败走可审计 SFU fallback。
2. 3～8 人继续 LiveKit SFU，使用真实 publication subscription/quality API、simulcast、dynacast、
   音频优先和可靠轨道生命周期。
3. 多房间使用 LiveKit 原生 Redis 路由、多节点登记、room admission 和 TURN 区域池。
4. 大规模直播使用少量 LiveKit Stage + SRS/CDN Audience；普通观众不进入双向 LiveKit 房间。
5. Spring/Kafka/聊天 WS 只承载控制、鉴权、审计和低频摘要，绝不转发媒体字节。

## 非目标

- 不用单机 compose、配置模板、mock stats 或架构文档宣称支持万人。
- 不在 RTC-006/007 未满足依赖时把 RTC-011～015 迁移为 `completed`。
- 不用 mesh、客户端 relay 或已接通后的静默拓扑切换换取表面服务器减载。

## 依赖与波次

| 波次 | 任务 | 启动门 |
|---|---|---|
| A | 本任务契约、状态审计、回滚 | 可立即执行，只改文档/契约/任务记录 |
| 前置 | RTC-006、RTC-007 | 先关闭 provider recovery、浏览器、QoE/弱网门禁 |
| B | RTC-011 | RTC-007 已 `completed` |
| C | RTC-012 | RTC-005、RTC-007 已 `completed` |
| D | RTC-013 | RTC-011 已 `completed`，包含容量与 admission 实现证据 |
| E | RTC-014 | RTC-006、RTC-013 完成 |
| F | RTC-015 | RTC-004、RTC-007 完成；最后评估 P2P |

## 对外契约

- 统一契约：`docs/contracts/rtc-scale-control-contract.md`
- 架构：`docs/architecture/RTC_SCALE_AND_CLIENT_OFFLOAD.md`
- ADR：`docs/adr/ADR-005-CLIENT-OFFLOAD-AND-SCALE.md`
- 部署与回滚：`docs/runbooks/RTC_SCALE_DEPLOYMENT_AND_ROLLBACK.md`
- 任务文件：RTC-011～RTC-015

## Definition of Done

- [x] Wave A 控制面/媒体面/SFU/TURN/SRS/CDN 边界完成独立只读审查。
- [x] 记录 RTC-006 `in_progress`、RTC-007 `planned` 和用户未提交修改边界。
- [ ] RTC-006 provider callback/restart/Redis 多实例/浏览器门禁完成并提交真实证据。
- [ ] RTC-007 QoE 聚合、ABR、弱网和恢复矩阵完成。
- [ ] RTC-011～015 分任务实现、测试、审查并以独立提交交付。
- [ ] 双浏览器、2/4/8 人、TURN、多节点、Stage + Audience、SRS restart 和回滚路径有可执行证据。
- [ ] 记录 SFU egress、TURN relay、客户端 CPU/网络、P2P 成功/回退率和真实环境限制。
- [ ] 所有任务/状态/工作日志写入真实提交哈希，未执行项无遗漏。

## 验证命令

```text
mvn -f server/pom.xml -DskipTests compile
mvn -f server/pom.xml test
pnpm exec vue-tsc --noEmit --pretty false
pnpm exec eslint src/modules/rtc
pnpm run build-only
docker compose -f docker-compose.streaming.yml --env-file deploy/streaming/.env.example config --quiet
git diff --check
```

真实媒体和负载矩阵见部署回滚手册。没有相应环境时只记录 `not_run` 和原因。

## 当前证据与风险

- 2026-08-20 基线重新执行 Maven compile、163 项 Maven test、Vue typecheck、RTC ESLint、compose
  config 和 diff check 均通过；这些结果不等于媒体、集群或容量验收。
- 项目依赖未安装 Playwright，但桌面工作区 bundled Playwright + Chrome 已完成直连 SRS smoke；
  当前仍没有两个登录态应用客户端 harness，也未取得 TURN NAT、多节点、Stage + Audience 或负载数据。
- RTC-004/005 在索引中为 `completed`，但各自任务文件仍保留真实浏览器门禁，状态文档需后续统一。
- `CallPanel.vue` 的用户 UI 修改与 RTC-012 视图区域重叠；在用户改动独立提交前，RTC-012 不拥有
  该文件。

## 提交与审查记录

- Wave A 提交：`3cd38fb48c4b4393bee83d11de4e7d14a659d5b1`（统筹契约与回滚）、
  `9c47755d20eeaa8b2461cc62e412b83b6a22e5c1`（RTC-011）、
  `484b747fbb7892d6143170ae5e3fa6a2bfb091c6`（RTC-012）、
  `8a50dcf6570770b1e69554d5082e6d789b5b19d4`（RTC-013）、
  `d4a74ce3645e8711d6cf25b07ee22b8d0f54950a`（RTC-014）、
  `95fa8f5d28a87f2fa1a88cbbebbf50b84bdfcc16`（RTC-015）。
- 测试结果：Maven compile、163 项测试、Vue typecheck、RTC ESLint、compose config 和 diff check
  在 2026-08-20 通过；生产 build 和本轮最终 Git 审计以工作日志为准。
- 遗留风险：所有未勾选 DoD；不得因本文件存在而降低发布门禁。
