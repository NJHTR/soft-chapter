package com.douyin.service.payment;

import com.douyin.entity.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 微信支付策略 — 模拟调用微信支付(当前为模拟实现)
 */
@Component
public class WechatPaymentStrategy implements PaymentStrategy {

    @Override
    public String getType() {
        return "wechat";
    }

    @Override
    public BigDecimal execute(Long userId, Order order) {
        // 模拟微信支付调用: 实际应调用微信统一下单API + 验签
        String simulatedTransactionId = "WX" + System.currentTimeMillis();
        System.out.println("[微信支付] 订单#" + order.getId() + " 金额￥" + order.getTotalAmount()
                + " 交易流水:" + simulatedTransactionId);
        return null; // 第三方支付不操作本地余额
    }
}
