package com.douyin.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_group_message")
public class GroupMessage {
    @TableId(type = IdType.AUTO)
    private Long id;

    @JsonProperty("group_id")
    private Long groupId;

    @JsonProperty("from_user_id")
    private Long fromUserId;

    private String content;

    @JsonProperty("msg_type")
    private Integer msgType;

    private String extra;

    @TableField(fill = FieldFill.INSERT)
    @JsonProperty("create_time")
    private LocalDateTime createTime;
}
