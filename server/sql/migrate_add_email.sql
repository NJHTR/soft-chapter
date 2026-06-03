-- 给已有数据库加邮箱字段
-- 如果 email 列已存在，忽略 ALTER TABLE 那行的错误即可
USE douyin;
ALTER TABLE t_user ADD COLUMN email VARCHAR(64) DEFAULT '' COMMENT '邮箱' AFTER phone;
ALTER TABLE t_user ADD UNIQUE KEY uk_email (email);
