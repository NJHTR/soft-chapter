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

export function joinLive(id: number) {
  return request({ url: '/live/' + id + '/join', method: 'post' })
}

export function leaveLive(id: number) {
  return request({ url: '/live/' + id + '/leave', method: 'post' })
}

export function likeLive(id: number) {
  return request({ url: '/live/' + id + '/like', method: 'post' })
}
