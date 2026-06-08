package com.douyin.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_group_read_cursor")
public class GroupReadCursor {
    @TableId(type = IdType.AUTO)
    private Long id;

    @JsonProperty("group_id")
    private Long groupId;

    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("last_read_msg_id")
    private Long lastReadMsgId;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonProperty("update_time")
    private LocalDateTime updateTime;
}
