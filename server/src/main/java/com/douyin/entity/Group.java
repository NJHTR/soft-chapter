package com.douyin.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_group")
public class Group {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String avatar;

    @JsonProperty("owner_uid")
    private Long ownerUid;

    @JsonProperty("member_count")
    private Integer memberCount;

    private String description;

    @TableField(fill = FieldFill.INSERT)
    @JsonProperty("create_time")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonProperty("update_time")
    private LocalDateTime updateTime;
}
