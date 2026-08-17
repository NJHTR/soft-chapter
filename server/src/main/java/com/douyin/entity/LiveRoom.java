package com.douyin.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    // Streaming endpoints (populated when live starts)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String streamUrl;     // SRT/RTMP ingest URL for broadcaster
    private String playUrl;       // Playback URL for viewers (WebRTC/HLS)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String srtStreamId;   // SRT stream ID for ingest auth
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String rtmpStreamKey; // RTMP stream key (fallback)

    // Quality settings
    private Integer targetBitrate;    // Current target bitrate
    private Integer maxBitrate;       // Max bitrate
    private Integer minBitrate;       // Min bitrate
    private String encoderType;       // "nvenc", "x264", "x265"
    private String codec;             // "av1", "h265", "h264"
    private Boolean enableBeauty;     // Beauty filter enabled
    private Boolean enableHdr;        // HDR mode enabled

    // Engine stats (populated during live)
    private Double captureFps;
    private Double encodeFps;
    private Integer gpuUsage;
    private Integer cpuUsage;

    @TableLogic
    private Integer isDelete;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
