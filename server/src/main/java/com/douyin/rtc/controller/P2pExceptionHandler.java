package com.douyin.rtc.controller;

import com.douyin.common.Result;
import com.douyin.rtc.domain.CallDomainException;
import com.douyin.rtc.domain.CallErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * P2P 控制面错误映射(仅作用于 P2pController,与 RtcExceptionHandler 同模式)。
 */
@Slf4j
@RestControllerAdvice(assignableTypes = P2pController.class)
public class P2pExceptionHandler {

    private static int httpStatus(CallErrorCode code) {
        switch (code) {
            case INVALID_STATE_TRANSITION:
            case GENERATION_STALE:
            case EVENT_DUPLICATE:
            case SEQ_OUT_OF_ORDER:
            case P2P_NOT_ELIGIBLE:
            case P2P_CONSENT_REQUIRED:
                return 409;
            case SESSION_NOT_FOUND:
                return 404;
            case NOT_AUTHORIZED:
            case NOT_INITIATOR:
            case NOT_TARGET:
                return 403;
            case P2P_CONSENT_EXPIRED:
                return 410;
            case INVALID_ARGUMENT:
                return 400;
            case RATE_LIMITED:
                return 429;
            case PROVIDER_ERROR:
                return 502;
            default:
                return 500;
        }
    }

    @ExceptionHandler(CallDomainException.class)
    public Result<?> handleDomain(CallDomainException ex) {
        return Result.fail(httpStatus(ex.getCode()), ex.getCode().name() + ": " + ex.getMessage());
    }

    @ExceptionHandler(NumberFormatException.class)
    public Result<?> handleNumberFormat(NumberFormatException ex) {
        return Result.fail(400, CallErrorCode.INVALID_ARGUMENT + ": 数字参数非法");
    }
}