package com.douyin.rtc.domain;

/**
 * 通话领域异常,携带 {@link CallErrorCode} 供上层(controller/websocket)
 * 映射为结构化错误响应。属于领域层,不依赖任何框架。
 */
public class CallDomainException extends RuntimeException {

    private final CallErrorCode code;

    public CallDomainException(CallErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public CallDomainException(CallErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public CallErrorCode getCode() {
        return code;
    }
}