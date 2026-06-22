-- 断点续播: 记录上次播放位置
ALTER TABLE t_watch_history
    ADD COLUMN last_position DOUBLE NULL DEFAULT NULL COMMENT '上次播放位置(秒), 用于断点续播';
