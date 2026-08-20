package com.douyin.rtc.p2p;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 审计脱敏与信令 HMAC 工具:原文永不入库/入日志,只保留摘要。 */
public final class P2pDigest {

    private P2pDigest() {
    }

    /** SHA-256 前 16 hex,为空返回空串 */
    public static String redact(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return sha256(value).substring(0, 16);
    }

    public static String redact(Long value) {
        return value == null ? "" : redact(String.valueOf(value));
    }

    public static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** envelope 签名载荷(server 与 client 必须一致) */
    public static String signingPayload(P2pSignalingEnvelope env) {
        return String.join("|",
                env.getVersion(),
                String.valueOf(env.getCallId()),
                String.valueOf(env.getTopologyGeneration()),
                String.valueOf(env.getFromUserId()),
                String.valueOf(env.getToUserId()),
                String.valueOf(env.getKind()),
                String.valueOf(env.getSeq()),
                env.getPayload() == null ? "" : env.getPayload());
    }

    /** 该 envelope 的持久化摘要(替代原文持久化) */
    public static String envelopeDigest(P2pSignalingEnvelope env) {
        return redact(env.getEventId()) + ":" + sha256(signingPayload(env)).substring(0, 16);
    }
}