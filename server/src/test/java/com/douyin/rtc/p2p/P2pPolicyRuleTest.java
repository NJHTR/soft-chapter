package com.douyin.rtc.p2p;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class P2pPolicyRuleTest {

    private static Map<String, String> okContext() {
        Map<String, String> ctx = new HashMap<>();
        ctx.put(P2pPolicyRule.KEY_SCOPE, "direct");
        ctx.put(P2pPolicyRule.KEY_PARTICIPANT_COUNT, "2");
        ctx.put(P2pPolicyRule.KEY_CALL_STATE, "ACCEPTED");
        ctx.put(P2pPolicyRule.KEY_HAS_RECORDING, "false");
        ctx.put(P2pPolicyRule.KEY_HAS_MODERATION, "false");
        ctx.put(P2pPolicyRule.KEY_HAS_STAGE, "false");
        ctx.put(P2pPolicyRule.KEY_HAS_GROUP, "false");
        ctx.put(P2pPolicyRule.KEY_ACL_OK, "true");
        return ctx;
    }

    @Test
    void featureFlagDefaultsToFailClosed() {
        assertThat(P2pPolicyRule.evaluate(okContext()).eligible()).isFalse();
        assertThat(P2pPolicyRule.evaluate(null).eligible()).isFalse();
    }

    @Test
    void allConditionsSatisfiedIsEligible() {
        Map<String, String> ctx = okContext();
        ctx.put(P2pPolicyRule.KEY_FEATURE_ENABLED, "true");
        P2pEligibility e = P2pPolicyRule.evaluate(ctx);
        assertThat(e.eligible()).isTrue();
        assertThat(e.failedReasons()).isEmpty();
    }

    @Test
    void groupOrStageOrRecordingDenies() {
        String[][] cases = {
                {P2pPolicyRule.KEY_SCOPE, "group"},
                {P2pPolicyRule.KEY_HAS_GROUP, "true"},
                {P2pPolicyRule.KEY_HAS_STAGE, "true"},
                {P2pPolicyRule.KEY_HAS_RECORDING, "true"},
                {P2pPolicyRule.KEY_HAS_MODERATION, "true"},
                {P2pPolicyRule.KEY_ACL_OK, "false"},
                {P2pPolicyRule.KEY_PARTICIPANT_COUNT, "3"}
        };
        for (String[] c : cases) {
            Map<String, String> ctx = okContext();
            ctx.put(P2pPolicyRule.KEY_FEATURE_ENABLED, "true");
            ctx.put(c[0], c[1]);
            assertThat(P2pPolicyRule.evaluate(ctx).eligible())
                    .withFailMessage(c[0] + "=" + c[1] + " 必须拒绝")
                    .isFalse();
        }
    }

    @Test
    void connectingStateNotEligible() {
        Map<String, String> ctx = okContext();
        ctx.put(P2pPolicyRule.KEY_FEATURE_ENABLED, "true");
        ctx.put(P2pPolicyRule.KEY_CALL_STATE, "CONNECTED");
        assertThat(P2pPolicyRule.evaluate(ctx).eligible()).isFalse();
    }
}