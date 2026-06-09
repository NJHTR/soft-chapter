-- =========================================
-- 管理员审核系统：t_user 加 role，t_video 加 status
-- =========================================

USE douyin;

-- 1. t_user 添加 role 字段
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS role VARCHAR(16) DEFAULT 'USER' COMMENT '角色: USER/ADMIN';

-- 2. t_video 添加审核相关字段
ALTER TABLE t_video ADD COLUMN IF NOT EXISTS status VARCHAR(16) DEFAULT 'APPROVED' COMMENT '审核状态: PENDING/APPROVED/REJECTED';
ALTER TABLE t_video ADD COLUMN IF NOT EXISTS review_comment VARCHAR(500) DEFAULT '' COMMENT '审核意见/驳回原因';
ALTER TABLE t_video ADD COLUMN IF NOT EXISTS reviewed_by BIGINT DEFAULT 0 COMMENT '审核人ID';
ALTER TABLE t_video ADD COLUMN IF NOT EXISTS review_time DATETIME NULL COMMENT '审核时间';

-- 3. 已有视频默认审核通过
UPDATE t_video SET status = 'APPROVED' WHERE status IS NULL OR status = '';

-- 4. 创建默认管理员
-- 注意：需要在应用启动后，先用 /api/register 注册 admin@seekflow.com 账号，
-- 然后执行: UPDATE t_user SET role = 'ADMIN' WHERE email = 'admin@seekflow.com';
-- 或者直接执行以下语句(密码需要在应用中通过 BCrypt 设置):
-- INSERT INTO t_user (uid, email, role) VALUES (9999, 'admin@seekflow.com', 'ADMIN')
-- ON DUPLICATE KEY UPDATE role = 'ADMIN';