# Douyin 全项目开发计划

**版本：** 1.0 | **日期：** 2026-08-14  
**当前阶段：** Phase 2（实时媒体质量）

---

## 阶段概览

| 阶段 | 名称 | 目标 | 预计周期 |
|---|---|---|---|
| Phase 0 | 基础功能 ✅ | 认证/视频/IM/搜索/推荐基线 | 已完成 |
| Phase 1 | RTC 控制面 ✅ | LiveKit/SRS/coturn 环境 + 通话状态机 | 已完成（RTC-001~003） |
| Phase 2 | 实时媒体质量 🔄 | 1 对 1/群聊/直播真实 WebRTC 路径 | 进行中 |
| Phase 3 | 管理后台完善 | 审核工作流/实时看板/权限分级 | Phase 2 后 |
| Phase 4 | 电商支付收口 | 支付安全/退款/对账 | 可与 Phase 3 并行 |
| Phase 5 | 搜索与推荐升级 | Elasticsearch/A-B 框架/冷启动 | Phase 3 后 |
| Phase 6 | 发布门禁与运营 | 压测/监控/灰度/回滚 | 最终 |

---

## Phase 2：实时媒体质量（当前）

### 优先级 P0 — 先修 blocker，再谈质量

在 B-001~B-006 修复完成之前，**禁止**把"提高分辨率/码率"定义为质量改善。

| Blocker | 描述 | 归属任务 |
|---|---|---|
| B-001 | StreamController SDP echo，非真实协商 | RTC-006 |
| B-002 | C++ WebRTCStreamer 全为 stub | RTC-009 |
| B-003 | `/ws/live` 文本 handler 被当二进制媒体总线 | RTC-006 |
| B-004 | WebCodecs payload copy/getVideoTracks 硬错误 | RTC-006 |
| B-005 | Call.vue 仅公共 STUN + mesh，远端视频绑定不完整 | RTC-004 |
| B-006 | JNI `isNativeAvailable() \|\| true` 可能崩溃 | RTC-002 |

### 任务序列

```
RTC-004（进行中）1 对 1 LiveKit 适配器
  ↓ 验收：双浏览器接通 + TURN relay + webhook 回调
RTC-005  群聊音视频 SFU 迁移
  ↓ 验收：8 人基线 + simulcast + active speaker
RTC-006  直播 WHIP/WHEP 迁移（同时修 B-001~B-004）
  ↓ 验收：主播 WHIP ingest + 观众 WHEP/HLS + 延迟 < 3s
RTC-007  QoE/ABR/弱网恢复
  ↓ 验收：RTT/jitter/丢包/冻结率指标 + 弱网降级
RTC-008  录制和转码
  ↓ 验收：Egress/DVR + FFmpeg 异步 + 对象存储
RTC-009  Legacy 退役（修 B-002 + B-003）
  ↓ 验收：WebSocket media bridge 停用 + C++ WebRTC stub 隔离
RTC-010  安全/负载/发布门禁
  ↓ 验收：NAT 矩阵 + 弱网测试 + 负载 + 回滚演练
```

---

## Phase 3：管理后台完善

### 任务列表

| ID | 任务 | 依赖 |
|---|---|---|
| ADM-001 | 审核工作流状态机（待审/通过/驳回/申诉） | 无 |
| ADM-002 | 实时看板 WebSocket 稳定性和鉴权 | B-007 修复后 |
| ADM-003 | 权限分级（超管/运营/客服角色） | ADM-001 |
| ADM-004 | 直播违规实时告警 | RTC-006 |
| ADM-005 | 数据导出和报表 | ADM-001 |

**工程门禁：** 审核操作必须有操作人/时间/原因审计日志；角色权限服务端校验，前端不判断权限。

---

## Phase 4：电商支付收口

### 任务列表

| ID | 任务 | 依赖 |
|---|---|---|
| SHOP-001 | 支付回调签名验证完整测试 | 无 |
| SHOP-002 | 退款状态机（申请/审核/打款/关闭） | SHOP-001 |
| SHOP-003 | 钱包对账报表（每日余额核对） | SHOP-002 |
| SHOP-004 | 幂等键覆盖率审计（所有支付入口） | SHOP-001 |
| SHOP-005 | 支付安全审计（OWASP 支付专项） | SHOP-004 |

**工程门禁：** 金额必须后端计算；支付状态机测试覆盖超时/重复/并发三个场景。

---

## Phase 5：搜索与推荐升级

### 任务列表

| ID | 任务 | 依赖 |
|---|---|---|
| SRC-001 | Elasticsearch 接入（替换 MySQL LIKE） | 无 |
| SRC-002 | 热词实时更新（Kafka → ES） | SRC-001 |
| REC-001 | A/B 实验框架（流量分桶 + 指标对比） | 无 |
| REC-002 | 冷启动策略（新用户/新视频） | REC-001 |
| REC-003 | 推荐解释性日志（why this video） | REC-001 |

---

## Phase 6：发布门禁与运营

### 任务列表

| ID | 任务 | 依赖 |
|---|---|---|
| OPS-001 | 全链路压测（视频/直播/通话并发） | RTC-010 |
| OPS-002 | 监控告警完整性（所有 P0 接口有 SLA 告警） | OPS-001 |
| OPS-003 | 灰度发布框架（feature flag + 流量切分） | OPS-001 |
| OPS-004 | 回滚演练手册和验证 | OPS-003 |
| OPS-005 | 安全渗透测试（认证/支付/媒体接口） | OPS-002 |

---

## 全局任务依赖图（简化）

```
Phase 0 ✅
  → Phase 1（RTC-001~003）✅
    → Phase 2（RTC-004~010）🔄  ←── 当前
      → Phase 3（ADM）
      → Phase 4（SHOP）     ← 可与 Phase 3 并行
        → Phase 5（SRC/REC）
          → Phase 6（OPS）
```

---

## 执行规则

1. 只有当前阶段的 P0 任务全部完成，才能启动下一阶段。
2. 每个任务完成后立即更新 `PROJECT_STATE.yaml` 和 `WORK_LOG.md`。
3. blocker 未修复时，对应能力不能宣称完成或对用户开放。
4. 每个阶段结束前执行一次安全审查（见 `security-review` skill）。
