package com.douyin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_video_exposure")
public class VideoExposure {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long videoId;

    @TableField("exposure_time")
    private LocalDateTime exposureTime;

    /** 停留秒数 (< 3 = 快速划过) */
    private Double swipeSeconds;

    /** 是否点击互动 */
    private Integer clicked;

    /** 流量来源: HOME_RECOMMEND/SEARCH/USER_PROFILE/FOLLOWING/HASHTAG/EXTERNAL/SHARE */
    private String trafficSource;

    /** 会话ID */
    private String sessionId;
}
