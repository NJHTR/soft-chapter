package com.douyin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.douyin.entity.PaymentAudit;
import com.douyin.entity.User;
import com.douyin.mapper.PaymentAuditMapper;
import com.douyin.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
public class PaymentSecurityService extends ServiceImpl<PaymentAuditMapper, PaymentAudit> {

    private final UserMapper userMapper;

    /** 大额交易阈值 */
    private static final BigDecimal LARGE_AMOUNT = new BigDecimal("5000");

    /** 短时间内交易次数阈值 */
    private static final int RAPID_TX_THRESHOLD = 10;

    /** 短时间内交易时间窗口(分钟) */
    private static final int RAPID_TX_WINDOW_MIN = 10;

    public PaymentSecurityService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 支付请求完整校验
     * @return null = 通过, 非null = 失败原因
     */
    public String validatePay(Long userId, Long orderId, BigDecimal requestAmount,
                              BigDecimal orderAmount, BigDecimal balance, String paymentMethod,
                              HttpServletRequest req) {

        // 1. 金额一致性校验
        if (requestAmount != null && orderAmount != null) {
            if (requestAmount.compareTo(orderAmount) != 0) {
                audit(userId, orderId, "PAY", requestAmount, orderAmount,
                        balance, null, paymentMethod, "FAIL",
                        "金额不一致: 请求" + requestAmount + " vs 订单" + orderAmount,
                        "HIGH", "金额篡改尝试", req);
                return "支付金额与订单金额不符";
            }
        }

        // 2. 零钱支付时余额校验
        if ("wallet".equals(paymentMethod) && balance.compareTo(orderAmount) < 0) {
            audit(userId, orderId, "PAY", requestAmount, orderAmount,
                    balance, null, paymentMethod, "FAIL",
                    "余额不足: 余额" + balance + " < 订单" + orderAmount,
                    "LOW", null, req);
            return "余额不足";
        }

        // 3. 大额交易标记
        if (orderAmount.compareTo(LARGE_AMOUNT) >= 0) {
            log.warn("[支付安全] 大额交易: userId={}, orderId={}, amount={}", userId, orderId, orderAmount);
        }

        // 4. 短时间高频检测
        int recentCount = countRecentTransactions(userId, RAPID_TX_WINDOW_MIN);
        if (recentCount >= RAPID_TX_THRESHOLD) {
            audit(userId, orderId, "PAY", requestAmount, orderAmount,
                    balance, null, paymentMethod, "FAIL",
                    "高频交易: " + RAPID_TX_WINDOW_MIN + "分钟内" + recentCount + "笔",
                    "HIGH", "高频交易风险", req);
            return "操作过于频繁，请稍后再试";
        }

        // 5. 中等风险标记
        String riskLevel = "LOW";
        String riskDetail = null;
        if (orderAmount.compareTo(LARGE_AMOUNT) >= 0) {
            riskLevel = "MEDIUM";
            riskDetail = "大额交易";
        }
        if (recentCount >= RAPID_TX_THRESHOLD / 2) {
            riskLevel = "MEDIUM";
            riskDetail = (riskDetail != null ? riskDetail + "; " : "") + "交易频率偏高";
        }

        audit(userId, orderId, "PAY", requestAmount, orderAmount,
                balance, null, paymentMethod, "PASS", null, riskLevel, riskDetail, req);

        return null; // 通过
    }

    /**
     * 充值安全校验
     */
    public String validateRecharge(Long userId, BigDecimal amount, BigDecimal balanceBefore,
                                   HttpServletRequest req) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return "充值金额必须大于0";
        }

        int recentCount = countRecentTransactions(userId, RAPID_TX_WINDOW_MIN);
        if (recentCount >= RAPID_TX_THRESHOLD) {
            audit(userId, null, "RECHARGE", amount, null,
                    balanceBefore, null, null, "FAIL",
                    "高频操作: " + RAPID_TX_WINDOW_MIN + "分钟内" + recentCount + "笔",
                    "HIGH", "高频充值风险", req);
            return "操作过于频繁，请稍后再试";
        }

        // 单笔充值上限
        BigDecimal maxRecharge = new BigDecimal("10000");
        if (amount.compareTo(maxRecharge) > 0) {
            audit(userId, null, "RECHARGE", amount, null,
                    balanceBefore, null, null, "FAIL",
                    "超出单笔充值上限: " + amount + " > " + maxRecharge,
                    "MEDIUM", "大额充值", req);
            return "单笔充值不能超过￥" + maxRecharge;
        }

        String riskLevel = "LOW";
        if (amount.compareTo(new BigDecimal("1000")) >= 0) {
            riskLevel = "MEDIUM";
        }

        audit(userId, null, "RECHARGE", amount, null,
                balanceBefore, null, null, "PASS", null, riskLevel, null, req);
        return null;
    }

    /**
     * 支付完成后记录审计
     */
    public void auditPaySuccess(Long userId, Long orderId, BigDecimal amount,
                                BigDecimal balanceBefore, BigDecimal balanceAfter,
                                String paymentMethod, String riskLevel,
                                HttpServletRequest req) {
        audit(userId, orderId, "PAY", amount, amount, balanceBefore, balanceAfter,
                paymentMethod, "PASS", null, riskLevel, null, req);
    }

    /**
     * 生成支付签名(用于客户端-服务端双重校验)
     */
    public String generatePaySign(Long userId, Long orderId, BigDecimal amount) {
        User user = userMapper.selectById(userId);
        String salt = user.getPaySalt();
        if (salt == null) {
            salt = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            user.setPaySalt(salt);
            userMapper.updateById(user);
        }
        String raw = userId + "|" + orderId + "|" + amount.stripTrailingZeros().toPlainString() + "|" + salt;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * 验签
     */
    public boolean verifyPaySign(Long userId, Long orderId, BigDecimal amount, String sign) {
        if (sign == null) return false;
        String expected = generatePaySign(userId, orderId, amount);
        return expected.equals(sign);
    }

    // ========== 内部方法 ==========

    private void audit(Long userId, Long orderId, String action,
                       BigDecimal requestAmount, BigDecimal orderAmount,
                       BigDecimal balanceBefore, BigDecimal balanceAfter,
                       String paymentMethod, String result, String failReason,
                       String riskLevel, String riskDetail,
                       HttpServletRequest req) {
        try {
            PaymentAudit a = new PaymentAudit();
            a.setUserId(userId);
            a.setOrderId(orderId);
            a.setAction(action);
            a.setRequestAmount(requestAmount);
            a.setOrderAmount(orderAmount);
            a.setBalanceBefore(balanceBefore);
            a.setBalanceAfter(balanceAfter);
            a.setPaymentMethod(paymentMethod);
            a.setResult(result);
            a.setFailReason(failReason);
            a.setIp(getClientIp(req));
            a.setUserAgent(req != null ? req.getHeader("User-Agent") : null);
            a.setRiskLevel(riskLevel != null ? riskLevel : "LOW");
            a.setRiskDetail(riskDetail);
            save(a);

            if ("HIGH".equals(riskLevel) || "FAIL".equals(result)) {
                log.error("[支付安全] 风险事件: action={}, userId={}, riskLevel={}, reason={}",
                        action, userId, riskLevel, failReason != null ? failReason : riskDetail);
            }
        } catch (Exception e) {
            log.error("审计日志写入失败", e);
        }
    }

    private int countRecentTransactions(Long userId, int windowMinutes) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(windowMinutes);
        return (int) count(new LambdaQueryWrapper<PaymentAudit>()
                .eq(PaymentAudit::getUserId, userId)
                .eq(PaymentAudit::getResult, "PASS")
                .ge(PaymentAudit::getCreateTime, since));
    }

    private String getClientIp(HttpServletRequest req) {
        if (req == null) return "unknown";
        String ip = req.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) ip = req.getHeader("X-Real-IP");
        if (ip == null || ip.isEmpty()) ip = req.getRemoteAddr();
        return ip;
    }
}
