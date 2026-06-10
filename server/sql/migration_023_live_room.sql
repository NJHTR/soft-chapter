-- =========================================
-- 直播功能：直播间表
-- =========================================

USE douyin;

CREATE TABLE IF NOT EXISTS t_live_room (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '直播间ID',
    host_user_id BIGINT NOT NULL COMMENT '主播用户ID',
    title VARCHAR(200) DEFAULT '' COMMENT '直播标题',
    cover_url VARCHAR(500) DEFAULT '' COMMENT '封面图URL',
    status VARCHAR(20) DEFAULT 'PREVIEW' COMMENT '状态: PREVIEW=预览(未开播), LIVE=直播中, ENDED=已结束',
    viewer_count INT DEFAULT 0 COMMENT '当前观看人数',
    total_viewers INT DEFAULT 0 COMMENT '累计观看人数',
    like_count INT DEFAULT 0 COMMENT '点赞数',
    stream_url VARCHAR(500) DEFAULT '' COMMENT '推流地址(预留)',
    play_url VARCHAR(500) DEFAULT '' COMMENT '播放地址(预留)',
    is_delete TINYINT DEFAULT 0 COMMENT '软删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='直播间表';

-- 索引
CREATE INDEX idx_host_user ON t_live_room(host_user_id);
CREATE INDEX idx_status ON t_live_room(status);
