-- RTC-CALL-001: authoritative call lifecycle versions and reconciliation indexes.
-- MySQL remains the durable fact; Redis timeout indexes are rebuildable accelerators.
-- MySQL 8.x does not support ADD COLUMN IF NOT EXISTS. Build each ALTER only
-- when the column is absent so this migration remains rerunnable.
SET @ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'rtc_call_session'
             AND column_name = 'ring_at'),
    'SELECT 1',
    'ALTER TABLE rtc_call_session ADD COLUMN ring_at DATETIME NULL AFTER client_request_id'
);
PREPARE migration_042_stmt FROM @ddl;
EXECUTE migration_042_stmt;
DEALLOCATE PREPARE migration_042_stmt;

SET @ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'rtc_call_session'
             AND column_name = 'state_version'),
    'SELECT 1',
    'ALTER TABLE rtc_call_session ADD COLUMN state_version BIGINT NOT NULL DEFAULT 0 AFTER state'
);
PREPARE migration_042_stmt FROM @ddl;
EXECUTE migration_042_stmt;
DEALLOCATE PREPARE migration_042_stmt;

SET @ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'rtc_call_session'
             AND index_name = 'idx_call_initiator_created'),
    'SELECT 1',
    'ALTER TABLE rtc_call_session ADD INDEX idx_call_initiator_created (initiator_id, create_time)'
);
PREPARE migration_042_stmt FROM @ddl;
EXECUTE migration_042_stmt;
DEALLOCATE PREPARE migration_042_stmt;

SET @ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'rtc_call_session'
             AND index_name = 'idx_call_state_version'),
    'SELECT 1',
    'ALTER TABLE rtc_call_session ADD INDEX idx_call_state_version (call_id, state_version)'
);
PREPARE migration_042_stmt FROM @ddl;
EXECUTE migration_042_stmt;
DEALLOCATE PREPARE migration_042_stmt;

SET @ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'rtc_call_session'
             AND index_name = 'idx_call_state_updated'),
    'SELECT 1',
    'ALTER TABLE rtc_call_session ADD INDEX idx_call_state_updated (state, update_time)'
);
PREPARE migration_042_stmt FROM @ddl;
EXECUTE migration_042_stmt;
DEALLOCATE PREPARE migration_042_stmt;

UPDATE rtc_call_session
SET ring_at = create_time
WHERE ring_at IS NULL AND state <> 'CREATED';

SET @ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'rtc_call_participant'
             AND index_name = 'idx_participant_user_state_call'),
    'SELECT 1',
    'ALTER TABLE rtc_call_participant ADD INDEX idx_participant_user_state_call (user_id, state, call_id)'
);
PREPARE migration_042_stmt FROM @ddl;
EXECUTE migration_042_stmt;
DEALLOCATE PREPARE migration_042_stmt;

SET @ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'rtc_call_event'
             AND column_name = 'event_version'),
    'SELECT 1',
    'ALTER TABLE rtc_call_event ADD COLUMN event_version BIGINT NOT NULL DEFAULT 0 AFTER seq'
);
PREPARE migration_042_stmt FROM @ddl;
EXECUTE migration_042_stmt;
DEALLOCATE PREPARE migration_042_stmt;

SET @ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'rtc_call_event'
             AND index_name = 'idx_call_event_version'),
    'SELECT 1',
    'ALTER TABLE rtc_call_event ADD INDEX idx_call_event_version (call_id, event_version, id)'
);
PREPARE migration_042_stmt FROM @ddl;
EXECUTE migration_042_stmt;
DEALLOCATE PREPARE migration_042_stmt;
