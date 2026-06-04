-- =========================================
-- 视频观看记录表
-- 记录用户观看视频的行为，含观看时长
-- =========================================
DROP TABLE IF EXISTS t_watch_history;
CREATE TABLE t_watch_history (
    id              BIGINT    NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT    NOT NULL COMMENT '观看用户ID',
    video_id        BIGINT    NOT NULL COMMENT '视频ID',
    author_user_id  BIGINT    NOT NULL COMMENT '视频作者ID',
    watch_duration  DOUBLE    DEFAULT 0 COMMENT '已观看时长(秒)',
    video_duration  DOUBLE    DEFAULT 0 COMMENT '视频总时长(秒)',
    finished        TINYINT   DEFAULT 0 COMMENT '是否看完 0未看完 1已看完',
    create_time     DATETIME  DEFAULT CURRENT_TIMESTAMP COMMENT '首次观看时间',
    update_time     DATETIME  DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后观看时间',
    UNIQUE KEY uk_user_video (user_id, video_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视频观看记录表';
