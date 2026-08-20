package com.douyin.rtc.p2p;

/** P2P 拓扑状态机命令。 */
public enum P2pCommand {
    /** 契约条件全部满足:DISABLED -> ELIGIBLE(条件不满足时整体保持 DISABLED,无状态变化) */
    EVALUATE,
    /** 某方就当前 call_id + topology_generation 明确同意,双方都同意后进入 CONSENTED */
    CONSENT,
    /** 撤销本人 consent:CONSENTED -> ELIGIBLE;PROBING 期间撤销 -> FALLING_BACK */
    REVOKE,
    /** 开始有界探测:CONSENTED -> PROBING */
    PROBE_START,
    /** 探测通过(selected pair 均非 relay):PROBING -> P2P_CONNECTED */
    PROBE_PASS,
    /** 探测失败(relay/ice_failed/turn_only/预算到期等):PROBING -> FALLING_BACK */
    PROBE_FAIL,
    /** 显式回退(权限变化/consent 过期/功能关闭):任意受控态 -> FALLING_BACK */
    FALLBACK,
    /** 控制面确认 SFU 已连接:FALLING_BACK -> SFU_CONNECTED */
    SFU_CONNECTED_ACK,
    /** 控制面失败收敛:FALLING_BACK -> FAILED */
    FAIL
}