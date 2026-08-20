/**
 * 受控 P2P 客户端(契约第 7 节)。
 * 仅在 RTC_P2P_ENABLED=true 且后端 topology=ELIGIBLE 后调用;consent 双签、
 * probe 预算、relay 信令全部以服务端控制面为准。SDP/ICE 原文不留存。
 */

import { request, type ApiResponse } from '@/utils/request'
import type { P2pFallbackReason, P2pStatus } from './p2pMachineTypes'

const BASE = '/api/rtc/p2p'

export function p2pTopology(callId: string): Promise<ApiResponse<P2pStatus>> {
  return request<P2pStatus>({ url: `${BASE}/call/${callId}/topology`, method: 'get' })
}

export function p2pEvaluate(callId: string, eventId: string): Promise<ApiResponse<P2pStatus>> {
  return request<P2pStatus>({
    url: `${BASE}/call/${callId}/evaluate`,
    method: 'post',
    data: { event_id: eventId }
  })
}

export function p2pConsent(
  callId: string,
  eventId: string,
  revoke = false
): Promise<ApiResponse<P2pStatus>> {
  return request<P2pStatus>({
    url: `${BASE}/call/${callId}/consent`,
    method: 'post',
    data: { event_id: eventId, action: revoke ? 'revoke' : 'consent' }
  })
}

export function p2pProbeStart(callId: string, eventId: string): Promise<ApiResponse<P2pStatus>> {
  return request<P2pStatus>({
    url: `${BASE}/call/${callId}/probe-start`,
    method: 'post',
    data: { event_id: eventId }
  })
}

export interface ProbeResultPayload {
  eventId: string
  outcome: 'DIRECT' | 'SRFLX' | 'RELAY' | 'SFU'
  localCandidateType?: string
  remoteCandidateType?: string
  rttMs?: number
  lossPct?: number
}

export function p2pProbeResult(
  callId: string,
  payload: ProbeResultPayload
): Promise<ApiResponse<P2pStatus>> {
  return request<P2pStatus>({
    url: `${BASE}/call/${callId}/probe-result`,
    method: 'post',
    data: {
      event_id: payload.eventId,
      outcome: payload.outcome,
      local_candidate_type: payload.localCandidateType,
      remote_candidate_type: payload.remoteCandidateType,
      rtt_ms: payload.rttMs,
      loss_pct: payload.lossPct
    }
  })
}

export function p2pFallback(
  callId: string,
  reason: P2pFallbackReason,
  eventId: string
): Promise<ApiResponse<P2pStatus>> {
  return request<P2pStatus>({
    url: `${BASE}/call/${callId}/fallback`,
    method: 'post',
    data: { event_id: eventId, reason }
  })
}

export interface P2pSignalEnvelope {
  version: 'v1'
  callId: string
  topologyGeneration: number
  fromUserId: number
  toUserId: number
  kind: 'offer' | 'answer' | 'ice'
  seq: number
  eventId: string
  createdEpochMs: number
  payload: string
  candidateCount: number
  signature: string
}

export function p2pRelaySignal(
  callId: string,
  envelope: P2pSignalEnvelope
): Promise<ApiResponse<{ seq: number }>> {
  return request<{ seq: number }>({
    url: `${BASE}/call/${callId}/signal`,
    method: 'post',
    data: {
      version: envelope.version,
      call_id: envelope.callId,
      topology_generation: String(envelope.topologyGeneration),
      from_user_id: String(envelope.fromUserId),
      to_user_id: String(envelope.toUserId),
      kind: envelope.kind,
      seq: String(envelope.seq),
      event_id: envelope.eventId,
      created_epoch_ms: String(envelope.createdEpochMs),
      payload: envelope.payload,
      candidate_count: String(envelope.candidateCount),
      signature: envelope.signature
    }
  })
}

/** 取走本人收件箱(瞬态缓冲,服务端不落库原文)。 */
export function p2pTakeInbox(callId: string): Promise<ApiResponse<P2pSignalEnvelope[]>> {
  return request<P2pSignalEnvelope[]>({ url: `${BASE}/call/${callId}/signal/inbox`, method: 'get' })
}
