-- ============================================================
-- 016: 群聊系统 (群信息 + 群成员 + 群消息)
-- ============================================================

CREATE TABLE IF NOT EXISTS t_group (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL COMMENT '群名称',
    avatar      VARCHAR(512) DEFAULT '' COMMENT '群头像URL',
    owner_uid   BIGINT       NOT NULL COMMENT '群主用户ID',
    member_count INT          DEFAULT 1 COMMENT '成员数量',
    description VARCHAR(255) DEFAULT '' COMMENT '群简介',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_owner (owner_uid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群聊信息表';

CREATE TABLE IF NOT EXISTS t_group_member (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    group_id    BIGINT       NOT NULL COMMENT '群ID',
    user_id     BIGINT       NOT NULL COMMENT '成员用户ID',
    role        TINYINT      DEFAULT 0 COMMENT '0普通成员 1群主 2管理员',
    nickname    VARCHAR(100) DEFAULT '' COMMENT '群内昵称',
    is_muted    TINYINT      DEFAULT 0 COMMENT '0正常 1禁言',
    join_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_group_user (group_id, user_id),
    KEY idx_user (user_id),
    KEY idx_group (group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群成员表';

CREATE TABLE IF NOT EXISTS t_group_message (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    group_id    BIGINT       NOT NULL COMMENT '群ID',
    from_user_id BIGINT      NOT NULL COMMENT '发送者ID',
    content     TEXT         COMMENT '消息内容',
    msg_type    TINYINT      DEFAULT 1 COMMENT '消息类型: 1文本 2图片 3语音 4视频 5红包',
    extra       VARCHAR(1024) DEFAULT '' COMMENT '附加数据',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    KEY idx_group_time (group_id, create_time),
    KEY idx_group_msg (group_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群聊消息表';

-- 群消息已读追踪表 (记录每个成员在每个群最后读取的消息ID)
CREATE TABLE IF NOT EXISTS t_group_read_cursor (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    group_id    BIGINT       NOT NULL COMMENT '群ID',
    user_id     BIGINT       NOT NULL COMMENT '用户ID',
    last_read_msg_id BIGINT  DEFAULT 0 COMMENT '最后已读消息ID',
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_group_user (group_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群消息已读游标';
