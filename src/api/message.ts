import { request } from '@/utils/request'

/** 发送私聊消息 */
export function sendMessage(data: { to_user_id: number; content: string; msg_type?: number; extra?: string }) {
  return request({ url: '/message/send', method: 'post', data })
}

/** 获取与某用户的聊天记录 */
export function getChatHistory(params: { with_user_id: number; pageSize?: number; beforeId?: number }) {
  return request({ url: '/message/history', method: 'get', params })
}

/** 获取会话列表 */
export function getConversations() {
  return request({ url: '/message/conversations', method: 'get' })
}

/** 标记某用户的聊天消息为已读 */
export function markRead(fromUserId: number) {
  return request({ url: `/message/read/${fromUserId}`, method: 'post' })
}

/** 获取未读私聊消息总数 */
export function getUnreadMessageCount() {
  return request({ url: '/message/unread/count', method: 'get' })
}

/** 获取通知列表 */
export function getNotifications(params: { type?: number; pageSize?: number; beforeId?: number }) {
  return request({ url: '/message/notifications', method: 'get', params })
}

/** 获取各类通知未读数 */
export function getNotificationUnread() {
  return request({ url: '/message/notifications/unread', method: 'get' })
}

/** 标记通知为已读 */
export function markNotificationRead(params: { type?: number }) {
  return request({ url: '/message/notifications/read', method: 'post', params })
}

/** 搜索聊天记录 */
export function searchChats(keyword: string) {
  return request({ url: '/message/search', method: 'get', params: { keyword } })
}

/** 搜索通知记录 */
export function searchNotifications(keyword: string) {
  return request({ url: '/message/notifications/search', method: 'get', params: { keyword } })
}
