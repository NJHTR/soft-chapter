-- ============================================================
-- 035: RTC provider webhook 账本 (rtc-persistence)
-- 归属: rtc-persistence; 只允许 rtc 模块读写。
-- event_id 来自 LiveKit webhook 事件,全局唯一;
-- INSERT IGNORE + 唯一索引保证重放/重复投递幂等(重复返回 200)。
-- 不修改任何已有表。引擎与字符集沿用现有库风格。
-- ============================================================

USE douyin;

CREATE TABLE IF NOT EXISTS rtc_webhook_ledger (
    id          BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花ID',
    event_id    VARCHAR(128) NOT NULL COMMENT 'LiveKit webhook 事件ID(全局唯一,幂等)',
    call_id     VARCHAR(64)  DEFAULT NULL COMMENT '解析出的通话ID,未知时为空',
    event_type  VARCHAR(40)  NOT NULL COMMENT 'room_started/room_finished/participant_joined/participant_left/track_*/...',
    payload     TEXT         DEFAULT NULL COMMENT '签名校验后的原始负载 JSON(审计)',
    received_at DATETIME     NOT NULL COMMENT '接收时间',
    processed   TINYINT      NOT NULL DEFAULT 1 COMMENT '1=已消费(去重后首次处理)',
    UNIQUE KEY uk_event_id (event_id),
    KEY idx_call (call_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RTC provider webhook 账本';
