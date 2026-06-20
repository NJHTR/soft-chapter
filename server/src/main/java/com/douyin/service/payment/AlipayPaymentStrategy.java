package com.douyin.service.payment;

import com.douyin.entity.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 支付宝支付策略 — 模拟调用支付宝(当前为模拟实现)
 */
@Component
public class AlipayPaymentStrategy implements PaymentStrategy {

    @Override
    public String getType() {
        return "alipay";
    }

    @Override
    public BigDecimal execute(Long userId, Order order) {
        String simulatedTradeNo = "AL" + System.currentTimeMillis();
        System.out.println("[支付宝] 订单#" + order.getId() + " 金额￥" + order.getTotalAmount()
                + " 交易号:" + simulatedTradeNo);
        return null;
    }
}
