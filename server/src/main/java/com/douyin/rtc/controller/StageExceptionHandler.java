package com.douyin.rtc.controller;

import com.douyin.common.Result;
import com.douyin.rtc.domain.CallDomainException;
import com.douyin.rtc.domain.CallErrorCode;
import com.douyin.rtc.stage.StageProviderException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Stage REST 错误处理:只处理 {@link StageController} 抛出的
 * {@link CallDomainException} 与 {@link StageProviderException}。
 */
@Slf4j
@RestControllerAdvice(assignableTypes = StageController.class)
public class StageExceptionHandler {

    private static final Map<CallErrorCode, Integer> HTTP_CODES = Map.ofEntries(
            Map.entry(CallErrorCode.INVALID_STATE_TRANSITION, 409),
            Map.entry(CallErrorCode.NOT_AUTHORIZED, 403),
            Map.entry(CallErrorCode.NOT_TARGET, 403),
            Map.entry(CallErrorCode.INVALID_ARGUMENT, 400),
            Map.entry(CallErrorCode.STAGE_LIMIT_REACHED, 409),
            Map.entry(CallErrorCode.GENERATION_STALE, 409),
            Map.entry(CallErrorCode.PROVIDER_ERROR, 502));

    @ExceptionHandler(CallDomainException.class)
    public Result<?> handleCallDomain(CallDomainException e) {
        log.warn("[STAGE-API] rejected: code={} msg={}", e.getCode(), e.getMessage());
        return Result.fail(HTTP_CODES.getOrDefault(e.getCode(), 400),
                e.getCode().name() + ": " + e.getMessage());
    }

    @ExceptionHandler(StageProviderException.class)
    public Result<?> handleProvider(StageProviderException e) {
        log.warn("[STAGE-API] provider error: {}", e.getMessage());
        return Result.fail(HTTP_CODES.getOrDefault(CallErrorCode.PROVIDER_ERROR, 502),
                CallErrorCode.PROVIDER_ERROR.name() + ": " + e.getMessage());
    }
}