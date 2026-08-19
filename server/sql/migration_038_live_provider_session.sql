-- =========================================
-- RTC-006: SRS provider session projection + live room provider state.
-- 036/037 are reserved by AI-003/AI-004; do not renumber this migration.
-- =========================================

USE douyin;

ALTER TABLE t_live_room
    ADD COLUMN IF NOT EXISTS provider_state VARCHAR(24) DEFAULT 'IDLE' COMMENT 'Provider state: IDLE/STARTING/ACTIVE/DISCONNECTED/UNAVAILABLE/ENDED',
    ADD COLUMN IF NOT EXISTS provider_session_id VARCHAR(128) DEFAULT '' COMMENT 'Current publish provider session',
    ADD COLUMN IF NOT EXISTS provider_last_seen_at DATETIME DEFAULT NULL COMMENT 'Provider last heartbeat',
    ADD COLUMN IF NOT EXISTS provider_grace_until DATETIME DEFAULT NULL COMMENT 'Provider reconciliation grace deadline';

-- Pre-provider rooms have no authenticated generation. Put them through the
-- same bounded reconciliation path instead of treating an old LIVE row as
-- durable proof that media still exists.
UPDATE t_live_room
SET status = 'DEGRADED',
    provider_state = 'MIGRATING',
    provider_grace_until = NULL
WHERE status = 'LIVE'
  AND (provider_session_id IS NULL OR provider_session_id = '');

CREATE TABLE IF NOT EXISTS live_provider_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Primary key',
    room_id BIGINT NOT NULL COMMENT 'Live room ID',
    provider VARCHAR(32) NOT NULL COMMENT 'Provider name',
    direction VARCHAR(16) NOT NULL COMMENT 'PUBLISH/PLAY',
    client_id VARCHAR(128) NOT NULL COMMENT 'SRS client ID',
    server_id VARCHAR(128) NOT NULL COMMENT 'SRS server ID',
    provider_session_id VARCHAR(128) NOT NULL COMMENT 'Provider session generation',
    stream_key VARCHAR(255) NOT NULL COMMENT 'Stream key',
    user_id BIGINT DEFAULT NULL COMMENT 'Authenticated actor ID',
    state VARCHAR(24) NOT NULL COMMENT 'ACTIVE/ENDED',
    last_event VARCHAR(64) NOT NULL DEFAULT '' COMMENT 'Latest callback event',
    first_seen_at DATETIME NOT NULL COMMENT 'First seen timestamp',
    last_seen_at DATETIME NOT NULL COMMENT 'Last heartbeat timestamp',
    ended_at DATETIME DEFAULT NULL COMMENT 'End timestamp',
    UNIQUE KEY uk_provider_session_generation (provider, direction, room_id, stream_key, provider_session_id),
    KEY idx_live_provider_session_room (room_id),
    KEY idx_live_provider_session_stream (stream_key),
    KEY idx_live_provider_session_state (state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Live provider session projection';
