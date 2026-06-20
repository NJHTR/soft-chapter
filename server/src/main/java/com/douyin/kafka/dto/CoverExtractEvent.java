package com.douyin.kafka.dto;

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

    public static CoverExtractEvent of(Long videoId, String videoUrl) {
        return new CoverExtractEvent(videoId, videoUrl, System.currentTimeMillis());
    }
}
