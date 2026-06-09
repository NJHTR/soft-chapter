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

    /** BGM起始偏移(秒) */
    private Double bgmStartOffset;

    /** BGM音量 0-1, 默认0.7 */
    private Double bgmVolume;

    /** 视频类型: recommend-video, long-video, image, text 等 */
    private String type;

    /** 多图URL列表 (JSON数组字符串), 用于图文轮播 */
    private String imageUrls;

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
    /** Review status: PENDING/APPROVED/REJECTED */
    private String status;

    /** Review comment / reject reason */
    private String reviewComment;

    /** Reviewer user ID */
    private Long reviewedBy;

    /** Review timestamp */
    private LocalDateTime reviewTime;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReviewComment() {
        return reviewComment;
    }

    public void setReviewComment(String reviewComment) {
        this.reviewComment = reviewComment;
    }

    public Long getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(Long reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public LocalDateTime getReviewTime() {
        return reviewTime;
    }

    public void setReviewTime(LocalDateTime reviewTime) {
        this.reviewTime = reviewTime;
    }
}
