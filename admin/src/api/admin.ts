import { request } from '@/utils/request'

// === Review ===
export function getPendingVideos(params?: { pageNo?: number; pageSize?: number; type?: string }) {
  return request({ url: '/admin/videos/pending', method: 'get', params })
}
export function approveVideo(id: number) {
  return request({ url: '/admin/videos/' + id + '/approve', method: 'post' })
}
export function rejectVideo(id: number, reason: string) {
  return request({ url: '/admin/videos/' + id + '/reject', method: 'post', data: { reason } })
}
export function getPendingMusic(params?: { pageNo?: number; pageSize?: number }) {
  return request({ url: '/admin/music/pending', method: 'get', params })
}
export function approveMusic(id: number) {
  return request({ url: '/admin/music/' + id + '/approve', method: 'post' })
}
export function rejectMusic(id: number, reason: string) {
  return request({ url: '/admin/music/' + id + '/reject', method: 'post', data: { reason } })
}
export function getAdminStats() {
  return request({ url: '/admin/stats', method: 'get' })
}

// === Search Alias ===
export function getSearchAliases(keyword?: string) {
  return request({ url: '/admin/search-alias/list', method: 'get', params: { keyword } })
}
export function createSearchAlias(data: any) {
  return request({ url: '/admin/search-alias', method: 'post', data })
}
export function updateSearchAlias(id: number, data: any) {
  return request({ url: '/admin/search-alias/' + id, method: 'put', data })
}
export function deleteSearchAlias(id: number) {
  return request({ url: '/admin/search-alias/' + id, method: 'delete' })
}

// === User Management ===
export function getUsers(params?: any) {
  return request({ url: '/admin/users/list', method: 'get', params })
}
export function changeUserRole(uid: number, role: string) {
  return request({ url: '/admin/users/' + uid + '/role', method: 'put', data: { role } })
}
export function banUser(uid: number) {
  return request({ url: '/admin/users/' + uid + '/ban', method: 'put' })
}
export function unbanUser(uid: number) {
  return request({ url: '/admin/users/' + uid + '/unban', method: 'put' })
}

// === Analytics ===
export function getAnalyticsOverview() {
  return request({ url: '/admin/analytics/overview', method: 'get' })
}
export function getVideoPostTrend(params?: { period?: string; days?: number }) {
  return request({ url: '/admin/analytics/video-post-trend', method: 'get', params })
}
export function getEngagementTrend(params?: { period?: string; days?: number; start?: string; end?: string }) {
  return request({ url: '/admin/analytics/engagement-trend', method: 'get', params })
}
export function getTopVideos(params?: { metric?: string; limit?: number }) {
  return request({ url: '/admin/analytics/top-videos', method: 'get', params })
}
export function getSearchStats(params?: { days?: number }) {
  return request({ url: '/admin/analytics/search-stats', method: 'get', params })
}
export function getGeoData() {
  return request({ url: '/admin/analytics/geo-data', method: 'get' })
}
export function getOnlineUsers() {
  return request({ url: '/admin/analytics/online-users', method: 'get' })
}
export function getUserActivityFlow(params?: { hours?: number }) {
  return request({ url: '/admin/analytics/user-activity-flow', method: 'get', params })
}
export function getUserProfile(uid: number) {
  return request({ url: '/admin/analytics/user-profile/' + uid, method: 'get' })
}
export function getVideoTags(videoId: string) {
  return request({ url: '/admin/analytics/video-tags/' + videoId, method: 'get' })
}
export function listVideoTags(params: {
  page?: number
  pageSize?: number
  keyword?: string
  extractStatus?: string
}) {
  return request({ url: '/admin/analytics/video-tags', method: 'get', params })
}
export function updateVideoTags(videoId: string, tags: any[]) {
  return request({ url: '/admin/analytics/video-tags/' + videoId, method: 'put', data: { tags } })
}

// === Content Feature Re-extraction ===
export function reExtractVideo(videoId: string) {
  return request({ url: '/admin/analytics/re-extract/' + videoId, method: 'post' })
}
export function reExtractAllFailed() {
  return request({ url: '/admin/analytics/re-extract-failed', method: 'post' })
}
export function getExtractQueueStatus() {
  return request({ url: '/admin/analytics/extract-queue-status', method: 'get' })
}

// === Enhanced Analytics ===
export function getDashboardSummary(params?: { start?: string; end?: string }) {
  return request({ url: '/admin/analytics/dashboard-summary', method: 'get', params })
}
export function getContentBreakdown() {
  return request({ url: '/admin/analytics/content-breakdown', method: 'get' })
}
export function getUserGrowth(params?: { days?: number }) {
  return request({ url: '/admin/analytics/user-growth', method: 'get', params })
}
export function getEngagementDetail(params?: { days?: number }) {
  return request({ url: '/admin/analytics/engagement-detail', method: 'get', params })
}
export function getTrafficSources(params?: { start?: string; end?: string }) {
  return request({ url: '/admin/analytics/traffic-sources', method: 'get', params })
}
export function getDeviceStats() {
  return request({ url: '/admin/analytics/device-stats', method: 'get' })
}
export function getEcommerceOverview(params?: { start?: string; end?: string }) {
  return request({ url: '/admin/analytics/ecommerce-overview', method: 'get', params })
}
export function getUserSegments() {
  return request({ url: '/admin/analytics/user-segments', method: 'get' })
}

// === Unified Metrics Query (design.md §2.1) ===
export function metricsQuery(data: {
  metrics: string[]
  timeRange: { start: string; end: string }
  interval?: string
  filters?: { field: string; operator: string; value: string }[]
  groupBy?: string[]
  compareWith?: string
}) {
  return request({ url: '/admin/analytics/metrics/query', method: 'post', data })
}

// === Audit Drill-down (design.md §2.2) ===
export function getAuditDrilldown(params?: { status?: string; page?: number; pageSize?: number }) {
  return request({ url: '/admin/analytics/audit-drilldown', method: 'get', params })
}

// === Recent Activity Feed ===
export function getRecentActivity(params?: { limit?: number }) {
  return request({ url: '/admin/analytics/recent-activity', method: 'get', params })
}

// === Dashboard Config ===
export function getDashboardConfig() {
  return request({ url: '/admin/dashboard/config', method: 'get' })
}
export function saveDashboardConfig(data: any) {
  return request({ url: '/admin/dashboard/config', method: 'put', data })
}
