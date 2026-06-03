import { request } from '@/utils/request'

export function historyOther(params?: any, data?: any) {
  return request({ url: '/video/historyOther', method: 'get', params, data })
}

export function historyVideo(params?: any, data?: any) {
  return request({ url: '/video/history', method: 'get', params, data })
}

export function recommendedVideo(params?: any, data?: any) {
  return request({ url: '/video/recommended', method: 'get', params, data })
}

export function recommendedLongVideo(params?: any, data?: any) {
  return request({ url: '/video/long/recommended/', method: 'get', params, data })
}

export function myVideo(params?: any, data?: any) {
  return request({ url: '/video/my', method: 'get', params, data })
}

export function privateVideo(params?: any, data?: any) {
  return request({ url: '/video/private', method: 'get', params, data })
}

export function likeVideo(params?: any, data?: any) {
  return request({ url: '/video/like', method: 'get', params, data })
}

export function videoComments(params?: any, data?: any) {
  return request({ url: '/video/comments', method: 'get', params, data })
}

export function publishVideo(data: { video_url: string; cover_url?: string; desc?: string; duration?: number; music_title?: string }) {
  return request({ url: '/video', method: 'post', data })
}

export function toggleVideoLike(videoId: number) {
  return request({ url: `/video/like/${videoId}`, method: 'post' })
}

export function recordShare(videoId: number) {
  return request({ url: `/video/share/${videoId}`, method: 'post' })
}

export function postComment(data: { video_id: number; content: string; parent_id?: number; reply_to_user_id?: number }) {
  return request({ url: '/video/comments', method: 'post', data })
}
