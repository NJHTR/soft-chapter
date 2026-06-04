-- =========================================
-- 消息与通知模块
-- 私聊消息 t_message + 互动通知 t_notification
-- =========================================

USE douyin;

-- ============ 私聊消息表 ============
DROP TABLE IF EXISTS t_message;
CREATE TABLE t_message (
    id            BIGINT       NOT NULL PRIMARY KEY COMMENT '消息ID',
    from_user_id  BIGINT       NOT NULL COMMENT '发送者ID',
    to_user_id    BIGINT       NOT NULL COMMENT '接收者ID',
    content       VARCHAR(1000) DEFAULT '' COMMENT '消息内容(文本)',
    msg_type      TINYINT      DEFAULT 1 COMMENT '消息类型: 1文本 2图片 3语音 4视频 5红包',
    extra         VARCHAR(512) DEFAULT '' COMMENT '附加数据(图片/语音url等)',
    is_read       TINYINT      DEFAULT 0 COMMENT '是否已读',
    create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    KEY idx_from_to (from_user_id, to_user_id),
    KEY idx_conversation (from_user_id, to_user_id, create_time),
    KEY idx_receiver_read (to_user_id, is_read, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='私聊消息表';

-- ============ 互动通知表 ============
DROP TABLE IF EXISTS t_notification;
CREATE TABLE t_notification (
    id             BIGINT       NOT NULL PRIMARY KEY COMMENT '通知ID',
    user_id        BIGINT       NOT NULL COMMENT '通知接收者ID',
    from_user_id   BIGINT       DEFAULT 0 COMMENT '触发者ID',
    type           TINYINT      NOT NULL COMMENT '通知类型: 1关注 2点赞 3评论 4收藏 5@提及',
    video_id       BIGINT       DEFAULT 0 COMMENT '关联视频ID',
    comment_id     BIGINT       DEFAULT 0 COMMENT '关联评论ID',
    content        VARCHAR(255) DEFAULT '' COMMENT '通知摘要',
    is_read        TINYINT      DEFAULT 0 COMMENT '是否已读',
    create_time    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '通知时间',
    KEY idx_user_read (user_id, is_read, create_time),
    KEY idx_user_type (user_id, type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='互动通知表';
