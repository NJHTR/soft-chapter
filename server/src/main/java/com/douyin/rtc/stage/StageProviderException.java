package com.douyin.rtc.stage;

import com.douyin.rtc.domain.CallDomainException;
import com.douyin.rtc.domain.CallErrorCode;

/** Stage provider 操作失败(映射 502 PROVIDER_ERROR)。 */
public class StageProviderException extends RuntimeException {

    public StageProviderException(String message) {
        super(message);
    }

    public StageProviderException(String message, Throwable cause) {
        super(message, cause);
    }

    public CallDomainException asDomain() {
        return new CallDomainException(CallErrorCode.PROVIDER_ERROR, getMessage());
    }
}