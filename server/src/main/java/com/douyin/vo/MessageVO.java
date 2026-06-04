package com.douyin.vo;

import com.douyin.entity.Message;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageVO {
    private Long id;

    @JsonProperty("from_user_id")
    private Long fromUserId;

    @JsonProperty("to_user_id")
    private Long toUserId;

    private String content;

    @JsonProperty("msg_type")
    private Integer msgType;

    private String extra;

    @JsonProperty("is_read")
    private Integer isRead;

    @JsonProperty("create_time")
    private LocalDateTime createTime;

    @JsonProperty("from_user")
    private UserVO fromUser;

    public static MessageVO from(Message msg, UserVO fromUser) {
        MessageVO vo = new MessageVO();
        vo.id = msg.getId();
        vo.fromUserId = msg.getFromUserId();
        vo.toUserId = msg.getToUserId();
        vo.content = msg.getContent();
        vo.msgType = msg.getMsgType();
        vo.extra = msg.getExtra();
        vo.isRead = msg.getIsRead();
        vo.createTime = msg.getCreateTime();
        vo.fromUser = fromUser;
        return vo;
    }
}
