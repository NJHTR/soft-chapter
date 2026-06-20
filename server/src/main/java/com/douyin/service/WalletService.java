package com.douyin.service;

import com.douyin.common.PageDTO;
import com.douyin.entity.WalletTransaction;
import jakarta.servlet.http.HttpServletRequest;

import java.math.BigDecimal;
import java.util.Map;

public interface WalletService {

    /** 获取余额 */
    BigDecimal getBalance(Long userId);

    /** 充值(含安全校验) */
    Map<String, Object> recharge(Long userId, BigDecimal amount, HttpServletRequest req);

    /** 钱包支付扣款(含余额前后一致性校验) */
    BigDecimal walletPay(Long userId, Long orderId, BigDecimal amount);

    /** 交易流水 */
    PageDTO<WalletTransaction> transactions(Long userId, int pageNo, int pageSize);
}
