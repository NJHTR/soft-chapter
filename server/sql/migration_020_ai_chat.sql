-- AI 客服聊天记录
CREATE TABLE IF NOT EXISTS t_ai_chat_message (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL COMMENT '商品ID (隔离维度)',
    user_id BIGINT COMMENT '用户ID (可为空，未登录也记录)',
    role VARCHAR(20) NOT NULL COMMENT 'user/assistant',
    content TEXT NOT NULL COMMENT '消息内容',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI客服聊天记录';
