package com.douyin.rtc.p2p;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * P2P eligibility 纯规则评估:输入为业务调用上下文(快照由 CallService 提供),
 * 输出 eligible 或首个拒绝原因。规则本身不依赖 DB/provider,便于契约级单测。
 */
public final class P2pPolicyRule {

    public static final String KEY_SCOPE = "scope";
    public static final String KEY_PARTICIPANT_COUNT = "participant_count";
    public static final String KEY_CALL_STATE = "call_state";
    public static final String KEY_HAS_RECORDING = "has_recording";
    public static final String KEY_HAS_MODERATION = "has_moderation";
    public static final String KEY_HAS_STAGE = "has_stage";
    public static final String KEY_HAS_GROUP = "has_group";
    public static final String KEY_FEATURE_ENABLED = "feature_enabled";
    public static final String KEY_ACL_OK = "acl_ok";

    private P2pPolicyRule() {
    }

    /**
     * 评估当前通话是否可产生 P2P attempt。所有必须条件缺一不可;
     * feature flag 默认关闭(fail-closed)。
     */
    public static P2pEligibility evaluate(Map<String, String> context) {
        if (context == null) {
            return P2pEligibility.denied(KEY_FEATURE_ENABLED, "flag disabled: RTC_P2P_ENABLED=false");
        }
        if (!"true".equalsIgnoreCase(context.get(KEY_FEATURE_ENABLED))) {
            return P2pEligibility.denied(KEY_FEATURE_ENABLED, "flag disabled: RTC_P2P_ENABLED=false (fail-closed)");
        }
        if (!"direct".equalsIgnoreCase(context.get(KEY_SCOPE))) {
            return P2pEligibility.denied(KEY_SCOPE, "scope must be direct, got: " + context.get(KEY_SCOPE));
        }
        if (!"2".equals(context.get(KEY_PARTICIPANT_COUNT))) {
            return P2pEligibility.denied(KEY_PARTICIPANT_COUNT, "exactly two valid participants required, got: " + context.get(KEY_PARTICIPANT_COUNT));
        }
        String state = context.get(KEY_CALL_STATE);
        if (!("ACCEPTED".equalsIgnoreCase(state) || "NEGOTIATING".equalsIgnoreCase(state))) {
            return P2pEligibility.denied(KEY_CALL_STATE, "business state must be ACCEPTED/NEGOTIATING, got: " + state);
        }
        if (Boolean.parseBoolean(context.get(KEY_HAS_RECORDING))) {
            return P2pEligibility.denied(KEY_HAS_RECORDING, "recording in progress");
        }
        if (Boolean.parseBoolean(context.get(KEY_HAS_MODERATION))) {
            return P2pEligibility.denied(KEY_HAS_MODERATION, "moderation active");
        }
        if (Boolean.parseBoolean(context.get(KEY_HAS_STAGE))) {
            return P2pEligibility.denied(KEY_HAS_STAGE, "stage room in use");
        }
        if (Boolean.parseBoolean(context.get(KEY_HAS_GROUP))) {
            return P2pEligibility.denied(KEY_HAS_GROUP, "group call, not direct");
        }
        if (!"true".equalsIgnoreCase(context.getOrDefault(KEY_ACL_OK, "false"))) {
            return P2pEligibility.denied(KEY_ACL_OK, "ACL/token not valid");
        }
        return P2pEligibility.granted();
    }

    /** 便捷构造:基于字段构造 context 快照(供 CallService 适配与单测使用)。 */
    public static Map<String, String> context(boolean featureEnabled, String scope, int participantCount,
                                              String callState, boolean hasRecording, boolean hasModeration,
                                              boolean hasStage, boolean hasGroup, boolean aclOk) {
        Map<String, String> ctx = new ConcurrentHashMap<>();
        ctx.put(KEY_FEATURE_ENABLED, String.valueOf(featureEnabled));
        ctx.put(KEY_SCOPE, scope);
        ctx.put(KEY_PARTICIPANT_COUNT, String.valueOf(participantCount));
        ctx.put(KEY_CALL_STATE, callState);
        ctx.put(KEY_HAS_RECORDING, String.valueOf(hasRecording));
        ctx.put(KEY_HAS_MODERATION, String.valueOf(hasModeration));
        ctx.put(KEY_HAS_STAGE, String.valueOf(hasStage));
        ctx.put(KEY_HAS_GROUP, String.valueOf(hasGroup));
        ctx.put(KEY_ACL_OK, String.valueOf(aclOk));
        return ctx;
    }
}