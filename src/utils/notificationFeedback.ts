export type FeedbackKind =
  | 'chat'
  | 'group'
  | 'follow'
  | 'like'
  | 'comment'
  | 'collect'
  | 'mention'
  | 'friend'
  | 'call'

export interface NotificationFeedbackSettings {
  enabled: boolean
  soundEnabled: boolean
  vibrationEnabled: boolean
  volume: number
  soundUrls: Partial<Record<FeedbackKind, string>>
  vibrationPatterns: Partial<Record<FeedbackKind, number[]>>
}

const STORAGE_KEY = 'douyin.notification-feedback.v1'
const DEFAULT_PATTERN: number[] = [80]
const DEFAULT_PATTERNS: Record<FeedbackKind, number[]> = {
  chat: [60],
  group: [40, 40, 40],
  follow: [90, 50, 90],
  like: [35],
  comment: [60, 35, 60],
  collect: [100, 50, 100],
  mention: [45, 35, 45],
  friend: [120, 60, 120],
  call: [220, 100, 220, 100, 220]
}
let settings: NotificationFeedbackSettings = readSettings()
let audioContext: AudioContext | null = null
let audioUnlocked = false
let unlockInstalled = false
let callRingtoneTimer: ReturnType<typeof setInterval> | null = null

function readSettings(): NotificationFeedbackSettings {
  const defaults: NotificationFeedbackSettings = {
    enabled: true,
    soundEnabled: true,
    vibrationEnabled: true,
    volume: 0.55,
    soundUrls: {},
    vibrationPatterns: {}
  }
  try {
    if (typeof localStorage === 'undefined') return defaults
    const stored = JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}')
    return {
      ...defaults,
      ...stored,
      volume: Math.max(0, Math.min(1, Number(stored.volume ?? defaults.volume))),
      soundUrls: stored.soundUrls || {},
      vibrationPatterns: stored.vibrationPatterns || {}
    }
  } catch {
    return defaults
  }
}

function persist() {
  try {
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(settings))
    }
  } catch (error) {
    console.warn('[notification-feedback] settings could not be persisted', error)
  }
}

export function getNotificationFeedbackSettings(): NotificationFeedbackSettings {
  return JSON.parse(JSON.stringify(settings))
}

export function updateNotificationFeedbackSettings(
  patch: Partial<NotificationFeedbackSettings>
): NotificationFeedbackSettings {
  settings = { ...settings, ...patch }
  persist()
  return getNotificationFeedbackSettings()
}

export function resetNotificationFeedbackSettings() {
  try {
    if (typeof localStorage !== 'undefined') localStorage.removeItem(STORAGE_KEY)
  } catch {
    /* ignore storage access errors */
  }
  settings = readSettings()
  return getNotificationFeedbackSettings()
}

export function setVibrationPattern(kind: FeedbackKind, pattern: number[]) {
  settings.vibrationPatterns = { ...settings.vibrationPatterns, [kind]: pattern }
  persist()
}

export function setCustomNotificationSound(kind: FeedbackKind, file: File): Promise<void> {
  return new Promise((resolve, reject) => {
    if (!file.type.startsWith('audio/')) {
      reject(new Error('请选择音频文件'))
      return
    }
    if (file.size > 2 * 1024 * 1024) {
      reject(new Error('自定义声音不能超过 2MB'))
      return
    }
    const reader = new FileReader()
    reader.onerror = () => reject(reader.error || new Error('声音读取失败'))
    reader.onload = () => {
      settings.soundUrls = { ...settings.soundUrls, [kind]: String(reader.result || '') }
      persist()
      resolve()
    }
    reader.readAsDataURL(file)
  })
}

export function installNotificationFeedbackUnlock(): () => void {
  if (unlockInstalled || typeof window === 'undefined') return () => {}
  unlockInstalled = true
  const unlock = () => {
    audioUnlocked = true
    if (audioContext?.state === 'suspended') void audioContext.resume()
    window.removeEventListener('pointerdown', unlock)
    window.removeEventListener('keydown', unlock)
    unlockInstalled = false
  }
  window.addEventListener('pointerdown', unlock, { once: true, passive: true })
  window.addEventListener('keydown', unlock, { once: true })
  return () => {
    window.removeEventListener('pointerdown', unlock)
    window.removeEventListener('keydown', unlock)
    unlockInstalled = false
  }
}

function getAudioContext(): AudioContext | null {
  if (typeof window === 'undefined') return null
  const AudioContextCtor = window.AudioContext || (window as any).webkitAudioContext
  if (!AudioContextCtor) return null
  if (!audioContext) audioContext = new AudioContextCtor()
  return audioContext
}

function scheduleTone(
  context: AudioContext,
  frequency: number,
  start: number,
  duration: number,
  peak: number,
  type: OscillatorType = 'sine',
  endFrequency?: number
) {
  const oscillator = context.createOscillator()
  const gain = context.createGain()
  oscillator.type = type
  oscillator.frequency.setValueAtTime(frequency, start)
  if (endFrequency) oscillator.frequency.exponentialRampToValueAtTime(endFrequency, start + duration)
  gain.gain.setValueAtTime(0.0001, start)
  gain.gain.exponentialRampToValueAtTime(Math.max(0.01, peak), start + 0.008)
  gain.gain.exponentialRampToValueAtTime(0.0001, start + duration)
  oscillator.connect(gain)
  gain.connect(context.destination)
  oscillator.start(start)
  oscillator.stop(start + duration + 0.02)
}

function scheduleNoise(
  context: AudioContext,
  start: number,
  duration: number,
  peak: number,
  frequency: number
) {
  const frameCount = Math.max(1, Math.floor(context.sampleRate * duration))
  const buffer = context.createBuffer(1, frameCount, context.sampleRate)
  const data = buffer.getChannelData(0)
  for (let i = 0; i < frameCount; i++) data[i] = Math.random() * 2 - 1
  const source = context.createBufferSource()
  const filter = context.createBiquadFilter()
  const gain = context.createGain()
  source.buffer = buffer
  filter.type = 'bandpass'
  filter.frequency.value = frequency
  filter.Q.value = 0.7
  gain.gain.setValueAtTime(0.0001, start)
  gain.gain.exponentialRampToValueAtTime(Math.max(0.01, peak), start + 0.006)
  gain.gain.exponentialRampToValueAtTime(0.0001, start + duration)
  source.connect(filter)
  filter.connect(gain)
  gain.connect(context.destination)
  source.start(start)
  source.stop(start + duration + 0.02)
}

function scheduleKnock(context: AudioContext, start: number, peak: number) {
  scheduleTone(context, 155, start, 0.11, peak, 'sine', 72)
  scheduleNoise(context, start, 0.045, peak * 0.45, 950)
}

function scheduleChime(context: AudioContext, start: number, frequency: number, peak: number) {
  scheduleTone(context, frequency, start, 0.32, peak, 'sine', frequency * 0.98)
  scheduleTone(context, frequency * 2, start, 0.22, peak * 0.35, 'sine')
}

async function playDefaultTone(kind: FeedbackKind) {
  if (!audioUnlocked) return
  const context = getAudioContext()
  if (!context) return
  if (context.state === 'suspended') await context.resume().catch(() => {})
  const now = context.currentTime + 0.01
  const peak = Math.min(0.48, Math.max(0.04, settings.volume * 0.42))

  switch (kind) {
    // 具有辨识度的双敲门声，作为私聊默认音。
    case 'chat':
      scheduleKnock(context, now, peak)
      scheduleKnock(context, now + 0.18, peak * 0.9)
      break
    // 群聊用三连敲，和私聊保持明显区别。
    case 'group':
      scheduleKnock(context, now, peak)
      scheduleKnock(context, now + 0.16, peak * 0.9)
      scheduleKnock(context, now + 0.32, peak * 0.8)
      break
    // 关注/收藏使用上扬的短铃声。
    case 'follow':
      scheduleChime(context, now, 620, peak)
      scheduleChime(context, now + 0.16, 820, peak * 0.9)
      scheduleChime(context, now + 0.32, 1040, peak * 0.8)
      break
    case 'collect':
      scheduleChime(context, now, 740, peak)
      scheduleChime(context, now + 0.18, 980, peak * 0.85)
      break
    // 点赞是短促气泡音，评论带一段轻微的咳嗽感噪声。
    case 'like':
      scheduleTone(context, 360, now, 0.08, peak, 'triangle', 720)
      break
    case 'comment':
      scheduleNoise(context, now, 0.16, peak * 0.72, 900)
      scheduleNoise(context, now + 0.13, 0.12, peak * 0.58, 1250)
      scheduleTone(context, 170, now, 0.22, peak * 0.38, 'sine', 110)
      break
    case 'mention':
      scheduleTone(context, 760, now, 0.13, peak, 'sine', 980)
      scheduleTone(context, 980, now + 0.16, 0.16, peak * 0.85, 'sine', 1180)
      break
    case 'friend':
      scheduleTone(context, 430, now, 0.2, peak, 'triangle', 520)
      scheduleTone(context, 650, now + 0.2, 0.25, peak * 0.9, 'triangle', 760)
      break
    // 来电使用双段电话铃，存在感最强。
    case 'call':
      scheduleTone(context, 440, now, 0.38, peak, 'sine', 520)
      scheduleTone(context, 660, now, 0.38, peak * 0.75, 'sine', 760)
      scheduleTone(context, 440, now + 0.55, 0.38, peak, 'sine', 520)
      scheduleTone(context, 660, now + 0.55, 0.38, peak * 0.75, 'sine', 760)
      break
  }
}

async function playCustomSound(url: string) {
  const audio = new Audio(url)
  audio.volume = settings.volume
  await audio.play().catch(() => {})
}

export async function playNotificationFeedback(kind: FeedbackKind) {
  if (!settings.enabled) return
  if (settings.soundEnabled) {
    const customUrl = settings.soundUrls[kind]
    if (customUrl) await playCustomSound(customUrl)
    else await playDefaultTone(kind)
  }
  if (settings.vibrationEnabled && typeof navigator !== 'undefined' && 'vibrate' in navigator) {
    const pattern = settings.vibrationPatterns[kind] || DEFAULT_PATTERNS[kind] || DEFAULT_PATTERN
    navigator.vibrate(pattern)
  }
}

/**
 * 通话建立前的持续提示音。必须由通话生命周期显式停止，避免在接听、
 * 拒绝、超时或挂断后继续播放。
 */
export function startCallRingtone(intervalMs = 1800) {
  stopCallRingtone()
  const tick = () => {
    void playNotificationFeedback('call')
  }
  tick()
  callRingtoneTimer = setInterval(tick, intervalMs)
}

export function stopCallRingtone() {
  if (callRingtoneTimer) {
    clearInterval(callRingtoneTimer)
    callRingtoneTimer = null
  }
  if (typeof navigator !== 'undefined' && 'vibrate' in navigator) {
    navigator.vibrate(0)
  }
}

export function notificationKindFromType(type: unknown): FeedbackKind {
  switch (Number(type)) {
    case 1:
      return 'follow'
    case 2:
      return 'like'
    case 3:
      return 'comment'
    case 4:
      return 'collect'
    case 5:
      return 'mention'
    case 6:
    case 7:
      return 'friend'
    default:
      return 'comment'
  }
}
