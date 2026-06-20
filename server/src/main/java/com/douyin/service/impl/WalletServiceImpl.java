package com.douyin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.douyin.common.PageDTO;
import com.douyin.entity.User;
import com.douyin.entity.WalletTransaction;
import com.douyin.mapper.UserMapper;
import com.douyin.mapper.WalletTransactionMapper;
import com.douyin.service.PaymentSecurityService;
import com.douyin.service.WalletService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class WalletServiceImpl extends ServiceImpl<WalletTransactionMapper, WalletTransaction>
        implements WalletService {

    private final UserMapper userMapper;
    private final PaymentSecurityService securityService;

    public WalletServiceImpl(UserMapper userMapper, PaymentSecurityService securityService) {
        this.userMapper = userMapper;
        this.securityService = securityService;
    }

    @Override
    public BigDecimal getBalance(Long userId) {
        User user = userMapper.selectById(userId);
        return user != null && user.getBalance() != null ? user.getBalance() : BigDecimal.ZERO;
    }

    @Override
    @Transactional
    public Map<String, Object> recharge(Long userId, BigDecimal amount, HttpServletRequest req) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new RuntimeException("用户不存在");

        BigDecimal oldBalance = user.getBalance() != null ? user.getBalance() : BigDecimal.ZERO;

        // 安全校验
        String failReason = securityService.validateRecharge(userId, amount, oldBalance, req);
        if (failReason != null) throw new RuntimeException(failReason);

        BigDecimal newBalance = oldBalance.add(amount);
        user.setBalance(newBalance);
        userMapper.updateById(user);

        // 更新后余额一致性校验
        User verify = userMapper.selectById(userId);
        if (verify.getBalance().compareTo(newBalance) != 0) {
            log.error("[钱包安全] 余额不一致: userId={}, expected={}, actual={}", userId, newBalance, verify.getBalance());
            throw new RuntimeException("余额校验失败，请联系客服");
        }

        WalletTransaction tx = new WalletTransaction();
        tx.setUserId(userId);
        tx.setType("RECHARGE");
        tx.setAmount(amount);
        tx.setBalanceAfter(newBalance);
        tx.setRemark("账户充值");
        save(tx);

        log.info("Recharge: userId={}, amount={}, balance={}", userId, amount, newBalance);
        return Map.of("balance", newBalance);
    }

    @Override
    @Transactional
    public BigDecimal walletPay(Long userId, Long orderId, BigDecimal amount) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new RuntimeException("用户不存在");

        BigDecimal balance = user.getBalance() != null ? user.getBalance() : BigDecimal.ZERO;
        if (balance.compareTo(amount) < 0) {
            throw new RuntimeException("余额不足，请先充值");
        }

        BigDecimal newBalance = balance.subtract(amount);
        user.setBalance(newBalance);
        userMapper.updateById(user);

        // 扣款后余额一致性校验
        User verify = userMapper.selectById(userId);
        if (verify.getBalance().compareTo(newBalance) != 0) {
            log.error("[钱包安全] 扣款后余额不一致: userId={}, expected={}, actual={}", userId, newBalance, verify.getBalance());
            throw new RuntimeException("余额校验失败，请联系客服");
        }

        WalletTransaction tx = new WalletTransaction();
        tx.setUserId(userId);
        tx.setType("PAY");
        tx.setAmount(amount.negate());
        tx.setBalanceAfter(newBalance);
        tx.setRemark("订单支付 #" + orderId);
        save(tx);

        log.info("WalletPay: userId={}, orderId={}, amount={}, balance={}", userId, orderId, amount, newBalance);
        return newBalance;
    }

    @Override
    public PageDTO<WalletTransaction> transactions(Long userId, int pageNo, int pageSize) {
        LambdaQueryWrapper<WalletTransaction> wrapper = new LambdaQueryWrapper<WalletTransaction>()
                .eq(WalletTransaction::getUserId, userId)
                .orderByDesc(WalletTransaction::getCreateTime);
        Page<WalletTransaction> page = page(new Page<>(pageNo, pageSize), wrapper);
        List<WalletTransaction> list = page.getRecords();
        return new PageDTO<>(page.getTotal(), pageNo, pageSize, list);
    }
}
