import { onSocketMsg } from '@/utils/socket'
import { useRtcStore } from '@/modules/rtc/store/useRtcStore'

/**
 * RTC-004/005 WS 信令桥:消费 call_request 拨号通知。
 * RTC_005 开启时同时处理群通话 call_request。
 * accept/reject/hangup 等一律走后端 REST。返回 cleanup。
 */
export function setupRtcSignaling(): () => void {
  const RTC005 = import.meta.env.VITE_RTC_005 !== 'off'
  return onSocketMsg('call_signal', (msg: any) => {
    const store = useRtcStore()
    if (msg?.signal_type !== 'call_request') return
    const data = msg.data || {}
    const isGroup = !!(data.isGroup || data.scope === 'group')
    if (isGroup && !RTC005) return
    if (!data.call_id) return
    if (!msg.from_user_id || String(msg.from_user_id) === String(store.myId)) return
    store.notifyIncoming({
      callId: String(data.call_id),
      fromUserId: String(msg.from_user_id ?? ''),
      name: data.name || '用户',
      avatar: data.avatar || '',
      mode: data.isVideo ? 'video' : 'audio',
      isGroup,
      groupMembers: Array.isArray(data.groupMembers) ? data.groupMembers : []
    })
  })
}
