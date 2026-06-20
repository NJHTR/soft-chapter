package com.douyin.service.payment;

import com.douyin.entity.Order;
import com.douyin.vo.OrderVO;

import java.math.BigDecimal;

/**
 * 支付策略接口 — 统一支付行为，不同支付方式各自实现
 */
public interface PaymentStrategy {

    /** 支付工具类型 */
    String getType();

    /** 执行支付并返回支付后的余额(零钱支付返回新余额，其他返回null) */
    BigDecimal execute(Long userId, Order order);
}
