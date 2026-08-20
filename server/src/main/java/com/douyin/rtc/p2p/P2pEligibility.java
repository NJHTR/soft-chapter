package com.douyin.rtc.p2p;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 服务端 P2P eligibility 规则(契约第 7 节):仅当全部条件满足才算 ELIGIBLE,
 * 否则保持 DISABLED 并给出拒绝原因(不产生 attempt)。
 */
public record P2pEligibility(boolean eligible, Map<String, String> failedReasons) {

    public static P2pEligibility granted() {
        return new P2pEligibility(true, Map.of());
    }

    public static P2pEligibility denied(String reason, String detail) {
        Map<String, String> reasons = new LinkedHashMap<>();
        reasons.put(reason, detail);
        return new P2pEligibility(false, reasons);
    }
}