/**
 * Stage + Audience 分层状态机(契约 §6)。
 *
 * 状态:AUDIENCE -> REQUESTED -> PROMOTING -> ON_STAGE -> DEMOTING -> AUDIENCE,
 * 任一受控状态可因权限撤销进入 REVOKING -> REVOKED。
 *
 * 客户端只镜像服务端状态用于 UI;幂等/CAS 以服务端 event_id + generation 为准。
 * 观众路径始终是 WHEP/LL-HLS/HLS/HTTP-FLV/CDN,普通观众拿不到 Stage publish token。
 */

export const STAGE_MEMBER_STATUS = [
  'AUDIENCE',
  'REQUESTED',
  'PROMOTING',
  'ON_STAGE',
  'DEMOTING',
  'REVOKING',
  'REVOKED'
] as const
export type StageMemberStatus = (typeof STAGE_MEMBER_STATUS)[number]

export type StageCommand = 'REQUEST' | 'APPROVE' | 'JOINED' | 'DEMOTE' | 'LEFT' | 'REVOKE'

/** 客户端接收的服务端命令事件(JOINED/LEFT/CONFIRM_* 由服务端注入)。 */
export interface StageCommandEvent {
  command: string
  liveId: number
  userId: number
  eventId: string
  generation: number
  arrivedAtMs: number
}

export interface StageMemberView {
  liveId: number
  userId: number
  status: StageMemberStatus
  generation: number
  mayPublish: boolean
  /** 客户端镜像专用:最近应用的 event_id,用于防重放(服务端字段不含此项)。 */
  lastEventId?: string
}

export type StageApplyOutcome =
  | { applied: true; member: StageMemberView }
  | { applied: false; reason: 'replay' | 'stale' | 'unknown'; member: StageMemberView }

/**
 * 以 event_id + generation 守卫的本地镜像应用。
 * - 相同 event_id:replay,返回现有成员;不重复请求服务端。
 * - 旧 generation:stale,拒绝(乱序旧事件不得复活成员)。
 * - 其余:覆盖本地镜像(真实权威仍以服务端响应为准)。
 */
export function applyStageEvent(
  member: StageMemberView | undefined,
  event: StageCommandEvent
): StageApplyOutcome {
  const current = member ?? {
    liveId: event.liveId,
    userId: event.userId,
    status: 'AUDIENCE' as StageMemberStatus,
    generation: 0,
    mayPublish: false
  }
  if (event.userId !== current.userId || event.liveId !== current.liveId) {
    return { applied: false, reason: 'unknown', member: current }
  }
  if (event.eventId && current.lastEventId && event.eventId === current.lastEventId) {
    return { applied: false, reason: 'replay', member: current }
  }
  if (event.generation !== current.generation) {
    return { applied: false, reason: 'stale', member: current }
  }
  const status = nextStatus(current.status, event.command)
  const updated: StageMemberView = {
    liveId: current.liveId,
    userId: current.userId,
    status,
    generation: current.generation + 1,
    mayPublish: status === 'ON_STAGE',
    lastEventId: event.eventId
  }
  return { applied: true, member: updated }
}

const TRANSITIONS: Record<string, Record<string, StageMemberStatus>> = {
  AUDIENCE: { REQUEST: 'REQUESTED' },
  REQUESTED: { REQUEST: 'REQUESTED', APPROVE: 'PROMOTING', LEFT: 'AUDIENCE', REVOKE: 'REVOKING' },
  PROMOTING: { JOINED: 'ON_STAGE', LEFT: 'AUDIENCE', REVOKE: 'REVOKING' },
  ON_STAGE: { DEMOTE: 'DEMOTING', REVOKE: 'REVOKING' },
  DEMOTING: { LEFT: 'AUDIENCE', REVOKE: 'REVOKING' },
  REVOKING: {},
  REVOKED: {}
}

export function nextStatus(from: StageMemberStatus, command: string): StageMemberStatus {
  return TRANSITIONS[from]?.[command] ?? from
}

export function isStagePublishGranted(status: StageMemberStatus): boolean {
  return status === 'ON_STAGE'
}
