# 架构文档导航

| 文档 | 内容 |
|---|---|
| [PROJECT_MODULE_MAP.md](./PROJECT_MODULE_MAP.md) | 全项目业务模块、平台模块、依赖和拆分方案 |
| [RTC_ARCHITECTURE.md](./RTC_ARCHITECTURE.md) | LiveKit、SRS、coturn 与 Spring 控制面的总体拓扑 |
| [CALL_DOMAIN_MODEL.md](./CALL_DOMAIN_MODEL.md) | 通话实体、状态机、事件和数据库演进 |
| [LIVE_STREAM_ARCHITECTURE.md](./LIVE_STREAM_ARCHITECTURE.md) | WHIP/WHEP、直播 fallback、presence 和迁移顺序 |
| [MEDIA_QUALITY_AND_QOE.md](./MEDIA_QUALITY_AND_QOE.md) | 编解码、档位、QoE、弱网和验收指标 |
| [DEPLOYMENT_TOPOLOGY.md](./DEPLOYMENT_TOPOLOGY.md) | 本地、测试和生产部署、密钥与故障策略 |

## 阅读顺序

先读项目宪法，再读模块地图和总体拓扑；实现 RTC 前阅读通话模型、质量文档和测试计划；实现直播前阅读直播架构和部署拓扑。架构文档描述目标边界，`docs/verification/` 和 `docs/runtime/` 描述已有验证记录，两者不能混为一谈。
