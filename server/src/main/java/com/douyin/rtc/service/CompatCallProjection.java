package com.douyin.rtc.service;

import com.douyin.entity.Message;
import com.douyin.rtc.domain.CallJson;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 旧消息兼容投影(t_message msg_type=10/11)的查询视图。
 * callState: 0=未接通 1=已接通 2=已结束; duration 单位为秒。
 * call_id 从 extra JSON 中读取;若历史数据缺失则回填(查询投影兜底)。
 */
public record CompatCallProjection(
        Long messageId,
        Long fromUserId,
        Long toUserId,
        Integer msgType,
        Integer callState,
        Long duration,
        String callId,
        String content,
        LocalDateTime createTime) {

    public static CompatCallProjection from(Message message, String fallbackCallId) {
        Map<String, Object> extra = CallJson.read(message.getExtra());
        String callId = CallJson.stringField(extra, "call_id");
        if (callId == null || callId.isBlank()) {
            callId = fallbackCallId;
        }
        return new CompatCallProjection(
                message.getId(),
                message.getFromUserId(),
                message.getToUserId(),
                message.getMsgType(),
                CallJson.intField(extra, "callState", 0),
                CallJson.longField(extra, "duration", 0),
                callId,
                message.getContent(),
                message.getCreateTime());
    }
}