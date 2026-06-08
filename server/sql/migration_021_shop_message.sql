-- 商城消息通知
CREATE TABLE IF NOT EXISTS t_shop_message (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '接收用户ID',
    title VARCHAR(200) NOT NULL COMMENT '消息标题',
    content VARCHAR(500) NOT NULL COMMENT '消息内容',
    type VARCHAR(20) NOT NULL COMMENT 'cart/order/service',
    related_id BIGINT COMMENT '关联ID (商品ID/订单ID)',
    is_read TINYINT DEFAULT 0 COMMENT '0=未读 1=已读',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_user_read (user_id, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城消息通知';
