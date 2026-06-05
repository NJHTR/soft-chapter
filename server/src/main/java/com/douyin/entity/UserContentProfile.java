package com.douyin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_user_content_profile")
public class UserContentProfile {

    @TableId(type = IdType.INPUT)
    private Long userId;

    // ===== 兴趣向量 =====

    /** 内容偏好向量 (融合, 512维 JSON) */
    private String contentVector;

    /** 短期兴趣向量 (24h EMA, JSON) */
    private String shortTermVector;

    /** 长期兴趣向量 (30d EMA, JSON) */
    private String longTermVector;

    /** 品类偏好 JSON: {"美食":0.85,"旅行":0.6} */
    private String categoryWeights;

    // ===== 创作者亲和力 =====

    /** 创作者亲和力 JSON: {authorId: score} */
    private String creatorAffinity;

    // ===== 内容偏好 =====

    private Double preferredDurationMin;
    private Double preferredDurationMax;
    private Double avgCompletionRate;

    private Double preferredBpmMin;
    private Double preferredBpmMax;
    private Double preferredEnergy;

    // ===== 行为统计 =====

    private Double avgWatchDuration;
    private Double bounceRate;
    private Double likeRate;
    private Double collectRate;
    private Double shareRate;
    private Double commentRate;
    private Double repeatViewRate;

    private Integer totalWatchCount;
    private Integer totalInteractCount;
    private Integer totalViewCount;
    private Integer totalLikeCount;
    private Integer totalCollectCount;
    private Integer totalShareCount;
    private Integer totalCommentCount;
    private Integer totalSearchCount;
    private Long totalWatchTimeSec;

    // ===== 社交特征 =====

    private Double socialEngagementRate;
    private Integer profileVisitCount;

    // ===== 搜索特征 =====

    private Double searchFrequency;
    private String recentSearchQueries;

    // ===== 流量来源偏好 =====

    /** 流量来源分布 JSON: {"HOME_RECOMMEND":0.6,"SEARCH":0.2,...} */
    private String trafficSources;

    // ===== 时间模式 =====

    /** 活跃时段分布 JSON [0-23] */
    private String activeHours;

    private Double avgSessionDuration;
    private Integer activeDaysLastWeek;

    // ===== 用户分类 =====

    /** 用户类型: passive_consumer/social_butterfly/power_liker/collector/active_searcher/creator_fan/explorer/balanced */
    private String userType;

    /** 用户分层: new_user/light/medium/heavy */
    private String userSegment;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
