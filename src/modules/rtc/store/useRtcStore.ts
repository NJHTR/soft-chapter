import { defineStore } from 'pinia'
import {
  createCall,
  getToken,
  acceptCall,
  rejectCall,
  cancelCall,
  hangupCall,
  joinCall,
  getCallDetail,
  genClientRequestId
} from '@/api/rtc'
import { sendCallSignal } from '@/utils/socket'
import { useBaseStore } from '@/store/pinia'
import { rtcMediaPort } from '@/modules/rtc/adapter/livekitAdapter'
import {
  ACCEPTING_CALL_STATES,
  TERMINAL_CALL_STATES,
  type CallMode,
  type CallPhase,
  type CallSession,
  type CallDetail,
  type DeviceStates,
  type IncomingCall,
  type OutgoingMeta,
  type RtcParticipant
} from '@/modules/rtc/types'

let pollTimer: ReturnType<typeof setInterval> | null = null
let durationTimer: ReturnType<typeof setInterval> | null = null
let idleTimer: ReturnType<typeof setTimeout> | null = null
let reconnectTimer: ReturnType<typeof setTimeout> | null = null
let joining = false
let rejoining = false
let listenersReady = false

function extractErr(res: any): string {
  const d = res?.data
  if (typeof d === 'string') return d
  return d?.msg || d?.message || '操作失败,请重试'
}

function clearTimer(timer: ReturnType<typeof setInterval | typeof setTimeout> | null) {
  if (timer) {
    clearInterval(timer)
    clearTimeout(timer)
  }
  return null
}

function genEventId(kind: string): string {
  return `client:${kind}:${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`
}

function genTraceId(): string {
  return `rtc-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`
}

export const useRtcStore = defineStore('rtc', {
  state: () => ({
    phase: 'idle' as CallPhase,
    session: null as CallSession | null,
    participants: [] as RtcParticipant[],
    remoteStreams: {} as Record<string, MediaStream>,
    remoteMuted: {} as Record<string, { audio: boolean; video: boolean }>,
    localStream: null as MediaStream | null,
    incoming: null as IncomingCall | null,
    outgoingMeta: null as OutgoingMeta | null,
    devices: {
      audioMuted: false,
      videoOff: false,
      speakerOn: true,
      activeInputId: null,
      activeOutputId: null
    } as DeviceStates,
    durationSeconds: 0,
    error: null as string | null,
    reconnectCount: 0,
    endReason: null as string | null,
    peerLeft: false,
    minimized: false,
    joined: false,
    facing: 'user' as 'user' | 'environment',
    activeSpeaker: null as string | null,
    traceId: null as string | null
  }),
  getters: {
    mode(state): CallMode {
      if (state.session?.mode) return state.session.mode
      if (state.outgoingMeta) return state.outgoingMeta.isVideo ? 'video' : 'audio'
      if (state.incoming) return state.incoming.mode
      return 'audio'
    },
    peerId(): string {
      const me = String(this.myId)
      return this.participants.find((p) => p.userId !== me)?.userId || ''
    },
    myId(): string {
      return String((useBaseStore().userinfo as any).uid ?? '')
    },
    durationText(): string {
      const m = Math.floor(this.durationSeconds / 60)
      const s = this.durationSeconds % 60
      return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
    }
  },
  actions: {
    /** 面板挂载时调用一次:注册 adapter 回调 + 全局重连监听 */
    init() {
      if (listenersReady) return
      listenersReady = true
      rtcMediaPort.setCallbacks({
        onRemoteTrack: (identity, stream) => {
          this.remoteStreams = { ...this.remoteStreams, [identity]: stream }
          if (!this.remoteMuted[identity]) {
            this.remoteMuted = { ...this.remoteMuted, [identity]: { audio: false, video: false } }
          }
        },
        onRemoteTrackRemoved: (identity) => {
          const next = { ...this.remoteStreams }
          delete next[identity]
          this.remoteStreams = next
          const muted = { ...this.remoteMuted }
          delete muted[identity]
          this.remoteMuted = muted
        },
        onConnectionState: (kind) => this.onAdapterConnection(kind),
        onClose: () => this.onAdapterClose(),
        onRemoteMute: (identity, kind, muted) => {
          const current = this.remoteMuted[identity] || { audio: false, video: false }
          this.remoteMuted = { ...this.remoteMuted, [identity]: { ...current, [kind]: muted } }
        },
        onActiveSpeaker: (identity) => {
          this.activeSpeaker = identity
        },
        onLocalTrack: (stream) => {
          this.localStream = stream
        }
      })
      document.addEventListener('visibilitychange', () => this.onVisibilityChange())
    },

    /** 发起方:入口,置 dialing 后自动 dial */
    openDial(meta: OutgoingMeta) {
      this.init()
      if (this.phase !== 'idle' && this.phase !== 'ended') return
      this.hardResetState()
      this.outgoingMeta = {
        toUserId: String(meta.toUserId),
        name: meta.name || '',
        avatar: meta.avatar || '',
        isVideo: !!meta.isVideo
      }
      this.traceId = genTraceId()
      this.phase = 'dialing'
      this.dial()
    },

    /** 发起方:创建会话 → WS 通知被叫 → 轮询 ACCEPTED → join → 轮询 CONNECTED */
    async dial() {
      const meta = this.outgoingMeta
      if (!meta) return
      const res = await createCall({
        scope: 'direct',
        mode: meta.isVideo ? 'video' : 'audio',
        target_user_id: meta.toUserId,
        client_request_id: genClientRequestId(),
        trace_id: this.traceId || undefined
      })
      if (!res.success || !res.data?.call_id) {
        this.error = extractErr(res)
        this.finishEnded(null)
        return
      }
      this.session = res.data
      const me = useBaseStore().userinfo
      sendCallSignal(meta.toUserId, 'call_request', {
        call_id: res.data.call_id,
        mode: res.data.mode,
        isVideo: meta.isVideo,
        name: me.nickname || '用户',
        avatar: me.avatar_168x168?.url_list?.[0] || ''
      })
      this.startPolling()
    },

    /** 被叫方:来电入口 */
    async notifyIncoming(payload: IncomingCall) {
      this.init()
      if (
        this.phase === 'dialing' ||
        this.phase === 'ringingIn' ||
        this.phase === 'connecting' ||
        this.phase === 'connected' ||
        this.phase === 'reconnecting'
      ) {
        return
      }
      const detail = await getCallDetail(payload.callId)
      const call = detail.data?.call as CallSession | undefined
      const members = detail.data?.participants || []
      if (
        !detail.success ||
        !call ||
        String(call.state).toUpperCase() !== 'RINGING' ||
        String(call.initiator_id) !== String(payload.fromUserId) ||
        !members.some((p: any) => String(p.user_id) === this.myId)
      ) {
        return
      }
      this.hardResetState()
      this.incoming = payload
      this.traceId = genTraceId()
      this.phase = 'ringingIn'
    },

    /** 被叫方:接听 */
    async accept() {
      if (!this.incoming?.callId || this.phase !== 'ringingIn') return
      this.phase = 'connecting'
      const res = await acceptCall(this.incoming.callId, {
        event_id: genEventId('accept'),
        trace_id: this.traceId || undefined
      })
      if (!res.success || !res.data?.call_id) {
        this.error = extractErr(res)
        this.finishEnded(null)
        return
      }
      this.session = res.data
      this.outgoingMeta = {
        toUserId: String(this.incoming.fromUserId),
        name: this.incoming.name,
        avatar: this.incoming.avatar,
        isVideo: this.incoming.mode === 'video'
      }
      this.incoming = null
      await this.connectFor()
      this.startPolling()
    },

    /** 被叫方:拒绝 */
    async reject() {
      if (this.incoming?.callId) {
        const res = await rejectCall(this.incoming.callId, {
          event_id: genEventId('reject'),
          trace_id: this.traceId || undefined
        })
        if (!res.success) this.error = extractErr(res)
      }
      this.finishEnded(null)
    },

    /** 发起方:振铃期取消 */
    async cancel() {
      const callId = this.session?.call_id
      if (callId) {
        const res = await cancelCall(callId, {
          event_id: genEventId('cancel'),
          trace_id: this.traceId || undefined
        })
        if (!res.success) this.error = extractErr(res)
      }
      this.finishEnded(null)
    },

    /** 挂断(幂等,错误忽略) */
    async hangup() {
      if (this.phase === 'idle') return
      const callId = this.session?.call_id || this.incoming?.callId
      this.stopPolling()
      if (callId) {
        await hangupCall(callId, {
          event_id: genEventId('hangup'),
          trace_id: this.traceId || undefined
        })
      }
      this.finishEnded(null)
    },

    /** token + join;RINGING 守卫报错视为"尚未接听",由轮询继续 */
    async connectFor() {
      if (joining || this.joined) return
      const callId = this.session?.call_id
      if (!callId) return
      joining = true
      try {
        const tk = await getToken({ call_id: callId, trace_id: this.traceId || undefined })
        if (!tk.success || !tk.data?.token) {
          const msg = String(extractErr(tk))
          if (!/CALL_NOT_ACCEPTED|RINGING/i.test(msg)) {
            this.error = msg
          }
          return
        }
        this.error = null
        const { token, room_name: roomName, identity } = tk.data
        const joined = await joinCall(callId, {
          event_id: genEventId('join'),
          trace_id: this.traceId || undefined
        })
        if (!joined.success) {
          this.error = extractErr(joined)
          return
        }
        await rtcMediaPort.join({ token, roomName, identity, mode: this.mode })
        this.joined = true
        this.phase = 'connecting'
      } catch (e) {
        console.warn('[rtc] join failed', e)
        this.error = '连接房间失败,正在重试'
      } finally {
        joining = false
      }
    },

    /** 1s 轮询 detail,驱动状态机 */
    startPolling() {
      this.stopPolling()
      pollTimer = setInterval(() => this.pollTick(), 1000)
      this.pollTick()
    },
    stopPolling() {
      pollTimer = clearTimer(pollTimer)
    },
    async pollTick() {
      const callId = this.session?.call_id
      if (!callId) return
      const res = await getCallDetail(callId)
      if (!res.success) return
      const detail = res.data as CallDetail
      const call = detail.call
      this.session = Object.assign({}, this.session, call)
      this.participants = (detail.participants || []).map((p) => ({
        userId: String(p.user_id ?? ''),
        role: p.role || '',
        state: p.state || ''
      }))
      const myId = this.myId
      const peer = (detail.participants || []).find((p) => String(p.user_id ?? '') !== myId)
      if (peer && String(peer.state || '').toUpperCase() === 'LEFT') this.peerLeft = true

      if (TERMINAL_CALL_STATES.includes(call.state)) {
        this.stopPolling()
        this.finishEnded(call.end_reason)
        return
      }
      if (call.state === 'CONNECTED') {
        if (this.phase !== 'reconnecting') this.phase = 'connected'
        this.startDuration()
        return
      }
      if (ACCEPTING_CALL_STATES.includes(call.state) && !this.joined) {
        this.connectFor()
      }
    },

    onAdapterConnection(kind: 'connected' | 'reconnecting' | 'disconnected') {
      if (kind === 'connected') {
        if (this.phase === 'reconnecting') this.phase = 'connected'
        this.reconnectCount = 0
        reconnectTimer = clearTimer(reconnectTimer)
        return
      }
      if (this.phase !== 'connected' && this.phase !== 'reconnecting') return
      this.phase = 'reconnecting'
      this.scheduleReconnect(1)
    },

    onAdapterClose() {
      if (this.phase !== 'connected' && this.phase !== 'reconnecting') return
      this.remoteStreams = {}
      this.remoteMuted = {}
      this.localStream = null
    },

    onVisibilityChange() {
      if (document.visibilityState !== 'visible' || this.phase !== 'reconnecting') return
      reconnectTimer = clearTimer(reconnectTimer)
      this.tryRejoin()
    },

    scheduleReconnect(attempt: number) {
      if (reconnectTimer) return
      if (attempt > 3) {
        this.error = '连接中断,请挂断后重试'
        return
      }
      this.reconnectCount = attempt
      reconnectTimer = setTimeout(async () => {
        reconnectTimer = clearTimer(reconnectTimer)
        if (this.phase !== 'reconnecting') return
        await this.tryRejoin()
        if (this.phase === 'reconnecting') this.scheduleReconnect(attempt + 1)
      }, 5000)
    },

    /** 重连:重新取 token + join(新 Room) */
    async tryRejoin() {
      const callId = this.session?.call_id
      if (!callId || rejoining) return
      rejoining = true
      try {
        const tk = await getToken({ call_id: callId, trace_id: this.traceId || undefined })
        if (!tk.success || !tk.data?.token) return
        const { token, room_name: roomName, identity } = tk.data
        rtcMediaPort.dispose()
        this.joined = false
        await rtcMediaPort.join({ token, roomName, identity, mode: this.mode })
        this.joined = true
      } catch (e) {
        console.warn('[rtc] rejoin failed', e)
      } finally {
        rejoining = false
      }
    },

    startDuration() {
      if (durationTimer) return
      durationTimer = setInterval(() => {
        this.durationSeconds++
      }, 1000)
    },
    stopDuration() {
      durationTimer = clearTimer(durationTimer)
      this.durationSeconds = 0
    },

    finishEnded(reason: string | null) {
      this.stopPolling()
      this.stopDuration()
      reconnectTimer = clearTimer(reconnectTimer)
      this.phase = 'ended'
      if (this.joined || this.localStream) rtcMediaPort.dispose()
      this.joined = false
      this.remoteStreams = {}
      this.remoteMuted = {}
      this.localStream = null
      this.endReason = reason || null
      this.scheduleIdle()
    },

    scheduleIdle() {
      idleTimer = clearTimer(idleTimer)
      idleTimer = setTimeout(() => this.hardResetState(), 10000)
    },
    clearIdleTimer() {
      idleTimer = clearTimer(idleTimer)
    },

    hardResetState() {
      this.clearIdleTimer()
      this.stopPolling()
      this.stopDuration()
      reconnectTimer = clearTimer(reconnectTimer)
      this.phase = 'idle'
      this.session = null
      this.participants = []
      this.remoteStreams = {}
      this.localStream = null
      this.incoming = null
      this.outgoingMeta = null
      this.error = null
      this.reconnectCount = 0
      this.endReason = null
      this.peerLeft = false
      this.minimized = false
      this.joined = false
      this.facing = 'user'
      this.activeSpeaker = null
      this.traceId = null
      this.devices = {
        audioMuted: false,
        videoOff: false,
        speakerOn: true,
        activeInputId: null,
        activeOutputId: null
      }
    },

    // ── 设备控制(全部走 adapter) ──
    async toggleMute() {
      this.devices.audioMuted = !this.devices.audioMuted
      if (this.joined) await rtcMediaPort.setMuted(this.devices.audioMuted)
    },
    async toggleCamera() {
      if (this.mode !== 'video') return
      this.devices.videoOff = !this.devices.videoOff
      if (this.joined) await rtcMediaPort.setVideoEnabled(!this.devices.videoOff)
    },
    toggleSpeaker() {
      this.devices.speakerOn = !this.devices.speakerOn
      rtcMediaPort.setSpeaker(this.devices.speakerOn)
    },
    async switchCamera() {
      if (this.mode !== 'video') return
      this.facing = this.facing === 'user' ? 'environment' : 'user'
      if (this.joined) await rtcMediaPort.switchFacing(this.facing)
    },
    async setDevice(kind: 'audioinput' | 'audiooutput', deviceId: string) {
      if (kind === 'audioinput') this.devices.activeInputId = deviceId
      else this.devices.activeOutputId = deviceId
      if (this.joined) await rtcMediaPort.setDevice(kind, deviceId)
    },
    toggleMinimize() {
      this.minimized = !this.minimized
    }
  }
})
