-- =========================================
-- 推荐系统：内容特征 + 用户画像 + 曝光记录
-- =========================================

-- 视频内容特征表
DROP TABLE IF EXISTS t_video_content;
CREATE TABLE t_video_content (
    video_id         BIGINT       NOT NULL PRIMARY KEY COMMENT '视频ID',
    -- 多模态描述
    visual_desc      TEXT         COMMENT 'Qwen2.5-VL 对画面的自然语言描述',
    scene_tags       VARCHAR(512) COMMENT '场景标签(JSON): ["厨房","室内","暖色调"]',
    object_tags      VARCHAR(512) COMMENT '物体标签(JSON): ["美食","人物","铁锅"]',
    -- 视觉 Embedding (ImageBind / Qwen-VL hidden state)
    visual_embedding TEXT         COMMENT '视觉向量(JSON float array, 1024维)',
    -- 音乐特征
    music_bpm        DOUBLE       COMMENT 'BPM',
    music_key        VARCHAR(16)  COMMENT '调性: C / Am / ...',
    music_energy     DOUBLE       COMMENT '能量 0-1',
    music_valence    DOUBLE       COMMENT '情绪效价 0-1 (欢快-悲伤)',
    music_spectral   DOUBLE       COMMENT '频谱质心 (明亮-暗淡)',
    music_mfcc       TEXT         COMMENT 'MFCC特征(JSON float array, 13维)',
    -- 文本特征
    keywords         TEXT         COMMENT '关键词(JSON): ["美食","红烧肉","教程"]',
    text_category    VARCHAR(64)  COMMENT '分类: 美食/舞蹈/搞笑/知识/旅行/音乐/...',
    text_embedding   TEXT         COMMENT '文本语义向量(JSON float array, 1024维)',
    -- 综合
    content_vector   TEXT         COMMENT '融合内容向量(JSON float array, 512维)',
    quality_score    DOUBLE       DEFAULT 0 COMMENT '预估内容质量分 0-1',
    extract_status   TINYINT      DEFAULT 0 COMMENT '提取状态: 0=待处理 1=完成 2=失败',
    extract_time_ms  INT          DEFAULT 0 COMMENT '特征提取耗时(毫秒)',
    create_time      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视频内容特征表';

-- 视频曝光记录表
DROP TABLE IF EXISTS t_video_exposure;
CREATE TABLE t_video_exposure (
    id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT       NOT NULL COMMENT '用户ID',
    video_id         BIGINT       NOT NULL COMMENT '视频ID',
    exposure_time    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '曝光时间',
    swipe_seconds    DOUBLE       DEFAULT 0 COMMENT '停留秒数(<3=快速划过)',
    clicked          TINYINT      DEFAULT 0 COMMENT '是否点击互动(评论/点赞等)',
    UNIQUE KEY uk_user_video_day (user_id, video_id, exposure_time),
    KEY idx_user_time (user_id, exposure_time),
    KEY idx_video (video_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视频曝光记录表';

-- 用户内容偏好画像表
DROP TABLE IF EXISTS t_user_content_profile;
CREATE TABLE t_user_content_profile (
    user_id              BIGINT       NOT NULL PRIMARY KEY COMMENT '用户ID',
    -- 品类偏好 (JSON: {"美食":0.85,"旅行":0.6,...})
    category_weights     TEXT         COMMENT '内容分类偏好权重',
    -- 时长偏好
    preferred_duration_min DOUBLE     COMMENT '偏好最小时长(秒)',
    preferred_duration_max DOUBLE     COMMENT '偏好最大时长(秒)',
    avg_completion_rate  DOUBLE       DEFAULT 0 COMMENT '平均完播率',
    -- 音乐偏好
    preferred_bpm_min    DOUBLE       COMMENT '偏好BPM下限',
    preferred_bpm_max    DOUBLE       COMMENT '偏好BPM上限',
    preferred_energy     DOUBLE       COMMENT '偏好能量均值 0-1',
    -- 行为特征
    user_type            VARCHAR(16)  COMMENT '用户类型: silent/social/collector/explorer/follower',
    total_watch_count    INT          DEFAULT 0 COMMENT '总观看数',
    total_interact_count INT          DEFAULT 0 COMMENT '总互动数',
    -- 融合画像向量
    content_vector       TEXT         COMMENT '用户内容偏好向量(JSON float array, 512维)',
    update_time          DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户内容偏好画像表';

-- 视频标签表（从 desc + Qwen 描述中提取）
DROP TABLE IF EXISTS t_video_tag;
CREATE TABLE t_video_tag (
    id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    video_id         BIGINT       NOT NULL,
    tag              VARCHAR(64)  NOT NULL,
    source           VARCHAR(16)  DEFAULT 'auto' COMMENT 'manual/auto',
    weight           DOUBLE       DEFAULT 1.0 COMMENT '标签权重',
    UNIQUE KEY uk_video_tag (video_id, tag),
    KEY idx_tag (tag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视频标签表';
