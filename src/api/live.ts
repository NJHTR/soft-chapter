import { request } from '@/utils/request'

export function createLiveRoom(data: { title: string; coverUrl?: string }) {
  return request({ url: '/live/create', method: 'post', data })
}

export function startLive(id: number) {
  return request({ url: '/live/' + id + '/start', method: 'post' })
}

export function endLive(id: number) {
  return request({ url: '/live/' + id + '/end', method: 'post' })
}

export function getLiveDetail(id: number) {
  return request({ url: '/live/' + id, method: 'get' })
}

export function getLiveRooms(params?: { pageNo?: number; pageSize?: number }) {
  return request({ url: '/live/rooms', method: 'get', params })
}

export function joinLive(id: number, sessionId?: string) {
  return request({
    url: '/live/' + id + '/join',
    method: 'post',
    data: sessionId ? { sessionId } : undefined
  })
}

export function leaveLive(id: number, sessionId?: string) {
  return request({
    url: '/live/' + id + '/leave',
    method: 'post',
    data: sessionId ? { sessionId } : undefined
  })
}

export function getFeaturedLive() {
  return request({ url: '/live/featured', method: 'get' })
}

export function getFollowingLiveRooms() {
  return request({ url: '/live/rooms/following', method: 'get' })
}

export function likeLive(id: number) {
  return request({ url: '/live/' + id + '/like', method: 'post' })
}

// ===== Streaming Engine API (ABR / Stats) =====

export function getABRLadder(roomId: number) {
  return request({ url: `/live/engine/${roomId}/abr/ladder`, method: 'get' })
}

export function updateABRLadder(roomId: number, ladder: any[]) {
  return request({ url: `/live/engine/${roomId}/abr/ladder`, method: 'put', data: ladder })
}

export function updateStreamBitrate(roomId: number, bitrate: number) {
  return request({ url: `/live/${roomId}/bitrate`, method: 'put', data: { bitrate } })
}

export function getStreamStats(roomId: number) {
  return request({ url: `/live/${roomId}/stats`, method: 'get' })
}

export function getEngineDashboard() {
  return request({ url: '/live/engine/dashboard', method: 'get' })
}
