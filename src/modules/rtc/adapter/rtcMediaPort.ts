/** RTC-004 媒体端口抽象:所有 WebRTC / getUserMedia / srcObject 操作全部收进 adapter 实现,页面只消费事件 */

export interface RtcMediaPortCallbacks {
  /** 远端参与者发布新音轨(组装进其 MediaStream 后回调;同一 stream 实例会被反复更新) */
  onRemoteTrack(identity: string, stream: MediaStream): void
  /** 远端参与者全部音轨移除/离开 */
  onRemoteTrackRemoved(identity: string): void
  onConnectionState(kind: 'connected' | 'reconnecting' | 'disconnected'): void
  /** 房间被关闭/本方 disconnect 后触发 */
  onClose(): void
  onRemoteMute(identity: string, kind: 'audio' | 'video', muted: boolean): void
  onActiveSpeaker(identity: string | null): void
  /** 本地预览流(发布后回调,由锚点元素 :srcObject 绑定) */
  onLocalTrack(stream: MediaStream): void
}

export interface RtcMediaPortJoinOptions {
  token: string
  roomName: string
  identity: string
  mode: 'audio' | 'video'
}

export interface RtcMediaPort {
  setCallbacks(cb: RtcMediaPortCallbacks): void
  /** connect + 自动发布音轨(join 后按 mode 开启麦克风/摄像头) */
  join(opts: RtcMediaPortJoinOptions): Promise<void>
  setMuted(muted: boolean): Promise<void>
  setVideoEnabled(enabled: boolean): Promise<void>
  /** 免提开关:作用于已注册的 audio/video 元素的输出设备 */
  setSpeaker(on: boolean): void
  switchFacing(facing: 'user' | 'environment'): Promise<void>
  setDevice(kind: 'audioinput' | 'audiooutput', deviceId: string): Promise<void>
  /** 断开房间(保留实例可复用) */
  leave(): void
  /** 断开 + 清理全部监听,不可复用 */
  dispose(): void
}

/** 输出设备应用器:由具体 adapter 注入,注册/反注册元素后会立即重放当前输出状态 */
let applier: (() => void) | null = null
export function setRtOutputApplier(fn: (() => void) | null): void {
  applier = fn
}

/** 页面把承载远端音视频的锚点元素按 key 注册进来,避免视频/音频切换时清掉彼此。 */
export function registerRtOutputEl(key: string, el: HTMLMediaElement | null): void {
  if (el) {
    outputEls.set(key, el)
  } else {
    outputEls.delete(key)
  }
  applier?.()
}

export function getRtOutputEls(): HTMLMediaElement[] {
  return Array.from(outputEls.values())
}

const outputEls = new Map<string, HTMLMediaElement>()
