import { request } from '@/utils/request'

export function userinfo(params?: any, data?: any) {
  return request({ url: '/user/userinfo', method: 'get', params, data })
}

export function userVideoList(params?: any, data?: any) {
  return request({ url: '/user/video_list', method: 'get', params, data })
}

export function panel(params?: any, data?: any) {
  return request({ url: '/user/panel', method: 'get', params, data })
}

export function friends(params?: any, data?: any) {
  return request({ url: '/user/friends', method: 'get', params, data })
}

export function userCollect(params?: any, data?: any) {
  return request({ url: '/user/collect', method: 'get', params, data })
}

export function recommendedPost(params?: any, data?: any) {
  return request({ url: '/post/recommended', method: 'get', params, data })
}

export function recommendedShop(params?: any, data?: any) {
  return request({ url: '/shop/recommended', method: 'get', params, data })
}

export function uploadVideo(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request({ url: '/upload/video', method: 'post', data: formData, headers: { 'Content-Type': 'multipart/form-data' } })
}

export function uploadImage(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request({ url: '/upload/image', method: 'post', data: formData, headers: { 'Content-Type': 'multipart/form-data' } })
}

export function updateProfile(data: { nickname?: string; signature?: string; gender?: number; birthday?: string; province?: string; city?: string }) {
  return request({ url: '/user/profile', method: 'put', data })
}

export function updateAvatar(url: string) {
  return request({ url: '/user/avatar', method: 'put', data: { url } })
}

export function updateCover(url: string) {
  return request({ url: '/user/cover', method: 'put', data: { url } })
}

export function toggleFollowUser(userId: number) {
  return request({ url: `/user/follow/${userId}`, method: 'post' })
}

export function getFollowings(uid: number) {
  return request({ url: '/user/followings', method: 'get', params: { uid } })
}

export function getFollowers(uid: number) {
  return request({ url: '/user/followers', method: 'get', params: { uid } })
}
