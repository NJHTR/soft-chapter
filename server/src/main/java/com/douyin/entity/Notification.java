package com.douyin.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_notification")
public class Notification {
    @TableId(type = IdType.ASSIGN_ID)
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

    @TableField(fill = FieldFill.INSERT)
    @JsonProperty("create_time")
    private LocalDateTime createTime;
}
