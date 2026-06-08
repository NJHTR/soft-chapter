package com.douyin.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_group_member")
public class GroupMember {
    @TableId(type = IdType.AUTO)
    private Long id;

    @JsonProperty("group_id")
    private Long groupId;

    @JsonProperty("user_id")
    private Long userId;

    /** 0普通成员 1群主 2管理员 */
    private Integer role;

    /** 群内昵称 */
    private String nickname;

    /** 0正常 1禁言 */
    @JsonProperty("is_muted")
    private Integer isMuted;

    @TableField(fill = FieldFill.INSERT)
    @JsonProperty("join_time")
    private LocalDateTime joinTime;
}
