package com.douyin.rtc.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/** Durable ringing/acceptance state for one user device in a call. */
@Data
@TableName("rtc_call_device")
public class CallDevice {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @JsonProperty("call_id")
    private String callId;

    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("device_id")
    private String deviceId;

    private String state;

    @JsonProperty("accepted_at")
    private LocalDateTime acceptedAt;

    @JsonProperty("rejected_at")
    private LocalDateTime rejectedAt;

    @JsonProperty("create_time")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @JsonProperty("update_time")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
