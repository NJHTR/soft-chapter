package com.douyin.rtc.p2p;

import java.util.List;

/** 拓扑状态视图(控制器返回,不含任何敏感原文)。 */
public record P2pStatusView(String callId, long topologyGeneration, String status, boolean eligible,
                            List<String> eligibilityReasons, String consentState, String fallbackReason,
                            long probeDeadlineEpochMs) {
}