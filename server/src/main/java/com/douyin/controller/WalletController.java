package com.douyin.controller;

import com.douyin.common.PageDTO;
import com.douyin.common.Result;
import com.douyin.entity.WalletTransaction;
import com.douyin.service.WalletService;
import com.douyin.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final WalletService walletService;
    private final JwtUtil jwtUtil;

    public WalletController(WalletService walletService, JwtUtil jwtUtil) {
        this.walletService = walletService;
        this.jwtUtil = jwtUtil;
    }

    /** 查询余额 */
    @GetMapping("/balance")
    public Result<Map<String, Object>> balance(HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        BigDecimal bal = walletService.getBalance(userId);
        return Result.ok(Map.of("balance", bal));
    }

    /** 充值 */
    @PostMapping("/recharge")
    public Result<Map<String, Object>> recharge(@RequestBody Map<String, Object> body,
                                                 HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        BigDecimal amount;
        try {
            amount = new BigDecimal(body.get("amount").toString());
        } catch (Exception e) {
            return Result.fail("金额格式错误");
        }
        try {
            Map<String, Object> result = walletService.recharge(userId, amount, req);
            return Result.ok(result);
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }

    /** 交易流水 */
    @GetMapping("/transactions")
    public Result<PageDTO<WalletTransaction>> transactions(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        return Result.ok(walletService.transactions(userId, pageNo, pageSize));
    }

    private Long getLoginUserId(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            try { return jwtUtil.getUserIdFromToken(auth.substring(7)); } catch (Exception ignored) {}
        }
        return null;
    }
}
