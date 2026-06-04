-- =========================================
-- 访客记录表
-- 每次查看他人主页时记录一条
-- =========================================
DROP TABLE IF EXISTS t_visitor;
CREATE TABLE t_visitor (
    id           BIGINT    NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT    NOT NULL COMMENT '被访问者(主页主人)',
    visitor_id   BIGINT    NOT NULL COMMENT '访问者',
    create_time  DATETIME  DEFAULT CURRENT_TIMESTAMP COMMENT '访问时间',
    INDEX idx_user_id (user_id),
    INDEX idx_visitor_id (visitor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='访客记录表';
