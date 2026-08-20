package com.douyin.rtc.stage;

/**
 * Stage 命令(契约 §6 命令表)。CONFIRM_* 为服务端/provider 收敛用内部命令,
 * 客户端不可直接携带(对应事件由 webhook/查询确认注入,带 event_id + generation)。
 */
public enum StageCommand {
    /** 观众本人申请上麦 */
    REQUEST,
    /** 主持人批准并预留发布名额 */
    APPROVE,
    /** provider 确认已入房,generation 匹配后进入 ON_STAGE */
    JOINED,
    /** 下麦(本人或主持人):停止补签并移除发布权限 */
    DEMOTE,
    /** provider 确认离开,收敛回 AUDIENCE */
    LEFT,
    /** 权限撤销:立即拒绝补签并移除发布权限 */
    REVOKE,
    /** 内部:provider 确认发布权限已移除(REVOKING -> REVOKED) */
    CONFIRM_REVOKED,
    /** 内部:provider 确认已断开/离开(DEMOTING -> AUDIENCE) */
    CONFIRM_LEFT,
    /** 仅审计用:Stage -> SRS 出流已启动,不参与状态转移 */
    EGRESS_START
}