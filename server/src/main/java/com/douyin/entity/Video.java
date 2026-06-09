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

    /** 视频时长(�? */
    private Double duration;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public Double getDuration() {
        return duration;
    }

    public void setDuration(Double duration) {
        this.duration = duration;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public Long getAuthorUserId() {
        return authorUserId;
    }

    public void setAuthorUserId(Long authorUserId) {
        this.authorUserId = authorUserId;
    }

    public Long getMusicId() {
        return musicId;
    }

    public void setMusicId(Long musicId) {
        this.musicId = musicId;
    }

    public String getMusicTitle() {
        return musicTitle;
    }

    public void setMusicTitle(String musicTitle) {
        this.musicTitle = musicTitle;
    }

    public Double getBgmStartOffset() {
        return bgmStartOffset;
    }

    public void setBgmStartOffset(Double bgmStartOffset) {
        this.bgmStartOffset = bgmStartOffset;
    }

    public Double getBgmVolume() {
        return bgmVolume;
    }

    public void setBgmVolume(Double bgmVolume) {
        this.bgmVolume = bgmVolume;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(String imageUrls) {
        this.imageUrls = imageUrls;
    }

    public Long getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(Long likeCount) {
        this.likeCount = likeCount;
    }

    public Long getCommentCount() {
        return commentCount;
    }

    public void setCommentCount(Long commentCount) {
        this.commentCount = commentCount;
    }

    public Long getShareCount() {
        return shareCount;
    }

    public void setShareCount(Long shareCount) {
        this.shareCount = shareCount;
    }

    public Long getCollectCount() {
        return collectCount;
    }

    public void setCollectCount(Long collectCount) {
        this.collectCount = collectCount;
    }

    public Long getPlayCount() {
        return playCount;
    }

    public void setPlayCount(Long playCount) {
        this.playCount = playCount;
    }

    public Integer getIsDelete() {
        return isDelete;
    }

    public void setIsDelete(Integer isDelete) {
        this.isDelete = isDelete;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

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

    /** BGM起始偏移(�? */
    private Double bgmStartOffset;

    /** BGM音量 0-1, 默认0.7 */
    private Double bgmVolume;

    /** 视频类型: recommend-video, long-video, image, text �?*/
    private String type;

    /** 多图URL列表 (JSON数组字符�?, 用于图文轮播 */
    private String imageUrls;

    /** 点赞�?*/
    private Long likeCount;

    /** 评论�?*/
    private Long commentCount;

    /** 分享�?*/
    private Long shareCount;

    /** 收藏�?*/
    private Long collectCount;

    /** 播放�?*/
    private Long playCount;

    @TableLogic
    private Integer isDelete;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** ���״̬: PENDING/APPROVED/REJECTED */
    private String status;

    /** ������/����ԭ�� */
    private String reviewComment;

    /** �����ID */
    private Long reviewedBy;

    /** ���ʱ�� */
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
