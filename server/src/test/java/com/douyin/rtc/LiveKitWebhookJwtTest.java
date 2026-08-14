package com.douyin.rtc;

import com.douyin.rtc.provider.RtcProperties;
import com.douyin.rtc.webhook.WebhookSignatureVerifier;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/** 官方 LiveKit Authorization JWT webhook 契约测试。 */
class LiveKitWebhookJwtTest {

    private static final String API_KEY = "devkey";
    private static final String SECRET = "livekit-webhook-secret-01234567890123456789";
    private static final byte[] BODY = "{\"event\":\"room_started\",\"id\":\"evt-jwt-1\"}".getBytes(StandardCharsets.UTF_8);

    @Test
    void officialAuthorizationJwtPasses() throws Exception {
        WebhookSignatureVerifier verifier = verifier();
        String token = token(BODY, SECRET, API_KEY, System.currentTimeMillis() + 60_000);

        assertThat(verifier.verify("Bearer " + token, null, BODY)).isTrue();
    }

    @Test
    void tamperedBodyFailsEvenWithValidJwt() throws Exception {
        WebhookSignatureVerifier verifier = verifier();
        String token = token(BODY, SECRET, API_KEY, System.currentTimeMillis() + 60_000);

        assertThat(verifier.verify("Bearer " + token, null,
                "{\"event\":\"room_finished\"}".getBytes(StandardCharsets.UTF_8))).isFalse();
    }

    @Test
    void invalidAuthorizationDoesNotFallBackToLegacyHeader() throws Exception {
        WebhookSignatureVerifier verifier = verifier();
        String legacy = "v0=" + WebhookSignatureVerifier.hmacSha256Hex(SECRET, BODY);

        assertThat(verifier.verify("Bearer invalid", legacy, BODY)).isFalse();
    }

    private static WebhookSignatureVerifier verifier() {
        RtcProperties props = new RtcProperties();
        props.setLivekitApiKey(API_KEY);
        props.setLivekitWebhookSecret(SECRET);
        return new WebhookSignatureVerifier(props);
    }

    private static String token(byte[] body, String secret, String apiKey, long expiresAt) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(body);
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .issuer(apiKey)
                .subject("webhook")
                .issuedAt(new Date(now))
                .expiration(new Date(expiresAt))
                .claim("sha256", Base64.getEncoder().encodeToString(digest))
                .signWith(key)
                .compact();
    }
}
