package com.douyin.rtc.service;

/**
 * 创建通话命令输入。scope 为 direct|group(live-interactive 预留);
 * client_request_id 是幂等键(≥8 位,与 signaling envelope 一致)。
 */
public record CreateCallCommand(
        Long initiatorId,
        String scope,
        Long targetUserId,
        Long groupId,
        String mode,
        String provider,
        String clientRequestId,
        String eventId,
        String traceId) {
}