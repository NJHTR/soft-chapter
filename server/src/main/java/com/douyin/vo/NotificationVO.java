package com.douyin.vo;

import com.douyin.entity.Notification;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NotificationVO {
    private Long id;

    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("from_user_id")
    private Long fromUserId;

    private Integer type;

    @JsonProperty("video_id")
    private Long videoId;

    @JsonProperty("comment_id")
    private Long commentId;

    private String content;

    @JsonProperty("is_read")
    private Integer isRead;

    @JsonProperty("create_time")
    private LocalDateTime createTime;

    @JsonProperty("from_user")
    private UserVO fromUser;

    public static final int TYPE_FOLLOW = 1;
    public static final int TYPE_LIKE = 2;
    public static final int TYPE_COMMENT = 3;
    public static final int TYPE_COLLECT = 4;
    public static final int TYPE_AT = 5;
    public static final int TYPE_FRIEND_REQUEST = 6;
    public static final int TYPE_FRIEND_ACCEPTED = 7;

    public static NotificationVO from(Notification n, UserVO fromUser) {
        NotificationVO vo = new NotificationVO();
        vo.id = n.getId();
        vo.userId = n.getUserId();
        vo.fromUserId = n.getFromUserId();
        vo.type = n.getType();
        vo.videoId = n.getVideoId();
        vo.commentId = n.getCommentId();
        vo.content = n.getContent();
        vo.isRead = n.getIsRead();
        vo.createTime = n.getCreateTime();
        vo.fromUser = fromUser;
        return vo;
    }
}
