-- =========================================
-- RTC-006 forward migration: normalize legacy provider-session sentinels.
-- Migration 038 was shipped with an empty-string default in some
-- environments. Do not rely on editing that already-applied migration:
-- normalize existing rows and make the column default NULL now.
-- =========================================

USE douyin;

UPDATE t_live_room
SET provider_session_id = NULL
WHERE provider_session_id = '';

ALTER TABLE t_live_room
    MODIFY COLUMN provider_session_id VARCHAR(128) DEFAULT NULL
        COMMENT 'Current publish provider session';
