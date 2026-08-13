package com.douyin.rtc;

import com.douyin.rtc.provider.RtcProperties;
import com.douyin.rtc.webhook.WebhookSignatureVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LiveKit webhook 签名契约: 正确签名通过,篡改 body / 篡改 header /
 * 空 header / 前缀错误一律失败(宪法 §2.7 安全)。纯 JUnit5。
 */
class WebhookSignatureTest {

    private static final String SECRET = "whsec-unit-test-0123456789abcdefghij";
    private static final String BODY = "{\"event\":\"participant_left\",\"id\":\"evt-0001\",\"room\":{\"name\":\"call-1\"}}";

    private WebhookSignatureVerifier verifier;

    @BeforeEach
    void setUp() {
        RtcProperties props = new RtcProperties();
        props.setLivekitWebhookSecret(SECRET);
        verifier = new WebhookSignatureVerifier(props);
    }

    @Test
    void validSignaturePasses() {
        String sig = "v0=" + WebhookSignatureVerifier.hmacSha256Hex(SECRET, body(BODY));

        assertThat(verifier.verify(sig, body(BODY))).isTrue();
    }

    @Test
    void tamperedBodyFails() {
        String sig = "v0=" + WebhookSignatureVerifier.hmacSha256Hex(SECRET, body(BODY));

        assertThat(verifier.verify(sig, body(BODY.replace("evt-0001", "evt-9999")))).isFalse();
    }

    @Test
    void tamperedHeaderFails() {
        String sig = "v0=" + WebhookSignatureVerifier.hmacSha256Hex(SECRET, body(BODY));

        assertThat(verifier.verify(sig.substring(0, sig.length() - 3) + "abc", body(BODY))).isFalse();
    }

    @Test
    void emptyOrMissingHeaderFails() {
        assertThat(verifier.verify(null, body(BODY))).isFalse();
        assertThat(verifier.verify("", body(BODY))).isFalse();
        assertThat(verifier.verify("v0=", body(BODY))).isFalse();
    }

    @Test
    void wrongPrefixFails() {
        String sig = "v0=" + WebhookSignatureVerifier.hmacSha256Hex(SECRET, body(BODY));

        assertThat(verifier.verify("v1=" + sig.substring(3), body(BODY))).isFalse();
    }

    @Test
    void wrongSecretFails() {
        String sig = "v0=" + WebhookSignatureVerifier.hmacSha256Hex("other-secret-value-0000000000", body(BODY));

        assertThat(verifier.verify(sig, body(BODY))).isFalse();
    }

    @Test
    void unconfiguredSecretFailsClosed() {
        RtcProperties empty = new RtcProperties();
        WebhookSignatureVerifier strict = new WebhookSignatureVerifier(empty);
        String sig = "v0=" + WebhookSignatureVerifier.hmacSha256Hex(SECRET, body(BODY));

        assertThat(strict.verify(sig, body(BODY))).isFalse();
    }

    private static byte[] body(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}