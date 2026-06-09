import { request } from '@/utils/request'

// ==========  ”∆µ/ÕºŒƒ…Û∫À ==========

export function getPendingVideos(params?: { pageNo?: number; pageSize?: number }) {
  return request({ url: '/admin/videos/pending', method: 'get', params })
}

export function approveVideo(id: number) {
  return request({ url: `/admin/videos/${id}/approve`, method: 'post' })
}

export function rejectVideo(id: number, reason: string) {
  return request({ url: `/admin/videos/${id}/reject`, method: 'post', data: { reason } })
}

// ========== “Ù¿÷…Û∫À ==========

export function getPendingMusic(params?: { pageNo?: number; pageSize?: number }) {
  return request({ url: '/admin/music/pending', method: 'get', params })
}

export function approveMusic(id: number) {
  return request({ url: `/admin/music/${id}/approve`, method: 'post' })
}

export function rejectMusic(id: number, reason: string) {
  return request({ url: `/admin/music/${id}/reject`, method: 'post', data: { reason } })
}

// ========== Õ≥º∆ ==========

export function getAdminStats() {
  return request({ url: '/admin/stats', method: 'get' })
}
