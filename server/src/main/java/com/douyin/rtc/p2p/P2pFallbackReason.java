package com.douyin.rtc.p2p;

/**
 * 稳定回退原因(契约第 7 节:每次 attempt 记录稳定的回退原因)。
 * 客户端必须以枚举名上报,不允许自由文本。
 */
public enum P2pFallbackReason {
    /** selected local/remote candidate pair 任一为 relay */
    RELAY_REQUIRED,
    /** ICE failed / candidates 耗尽 / connection failed */
    ICE_FAILED,
    /** 仅 TURN relay 可用但 P2P 只接受非 relay */
    TURN_ONLY,
    /** 1.5~3 秒探测预算到期仍未连接 */
    PROBE_TIMEOUT,
    /** 探测期间功能开关被关闭 */
    FEATURE_DISABLED,
    /** 权限被撤销或业务状态离开 ACCEPTED/NEGOTIATING */
    PERMISSION_REVOKED,
    /** 双方 consent 过期或在探测前被撤销 */
    CONSENT_EXPIRED,
    /** 探测质量不达标(丢包/延迟/带宽) */
    QUALITY_DEGRADED,
    /** 信令非法或超限(大小/候选数/seq/速率) */
    SIGNALING_LIMIT
}