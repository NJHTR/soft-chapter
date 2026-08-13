package com.douyin.rtc;

import com.douyin.rtc.provider.RtcProperties;
import com.douyin.rtc.webhook.WebhookSignatureVerifier;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 签名算法向量: 固定 secret + body 的期望 hex 硬编码在此,防算法漂移
 * (任何人改动 HMAC 算法/编码方式都会破坏本测试)。
 * 向量由独立实现计算: HMAC-SHA256(secret, body 的 UTF-8 字节) 的十六进制小写。
 */
class WebhookSignatureVectorTest {

    private static final String SECRET = "whsec-unit-test-0123456789abcdefghij";
    private static final String BODY =
            "{\"event\":\"room_started\",\"id\":\"evt-vector-0001\",\"room\":{\"name\":\"call-vector-0001\"}}";
    private static final String EXPECTED_HEX =
            "84cb1d161173c028a94f3ba7972028d7d79aeec7b50f82c1689f820b6a417256";

    @Test
    void vectorPreventsAlgorithmDrift() {
        String hex = WebhookSignatureVerifier.hmacSha256Hex(SECRET, BODY.getBytes(StandardCharsets.UTF_8));

        assertThat(hex).isEqualTo(EXPECTED_HEX);
    }

    @Test
    void vectorSignatureVerifiedThroughVerifier() {
        RtcProperties props = new RtcProperties();
        props.setLivekitWebhookSecret(SECRET);
        WebhookSignatureVerifier verifier = new WebhookSignatureVerifier(props);

        assertThat(verifier.verify("v0=" + EXPECTED_HEX, BODY.getBytes(StandardCharsets.UTF_8))).isTrue();
    }
}