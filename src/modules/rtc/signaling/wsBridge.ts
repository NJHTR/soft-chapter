import { onSocketMsg } from '@/utils/socket'
import { useRtcStore } from '@/modules/rtc/store/useRtcStore'

/**
 * RTC-004 旧 WS 信令桥:仅消费 1:1 的 call_request(拨号通知)。
 * accept/reject/hangup 等一律走后端 REST,不在此处处理(避免与 t_message 投影重复)。
 * 返回 cleanup。
 */
export function setupRtcSignaling(): () => void {
  return onSocketMsg('call_signal', (msg: any) => {
    const store = useRtcStore()
    if (msg?.signal_type !== 'call_request') return
    const data = msg.data || {}
    if (data.isGroup || data.scope === 'group') return
    if (!data.call_id) return
    if (!msg.from_user_id || String(msg.from_user_id) === String(store.myId)) return
    store.notifyIncoming({
      callId: String(data.call_id),
      fromUserId: String(msg.from_user_id ?? ''),
      name: data.name || '用户',
      avatar: data.avatar || '',
      mode: data.isVideo ? 'video' : 'audio'
    })
  })
}
