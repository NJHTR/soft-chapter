package com.douyin.vo;

import com.douyin.entity.GroupMessage;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GroupMessageVO {
    private Long id;

    @JsonProperty("group_id")
    private Long groupId;

    @JsonProperty("from_user_id")
    private Long fromUserId;

    private String content;

    @JsonProperty("msg_type")
    private Integer msgType;

    private String extra;

    @JsonProperty("create_time")
    private LocalDateTime createTime;

    @JsonProperty("from_user")
    private UserVO fromUser;

    public static GroupMessageVO from(GroupMessage msg, UserVO fromUser) {
        GroupMessageVO vo = new GroupMessageVO();
        vo.id = msg.getId();
        vo.groupId = msg.getGroupId();
        vo.fromUserId = msg.getFromUserId();
        vo.content = msg.getContent();
        vo.msgType = msg.getMsgType();
        vo.extra = msg.getExtra();
        vo.createTime = msg.getCreateTime();
        vo.fromUser = fromUser;
        return vo;
    }
}
