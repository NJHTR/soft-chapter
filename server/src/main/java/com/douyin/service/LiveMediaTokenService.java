package com.douyin.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Signs short-lived media capabilities without putting media bytes through
 * Spring. The token is carried as a query parameter and is checked by the
 * SRS callback endpoint before a WHIP/WHEP session is admitted.
 */
@Service
public class LiveMediaTokenService {

    public enum Purpose {
        INGEST,
        PLAY
    }

    public record IssuedToken(String value, long expiresAt) {
    }

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final boolean enabled;
    private final byte[] secret;
    private final long ttlSeconds;
    private final String callbackToken;
    private final Clock clock;

    @Autowired
    public LiveMediaTokenService(
            @Value("${live.media.auth.enabled:false}") boolean enabled,
            @Value("${live.media.auth.token-secret:}") String tokenSecret,
            @Value("${live.media.auth.token-ttl-seconds:300}") long ttlSeconds,
            @Value("${live.media.auth.srs-callback-token:}") String callbackToken) {
        this(enabled, tokenSecret, ttlSeconds, callbackToken, Clock.systemUTC());
    }

    public LiveMediaTokenService(
            boolean enabled,
            String tokenSecret,
            long ttlSeconds,
            String callbackToken,
            Clock clock) {
        this.enabled = enabled;
        String normalizedSecret = tokenSecret == null ? "" : tokenSecret.trim();
        if (enabled && normalizedSecret.length() < 32) {
            throw new IllegalStateException(
                    "LIVE_MEDIA_TOKEN_SECRET must contain at least 32 characters when media auth is enabled");
        }
        this.secret = normalizedSecret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = Math.max(30, Math.min(ttlSeconds, 3600));
        this.callbackToken = callbackToken == null ? "" : callbackToken.trim();
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isCallbackConfigured() {
        return enabled && !callbackToken.isBlank();
    }

    public IssuedToken issue(Long roomId, String streamKey, Long userId, Purpose purpose) {
        if (!enabled) return new IssuedToken("", 0);
        long expiresAt = Instant.now(clock).getEpochSecond() + ttlSeconds;
        String payload = String.join(
                "|",
                String.valueOf(roomId),
                streamKey,
                String.valueOf(userId),
                purpose.name(),
                String.valueOf(expiresAt));
        return new IssuedToken(sign(payload), expiresAt);
    }

    public boolean validate(
            String token,
            Long roomId,
            String streamKey,
            Purpose purpose) {
        return validate(token, roomId, streamKey, purpose, null);
    }

    public boolean validate(
            String token,
            Long roomId,
            String streamKey,
            Purpose purpose,
            Long expectedUserId) {
        if (!enabled || token == null || token.isBlank() || roomId == null || streamKey == null || purpose == null) {
            return false;
        }
        try {
            String[] parts = token.split("\\.", 2);
            if (parts.length != 2) return false;
            String payload = new String(DECODER.decode(parts[0]), StandardCharsets.UTF_8);
            String expectedSignature = signature(payload);
            if (!MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.US_ASCII),
                    parts[1].getBytes(StandardCharsets.US_ASCII))) {
                return false;
            }
            String[] claims = payload.split("\\|", -1);
            if (claims.length != 5) return false;
            if (!String.valueOf(roomId).equals(claims[0])
                    || !streamKey.equals(claims[1])
                    || !purpose.name().equals(claims[3])) {
                return false;
            }
            if (expectedUserId != null && !String.valueOf(expectedUserId).equals(claims[2])) {
                return false;
            }
            return Long.parseLong(claims[4]) > Instant.now(clock).getEpochSecond();
        } catch (RuntimeException e) {
            return false;
        }
    }

    public boolean validateCallbackToken(String candidate) {
        if (!isCallbackConfigured() || candidate == null) return false;
        return MessageDigest.isEqual(
                callbackToken.getBytes(StandardCharsets.UTF_8),
                candidate.trim().getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String payload) {
        String encodedPayload = ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encodedPayload + "." + signature(payload);
    }

    private String signature(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return ENCODER.encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            throw new IllegalStateException("media token signer unavailable", e);
        }
    }
}
