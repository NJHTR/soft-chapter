package com.douyin.service.payment;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Callable;

/**
 * 指数退避重试工具
 *
 * 策略: 第1次重试等1s, 第2次等2s, 第3次等4s
 * 仅重试可恢复异常(TimeoutException), 不可恢复异常直接抛出
 */
@Slf4j
public final class RetryUtil {

    private static final int MAX_RETRIES = 3;
    private static final long BASE_DELAY_MS = 1000;

    private RetryUtil() {}

    /**
     * 带指数退避的重试执行
     * @param callable 业务逻辑
     * @param operation 操作描述(用于日志)
     * @param <T> 返回值类型
     */
    public static <T> T executeWithRetry(Callable<T> callable, String operation) {
        Exception lastException = null;

        for (int i = 0; i <= MAX_RETRIES; i++) {
            try {
                if (i > 0) {
                    long delay = BASE_DELAY_MS * (1L << (i - 1)); // 1s, 2s, 4s
                    log.info("[重试] {} 第{}次重试, 等待{}ms", operation, i, delay);
                    Thread.sleep(delay);
                }
                return callable.call();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(operation + " 被中断", e);
            } catch (Exception e) {
                lastException = e;
                if (!isRetryable(e)) {
                    break; // 不可重试异常, 立即退出
                }
                log.warn("[重试] {} 第{}次失败: {}", operation, i, e.getMessage());
            }
        }

        throw new RuntimeException(operation + " 重试" + MAX_RETRIES + "次后仍失败", lastException);
    }

    /**
     * 判断异常是否可重试
     * - 超时、网络异常: 可重试
     * - 业务异常(余额不足、订单不存在等): 不可重试
     */
    private static boolean isRetryable(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage() : "";
        String className = e.getClass().getName();

        // 不可重试的业务异常
        if (className.contains("RuntimeException")) {
            return msg.contains("超时") || msg.contains("timeout") || msg.contains("连接");
        }
        if (className.contains("TimeoutException") || className.contains("ConnectException")) {
            return true;
        }
        return false;
    }
}
