/**
 * 受控 1 对 1 P2P 拓扑镜像(契约第 7 节)。
 *
 * 客户端只镜像服务端拓扑用于 UI;权威以服务端为准。默认开关 RTC_P2P_ENABLED=false 且
 * fail-closed:功能未开时永远 DISABLED,新通话走 LiveKit SFU。
 */

export const P2P_TOPOLOGY_STATUS = [
  'DISABLED',
  'ELIGIBLE',
  'CONSENTED',
  'PROBING',
  'P2P_CONNECTED',
  'FALLING_BACK',
  'SFU_CONNECTED',
  'FAILED'
] as const
export type P2pTopologyStatus = (typeof P2P_TOPOLOGY_STATUS)[number]

export const P2P_FALLBACK_REASON = [
  'RELAY_REQUIRED',
  'ICE_FAILED',
  'TURN_ONLY',
  'PROBE_TIMEOUT',
  'FEATURE_DISABLED',
  'PERMISSION_REVOKED',
  'CONSENT_EXPIRED',
  'QUALITY_DEGRADED',
  'SIGNALING_LIMIT'
] as const
export type P2pFallbackReason = (typeof P2P_FALLBACK_REASON)[number]

export const P2P_COMMAND = [
  'EVALUATE',
  'CONSENT',
  'REVOKE',
  'PROBE_START',
  'PROBE_PASS',
  'PROBE_FAIL',
  'FALLBACK'
] as const
export type P2pCommand = (typeof P2P_COMMAND)[number]

const TRANSITIONS: Record<string, Partial<Record<P2pCommand, P2pTopologyStatus>>> = {
  DISABLED: { EVALUATE: 'ELIGIBLE' },
  ELIGIBLE: { CONSENT: 'CONSENTED', FALLBACK: 'FALLING_BACK' },
  CONSENTED: {
    CONSENT: 'CONSENTED',
    REVOKE: 'ELIGIBLE',
    PROBE_START: 'PROBING',
    FALLBACK: 'FALLING_BACK'
  },
  PROBING: {
    PROBE_PASS: 'P2P_CONNECTED',
    PROBE_FAIL: 'FALLING_BACK',
    REVOKE: 'FALLING_BACK',
    FALLBACK: 'FALLING_BACK'
  },
  FALLING_BACK: { FALLBACK: 'FALLING_BACK' },
  P2P_CONNECTED: {},
  SFU_CONNECTED: {},
  FAILED: {}
}

export function nextTopology(from: P2pTopologyStatus, command: P2pCommand): P2pTopologyStatus {
  return TRANSITIONS[from]?.[command] ?? from
}

export function isTerminal(status: P2pTopologyStatus): boolean {
  return status === 'P2P_CONNECTED' || status === 'SFU_CONNECTED' || status === 'FAILED'
}

/** 仅 ON:selected pair 均非 relay 时才可能 P2P_CONNECTED。 */
export function canAcceptDirectPair(status: P2pTopologyStatus): boolean {
  return status === 'PROBING'
}

export type CandidateType = 'host' | 'srflx' | 'prflx' | 'relay'
export type ProbeOutcome = 'direct' | 'srflx' | 'relay'

export const PROBE_BUDGET_MIN_MS = 1500
export const PROBE_BUDGET_MAX_MS = 3000

/** 标准分类:未知/缺失一律视为 relay(fail-closed)。 */
export function classifyCandidate(raw: string | null | undefined): CandidateType {
  switch ((raw ?? '').trim().toLowerCase()) {
    case 'host':
      return 'host'
    case 'srflx':
      return 'srflx'
    case 'prflx':
      return 'prflx'
    default:
      return 'relay'
  }
}

/** 派生首次 attempt outcome:direct | srflx | relay。 */
export function deriveProbeOutcome(
  localType: string | null | undefined,
  remoteType: string | null | undefined
): ProbeOutcome {
  const l = classifyCandidate(localType)
  const r = classifyCandidate(remoteType)
  if (l === 'relay' || r === 'relay') {
    return 'relay'
  }
  if (l === 'host' && r === 'host') {
    return 'direct'
  }
  return 'srflx'
}

/** 探测预算校验:1.5~3 秒。 */
export function isBudgetValid(budgetMs: number): boolean {
  return budgetMs >= PROBE_BUDGET_MIN_MS && budgetMs <= PROBE_BUDGET_MAX_MS
}

export function probeDeadline(startedEpochMs: number, budgetMs: number): number {
  return startedEpochMs + budgetMs
}

export function isProbeExpired(nowEpochMs: number, deadlineEpochMs: number): boolean {
  return nowEpochMs >= deadlineEpochMs
}
