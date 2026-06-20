-- 订单表
CREATE TABLE IF NOT EXISTS t_order (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '买家ID',
    seller_id BIGINT NOT NULL COMMENT '卖家ID',
    goods_id BIGINT NOT NULL COMMENT '商品ID',
    goods_name VARCHAR(255) COMMENT '商品名称快照',
    goods_cover VARCHAR(500) COMMENT '商品封面快照',
    price DECIMAL(10,2) COMMENT '成交单价',
    quantity INT DEFAULT 1 COMMENT '数量',
    total_amount DECIMAL(10,2) COMMENT '总金额',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING/PAID/SHIPPED/RECEIVED/CANCELLED',
    receiver_name VARCHAR(50) COMMENT '收货人',
    receiver_phone VARCHAR(30) COMMENT '收货电话',
    receiver_address VARCHAR(500) COMMENT '收货地址',
    remark VARCHAR(500) COMMENT '买家备注',
    is_delete TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_goods_id (goods_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';
