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
  return request({
    url: '/upload/video',
    method: 'post',
    data: formData /* let axios auto-set Content-Type with boundary */
  })
}

export function uploadImage(file: Blob | File) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/upload/image',
    method: 'post',
    data: formData /* let axios auto-set Content-Type with boundary */
  })
}

export function uploadVoice(file: Blob | File) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/upload/voice',
    method: 'post',
    data: formData /* let axios auto-set Content-Type with boundary */
  })
}

export function updateProfile(data: {
  nickname?: string
  signature?: string
  gender?: number
  birthday?: string
  province?: string
  city?: string
}) {
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

export function searchUsers(keyword: string) {
  return request({ url: '/user/search', method: 'get', params: { keyword } })
}

// 搜索历史
export function getSearchHistory() {
  return request({ url: '/search-history', method: 'get' })
}

export function saveSearchHistory(keyword: string) {
  return request({ url: '/search-history', method: 'post', data: { keyword } })
}

export function clearSearchHistory() {
  return request({ url: '/search-history', method: 'delete' })
}

export function deleteSearchHistoryKeyword(keyword: string) {
  return request({ url: '/search-history/keyword', method: 'delete', data: { keyword } })
}

export function getRecentAuthors() {
  return request({ url: '/user/recent-authors', method: 'get' })
}

export function recordVisit(userId: number) {
  return request({ url: `/user/visitors/${userId}`, method: 'post' })
}

export function getVisitors() {
  return request({ url: '/user/visitors', method: 'get' })
}

export function getMutualFriends(params?: any, data?: any) {
  return request({ url: '/user/friends-mutual', method: 'get', params, data })
}

export function setVisitorDisplay(enabled: boolean) {
  return request({ url: '/user/visitor-display', method: 'put', data: { enabled } })
}

// 朋友系统
export function sendFriendRequest(targetId: number) {
  return request({ url: '/user/friend/request', method: 'post', data: { target_id: targetId } })
}

export function acceptFriendRequest(fromId: number) {
  return request({ url: '/user/friend/accept', method: 'post', data: { from_id: fromId } })
}

export function rejectFriendRequest(fromId: number) {
  return request({ url: '/user/friend/reject', method: 'post', data: { from_id: fromId } })
}

export function getFriendList(params?: any, data?: any) {
  return request({ url: '/user/friend/list', method: 'get', params, data })
}

export function getFriendRequests(params?: any, data?: any) {
  return request({ url: '/user/friend/requests', method: 'get', params, data })
}

// 系统通知
export function getSystemNotices(params?: any, data?: any) {
  return request({ url: '/system-notice', method: 'get', params, data })
}

export function markNoticeRead(id: number) {
  return request({ url: `/system-notice/${id}/read`, method: 'put' })
}

export function markAllNoticesRead() {
  return request({ url: '/system-notice/read-all', method: 'put' })
}

// 登录设备管理
export function getLoginHistory(params?: any) {
  return request({ url: '/user/login-history', method: 'get', params })
}
