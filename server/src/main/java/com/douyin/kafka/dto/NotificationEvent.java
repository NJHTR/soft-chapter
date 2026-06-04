package com.douyin.kafka.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kafka 消息体 — 互动通知事件（关注/点赞/评论/收藏/@提及）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {
    @JsonProperty("user_id")
    private Long userId;           // 通知接收者

    @JsonProperty("from_user_id")
    private Long fromUserId;      // 触发者

    private Integer type;          // 1关注 2点赞 3评论 4收藏 5@提及

    @JsonProperty("video_id")
    private Long videoId;

    @JsonProperty("comment_id")
    private Long commentId;

    private String content;       // 通知摘要

    private long timestamp;
}
