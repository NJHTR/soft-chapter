package com.douyin.rtc.p2p;

/**
 * 受控 1 对 1 P2P 拓扑状态(契约第 7 节)。
 *
 * DISABLED -> ELIGIBLE -> CONSENTED -> PROBING
 * PROBING -> P2P_CONNECTED | FALLING_BACK -> SFU_CONNECTED | FAILED
 *
 * P2P_CONNECTED / SFU_CONNECTED / FAILED 为终态;业务已 CONNECTED 后的质量恶化必须走
 * 用户可见 RECONNECTING(由 CallService 控制面按 generation 记录),本拓扑机不静默换拓扑。
 */
public enum P2pTopologyStatus {
    DISABLED,
    ELIGIBLE,
    CONSENTED,
    PROBING,
    P2P_CONNECTED,
    FALLING_BACK,
    SFU_CONNECTED,
    FAILED
}