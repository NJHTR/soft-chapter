-- ============================================================
-- 017: 商城商品模块
-- ============================================================

DROP TABLE IF EXISTS t_goods;
CREATE TABLE t_goods (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    seller_id BIGINT NOT NULL COMMENT '卖家用户ID',
    name VARCHAR(255) NOT NULL COMMENT '商品名称',
    price DECIMAL(10,2) NOT NULL COMMENT '原价',
    real_price DECIMAL(10,2) COMMENT '券后价',
    cover VARCHAR(512) COMMENT '封面图',
    imgs TEXT COMMENT '商品图片列表(JSON数组)',
    description TEXT COMMENT '商品描述',
    sold INT DEFAULT 0 COMMENT '已售数量',
    discount VARCHAR(100) COMMENT '优惠标签(如: 满200减20)',
    status TINYINT DEFAULT 1 COMMENT '状态: 1=上架 0=下架',
    is_delete TINYINT DEFAULT 0 COMMENT '逻辑删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_seller (seller_id),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    COMMENT='商城商品';
