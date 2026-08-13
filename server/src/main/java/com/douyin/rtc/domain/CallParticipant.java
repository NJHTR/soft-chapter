package com.douyin.rtc.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * RTC 通话参与者 (CALL_DOMAIN_MODEL.md §1)。
 * 成员列表来自服务端群组快照,不信任客户端 roster。
 */
@Data
@TableName("rtc_call_participant")
public class CallParticipant {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @JsonProperty("call_id")
    private String callId;

    @JsonProperty("user_id")
    private Long userId;

    /** initiator | member */
    private String role;

    /** ParticipantState 枚举名 */
    private String state;

    @JsonProperty("joined_at")
    private LocalDateTime joinedAt;

    @JsonProperty("left_at")
    private LocalDateTime leftAt;

    private String reason;

    @JsonProperty("profile_snapshot")
    private String profileSnapshot;

    @TableField(fill = FieldFill.INSERT)
    @JsonProperty("create_time")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonProperty("update_time")
    private LocalDateTime updateTime;
}