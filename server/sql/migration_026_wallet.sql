-- 钱包系统
-- 用户余额
ALTER TABLE t_user ADD COLUMN balance DECIMAL(10,2) DEFAULT 0.00 COMMENT '账户余额';

-- 钱包交易流水
CREATE TABLE IF NOT EXISTS t_wallet_transaction (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    type VARCHAR(20) NOT NULL COMMENT 'RECHARGE/PAY/REFUND',
    amount DECIMAL(10,2) NOT NULL COMMENT '金额',
    balance_after DECIMAL(10,2) NOT NULL COMMENT '交易后余额',
    remark VARCHAR(255) COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钱包交易流水';

-- 订单支付方式
ALTER TABLE t_order ADD COLUMN payment_method VARCHAR(20) DEFAULT 'wallet' COMMENT 'wallet/wechat/alipay/bank';
