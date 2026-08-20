package com.douyin.rtc.stage;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * 脱敏审计条目:目标与操作者 userId 以 SHA-256 前 16 hex 记录,
 * 保留相关性但不直出原始 ID(契约 §6 权限审计)。注意短哈希存在碰撞/字典风险,
 * 生产入库前应使用服务端密钥 HMAC(§6 风险记录)。
 */
public record StageAuditEntry(
        long liveId,
        String userIdRedacted,
        String actorUserIdRedacted,
        StageCommand command,
        String eventId,
        long generation,
        StageMemberStatus from,
        StageMemberStatus to,
        long atEpochMs) {

    private static final HexFormat HEX = HexFormat.of();

    public static String redact(long userId) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(("douyin:stage:" + userId).getBytes(StandardCharsets.UTF_8));
            return HEX.formatHex(digest, 0, 8);
        } catch (Exception e) {
            // 摘要不可用时退回可关联的短 id(审计不阻断状态推进)
            return "u" + Long.toHexString(userId);
        }
    }
}