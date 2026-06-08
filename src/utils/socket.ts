import bus from './bus'

let ws: WebSocket | null = null
let reconnectTimer: ReturnType<typeof setTimeout> | null = null
let openPromiseResolve: (() => void) | null = null
const handlers = new Map<string, Set<(msg: any) => void>>()

/** 注册消息处理器，返回取消注册函数 */
export function onSocketMsg(
  eventType: 'chat' | 'notification' | 'raw' | 'read_receipt' | 'group_message' | 'call_signal',
  fn: (msg: any) => void
) {
  if (!handlers.has(eventType)) handlers.set(eventType, new Set())
  handlers.get(eventType)!.add(fn)
  return () => {
    handlers.get(eventType)?.delete(fn)
  }
}

function dispatch(eventType: string, msg: any) {
  handlers.get(eventType)?.forEach((fn) => fn(msg))
}

export function connectSocket(): Promise<void> {
  if (ws && ws.readyState === WebSocket.OPEN) return Promise.resolve()
  if (ws && ws.readyState === WebSocket.CONNECTING) {
    return new Promise((resolve) => {
      openPromiseResolve = resolve
    })
  }

  const token = localStorage.getItem('token')
  if (!token) return Promise.reject(new Error('no token'))

  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  const wsUrl = `${protocol}//${location.host}/ws/chat?token=${token}`
  ws = new WebSocket(wsUrl)

  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => {
      reject(new Error('ws connect timeout'))
    }, 5000)

    ws!.onopen = () => {
      clearTimeout(timeout)
      console.log('[WS] connected at', wsUrl)
      if (reconnectTimer) {
        clearTimeout(reconnectTimer)
        reconnectTimer = null
      }
      if (openPromiseResolve) {
        openPromiseResolve()
        openPromiseResolve = null
      }
      resolve()
    }

    ws!.onmessage = (event) => {
      try {
        const msg = JSON.parse(event.data)
        console.log('[WS] received:', msg.type || msg.msg_type, msg)
        dispatch('raw', msg)
        // 已读回执：对方已读了我的消息
        if (msg.type === 'read_receipt') {
          dispatch('read_receipt', msg)
          bus.emit('READ_RECEIPT', msg)
          return
        }
        // 通知消息：type 为数字（通知类型1-5）且没有 to_user_id
        if (typeof msg.type === 'number' && msg.to_user_id === undefined) {
          dispatch('notification', msg)
          bus.emit('NEW_NOTIFICATION', msg)
        }
        // 群聊消息
        if (msg.type === 'group_message' && msg.group_id !== undefined) {
          dispatch('group_message', msg)
          bus.emit('GROUP_MESSAGE', msg)
        }
        // 聊天消息：有 to_user_id 字段
        if (
          msg.from_user_id !== undefined &&
          msg.to_user_id !== undefined &&
          msg.type !== 'call_signal'
        ) {
          dispatch('chat', msg)
          bus.emit('CHAT_MESSAGE', msg)
        }
        // 通话信令
        if (msg.type === 'call_signal') {
          dispatch('call_signal', msg)
          bus.emit('CALL_SIGNAL', msg)
        }
      } catch {
        /* ignore malformed messages */
      }
    }

    ws!.onclose = () => {
      ws = null
      reconnectTimer = setTimeout(() => connectSocket(), 3000)
    }

    ws!.onerror = () => {
      clearTimeout(timeout)
      reject(new Error('ws connect error'))
    }
  })
}

export function disconnectSocket() {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  if (ws) {
    ws.close()
    ws = null
  }
}

export function getSocket() {
  return ws
}

/** 发送通话信令，自动等待 WebSocket 就绪 */
export async function sendCallSignal(toUserId: number | string, signalType: string, data?: any) {
  try {
    await connectSocket()
  } catch {
    return
  }
  if (!ws || ws.readyState !== WebSocket.OPEN) return
  ws.send(
    JSON.stringify({
      type: 'call_signal',
      to_user_id: String(toUserId),
      signal_type: signalType,
      data: data || null
    })
  )
}

/** 发送群通话信令给多人（to_user_ids 逗号分隔），后端一次推送所有目标 */
export async function sendCallSignalToGroup(
  toUserIds: (number | string)[],
  signalType: string,
  data?: any,
  callId?: string
) {
  try {
    await connectSocket()
  } catch {
    return
  }
  if (!ws || ws.readyState !== WebSocket.OPEN) return
  const payload: any = {
    type: 'call_signal',
    to_user_ids: toUserIds.map(String).join(','),
    signal_type: signalType,
    data: data || null
  }
  if (callId) payload.call_id = String(callId)
  ws.send(JSON.stringify(payload))
}
