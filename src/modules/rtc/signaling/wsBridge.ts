import { onSocketMsg } from '@/utils/socket'
import { useRtcStore } from '@/modules/rtc/store/useRtcStore'

/**
 * RTC-004/005 WS 信令桥:消费 call_request 拨号通知。
 * RTC_005 开启时同时处理群通话 call_request。
 * accept/reject/hangup 等一律走后端 REST。返回 cleanup。
 */
export function setupRtcSignaling(): () => void {
  if (import.meta.env.VITE_RTC_004 === 'off') return () => {}
  const RTC005 = import.meta.env.VITE_RTC_005 !== 'off'
  const cleanupSignal = onSocketMsg('call_signal', (msg: any) => {
    const store = useRtcStore()
    if (msg?.signal_type === 'call_busy') {
      const data = msg.data || {}
      const callId = data.call_id || msg.call_id
      if (callId && msg.from_user_id && String(msg.from_user_id) !== String(store.myId)) {
        void store.handleBusySignal({
          callId: String(callId),
          fromUserId: String(msg.from_user_id)
        })
      }
      return
    }
    if (msg?.signal_type === 'call_cancelled') {
      const data = msg.data || {}
      const callId = data.call_id || msg.call_id
      if (callId && msg.from_user_id && String(msg.from_user_id) !== String(store.myId)) {
        store.handleRemoteCancel(String(callId))
      }
      return
    }
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
  const cleanupReconciliation = setupRtcReconciliation()
  return () => {
    cleanupSignal()
    cleanupReconciliation()
  }
}

/** Durable state notifications are hints; the store fetches call detail before showing UI. */
export function setupRtcReconciliation(): () => void {
  return onSocketMsg('raw', (msg: any) => {
    const store = useRtcStore()
    if (msg?.type === 'rtc.call.reconciliation') {
      const calls = Array.isArray(msg.calls) ? msg.calls : []
      const pending = calls.find((call: any) =>
        ['RINGING', 'ACCEPTED', 'NEGOTIATING'].includes(String(call?.state).toUpperCase())
      )
      if (pending?.call_id) void store.reconcileIncomingCall(String(pending.call_id))
      return
    }
    if (msg?.type === 'rtc.call.state' && msg.call?.call_id) {
      const state = String(msg.call.state || '').toUpperCase()
      if (['RINGING', 'ACCEPTED', 'NEGOTIATING'].includes(state))
        void store.reconcileIncomingCall(String(msg.call.call_id))
    }
  })
}
