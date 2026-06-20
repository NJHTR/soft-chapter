-- =========================================
-- 性能索引优化 — 推荐引擎 / 聊天 / 通知查询
-- =========================================

-- 1. t_video: 推荐召回核心复合索引
--    findRecallCandidates / hotRecall / socialRecall 都走 status + type + is_delete + create_time
ALTER TABLE t_video ADD INDEX idx_recall (status, is_delete, type, create_time);

-- 2. t_video: 作者召回复合索引
--    socialRecall / creatorBacklogRecall 按 author_user_id + status + create_time 查询
ALTER TABLE t_video ADD INDEX idx_author_status_time (author_user_id, status, create_time);

-- 3. t_watch_history: 完播历史查询
--    "24h 内完整看完" → WHERE user_id=？ AND finished=1 AND create_time >= ？
ALTER TABLE t_watch_history ADD INDEX idx_user_finished_time (user_id, finished, create_time);

-- 4. t_watch_history: 全站完播率统计
--    avgCompletionRate → WHERE video_id IN (？) GROUP BY video_id
ALTER TABLE t_watch_history ADD INDEX idx_video_comp (video_id, watch_duration, video_duration);

-- 5. t_like: 近期点赞统计 (countRecentLikes)
--    WHERE create_time >= ？AND video_id IN (？) GROUP BY video_id
ALTER TABLE t_like ADD INDEX idx_time_video (create_time, video_id);

-- 6. t_message: 聊天消息分页查询
--    WHERE ((from_user_id=？ AND to_user_id=？) OR (from_user_id=？ AND to_user_id=？)) ORDER BY create_time DESC
ALTER TABLE t_message ADD INDEX idx_conversation_time (from_user_id, to_user_id, create_time);

-- 7. t_notification: 通知列表分页查询
--    WHERE user_id=？ ORDER BY create_time DESC
ALTER TABLE t_notification ADD INDEX idx_user_time (user_id, create_time);

-- 8. t_comment: 评论列表 + 子评论查询
--    WHERE video_id=？AND parent_id IS NULL ORDER BY create_time DESC
ALTER TABLE t_comment ADD INDEX idx_video_parent_time (video_id, parent_id, create_time);
