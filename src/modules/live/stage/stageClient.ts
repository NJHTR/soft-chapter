/**
 * Stage + Audience 控制面客户端(契约 §6)。
 *
 * 调用方通过 Host view(assumeHost=true)下发主持人命令;观众侧仅 REQUEST。
 * UI 侧先 applyStageEvent(本地镜像,event_id + generation 防重放),再请求服务端
 * 以服务端响应为准;服务端错误码 STAGE_LIMIT_REACHED / GENERATION_STALE 原样透出。
 */

import { request, type ApiResponse } from '@/utils/request'
import type { StageMemberView } from './stageMachine'

const BASE = '/api/live/stage'

export interface StageCommandPayload {
  liveId: number
  eventId: string
  targetUserId?: number
}

type StageRequest<T = unknown> = Promise<ApiResponse<T>>

export function stageRequest(liveId: number, token: string): StageRequest<StageMemberView> {
  return request<StageMemberView>({
    url: `${BASE}/request`,
    method: 'post',
    data: { live_id: liveId, event_id: token }
  })
}

export function stageApprove(payload: StageCommandPayload): StageRequest<StageMemberView> {
  return request<StageMemberView>({
    url: `${BASE}/approve`,
    method: 'post',
    data: {
      live_id: payload.liveId,
      target_user_id: payload.targetUserId,
      event_id: payload.eventId
    }
  })
}

export function stageJoined(payload: StageCommandPayload): StageRequest<StageMemberView> {
  return request<StageMemberView>({
    url: `${BASE}/joined`,
    method: 'post',
    data: {
      live_id: payload.liveId,
      target_user_id: payload.targetUserId,
      event_id: payload.eventId
    }
  })
}

export function stageDemote(
  liveId: number,
  targetUserId: number,
  eventId: string
): StageRequest<StageMemberView> {
  return request<StageMemberView>({
    url: `${BASE}/demote`,
    method: 'post',
    data: { live_id: liveId, target_user_id: targetUserId, event_id: eventId }
  })
}

export function stageRevoke(
  liveId: number,
  targetUserId: number,
  eventId: string
): StageRequest<StageMemberView> {
  return request<StageMemberView>({
    url: `${BASE}/revoke`,
    method: 'post',
    data: { live_id: liveId, target_user_id: targetUserId, event_id: eventId }
  })
}

export function stageLeft(liveId: number, eventId: string): StageRequest<StageMemberView> {
  return request<StageMemberView>({
    url: `${BASE}/left`,
    method: 'post',
    data: { live_id: liveId, event_id: eventId }
  })
}

export function stageMembers(liveId: number): StageRequest<StageMemberView[]> {
  return request<StageMemberView[]>({ url: `${BASE}/${liveId}/members`, method: 'get' })
}

export function stageAudit(liveId: number): StageRequest<unknown[]> {
  return request<unknown[]>({ url: `${BASE}/${liveId}/audit`, method: 'get' })
}
