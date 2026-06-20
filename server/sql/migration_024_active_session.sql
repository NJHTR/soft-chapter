-- ============================================================
-- 024: 活跃会话表 (设备管理 & 强制下线)
-- ============================================================

CREATE TABLE IF NOT EXISTS t_active_session (
    id                 BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id            BIGINT       NOT NULL COMMENT '用户ID',
    token_hash         VARCHAR(128) NOT NULL COMMENT 'JWT Token SHA-256哈希',
    device_fingerprint VARCHAR(128) COMMENT '设备指纹',
    device_name        VARCHAR(256) COMMENT '设备名称(浏览器+OS+城市)',
    ip                 VARCHAR(64)  COMMENT '登录IP',
    city               VARCHAR(128) COMMENT '城市',
    browser_name       VARCHAR(64)  COMMENT '浏览器名称',
    browser_version    VARCHAR(32)  COMMENT '浏览器版本',
    device_os          VARCHAR(64)  COMMENT '操作系统',
    os_version         VARCHAR(32)  COMMENT '系统版本',
    screen_width       INT          COMMENT '屏幕宽度',
    screen_height      INT          COMMENT '屏幕高度',
    cpu_cores          INT          COMMENT 'CPU核心数',
    device_memory_gb   INT          COMMENT '设备内存(GB)',
    gpu_renderer       VARCHAR(256) COMMENT 'GPU渲染器信息',
    login_time         DATETIME     COMMENT '登录时间',
    last_active_time   DATETIME     COMMENT '最后活跃时间',
    is_active          TINYINT      DEFAULT 1 COMMENT '1=活跃 0=已撤销',
    create_time        DATETIME     DEFAULT CURRENT_TIMESTAMP,

    KEY idx_user_id (user_id),
    KEY idx_token_hash (token_hash),
    KEY idx_user_active (user_id, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活跃会话表';
