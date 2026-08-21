package com.douyin.kafka.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 视频互动事件 — 播放/点赞/收藏/取消等行为异步入库。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoEvent {

    /** LIKE / COLLECT / WATCH / UNLIKE / UNCOLLECT */
    private String action;

    private Long userId;
    private Long videoId;
    private Long authorUserId;

    /** 观看时长(秒), 仅 WATCH 事件 */
    private Double watchDuration;

    /** 视频总时长(秒), 仅 WATCH 事件 */
    private Double videoDuration;

    /** 是否完播, 仅 WATCH 事件 */
    private Boolean finished;

    /** 滑动速度(秒), 仅 WATCH 事件 */
    private Double swipeSeconds;

    private long timestamp;

    /** 幂等事件 ID（可选；生产者未赋值时由发布器生成，消费端用于去重） */
    private String eventId;

    public static VideoEvent watch(Long userId, Long videoId, Long authorUserId,
                                   double watchDuration, double videoDuration,
                                   boolean finished, double swipeSeconds) {
        VideoEvent e = new VideoEvent();
        e.action = "WATCH";
        e.userId = userId;
        e.videoId = videoId;
        e.authorUserId = authorUserId;
        e.watchDuration = watchDuration;
        e.videoDuration = videoDuration;
        e.finished = finished;
        e.swipeSeconds = swipeSeconds;
        e.timestamp = System.currentTimeMillis();
        return e;
    }

    public static VideoEvent like(Long userId, Long videoId, Long authorUserId) {
        VideoEvent e = new VideoEvent();
        e.action = "LIKE";
        e.userId = userId;
        e.videoId = videoId;
        e.authorUserId = authorUserId;
        e.timestamp = System.currentTimeMillis();
        return e;
    }

    public static VideoEvent unlike(Long userId, Long videoId) {
        VideoEvent e = new VideoEvent();
        e.action = "UNLIKE";
        e.userId = userId;
        e.videoId = videoId;
        e.timestamp = System.currentTimeMillis();
        return e;
    }

    public static VideoEvent collect(Long userId, Long videoId, Long authorUserId) {
        VideoEvent e = new VideoEvent();
        e.action = "COLLECT";
        e.userId = userId;
        e.videoId = videoId;
        e.authorUserId = authorUserId;
        e.timestamp = System.currentTimeMillis();
        return e;
    }

    public static VideoEvent uncollect(Long userId, Long videoId) {
        VideoEvent e = new VideoEvent();
        e.action = "UNCOLLECT";
        e.userId = userId;
        e.videoId = videoId;
        e.timestamp = System.currentTimeMillis();
        return e;
    }
}
