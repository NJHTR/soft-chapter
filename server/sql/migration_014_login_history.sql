-- ============================================================
-- 014: 登录历史/设备记录表 (安全风控)
-- ============================================================

CREATE TABLE IF NOT EXISTS t_login_history (
    id                BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT       NOT NULL COMMENT '用户ID',
    email             VARCHAR(255) COMMENT '登录邮箱',
    login_method      VARCHAR(32)  COMMENT '登录方式: password/code',
    login_status      VARCHAR(16)  DEFAULT 'success' COMMENT 'success/fail',

    -- IP & 归属地
    ip                VARCHAR(64)  COMMENT '登录IP',
    country           VARCHAR(64)  COMMENT '国家',
    region            VARCHAR(64)  COMMENT '省/州',
    city              VARCHAR(64)  COMMENT '城市',
    isp               VARCHAR(128) COMMENT 'ISP运营商',

    -- 设备指纹
    device_fingerprint VARCHAR(128) COMMENT '综合设备指纹hash',
    user_agent_raw    TEXT         COMMENT '原始User-Agent',

    -- 操作系统
    device_os         VARCHAR(64)  COMMENT '操作系统',
    os_version        VARCHAR(32)  COMMENT '系统版本',

    -- 浏览器
    browser_name      VARCHAR(64)  COMMENT '浏览器名称',
    browser_version   VARCHAR(32)  COMMENT '浏览器版本',
    browser_language  VARCHAR(16)  COMMENT '浏览器语言',

    -- 屏幕
    screen_width      INT          COMMENT '屏幕宽度',
    screen_height     INT          COMMENT '屏幕高度',
    color_depth       INT          COMMENT '色深',
    pixel_ratio       DECIMAL(4,2) COMMENT '设备像素比',

    -- 硬件
    cpu_cores         INT          COMMENT 'CPU核心数',
    device_memory_gb  INT          COMMENT '设备内存(GB)',
    gpu_renderer      VARCHAR(256) COMMENT 'GPU渲染器信息',

    -- 网络/设备
    connection_type   VARCHAR(16)  COMMENT '网络类型: 4g/wifi/ethernet',
    platform_type     VARCHAR(32)  COMMENT '平台: Win32/MacIntel',
    touch_support     TINYINT      DEFAULT 0 COMMENT '是否触屏',

    -- 时间
    create_time       DATETIME     DEFAULT CURRENT_TIMESTAMP,

    KEY idx_user_id (user_id),
    KEY idx_user_time (user_id, create_time),
    KEY idx_ip (ip)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录历史/设备记录表';
