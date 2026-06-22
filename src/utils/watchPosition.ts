import { getLastPosition } from '@/api/videos'
import { getBrowserFingerprint } from './fingerprint'

const STORAGE_KEY = '_dyn_wp' // watch positions

interface PositionStore {
  [videoId: string]: { pos: number; ts: number }
}

/** 读取 localStorage 中的所有观看位置 */
function loadLocalStore(): PositionStore {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : {}
  } catch {
    return {}
  }
}

/** 保存到 localStorage */
function saveLocalStore(store: PositionStore) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(store))
  } catch {
    /* quota exceeded */
  }
}

/** 从 localStorage 获取某视频的播放位置 (匿名用户) */
export function getLocalPosition(videoId: string): number {
  const store = loadLocalStore()
  const entry = store[videoId]
  if (entry && entry.pos > 1) return entry.pos
  return 0
}

/** 保存播放位置到 localStorage (匿名用户) */
export function saveLocalPosition(videoId: string, position: number) {
  const store = loadLocalStore()
  store[videoId] = { pos: Math.floor(position), ts: Date.now() }
  // 最多保留 200 条, 优先删除旧数据
  const keys = Object.keys(store)
  if (keys.length > 200) {
    keys.sort((a, b) => (store[a].ts || 0) - (store[b].ts || 0))
    const toRemove = keys.slice(0, keys.length - 200)
    for (const k of toRemove) delete store[k]
  }
  saveLocalStore(store)
}

/**
 * 获取断点位置 — 两套逻辑:
 *   - 已登录: 从后端 API 获取
 *   - 未登录: 从 localStorage 获取 (跨 session 持久)
 * 返回 0 表示无历史位置
 */
export async function getResumePosition(videoId: string, isLoggedIn: boolean): Promise<number> {
  if (isLoggedIn) {
    try {
      const res = await getLastPosition(videoId)
      if (res.success && res.data?.position > 1) return res.data.position
    } catch {
      /* network error, fallback silently */
    }
    return 0
  }
  return getLocalPosition(videoId)
}

/**
 * 持久化播放位置 — 两套逻辑自动切换,
 * 同时写入 localStorage 作为双保险
 */
export function persistPosition(videoId: string, position: number, isLoggedIn: boolean) {
  if (position < 2) return
  // 始终写 localStorage 备份 (已登录时也写, 极端情况下后端丢了还能救回)
  saveLocalPosition(videoId, position)
  // 已登录时不额外调后端, 因为 sendWatchProgress 已经附带了 last_position
}

/** 获取未登录用户标识, 用于区分不同设备 */
export async function getAnonymousId(): Promise<string> {
  return getBrowserFingerprint()
}
