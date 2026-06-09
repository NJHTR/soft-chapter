-- =========================================
-- 022: 音乐审核字段
-- =========================================

USE douyin;

-- t_music 添加审核相关字段
ALTER TABLE t_music ADD COLUMN IF NOT EXISTS status VARCHAR(16) DEFAULT 'APPROVED' COMMENT '审核状态: PENDING/APPROVED/REJECTED';
ALTER TABLE t_music ADD COLUMN IF NOT EXISTS review_comment VARCHAR(500) DEFAULT '' COMMENT '审核意见/驳回原因';
ALTER TABLE t_music ADD COLUMN IF NOT EXISTS reviewed_by BIGINT DEFAULT 0 COMMENT '审核人ID';
ALTER TABLE t_music ADD COLUMN IF NOT EXISTS review_time DATETIME NULL COMMENT '审核时间';

-- 已有音乐默认审核通过
UPDATE t_music SET status = 'APPROVED' WHERE status IS NULL OR status = '';
