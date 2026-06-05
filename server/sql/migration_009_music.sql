-- ============================================================
-- 009: 音乐模块
-- ============================================================

-- 音乐曲库表
DROP TABLE IF EXISTS t_music;
CREATE TABLE t_music (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL COMMENT '歌曲名',
    artist VARCHAR(255) COMMENT '歌手',
    album VARCHAR(255) COMMENT '专辑',
    cover_url VARCHAR(512) COMMENT '封面图',
    duration INT DEFAULT 0 COMMENT '时长(秒)',
    source VARCHAR(32) DEFAULT 'netease' COMMENT '来源: netease/local',
    source_id BIGINT COMMENT '来源ID(如网易云歌曲ID)',
    play_url VARCHAR(1024) COMMENT '播放地址',
    lyric TEXT COMMENT '歌词',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_music_source (source, source_id),
    INDEX idx_music_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    COMMENT='音乐曲库';

-- 视频 BGM 信息 (发布时选择)
ALTER TABLE t_video
    ADD COLUMN bgm_start_offset DOUBLE DEFAULT 0 COMMENT 'BGM起始偏移(秒)',
    ADD COLUMN bgm_volume DOUBLE DEFAULT 0.7 COMMENT 'BGM音量(0-1), 0.7=70%';
