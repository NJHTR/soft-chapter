package com.douyin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_live_room")
public class LiveRoom {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long hostUserId;
    private String title;
    private String coverUrl;
    private String status;   // PREVIEW / LIVE / ENDED
    private Integer viewerCount;
    private Integer totalViewers;
    private Integer likeCount;
    private String streamUrl;
    private String playUrl;

    @TableLogic
    private Integer isDelete;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
