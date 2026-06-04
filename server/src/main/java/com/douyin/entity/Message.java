package com.douyin.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_message")
public class Message {
    @TableId(type = IdType.ASSIGN_ID)
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

    @TableField(fill = FieldFill.INSERT)
    @JsonProperty("create_time")
    private LocalDateTime createTime;
}
