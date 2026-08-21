package com.douyin.kafka.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupMessageEvent {
    @JsonProperty("group_id")
    private Long groupId;

    @JsonProperty("from_user_id")
    private Long fromUserId;

    private String content;

    @JsonProperty("msg_type")
    private Integer msgType;

    private String extra;

    private long timestamp;

    /** 幂等事件 ID（可选；生产者未赋值时由发布器生成，消费端用于去重） */
    @JsonProperty("event_id")
    private String eventId;
}
