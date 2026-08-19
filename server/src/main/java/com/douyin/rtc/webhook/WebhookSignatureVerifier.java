package com.douyin.rtc.webhook;

import com.douyin.rtc.provider.RtcProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

/**
 * LiveKit webhook 签名校验 (rtc-media-adapter)。
 *
 * <p>生产首选 LiveKit 官方 {@code Authorization: Bearer <JWT>}：JWT 使用
 * LiveKit API secret 签名，{@code iss} 为 API key，{@code sha256} claim 为
 * 原始 body SHA-256 的 Base64 摘要。迁移期仍兼容旧的
 * {@code LiveKit-Signature: v0=<hex>} HMAC header；两种格式都 fail-closed。
 */
@Component
@Slf4j
public class WebhookSignatureVerifier {

    private static final String PREFIX = "v0=";
    private static final String HMAC_ALGO = "HmacSHA256";
    private static final String BEARER_PREFIX = "Bearer ";

    private final RtcProperties properties;

    public WebhookSignatureVerifier(RtcProperties properties) {
        this.properties = properties;
    }

    public boolean verify(String signatureHeader, byte[] rawBody) {
        return verifyLegacyHmac(signatureHeader, rawBody);
    }

    /**
     * 验证官方 LiveKit webhook。Authorization 存在但无效时不能回退到旧 header，
     * 避免攻击者利用兼容路径绕过官方签名。
     */
    public boolean verify(String authorizationHeader, String legacySignatureHeader, byte[] rawBody) {
        if (authorizationHeader != null && !authorizationHeader.isBlank()) {
            return verifyAuthorization(authorizationHeader, rawBody);
        }
        return verifyLegacyHmac(legacySignatureHeader, rawBody);
    }

    public boolean verifyAuthorization(String authorizationHeader, byte[] rawBody) {
        String secret = webhookSecret();
        String apiKey = properties.getLivekitApiKey();
        if (secret == null || secret.isBlank() || apiKey == null || apiKey.isBlank() || rawBody == null) {
            log.warn("[RTC-WEBHOOK] verification unavailable: secret={}, apiKey={}, body={}",
                    configured(secret), configured(apiKey), rawBody != null ? "present" : "missing");
            return false;
        }
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            log.warn("[RTC-WEBHOOK] Authorization header is missing or does not use Bearer");
            return false;
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            log.warn("[RTC-WEBHOOK] Authorization Bearer token is empty");
            return false;
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                    .requireIssuer(apiKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String provided = claims.get("sha256", String.class);
            if (provided == null || provided.isBlank()) return false;
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawBody);
            byte[] expected = Base64.getEncoder().encode(digest);
            boolean valid = MessageDigest.isEqual(provided.getBytes(StandardCharsets.US_ASCII), expected);
            if (!valid) {
                log.warn("[RTC-WEBHOOK] sha256 claim does not match request body");
            }
            return valid;
        } catch (JwtException | IllegalArgumentException | java.security.GeneralSecurityException e) {
            log.warn("[RTC-WEBHOOK] Authorization JWT rejected: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    private boolean verifyLegacyHmac(String signatureHeader, byte[] rawBody) {
        String secret = webhookSecret();
        if (secret == null || secret.isBlank() || rawBody == null) {
            log.warn("[RTC-WEBHOOK] legacy verification unavailable: secret={}, body={}",
                    configured(secret), rawBody != null ? "present" : "missing");
            return false;
        }
        if (signatureHeader == null || !signatureHeader.startsWith(PREFIX)) {
            log.warn("[RTC-WEBHOOK] legacy signature header is missing or malformed");
            return false;
        }
        String provided = signatureHeader.substring(PREFIX.length()).trim();
        if (provided.isEmpty()) {
            return false;
        }
        String expected = hmacSha256Hex(secret, rawBody);
        boolean valid = MessageDigest.isEqual(
                provided.toLowerCase().getBytes(StandardCharsets.US_ASCII),
                expected.getBytes(StandardCharsets.US_ASCII));
        if (!valid) {
            log.warn("[RTC-WEBHOOK] legacy HMAC does not match request body");
        }
        return valid;
    }

    /** Use an explicit webhook secret when configured, otherwise the LiveKit API secret. */
    private String webhookSecret() {
        String configured = properties.getLivekitWebhookSecret();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return properties.getLivekitApiSecret();
    }

    private static String configured(String value) {
        return value == null || value.isBlank() ? "missing" : "configured";
    }

    public static String hmacSha256Hex(String secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] digest = mac.doFinal(body);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 不可用: " + e.getMessage(), e);
        }
    }
}
