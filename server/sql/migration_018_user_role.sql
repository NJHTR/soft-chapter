-- ============================================================
-- 018: 用户角色字段
-- ============================================================

ALTER TABLE t_user
    ADD COLUMN role VARCHAR(20) DEFAULT 'user' COMMENT '角色: user=普通用户, merchant=SeekFlow商家';
