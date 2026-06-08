import { request } from '@/utils/request'

/** 发送私聊消息 */
export function sendMessage(data: {
  to_user_id: number
  content: string
  msg_type?: number
  extra?: string
}) {
  return request({ url: '/message/send', method: 'post', data })
}

/** 获取与某用户的聊天记录 */
export function getChatHistory(params: {
  with_user_id: number
  pageSize?: number
  beforeId?: number
}) {
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

// ========== 群聊 ==========

/** 创建群聊 */
export function createGroup(data: { name: string; member_uids: string[] }) {
  return request({ url: '/group/create', method: 'post', data })
}

/** 获取我加入的群聊列表 */
export function getGroupList() {
  return request({ url: '/group/list', method: 'get' })
}

/** 获取群聊会话列表(含最后消息和未读数) */
export function getGroupConversations() {
  return request({ url: '/group/conversations', method: 'get' })
}

/** 获取群信息 */
export function getGroupInfo(groupId: number) {
  return request({ url: `/group/${groupId}/info`, method: 'get' })
}

/** 获取群成员 */
export function getGroupMembers(groupId: number) {
  return request({ url: `/group/${groupId}/members`, method: 'get' })
}

/** 发送群消息 */
export function sendGroupMessage(
  groupId: number,
  data: { content: string; msg_type?: number; extra?: string }
) {
  return request({ url: `/group/${groupId}/message/send`, method: 'post', data })
}

/** 获取群消息历史 */
export function getGroupMessageHistory(
  groupId: number,
  params: { pageSize?: number; beforeId?: number }
) {
  return request({ url: `/group/${groupId}/message/history`, method: 'get', params })
}

/** 标记群消息已读 */
export function markGroupRead(groupId: number, lastMsgId: number) {
  return request({
    url: `/group/${groupId}/read`,
    method: 'post',
    data: { last_msg_id: lastMsgId }
  })
}

/** 邀请成员入群 */
export function addGroupMembers(groupId: number, memberUids: string[]) {
  return request({
    url: `/group/${groupId}/add-members`,
    method: 'post',
    data: { member_uids: memberUids }
  })
}

/** 退出群聊 */
export function leaveGroup(groupId: number) {
  return request({ url: `/group/${groupId}/leave`, method: 'post' })
}

/** 移除群成员 */
export function removeGroupMember(groupId: number, targetUid: number) {
  return request({
    url: `/group/${groupId}/remove-member`,
    method: 'post',
    data: { target_uid: targetUid }
  })
}

/** 搜索群聊 */
export function searchGroups(keyword: string) {
  return request({ url: '/group/search', method: 'get', params: { keyword } })
}
