package com.douyin.rtc.webhook;

import com.douyin.rtc.provider.RtcProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * LiveKit webhook 签名校验 (rtc-media-adapter)。
 *
 * <p>算法: header {@code LiveKit-Signature: v0=<hex>} 中 hex 为
 * HMAC-SHA256(webhook-secret, 原始 body 字节) 的十六进制小写串;
 * 校验失败(缺 header/前缀不对/摘要不匹配)一律返回 false,
 * 用 {@link MessageDigest#isEqual} 常量时间比较防时序侧信道。
 */
@Component
public class WebhookSignatureVerifier {

    private static final String PREFIX = "v0=";
    private static final String HMAC_ALGO = "HmacSHA256";

    private final RtcProperties properties;

    public WebhookSignatureVerifier(RtcProperties properties) {
        this.properties = properties;
    }

    public boolean verify(String signatureHeader, byte[] rawBody) {
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