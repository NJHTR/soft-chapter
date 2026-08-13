# RTC-009：Legacy 媒体路径退役和 native 收口

## 状态与边界

- 状态：`planned`
- 依赖：RTC-007、RTC-008
- 负责目录：legacy feature flag、迁移 adapter、`streaming-engine/network/webrtc`、JNI 生命周期文档
- 禁止修改：在未达门槛前删除回滚点、把 C++ stub 宣称为实现、强制启用缺失 DLL

## 目标

按 2026-08-31、09-30、10-31、11-30 里程碑冻结、灰度、停用和删除 `/ws/live` 媒体、Call.vue mesh、SDP echo 和 C++ WebRTC stub；native engine 只能在独立 artifact 和测试通过后可选启用。

## DoD

- [ ] legacy 使用率、错误率、provider 对比和关闭开关可观测。
- [ ] native unavailable 安全回退，不触发 JNI 崩溃。
- [ ] 删除前保留 provider 回滚版本、数据迁移和 runbook。
- [ ] 退役后仓库搜索不再出现生产入口引用，实验代码明确隔离。

