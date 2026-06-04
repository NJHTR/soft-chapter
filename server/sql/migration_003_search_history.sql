-- ============ 搜索历史表 ============
DROP TABLE IF EXISTS t_search_history;
CREATE TABLE t_search_history (
    id          BIGINT    NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT    NOT NULL COMMENT '用户ID',
    keyword     VARCHAR(200) NOT NULL COMMENT '搜索关键词',
    create_time DATETIME  DEFAULT CURRENT_TIMESTAMP COMMENT '搜索时间',
    KEY idx_user_time (user_id, create_time DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='搜索历史表';
