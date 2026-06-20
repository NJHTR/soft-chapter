package com.douyin.kafka;

import com.douyin.entity.Video;
import com.douyin.kafka.dto.CoverExtractEvent;
import com.douyin.mapper.VideoMapper;
import com.douyin.service.CoverService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

/**
 * 封面提取消费者 — 从 Kafka 拉取任务，异步执行 FFmpeg 提取 + MinIO 上传。
 *
 * 单线程消费（1 分区），避免并发 FFmpeg 抢占 CPU/内存。
 */
@Slf4j
@Service
@ConditionalOnProperty(value = "douyin.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class CoverExtractConsumer {

    private final CoverService coverService;
    private final VideoMapper videoMapper;

    public CoverExtractConsumer(CoverService coverService, VideoMapper videoMapper) {
        this.coverService = coverService;
        this.videoMapper = videoMapper;
    }

    @KafkaListener(
            topics = KafkaTopicConfig.TOPIC_COVER_EXTRACT,
            concurrency = "1",
            containerFactory = "kafkaListenerContainerFactory")
    public void onCoverExtract(CoverExtractEvent event, Acknowledgment ack) {
        try {
            log.info("Cover extract start: videoId={}", event.getVideoId());

            String coverUrl = coverService.extractAndUpload(event.getVideoUrl());

            if (coverUrl != null && !coverUrl.isEmpty()) {
                Video video = videoMapper.selectById(event.getVideoId());
                if (video != null) {
                    video.setCoverUrl(coverUrl);
                    videoMapper.updateById(video);
                    log.info("Cover extract success: videoId={} cover={}", event.getVideoId(), coverUrl);
                }
            } else {
                log.warn("Cover extract returned null: videoId={}", event.getVideoId());
            }

        } catch (Exception e) {
            log.error("Cover extract failed: videoId={}", event.getVideoId(), e);
        } finally {
            ack.acknowledge();
        }
    }
}
