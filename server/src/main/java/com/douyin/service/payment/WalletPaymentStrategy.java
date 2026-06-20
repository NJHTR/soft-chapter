package com.douyin.service.payment;

import com.douyin.entity.Order;
import com.douyin.service.WalletService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 零钱支付策略 — 从用户余额扣款
 */
@Component
public class WalletPaymentStrategy implements PaymentStrategy {

    private final WalletService walletService;

    public WalletPaymentStrategy(WalletService walletService) {
        this.walletService = walletService;
    }

    @Override
    public String getType() {
        return "wallet";
    }

    @Override
    public BigDecimal execute(Long userId, Order order) {
        return walletService.walletPay(userId, order.getId(), order.getTotalAmount());
    }
}
