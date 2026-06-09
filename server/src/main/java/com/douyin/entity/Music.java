package com.douyin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_music")
public class Music {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String artist;
    private String album;
    private String coverUrl;
    private Integer duration;

    /** 来源: netease/local */
    private String source;

    /** 来源ID (网易云歌曲ID) */
    private Long sourceId;

    /** 播放地址 */
    private String playUrl;

    /** 歌词 */
    private String lyric;

    private LocalDateTime createTime;
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
