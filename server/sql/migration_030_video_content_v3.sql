-- =========================================
-- v3.0 内容特征增强: 开放词汇多标签分类 + ASR + 人脸 + 增强音频
-- =========================================

ALTER TABLE t_video_content
    -- 开放词汇多标签分类 (替代单一 text_category)
    ADD COLUMN categories         TEXT         COMMENT '多标签分类JSON: [{"label":"美食","confidence":0.85}, ...]' AFTER visual_embedding,
    ADD COLUMN mood               VARCHAR(32)  COMMENT '情绪/氛围: 欢乐/治愈/紧张/...' AFTER categories,
    ADD COLUMN style              VARCHAR(32)  COMMENT '视频风格: Vlog/教程/混剪/...' AFTER mood,
    ADD COLUMN quality_label      VARCHAR(32)  COMMENT '画质标签: 精良专业/清晰良好/一般/模糊' AFTER style,
    ADD COLUMN music_genre        VARCHAR(32)  COMMENT '音乐流派: 流行/电子/古风/纯音乐/...' AFTER quality_label,
    ADD COLUMN content_attributes TEXT         COMMENT '内容属性JSON: ["有人脸","有字幕","室外拍摄",...]' AFTER music_genre,

    -- ASR 语音转录
    ADD COLUMN transcript         TEXT         COMMENT 'Whisper 语音转录全文' AFTER content_attributes,
    ADD COLUMN asr_keywords       TEXT         COMMENT 'ASR关键词JSON array' AFTER transcript,
    ADD COLUMN has_speech         TINYINT(1)   COMMENT '是否有语音内容' AFTER asr_keywords,

    -- 人脸检测
    ADD COLUMN face_count         DOUBLE       COMMENT '平均每帧人脸数' AFTER has_speech,
    ADD COLUMN face_ratio         DOUBLE       COMMENT '人脸占画面比例 0-1' AFTER face_count,
    ADD COLUMN is_closeup         TINYINT(1)   COMMENT '是否为特写镜头' AFTER face_ratio,
    ADD COLUMN is_single_person   TINYINT(1)   COMMENT '是否为单人出镜' AFTER is_closeup,
    ADD COLUMN is_multi_person    TINYINT(1)   COMMENT '是否为多人场景' AFTER is_single_person,

    -- 增强音频特征
    ADD COLUMN music_rolloff      DOUBLE       COMMENT '频谱滚降 (明亮度)' AFTER music_spectral,
    ADD COLUMN music_bandwidth    DOUBLE       COMMENT '频谱带宽' AFTER music_rolloff,
    ADD COLUMN music_zcr          DOUBLE       COMMENT '过零率 (语音/非语音指示)' AFTER music_bandwidth,
    ADD COLUMN music_onset_rate   DOUBLE       COMMENT '节拍密度 (节奏复杂度)' AFTER music_zcr,
    ADD COLUMN audio_has_music    TINYINT(1)   COMMENT '是否包含音乐' AFTER music_mfcc;
