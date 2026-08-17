-- =========================================
-- 直播引擎升级：添加流媒体引擎字段
-- =========================================

USE douyin;

ALTER TABLE t_live_room
    ADD COLUMN srt_stream_id VARCHAR(100) DEFAULT '' COMMENT 'SRT流ID' AFTER play_url,
    ADD COLUMN rtmp_stream_key VARCHAR(100) DEFAULT '' COMMENT 'RTMP推流密钥' AFTER srt_stream_id,
    ADD COLUMN target_bitrate INT DEFAULT 4000000 COMMENT '目标码率(bps)' AFTER rtmp_stream_key,
    ADD COLUMN max_bitrate INT DEFAULT 8000000 COMMENT '最大码率(bps)' AFTER target_bitrate,
    ADD COLUMN min_bitrate INT DEFAULT 500000 COMMENT '最小码率(bps)' AFTER max_bitrate,
    ADD COLUMN encoder_type VARCHAR(20) DEFAULT 'nvenc' COMMENT '编码器类型: nvenc/x264/x265' AFTER min_bitrate,
    ADD COLUMN codec VARCHAR(10) DEFAULT 'av1' COMMENT '编码格式: av1/h265/h264' AFTER encoder_type,
    ADD COLUMN enable_beauty TINYINT DEFAULT 1 COMMENT '是否启用美颜' AFTER codec,
    ADD COLUMN enable_hdr TINYINT DEFAULT 0 COMMENT '是否启用HDR' AFTER enable_beauty,
    ADD COLUMN capture_fps DOUBLE DEFAULT 0 COMMENT '采集帧率' AFTER enable_hdr,
    ADD COLUMN encode_fps DOUBLE DEFAULT 0 COMMENT '编码帧率' AFTER capture_fps,
    ADD COLUMN gpu_usage INT DEFAULT 0 COMMENT 'GPU使用率(%)' AFTER encode_fps,
    ADD COLUMN cpu_usage INT DEFAULT 0 COMMENT 'CPU使用率(%)' AFTER gpu_usage;

-- 新索引
CREATE INDEX idx_srt_stream ON t_live_room(srt_stream_id);
CREATE INDEX idx_codec ON t_live_room(codec);
CREATE INDEX idx_encoder ON t_live_room(encoder_type);
