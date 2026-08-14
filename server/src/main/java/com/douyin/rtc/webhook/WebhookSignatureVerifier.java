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

/**
 * LiveKit webhook 签名校验 (rtc-media-adapter)。
 *
 * <p>生产首选 LiveKit 官方 {@code Authorization: Bearer <JWT>}：JWT 使用
 * LiveKit API secret 签名，{@code iss} 为 API key，{@code sha256} claim 为
 * 原始 body SHA-256 的 Base64 摘要。迁移期仍兼容旧的
 * {@code LiveKit-Signature: v0=<hex>} HMAC header；两种格式都 fail-closed。
 */
@Component
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
        String secret = properties.getLivekitWebhookSecret();
        String apiKey = properties.getLivekitApiKey();
        if (secret == null || secret.isBlank() || apiKey == null || apiKey.isBlank() || rawBody == null) {
            return false;
        }
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return false;
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
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
            return MessageDigest.isEqual(provided.getBytes(StandardCharsets.US_ASCII), expected);
        } catch (JwtException | IllegalArgumentException | java.security.GeneralSecurityException e) {
            return false;
        }
    }

    private boolean verifyLegacyHmac(String signatureHeader, byte[] rawBody) {
        String secret = properties.getLivekitWebhookSecret();
        if (secret == null || secret.isBlank() || rawBody == null) {
            return false;
        }
        if (signatureHeader == null || !signatureHeader.startsWith(PREFIX)) {
            return false;
        }
        String provided = signatureHeader.substring(PREFIX.length()).trim();
        if (provided.isEmpty()) {
            return false;
        }
        String expected = hmacSha256Hex(secret, rawBody);
        return MessageDigest.isEqual(
                provided.toLowerCase().getBytes(StandardCharsets.US_ASCII),
                expected.getBytes(StandardCharsets.US_ASCII));
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
