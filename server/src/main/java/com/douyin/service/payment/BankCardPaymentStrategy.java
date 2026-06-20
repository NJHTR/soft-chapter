package com.douyin.service.payment;

import com.douyin.entity.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 银行卡支付策略 — 模拟调用银联/网联(当前为模拟实现)
 */
@Component
public class BankCardPaymentStrategy implements PaymentStrategy {

    @Override
    public String getType() {
        return "bank";
    }

    @Override
    public BigDecimal execute(Long userId, Order order) {
        String simulatedRrn = "BC" + System.currentTimeMillis();
        System.out.println("[银行卡] 订单#" + order.getId() + " 金额￥" + order.getTotalAmount()
                + " 参考号:" + simulatedRrn);
        return null;
    }
}
