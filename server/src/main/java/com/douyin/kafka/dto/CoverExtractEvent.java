package com.douyin.kafka.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 封面提取事件 — 视频上传后异步提取封面。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CoverExtractEvent {
    private Long videoId;
    private String videoUrl;
    private long timestamp;

    /** 幂等事件 ID（可选；生产者未赋值时由发布器生成，消费端用于去重） */
    private String eventId;

    public static CoverExtractEvent of(Long videoId, String videoUrl) {
        return new CoverExtractEvent(videoId, videoUrl, System.currentTimeMillis(), UUID.randomUUID().toString());
    }
}
