# 企业级数据看板系统与 API 设计文档

## 1. 架构设计概览

为了支撑高密度、高实时性的前端看板，后端架构需要进行读写分离与实时流改造。

* **业务数据库 (OLTP):** MySQL / PostgreSQL（负责记录核心业务流水、订单、用户状态）。
* **分析数据库 (OLAP):** ClickHouse 或 Elasticsearch（负责处理聚合查询、多维下钻、时序分析，从业务库通过 Canal/Flink 实时同步）。
* **缓存与消息层:** Redis（存储短时间的聚合缓存及 Pub/Sub 消息发布）。
* **实时推送网关:** WebSocket / SSE 独立服务（专门负责将高频变化的指标推送给前端）。

---

## 2. 核心 API 规范设计

### 2.1 动态时序指标查询 (统一聚合查询口)
*摒弃写死的 `getTodayData`，采用统一的查询引擎接口。*

* **Endpoint:** `POST /api/v1/metrics/query`
* **描述:** 查询指定时间范围、指定粒度、指定维度的聚合指标，支持自动计算同环比。
* **Request Payload:**

```json
{
  "metrics": ["page_views", "active_users", "order_volume"],
  "timeRange": {
    "start": "2026-06-17T00:00:00Z",
    "end": "2026-06-24T00:00:00Z"
  },
  "interval": "1d",            // 聚合粒度: 1h(小时), 1d(天), 1w(周)
  "filters": [                 // 筛选条件 (可选)
    { "field": "platform", "operator": "eq", "value": "ios" }
  ],
  "groupBy": ["user_segment"], // 分组维度 (对应多维交叉分析)
  "compareWith": "previous_period" // 同环比: previous_period(上周), previous_year(去年)
}
```

* **Response Payload:**

```json
{
  "success": true,
  "data": {
    "timeSeries": [
      {
        "timestamp": "2026-06-17",
        "page_views": 45000,
        "active_users": 12000,
        "order_volume": 320
      }
      // ... 更多时间点数据
    ],
    "summary": {
      "page_views": { "total": 315000, "trend": 5.2 }, // trend 为环比增长率百分比
      "active_users": { "total": 84000, "trend": -1.5 }
    }
  }
}
```

### 2.2 数据下钻查询 (Drill-down API)
*当管理人员点击看板上的某个聚合数字时，需要调用的明细接口。*

* **Endpoint:** `GET /api/v1/audit/queue/details`
* **描述:** 获取高风险/待审核等状态的具体业务实体列表。
* **Query Parameters:**
    * `status`: pending / rejected
    * `risk_level`: high / medium
    * `page`: 1
    * `size`: 50
* **Response:** 标准的分页列表数据，包含具体的用户ID、触发风控规则、操作时间等，供管理人员直接干预。

### 2.3 异步报表导出任务
*防止大报表导出导致服务器 OOM 或请求超时。*

* **步骤 1: 提交导出任务**
    * `POST /api/v1/reports/export-tasks`
    * 请求体与 `metrics/query` 类似，返回 `taskId` (如: `task_9527`)。
* **步骤 2: 轮询任务状态**
    * `GET /api/v1/reports/export-tasks/{taskId}`
    * 返回状态: `PENDING` -> `PROCESSING` -> `COMPLETED`。
    * 完成后返回下载链接: `downloadUrl: "https://cdn.xxx.com/reports/xxx.xlsx"`。

---

## 3. 实时推流协议设计 (WebSocket)

针对需要实现“呼吸感”跳动的高频指标（如：当前在线人数、活跃直播推流数）。

* **连接地址:** `wss://api.yourdomain.com/ws/v1/dashboard/stream`
* **鉴权方式:** URL Token 参数或协议头鉴权。
* **通信指令 (Client -> Server):**
  前端连接成功后，主动订阅需要实时更新的 Topic。
```json
{
  "action": "subscribe",
  "topics": ["live_online_users", "realtime_transactions"]
}
```
* **推送数据 (Server -> Client):**
  后端监听 Redis Pub/Sub 消息，按照设定的频率（如每 3 秒）向客户端推送 diff（差异）或绝对值。
```json
{
  "topic": "live_online_users",
  "timestamp": 1719232277,
  "data": {
    "current_val": 1425,
    "delta": "+5"
  }
}
```

---

## 4. 千人千面：工作台配置持久化

* **Endpoint:** `GET /api/v1/users/me/dashboard-layout` & `PUT /api/v1/users/me/dashboard-layout`
* **描述:** 保存当前管理员的看板布局和指标偏好。
* **Payload 示例:**
```json
{
  "version": "1.0",
  "layout": [
    { "panelId": "trend_chart", "position": { "x": 0, "y": 0, "w": 8, "h": 4 }, "visible": true },
    { "panelId": "risk_queue", "position": { "x": 8, "y": 0, "w": 4, "h": 4 }, "visible": true }
  ],
  "preferences": {
    "defaultTimeRange": "last_7_days",
    "refreshInterval": 30000
  }
}
```