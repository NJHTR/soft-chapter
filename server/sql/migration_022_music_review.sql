-- =========================================
-- 022: Music review fields
-- =========================================

USE douyin;

-- t_music add review columns
ALTER TABLE t_music ADD COLUMN IF NOT EXISTS status VARCHAR(16) DEFAULT 'APPROVED' COMMENT 'Review status: PENDING/APPROVED/REJECTED';
ALTER TABLE t_music ADD COLUMN IF NOT EXISTS review_comment VARCHAR(500) DEFAULT '' COMMENT 'Review comment / reject reason';
ALTER TABLE t_music ADD COLUMN IF NOT EXISTS reviewed_by BIGINT DEFAULT 0 COMMENT 'Reviewer ID';
ALTER TABLE t_music ADD COLUMN IF NOT EXISTS review_time DATETIME NULL COMMENT 'Review time';

-- Default existing music to approved
UPDATE t_music SET status = 'APPROVED' WHERE status IS NULL OR status = '';