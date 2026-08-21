package com.douyin.rtc.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.Instant;

/**
 * RTC 通话会话 (CALL_DOMAIN_MODEL.md §1)。rtc-persistence 真相源。
 */
@Data
@TableName("rtc_call_session")
public class CallSession {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @JsonProperty("call_id")
    private String callId;

    @JsonProperty("room_id")
    private String roomId;

    private String scope;

    private String mode;

    @JsonProperty("initiator_id")
    private Long initiatorId;

    private String provider;

    /** CallState 枚举名 */
    private String state;

    @JsonProperty("state_version")
    private Long stateVersion;

    @JsonProperty("client_request_id")
    private String clientRequestId;

    @JsonProperty("ring_at")
    private LocalDateTime ringAt;

    @JsonProperty("expires_at")
    private LocalDateTime expiresAt;

    @JsonProperty("connected_at")
    private LocalDateTime connectedAt;

    @JsonProperty("ended_at")
    private LocalDateTime endedAt;

    @JsonProperty("end_reason")
    private String endReason;

    @JsonProperty("trace_id")
    private String traceId;

    @TableField(fill = FieldFill.INSERT)
    @JsonProperty("create_time")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonProperty("update_time")
    private LocalDateTime updateTime;

    /** Serialization-time server clock; clients must not use Date.now() as authority. */
    @JsonProperty("server_now")
    public Instant serverNow() {
        return Instant.now();
    }
}
