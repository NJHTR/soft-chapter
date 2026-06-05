-- ============================================================
-- 008: 全行为链路用户画像增强
-- 将分散在各表的用户行为数据统一聚合为可被推荐引擎使用的画像特征
-- ============================================================

-- -------------------------------------------------------
-- 1. 增强 t_video_exposure: 增加流量来源 + 会话ID
-- -------------------------------------------------------
ALTER TABLE t_video_exposure
    ADD COLUMN traffic_source VARCHAR(32) DEFAULT 'HOME_RECOMMEND'
        COMMENT '流量来源: HOME_RECOMMEND/SEARCH/USER_PROFILE/FOLLOWING/HASHTAG/EXTERNAL/SHARE',
    ADD COLUMN session_id VARCHAR(64) DEFAULT NULL COMMENT '会话ID',
    ADD INDEX idx_exposure_source (user_id, traffic_source, exposure_time),
    ADD INDEX idx_exposure_session (session_id, exposure_time);

-- -------------------------------------------------------
-- 2. 增强 t_watch_history: 增加重复观看 + 流量来源 + 会话
-- -------------------------------------------------------
ALTER TABLE t_watch_history
    ADD COLUMN repeat_count INT DEFAULT 1 COMMENT '重复观看次数',
    ADD COLUMN traffic_source VARCHAR(32) DEFAULT 'HOME_RECOMMEND'
        COMMENT '流量来源: HOME_RECOMMEND/SEARCH/USER_PROFILE/FOLLOWING/HASHTAG/EXTERNAL/SHARE',
    ADD COLUMN session_id VARCHAR(64) DEFAULT NULL COMMENT '会话ID',
    ADD COLUMN swipe_seconds DOUBLE DEFAULT NULL COMMENT '本次观看划走耗时(秒)',
    ADD INDEX idx_watch_source (user_id, traffic_source, create_time),
    ADD INDEX idx_watch_session (session_id, create_time);

-- -------------------------------------------------------
-- 3. 增强 t_user_content_profile: 多维度画像特征
-- -------------------------------------------------------
ALTER TABLE t_user_content_profile
    -- 短期/长期兴趣向量分离
    ADD COLUMN short_term_vector TEXT COMMENT '24h EMA 兴趣向量 JSON',
    ADD COLUMN long_term_vector TEXT COMMENT '30d EMA 兴趣向量 JSON',

    -- 创作者亲和力
    ADD COLUMN creator_affinity TEXT COMMENT '创作者亲和力 JSON {authorId: score}',

    -- 行为统计
    ADD COLUMN avg_watch_duration DOUBLE DEFAULT 0 COMMENT '平均观看时长(秒)',
    ADD COLUMN bounce_rate DOUBLE DEFAULT 0 COMMENT '快速划走率 (<3秒)',
    ADD COLUMN like_rate DOUBLE DEFAULT 0 COMMENT '点赞率 (like/view)',
    ADD COLUMN collect_rate DOUBLE DEFAULT 0 COMMENT '收藏率',
    ADD COLUMN share_rate DOUBLE DEFAULT 0 COMMENT '分享率',
    ADD COLUMN comment_rate DOUBLE DEFAULT 0 COMMENT '评论率',
    ADD COLUMN repeat_view_rate DOUBLE DEFAULT 0 COMMENT '重复观看率',

    -- 社交特征
    ADD COLUMN social_engagement_rate DOUBLE DEFAULT 0 COMMENT '对关注作者的互动占比',
    ADD COLUMN profile_visit_count INT DEFAULT 0 COMMENT '浏览他人主页次数',

    -- 搜索特征
    ADD COLUMN search_frequency DOUBLE DEFAULT 0 COMMENT '搜索频率(次/会话)',
    ADD COLUMN recent_search_queries TEXT COMMENT '近期搜索词 JSON',

    -- 流量来源偏好
    ADD COLUMN traffic_sources TEXT COMMENT '流量来源分布 JSON {source: ratio}',

    -- 时间模式
    ADD COLUMN active_hours TEXT COMMENT '活跃时段分布 JSON [0-23]',
    ADD COLUMN avg_session_duration DOUBLE DEFAULT 0 COMMENT '平均会话时长(秒)',
    ADD COLUMN active_days_last_week INT DEFAULT 0 COMMENT '近7天活跃天数',

    -- 用户分类
    ADD COLUMN user_segment VARCHAR(32) DEFAULT 'new_user'
        COMMENT '用户分层: new_user/light/medium/heavy',

    -- 统计基准
    ADD COLUMN total_view_count INT DEFAULT 0 COMMENT '总观看视频数',
    ADD COLUMN total_like_count INT DEFAULT 0 COMMENT '总点赞数',
    ADD COLUMN total_collect_count INT DEFAULT 0 COMMENT '总收藏数',
    ADD COLUMN total_share_count INT DEFAULT 0 COMMENT '总分享数',
    ADD COLUMN total_comment_count INT DEFAULT 0 COMMENT '总评论数',
    ADD COLUMN total_search_count INT DEFAULT 0 COMMENT '总搜索次数',
    ADD COLUMN total_watch_time_sec BIGINT DEFAULT 0 COMMENT '总观看时长(秒)';

-- -------------------------------------------------------
-- 4. t_user_behavior_log: 统一行为日志表 (用于离线分析/特征回溯)
-- -------------------------------------------------------
DROP TABLE IF EXISTS t_user_behavior_log;
CREATE TABLE t_user_behavior_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    video_id BIGINT DEFAULT NULL,
    author_id BIGINT DEFAULT NULL,
    target_user_id BIGINT DEFAULT NULL COMMENT '社交行为目标用户',

    action_type VARCHAR(32) NOT NULL COMMENT 'VIEW/LIKE/COLLECT/SHARE/COMMENT/FOLLOW/UNFOLLOW/PROFILE_VIEW/SEARCH/COMMENT_LIKE/COMMENT_DISLIKE/SCROLL_SKIP',

    traffic_source VARCHAR(32) DEFAULT NULL COMMENT '流量来源',

    -- 数值型附加信息
    value_double_1 DOUBLE DEFAULT NULL COMMENT '通用数值字段1 (如观看时长)',
    value_double_2 DOUBLE DEFAULT NULL COMMENT '通用数值字段2 (如完播率)',
    value_int_1 INT DEFAULT NULL COMMENT '通用整数字段1 (如重复次数)',
    value_str_1 VARCHAR(512) DEFAULT NULL COMMENT '通用字符串字段1 (如搜索词)',

    session_id VARCHAR(64) DEFAULT NULL,
    device_type VARCHAR(32) DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_behavior_user_time (user_id, create_time),
    INDEX idx_behavior_user_action (user_id, action_type, create_time),
    INDEX idx_behavior_session (session_id, create_time),
    INDEX idx_behavior_video (video_id, action_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    COMMENT='统一用户行为日志';

-- -------------------------------------------------------
-- 5. 评论互动详情表 (评论点赞/点踩)
-- -------------------------------------------------------
DROP TABLE IF EXISTS t_comment_interaction;
CREATE TABLE t_comment_interaction (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '操作人',
    comment_id BIGINT NOT NULL COMMENT '评论ID',
    video_id BIGINT DEFAULT NULL COMMENT '所属视频ID',
    action VARCHAR(16) NOT NULL COMMENT 'LIKE/DISLIKE',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uk_user_comment_action (user_id, comment_id, action),
    INDEX idx_comment_interaction_video (video_id),
    INDEX idx_comment_interaction_user (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    COMMENT='评论互动(点赞/点踩)';
