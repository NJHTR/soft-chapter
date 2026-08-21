-- RTC-CALL-001: durable independent ringing state per user device.
CREATE TABLE IF NOT EXISTS rtc_call_device (
    id           BIGINT NOT NULL PRIMARY KEY COMMENT '雪花ID',
    call_id      VARCHAR(64) NOT NULL,
    user_id      BIGINT NOT NULL,
    device_id    VARCHAR(128) NOT NULL COMMENT '客户端持久化设备标识',
    state        VARCHAR(20) NOT NULL COMMENT 'RINGING/ACCEPTED/REJECTED/CANCELLED/CONNECTED/LEFT',
    accepted_at  DATETIME DEFAULT NULL,
    rejected_at  DATETIME DEFAULT NULL,
    create_time  DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_call_user_device (call_id, user_id, device_id),
    KEY idx_call_device_state (call_id, state),
    KEY idx_user_device_state (user_id, state, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RTC 通话设备级状态';
