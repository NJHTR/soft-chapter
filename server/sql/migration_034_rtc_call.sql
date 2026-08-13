-- ============================================================
-- 034: RTC 通话控制面 (rtc-persistence 真相源)
-- 归属: rtc-persistence; 只允许 rtc 模块读写。
-- chat-persistence 只是投影(t_message msg_type=10/11 带 call_id)。
-- 不修改任何已有表。引擎与字符集沿用现有库风格。
-- ============================================================

USE douyin;

-- 通话会话
CREATE TABLE IF NOT EXISTS rtc_call_session (
    id                BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花ID',
    call_id           VARCHAR(64)  NOT NULL COMMENT '全局不可变通话ID',
    room_id           VARCHAR(128) DEFAULT NULL COMMENT 'provider room ID (livekit/p2p-fallback)',
    scope             VARCHAR(20)  NOT NULL COMMENT 'direct|group|live-interactive',
    mode              VARCHAR(10)  NOT NULL COMMENT 'audio|video',
    initiator_id      BIGINT       NOT NULL COMMENT '发起者用户ID',
    provider          VARCHAR(20)  DEFAULT 'livekit' COMMENT 'livekit|p2p-fallback|legacy',
    state             VARCHAR(20)  NOT NULL COMMENT 'CREATED/RINGING/ACCEPTED/NEGOTIATING/CONNECTED/ENDING/REJECTED/CANCELLED/EXPIRED/FAILED/ENDED',
    client_request_id VARCHAR(128) NOT NULL COMMENT '发起幂等键(唯一)',
    expires_at        DATETIME     DEFAULT NULL COMMENT 'ringing/negotiating TTL 过期时间',
    connected_at      DATETIME     DEFAULT NULL COMMENT '首次进入 CONNECTED 时间',
    ended_at          DATETIME     DEFAULT NULL COMMENT '结束时间',
    end_reason        VARCHAR(20)  DEFAULT NULL COMMENT 'hangup/rejected/expired/failed/permission/provider',
    trace_id          VARCHAR(128) DEFAULT NULL COMMENT '链路追踪ID',
    create_time       DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_call_id (call_id),
    UNIQUE KEY uk_client_request_id (client_request_id),
    KEY idx_state_expires (state, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RTC 通话会话表';

-- 通话参与者
CREATE TABLE IF NOT EXISTS rtc_call_participant (
    id               BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花ID',
    call_id          VARCHAR(64)  NOT NULL COMMENT '通话ID',
    user_id          BIGINT       NOT NULL COMMENT '用户ID',
    role             VARCHAR(20)  DEFAULT 'member' COMMENT 'initiator|member',
    state            VARCHAR(20)  NOT NULL COMMENT 'INVITED/RINGING/JOINING/CONNECTED/RECONNECTING/LEFT/REJECTED/CANCELLED/FAILED',
    joined_at        DATETIME     DEFAULT NULL COMMENT '加入时间',
    left_at          DATETIME     DEFAULT NULL COMMENT '离开时间',
    reason           VARCHAR(30)  DEFAULT NULL COMMENT '离开/失败原因',
    profile_snapshot VARCHAR(512) DEFAULT NULL COMMENT '历史展示快照,不作为权限来源',
    create_time      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_call_user (call_id, user_id),
    KEY idx_call (call_id),
    KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RTC 通话参与者表';

-- 通话事件账本
CREATE TABLE IF NOT EXISTS rtc_call_event (
    id             BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花ID',
    event_id       VARCHAR(128) NOT NULL COMMENT '全局唯一事件ID(幂等)',
    call_id        VARCHAR(64)  NOT NULL COMMENT '通话ID',
    participant_id BIGINT       NOT NULL DEFAULT 0 COMMENT '触发参与者用户ID, 0=系统事件',
    kind           VARCHAR(40)  NOT NULL COMMENT 'call.request/call.accept/call.reject/call.cancel/call.join/call.leave/call.hangup/call.state/call.connected/call.ended/call.expired/call.failed',
    seq            BIGINT       NOT NULL COMMENT '同一(call_id,participant_id)单调递增序号',
    occurred_at    DATETIME     NOT NULL COMMENT '事件发生时间',
    payload        TEXT         DEFAULT NULL COMMENT '事件负载 JSON',
    trace_id       VARCHAR(128) DEFAULT NULL COMMENT '链路追踪ID',
    UNIQUE KEY uk_event_id (event_id),
    KEY idx_call_participant_seq (call_id, participant_id, seq),
    KEY idx_call_time (call_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RTC 通话事件账本';