package com.douyin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_watch_history")
public class WatchHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long videoId;
    private Long authorUserId;
    private Double watchDuration;
    private Double videoDuration;
    private Integer finished;

    /** 重复观看次数 */
    private Integer repeatCount;

    /** 流量来源 */
    private String trafficSource;

    /** 会话ID */
    private String sessionId;

    /** 划走耗时(秒) */
    private Double swipeSeconds;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;
}
