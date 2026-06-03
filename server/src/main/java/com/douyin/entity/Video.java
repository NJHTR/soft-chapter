package com.douyin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_video")
public class Video {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 视频地址 */
    private String videoUrl;

    /** 封面地址 */
    private String coverUrl;

    /** 标题/描述 */
    @TableField("`desc`")
    private String desc;

    /** 视频时长(秒) */
    private Double duration;

    /** 宽度 */
    private Integer width;

    /** 高度 */
    private Integer height;

    /** 作者ID */
    private Long authorUserId;

    /** 音乐ID */
    private Long musicId;

    /** 音乐标题 */
    private String musicTitle;

    /** 视频类型: recommend-video, long-video 等 */
    private String type;

    /** 点赞数 */
    private Long likeCount;

    /** 评论数 */
    private Long commentCount;

    /** 分享数 */
    private Long shareCount;

    /** 收藏数 */
    private Long collectCount;

    /** 播放数 */
    private Long playCount;

    @TableLogic
    private Integer isDelete;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
