package com.douyin.rtc.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * RTC 通话事件账本 (CALL_DOMAIN_MODEL.md §1)。
 * event_id 全局幂等;同一 (call_id, participant_id) 的 seq 单调递增。
 * participant_id 为事件触发者的 user_id,0 表示系统事件。
 */
@Data
@TableName("rtc_call_event")
public class CallEvent {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @JsonProperty("event_id")
    private String eventId;

    @JsonProperty("call_id")
    private String callId;

    @JsonProperty("participant_id")
    private Long participantId;

    /** CallEventKind.wire 值 */
    private String kind;

    private Long seq;

    @JsonProperty("event_version")
    private Long eventVersion;

    @JsonProperty("occurred_at")
    private LocalDateTime occurredAt;

    private String payload;

    @JsonProperty("trace_id")
    private String traceId;
}
