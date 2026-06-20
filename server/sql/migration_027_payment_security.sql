-- 安全审计日志表
CREATE TABLE IF NOT EXISTS t_payment_audit (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    order_id BIGINT COMMENT '关联订单ID',
    action VARCHAR(30) NOT NULL COMMENT 'PAY/RECHARGE/REFUND/CANCEL',
    request_amount DECIMAL(10,2) COMMENT '请求金额',
    order_amount DECIMAL(10,2) COMMENT '订单金额',
    balance_before DECIMAL(10,2) COMMENT '交易前余额',
    balance_after DECIMAL(10,2) COMMENT '交易后余额',
    payment_method VARCHAR(20) COMMENT '支付方式',
    result VARCHAR(10) NOT NULL COMMENT 'PASS/FAIL',
    fail_reason VARCHAR(200) COMMENT '失败原因',
    ip VARCHAR(50) COMMENT '请求IP',
    user_agent VARCHAR(500) COMMENT 'UA',
    risk_level VARCHAR(10) DEFAULT 'LOW' COMMENT 'LOW/MEDIUM/HIGH',
    risk_detail VARCHAR(500) COMMENT '风险详情',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_order_id (order_id),
    INDEX idx_result (result),
    INDEX idx_risk (risk_level),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付安全审计日志';

-- 用户表加支付验签字段
ALTER TABLE t_user ADD COLUMN pay_salt VARCHAR(32) DEFAULT NULL COMMENT '支付签名盐值';

-- 订单表加幂等键
ALTER TABLE t_order ADD COLUMN idempotency_key VARCHAR(64) DEFAULT NULL COMMENT '幂等键';
CREATE UNIQUE INDEX idx_idempotency_key ON t_order (idempotency_key);
