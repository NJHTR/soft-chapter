package com.douyin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_video_collect")
public class VideoCollect {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long videoId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
