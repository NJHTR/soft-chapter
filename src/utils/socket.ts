import bus from './bus'

let ws: WebSocket | null = null
let reconnectTimer: ReturnType<typeof setTimeout> | null = null
const handlers = new Map<string, Set<(msg: any) => void>>()

/** 注册消息处理器，返回取消注册函数 */
export function onSocketMsg(eventType: 'chat' | 'notification' | 'raw', fn: (msg: any) => void) {
  if (!handlers.has(eventType)) handlers.set(eventType, new Set())
  handlers.get(eventType)!.add(fn)
  return () => { handlers.get(eventType)?.delete(fn) }
}

function dispatch(eventType: string, msg: any) {
  handlers.get(eventType)?.forEach(fn => fn(msg))
}

export function connectSocket() {
  if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) return

  const token = localStorage.getItem('token')
  if (!token) return

  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  const wsUrl = `${protocol}//${location.host}/ws/chat?token=${token}`
  ws = new WebSocket(wsUrl)

  ws.onopen = () => {
    console.log('[WS] connected at', wsUrl)
    if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null }
  }

  ws.onmessage = (event) => {
    try {
      const msg = JSON.parse(event.data)
      console.log('[WS] received:', msg.type !== undefined ? 'notification' : 'chat', msg)
      dispatch('raw', msg)
      // 通知消息：有 type 字段（通知类型1-5）且没有 to_user_id
      if (msg.type !== undefined && msg.to_user_id === undefined) {
        dispatch('notification', msg)
        bus.emit('NEW_NOTIFICATION', msg)
      }
      // 聊天消息：有 to_user_id 字段
      if (msg.from_user_id !== undefined && msg.to_user_id !== undefined) {
        dispatch('chat', msg)
        bus.emit('CHAT_MESSAGE', msg)
      }
    } catch { /* ignore malformed messages */ }
  }

  ws.onclose = () => {
    ws = null
    // 断线重连
    reconnectTimer = setTimeout(() => connectSocket(), 3000)
  }

  ws.onerror = () => { /* onclose 会处理 */ }
}

export function disconnectSocket() {
  if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null }
  if (ws) { ws.close(); ws = null }
}

export function getSocket() { return ws }
