import { request } from '@/utils/request'

/** 生成 >= 8 位的 client_request_id,不依赖 uuid */
export function genClientRequestId(): string {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`
}

export interface CreateCallParams {
  scope: 'direct' | 'group'
  mode: 'audio' | 'video'
  target_user_id?: string | number
  group_id?: string | number
  client_request_id: string
  event_id?: string
  trace_id?: string
  provider?: 'livekit' | 'p2p-fallback' | 'legacy'
}

export interface RtcActionRequest {
  event_id?: string
  trace_id?: string
}

/** POST /api/rtc/call → CallSession */
export function createCall(data: CreateCallParams) {
  return request({ url: '/rtc/call', method: 'post', data })
}

/** POST /api/rtc/token → { token, room_name, call_id, identity, ttl_seconds, expires_at }
 *  守卫:仅 ACCEPTED/NEGOTIATING 之后可签发,RINGING 申请报错属正常 */
export function getToken(data: { call_id: string; ttl_seconds?: number; trace_id?: string }) {
  return request({ url: '/rtc/token', method: 'post', data })
}

export function acceptCall(callId: string, data: RtcActionRequest = {}) {
  return request({ url: `/rtc/call/${callId}/accept`, method: 'post', data })
}

export function rejectCall(callId: string, data: RtcActionRequest = {}) {
  return request({ url: `/rtc/call/${callId}/reject`, method: 'post', data })
}

export function cancelCall(callId: string, data: RtcActionRequest = {}) {
  return request({ url: `/rtc/call/${callId}/cancel`, method: 'post', data })
}

export function hangupCall(callId: string, data: RtcActionRequest = {}) {
  return request({ url: `/rtc/call/${callId}/hangup`, method: 'post', data })
}

export function joinCall(callId: string, data: RtcActionRequest = {}) {
  return request({ url: `/rtc/call/${callId}/join`, method: 'post', data })
}

/** POST /api/rtc/call/{callId}/connected → 客户端媒体连接成功确认 */
export function confirmConnectedCall(callId: string, data: RtcActionRequest = {}) {
  return request({ url: `/rtc/call/${callId}/connected`, method: 'post', data })
}

export function leaveCall(callId: string, data: RtcActionRequest = {}) {
  return request({ url: `/rtc/call/${callId}/leave`, method: 'post', data })
}

/** GET /api/rtc/call/{callId} → { call, participants, events } */
export function getCallDetail(callId: string) {
  return request({ url: `/rtc/call/${callId}`, method: 'get' })
}
