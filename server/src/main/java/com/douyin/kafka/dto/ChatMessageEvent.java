package com.douyin.kafka.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kafka 消息体 — 聊天消息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageEvent {
    @JsonProperty("from_user_id")
    private Long fromUserId;

    @JsonProperty("to_user_id")
    private Long toUserId;

    private String content;

    @JsonProperty("msg_type")
    private Integer msgType;

    private String extra;

    private long timestamp;
}
