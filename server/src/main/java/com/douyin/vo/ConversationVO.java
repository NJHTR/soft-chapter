package com.douyin.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationVO {

    @JsonProperty("target_user")
    private UserVO targetUser;

    @JsonProperty("last_message")
    private String lastMessage;

    @JsonProperty("last_msg_type")
    private Integer lastMsgType;

    @JsonProperty("last_time")
    private LocalDateTime lastTime;

    @JsonProperty("unread_count")
    private Long unreadCount;

    @JsonProperty("last_msg_from_user_id")
    private Long lastMsgFromUserId;

    @JsonProperty("last_msg_is_read")
    private Integer lastMsgIsRead;
}
