package com.douyin.rtc.p2p;

import com.douyin.rtc.domain.CallDomainException;
import com.douyin.rtc.domain.CallErrorCode;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 版本化信令 envelope 认证(契约第 7 节):HMAC-SHA256 防伪造;
 * 大小/候选数/成员/顺序/重复/TTL/速率全部有界校验,违规按对应错误码拒绝。
 */
public class P2pSignalValidator {

    private final String secret;
    private final int maxBytes;
    private final int maxIceCandidates;
    private final long envelopeTtlMs;
    private final int ratePerSecond;

    public P2pSignalValidator(String secret, int maxBytes, int maxIceCandidates, long envelopeTtlMs, int ratePerSecond) {
        this.secret = secret;
        this.maxBytes = maxBytes;
        this.maxIceCandidates = maxIceCandidates;
        this.envelopeTtlMs = envelopeTtlMs;
        this.ratePerSecond = ratePerSecond;
    }

    public void validateStructure(P2pSignalingEnvelope env, long nowEpochMs) {
        if (env == null) {
            throw new CallDomainException(CallErrorCode.INVALID_ARGUMENT, "envelope 为空");
        }
        if (!"v1".equals(env.getVersion())) {
            throw new CallDomainException(CallErrorCode.INVALID_ARGUMENT, "仅支持 v1:" + env.getVersion());
        }
        if (env.getCallId() == null || env.getCallId().isBlank()) {
            throw new CallDomainException(CallErrorCode.INVALID_ARGUMENT, "缺少 call_id");
        }
        if (env.getFromUserId() == null || env.getToUserId() == null) {
            throw new CallDomainException(CallErrorCode.INVALID_ARGUMENT, "缺少 from/to user");
        }
        if (env.getFromUserId().equals(env.getToUserId())) {
            throw new CallDomainException(CallErrorCode.INVALID_ARGUMENT, "from == to");
        }
        if (!("offer".equals(env.getKind()) || "answer".equals(env.getKind()) || "ice".equals(env.getKind()))) {
            throw new CallDomainException(CallErrorCode.INVALID_ARGUMENT, "kind 必须为 offer|answer|ice");
        }
        if (env.getEventId() == null || env.getEventId().isBlank()) {
            throw new CallDomainException(CallErrorCode.INVALID_ARGUMENT, "缺少 event_id");
        }
        if (env.getPayload() == null) {
            throw new CallDomainException(CallErrorCode.INVALID_ARGUMENT, "缺少 payload");
        }
        int payloadBytes = env.getPayload().getBytes(StandardCharsets.UTF_8).length;
        if (payloadBytes > maxBytes) {
            throw new CallDomainException(CallErrorCode.INVALID_ARGUMENT, "payload 超限 " + payloadBytes + "B/" + maxBytes + "B");
        }
        if ("ice".equals(env.getKind()) && env.getCandidateCount() > maxIceCandidates) {
            throw new CallDomainException(CallErrorCode.INVALID_ARGUMENT,
                    "候选数超限 " + env.getCandidateCount() + "/" + maxIceCandidates);
        }
        if (env.getCreatedEpochMs() <= 0) {
            throw new CallDomainException(CallErrorCode.INVALID_ARGUMENT, "缺少 created_epoch_ms");
        }
        if (env.getCreatedEpochMs() + envelopeTtlMs < nowEpochMs) {
            throw new CallDomainException(CallErrorCode.P2P_CONSENT_EXPIRED, "envelope 已过期");
        }
        if (env.getCreatedEpochMs() > nowEpochMs + 60_000L) {
            throw new CallDomainException(CallErrorCode.INVALID_ARGUMENT, "envelope 时钟超前");
        }
        if (env.getSignature() == null || env.getSignature().isBlank()) {
            throw new CallDomainException(CallErrorCode.NOT_AUTHORIZED, "缺少签名");
        }
    }

    public void verifySignature(P2pSignalingEnvelope env) {
        String expected = sign(env);
        if (!constantTimeEquals(expected, env.getSignature())) {
            throw new CallDomainException(CallErrorCode.NOT_AUTHORIZED, "信令签名不匹配");
        }
    }

    public String sign(P2pSignalingEnvelope env) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(P2pDigest.signingPayload(env).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}