# RTC-010：安全、负载、故障演练和发布门禁

## 状态与边界

- 状态：`planned`
- 依赖：RTC-007、RTC-008、RTC-009
- 负责目录：`docs/testing/`、`docs/runbooks/`、压测/故障注入脚本、监控与安全配置
- 禁止修改：以单次手工成功替代矩阵测试、跳过审查或强制推送

## 目标

完成跨浏览器、NAT/TURN、弱网、长通话、群规模、直播观看规模、provider 故障、信令安全和回滚演练。

## DoD

- [ ] 1/4/8 人通话和 1/100/1000 观众直播负载有报告。
- [ ] RTT、丢包、jitter、后台/锁屏、设备拒绝、重连和 provider 节点故障覆盖。
- [ ] JWT、房间 ACL、webhook 签名、重放、限流、Origin、TURN 凭据和日志脱敏通过。
- [ ] `/ws/chat`、`/ws/live`、`/ws/dashboard/stream` 的生产 Origin allowlist 拒绝未知来源；禁止以 `*` 通过验收。
- [ ] 发布、灰度、暂停、回滚和人工升级 runbook 可执行。
- [ ] 主智能体检查所有提交、契约、集成和安全结果后，才能将状态置为 `completed`。
