import { request } from '@/utils/request'

/** 获取当前用户的所有活跃会话 */
export function getSessions() {
  return request({ url: '/session', method: 'get' })
}

/** 撤销单个会话 (踢掉某个设备) */
export function revokeSession(sessionId: number) {
  return request({ url: `/session/${sessionId}`, method: 'delete' })
}

/** 一键登出所有其他设备 */
export function revokeAllOtherSessions() {
  return request({ url: '/session/all-others', method: 'delete' })
}
