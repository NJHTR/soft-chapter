package com.douyin.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 群聊会话列表项 (前端消息页"群聊"列表)
 */
@Data
public class GroupConversationVO {
    @JsonProperty("group_id")
    private Long groupId;

    @JsonProperty("group_name")
    private String groupName;

    @JsonProperty("group_avatar")
    private String groupAvatar;

    @JsonProperty("member_count")
    private Integer memberCount;

    @JsonProperty("last_message")
    private String lastMessage;

    @JsonProperty("last_msg_type")
    private Integer lastMsgType;

    @JsonProperty("last_time")
    private LocalDateTime lastTime;

    @JsonProperty("unread_count")
    private Long unreadCount;

    /** 群成员头像列表(最多9张), 用于前端拼接头像 */
    @JsonProperty("member_avatars")
    private List<String> memberAvatars;
}
