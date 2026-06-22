-- =========================================
-- 动态标签系统: 多源信号融合 + 行为校准 + 时效衰减
-- =========================================

ALTER TABLE t_video_tag
    ADD COLUMN confidence   DOUBLE       DEFAULT 0.5  COMMENT '置信度 0-1',
    ADD COLUMN signal_count INT          DEFAULT 0    COMMENT '贡献此标签的行为信号次数',
    ADD COLUMN ai_base      DOUBLE       DEFAULT 0    COMMENT 'AI 基础分 (动态标签=0)',
    ADD COLUMN last_signal  DATETIME     COMMENT '最后一次收到信号的时间';

UPDATE t_video_tag SET last_signal = NOW() WHERE last_signal IS NULL;

ALTER TABLE t_video_tag ADD INDEX idx_video_weight (video_id, weight);
