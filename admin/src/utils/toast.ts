import { reactive } from 'vue'

export interface ToastItem {
  id: number
  message: string
  type: 'success' | 'error' | 'warning' | 'info'
}

let nextId = 1
export const toasts = reactive<ToastItem[]>([])

function addToast(message: string, type: ToastItem['type'], duration = 3000) {
  const id = nextId++
  toasts.push({ id, message, type })
  if (duration > 0) {
    setTimeout(() => {
      const idx = toasts.findIndex(t => t.id === id)
      if (idx >= 0) toasts.splice(idx, 1)
    }, duration)
  }
}

export const toast = {
  success(msg: string, duration?: number) { addToast(msg, 'success', duration) },
  error(msg: string, duration?: number) { addToast(msg, 'error', duration ?? 5000) },
  warning(msg: string, duration?: number) { addToast(msg, 'warning', duration) },
  info(msg: string, duration?: number) { addToast(msg, 'info', duration) }
}
