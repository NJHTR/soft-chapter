package com.douyin.rtc.provider;

/**
 * LiveKit token 签发结果(不包含任何日志敏感字段外的内容)。
 *
 * @param token        LiveKit AccessToken(JWS HS256)
 * @param roomName     服务端决定的媒体房间名(room_id 非空用之,否则确定性派生 callId)
 * @param callId       通话 ID
 * @param userId       参与者用户 ID(identity)
 * @param ttlSeconds   生效 TTL(秒)
 * @param expiresAt    epoch 秒过期时间
 */
public record TokenResult(
        String token,
        String roomName,
        String callId,
        Long userId,
        long ttlSeconds,
        long expiresAt) {
}