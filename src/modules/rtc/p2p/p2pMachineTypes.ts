import type { P2pFallbackReason } from './p2pMachine'

export interface P2pStatus {
  callId: string
  topologyGeneration: number
  status: string
  eligible: boolean
  eligibilityReasons: string[]
  consentState: string
  fallbackReason: P2pFallbackReason | null
  probeDeadlineEpochMs: number
}
