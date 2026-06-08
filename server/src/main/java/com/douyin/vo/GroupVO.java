package com.douyin.vo;

import com.douyin.entity.Group;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class GroupVO {
    private Long id;
    private String name;
    private String avatar;

    @JsonProperty("owner_uid")
    private Long ownerUid;

    @JsonProperty("member_count")
    private Integer memberCount;

    private String description;

    @JsonProperty("create_time")
    private LocalDateTime createTime;

    /** 未读消息数 */
    @JsonProperty("unread_count")
    private Long unreadCount;

    /** 最后一条消息 */
    @JsonProperty("last_message")
    private String lastMessage;

    @JsonProperty("last_msg_type")
    private Integer lastMsgType;

    @JsonProperty("last_time")
    private LocalDateTime lastTime;

    /** 群成员头像列表(最多9张), 用于前端拼接头像 */
    @JsonProperty("member_avatars")
    private List<String> memberAvatars;

    public static GroupVO from(Group group) {
        GroupVO vo = new GroupVO();
        vo.id = group.getId();
        vo.name = group.getName();
        vo.avatar = group.getAvatar();
        vo.ownerUid = group.getOwnerUid();
        vo.memberCount = group.getMemberCount();
        vo.description = group.getDescription();
        vo.createTime = group.getCreateTime();
        return vo;
    }
}
