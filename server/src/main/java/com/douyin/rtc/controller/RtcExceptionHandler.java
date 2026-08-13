package com.douyin.rtc.controller;

import com.douyin.common.Result;
import com.douyin.rtc.domain.CallDomainException;
import com.douyin.rtc.domain.CallErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * RTC REST 错误处理: 只处理 {@link CallDomainException},
 * 映射为 {@link Result#fail(int, String)};更具体的异常由本 advice 优先于
 * GlobalExceptionHandler(RuntimeException) 生效。
 */
@Slf4j
@RestControllerAdvice(assignableTypes = RtcCallController.class)
public class RtcExceptionHandler {

    private static final Map<CallErrorCode, Integer> HTTP_CODES = Map.ofEntries(
            Map.entry(CallErrorCode.INVALID_STATE_TRANSITION, 409),
            Map.entry(CallErrorCode.SESSION_NOT_FOUND, 404),
            Map.entry(CallErrorCode.SESSION_ALREADY_EXISTS, 409),
            Map.entry(CallErrorCode.NOT_AUTHORIZED, 403),
            Map.entry(CallErrorCode.NOT_INITIATOR, 403),
            Map.entry(CallErrorCode.NOT_TARGET, 403),
            Map.entry(CallErrorCode.CALL_EXPIRED, 410),
            Map.entry(CallErrorCode.EVENT_DUPLICATE, 409),
            Map.entry(CallErrorCode.SEQ_OUT_OF_ORDER, 409),
            Map.entry(CallErrorCode.INVALID_ARGUMENT, 400),
            Map.entry(CallErrorCode.RATE_LIMITED, 429),
            Map.entry(CallErrorCode.PROVIDER_ERROR, 502));

    @ExceptionHandler(CallDomainException.class)
    public Result<?> handleCallDomain(CallDomainException e) {
        log.warn("[RTC-API] rejected: code={} msg={}", e.getCode(), e.getMessage());
        return Result.fail(HTTP_CODES.getOrDefault(e.getCode(), 400),
                e.getCode().name() + ": " + e.getMessage());
    }
}