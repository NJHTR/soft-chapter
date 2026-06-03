-- =========================================
-- 抖音后端数据库初始化脚本
-- =========================================

CREATE DATABASE IF NOT EXISTS douyin DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE douyin;

-- ============ 用户表 ============
DROP TABLE IF EXISTS t_user;
CREATE TABLE t_user (
    uid           BIGINT        NOT NULL PRIMARY KEY COMMENT '用户ID',
    unique_id     VARCHAR(32)   DEFAULT '' COMMENT '抖音号',
    nickname      VARCHAR(64)   DEFAULT '抖音用户' COMMENT '昵称',
    signature     VARCHAR(255)  DEFAULT '' COMMENT '个性签名',
    gender        TINYINT       DEFAULT 0 COMMENT '性别: 0未知 1男 2女',
    birthday      VARCHAR(16)   DEFAULT '' COMMENT '生日',
    province      VARCHAR(32)   DEFAULT '' COMMENT '省份',
    city          VARCHAR(32)   DEFAULT '' COMMENT '城市',
    avatar_168_url VARCHAR(512) DEFAULT '' COMMENT '头像小图',
    avatar_300_url VARCHAR(512) DEFAULT '' COMMENT '头像大图',
    cover_url     VARCHAR(512)  DEFAULT '' COMMENT '个人主页背景',
    follower_count   BIGINT     DEFAULT 0 COMMENT '粉丝数',
    following_count  BIGINT     DEFAULT 0 COMMENT '关注数',
    total_favorited  BIGINT     DEFAULT 0 COMMENT '获赞总数',
    video_count      INT        DEFAULT 0 COMMENT '作品数',
    phone         VARCHAR(16)   DEFAULT '' COMMENT '手机号',
    email         VARCHAR(64)   DEFAULT '' COMMENT '邮箱',
    password      VARCHAR(255)  DEFAULT '' COMMENT '密码(bcrypt)',
    is_delete     TINYINT       DEFAULT 0 COMMENT '逻辑删除',
    create_time   DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_phone (phone),
    UNIQUE KEY uk_email (email),
    KEY idx_unique_id (unique_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ============ 视频表 ============
DROP TABLE IF EXISTS t_video;
CREATE TABLE t_video (
    id             BIGINT       NOT NULL PRIMARY KEY COMMENT '视频ID',
    video_url      VARCHAR(512) DEFAULT '' COMMENT '视频地址',
    cover_url      VARCHAR(512) DEFAULT '' COMMENT '封面地址',
    `desc`         VARCHAR(500) DEFAULT '' COMMENT '标题描述',
    duration       DOUBLE       DEFAULT 0 COMMENT '视频时长(秒)',
    width          INT          DEFAULT 0 COMMENT '宽度',
    height         INT          DEFAULT 0 COMMENT '高度',
    author_user_id BIGINT       DEFAULT 0 COMMENT '作者ID',
    music_id       BIGINT       DEFAULT 0 COMMENT '音乐ID',
    music_title    VARCHAR(128) DEFAULT '' COMMENT '音乐标题',
    type           VARCHAR(32)  DEFAULT 'recommend-video' COMMENT '视频类型',
    like_count     BIGINT       DEFAULT 0 COMMENT '点赞数',
    comment_count  BIGINT       DEFAULT 0 COMMENT '评论数',
    share_count    BIGINT       DEFAULT 0 COMMENT '分享数',
    collect_count  BIGINT       DEFAULT 0 COMMENT '收藏数',
    play_count     BIGINT       DEFAULT 0 COMMENT '播放数',
    is_delete      TINYINT      DEFAULT 0 COMMENT '逻辑删除',
    create_time    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_author (author_user_id),
    KEY idx_type (type),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视频表';

-- ============ 评论表 ============
DROP TABLE IF EXISTS t_comment;
CREATE TABLE t_comment (
    id               BIGINT       NOT NULL PRIMARY KEY COMMENT '评论ID',
    video_id         BIGINT       DEFAULT 0 COMMENT '视频ID',
    user_id          BIGINT       DEFAULT 0 COMMENT '评论用户ID',
    content          VARCHAR(500) DEFAULT '' COMMENT '评论内容',
    like_count       BIGINT       DEFAULT 0 COMMENT '点赞数',
    reply_count      INT          DEFAULT 0 COMMENT '回复数',
    parent_id        BIGINT       DEFAULT 0 COMMENT '父评论ID(0=一级评论)',
    reply_to_user_id BIGINT       DEFAULT 0 COMMENT '回复目标用户ID',
    is_delete        TINYINT      DEFAULT 0 COMMENT '逻辑删除',
    create_time      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_video (video_id),
    KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论表';

-- ============ 关注关系表 ============
DROP TABLE IF EXISTS t_follow;
CREATE TABLE t_follow (
    id          BIGINT    NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT    NOT NULL COMMENT '关注者ID',
    follow_id   BIGINT    NOT NULL COMMENT '被关注者ID',
    create_time DATETIME  DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_follow (user_id, follow_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='关注关系表';

-- ============ 点赞记录表 ============
DROP TABLE IF EXISTS t_like;
CREATE TABLE t_like (
    id          BIGINT    NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT    NOT NULL COMMENT '用户ID',
    video_id    BIGINT    NOT NULL COMMENT '视频ID',
    create_time DATETIME  DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_video (user_id, video_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='点赞记录表';
