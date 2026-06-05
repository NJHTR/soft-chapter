-- ============================================================
-- 013: 系统通知表 (登录提醒、发布成功、好友通过等)
-- ============================================================

CREATE TABLE IF NOT EXISTS t_system_notice (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL COMMENT '接收通知的用户ID',
    title       VARCHAR(255) NOT NULL COMMENT '通知标题',
    content     TEXT         COMMENT '通知详细内容',
    type        VARCHAR(32)  DEFAULT 'system' COMMENT '类型: friend_accepted/login/publish_video/publish_image/publish_text/system',
    is_read     TINYINT      DEFAULT 0 COMMENT '0未读 1已读',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    KEY idx_user_id (user_id),
    KEY idx_user_read (user_id, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统通知表';
