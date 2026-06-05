-- ============================================================
-- 012: 朋友系统 (互相关注 + 双向确认)
-- ============================================================

CREATE TABLE IF NOT EXISTS t_friend (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL COMMENT '发起方用户ID',
    friend_id   BIGINT       NOT NULL COMMENT '接收方用户ID',
    status      TINYINT      DEFAULT 0 COMMENT '0待确认 1已接受 2已拒绝',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_friend (user_id, friend_id),
    KEY idx_friend_id (friend_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='朋友关系表';
