-- =========================================
-- 修复: t_message.content 改为 TEXT 类型
-- VARCHAR(1000) 不足以存放分享视频消息的 JSON (含长签名 URL)
-- =========================================

USE douyin;

ALTER TABLE t_message MODIFY COLUMN content TEXT DEFAULT NULL COMMENT '消息内容';
