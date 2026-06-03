package com.douyin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_follow")
public class Follow {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long followId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
