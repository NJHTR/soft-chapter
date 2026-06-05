import { request } from '@/utils/request'

export function searchMusic(params: { keyword: string; pageNo?: number; pageSize?: number }) {
  return request({ url: '/music/search', method: 'get', params })
}

export function getMusicDetail(id: number) {
  return request({ url: `/music/${id}`, method: 'get' })
}

export function getMusicList(params?: { pageNo?: number; pageSize?: number }) {
  return request({ url: '/music/list', method: 'get', params })
}

export function getHotMusic(params?: { limit?: number }) {
  return request({ url: '/music/hot', method: 'get', params })
}
