package com.douyin.rtc.p2p;

import com.douyin.rtc.domain.CallDomainException;
import com.douyin.rtc.domain.CallErrorCode;

/**
 * 标准 ICE candidate type 分类与派生 outcome(契约第 7 节)。
 *
 * selected pair 仅当 local 与 remote 均非 relay 才使用 P2P:
 * host/host -> direct;任一 srflx|prflx -> srflx;任一 relay -> relay(回退 SFU)。
 * 未知/空 type 视为 relay(fail-closed)。
 */
public final class P2pCandidateClassification {

    public enum CandidateType {
        HOST, SRFLX, PRFLX, RELAY
    }

    public enum ProbeOutcome {
        DIRECT, SRFLX, RELAY, SFU
    }

    private P2pCandidateClassification() {
    }

    public static CandidateType classify(String raw) {
        if (raw == null) {
            return CandidateType.RELAY;
        }
        String t = raw.trim().toLowerCase();
        switch (t) {
            case "host":
                return CandidateType.HOST;
            case "srflx":
                return CandidateType.SRFLX;
            case "prflx":
                return CandidateType.PRFLX;
            case "relay":
                return CandidateType.RELAY;
            default:
                return CandidateType.RELAY;
        }
    }

    /** 派生初次尝试 outcome:仅双方均非 relay 才允许 P2P 连接。 */
    public static ProbeOutcome deriveProbeOutcome(String localType, String remoteType) {
        CandidateType local = classify(localType);
        CandidateType remote = classify(remoteType);
        if (local == CandidateType.RELAY || remote == CandidateType.RELAY) {
            return ProbeOutcome.RELAY;
        }
        if (local == CandidateType.HOST && remote == CandidateType.HOST) {
            return ProbeOutcome.DIRECT;
        }
        return ProbeOutcome.SRFLX;
    }

    /** 稳定回退原因:relay 出现时统一为 RELAY_REQUIRED,否则按情况细分。 */
    public static P2pFallbackReason fallbackReasonFor(ProbeOutcome outcome) {
        switch (outcome) {
            case RELAY:
                return P2pFallbackReason.RELAY_REQUIRED;
            case SFU:
                return P2pFallbackReason.TURN_ONLY;
            default:
                throw new CallDomainException(CallErrorCode.INVALID_ARGUMENT,
                        "仅 relay/sfu outcome 可映射回退原因: " + outcome);
        }
    }
}