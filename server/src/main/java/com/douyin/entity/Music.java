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

    /** 鏉ユ簮: netease/local */
    private String source;

    /** 鏉ユ簮ID (缃戞槗浜戞瓕鏇睮D) */
    private Long sourceId;

    /** 鎾斁鍦板潃 */
    private String playUrl;

    /** 姝岃瘝 */
    private String lyric;

    private LocalDateTime createTime;

    /** 审核状态: PENDING/APPROVED/REJECTED */
    private String status;

    /** 审核意见/驳回原因 */
    private String reviewComment;

    /** 审核人ID */
    private Long reviewedBy;

    /** 审核时间 */
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
