# RTC-007：媒体质量、QoE、自适应和弱网恢复

## 状态与边界

- 状态：`planned`
- 依赖：RTC-004、RTC-005、RTC-006
- 负责目录：`src/modules/rtc/quality/`、`src/modules/live/quality/`、`server/.../metrics/`、监控配置
- 禁止修改：用超分/滤镜掩盖源质量、将指标写成假常量、绕过 provider 拆自制拥塞控制

## 目标

实现能力协商、simulcast/ABR、QoE 事件、音频优先、ICE restart、降层/降分辨率/降帧率和恢复滞后策略。

## DoD

- [ ] 指标 schema 和 2-5 秒采样窗口生效，关联 room/call/trace。
- [ ] RTT/jitter/loss/available bitrate 驱动质量策略，重复切换有滞后。
- [ ] 正常网、移动网、企业网 TURN、极端弱网矩阵通过。
- [ ] 接通、首帧、冻结率、RTT、TURN 成功率和恢复率达到文档 SLO。
- [ ] QoE dashboard 能区分 provider、浏览器、设备、地域和版本。

