import {
  Room,
  RoomEvent,
  ParticipantEvent,
  type ConnectionState,
  type LocalParticipant,
  type LocalTrackPublication,
  type LocalVideoTrack,
  type Participant,
  type RemoteParticipant,
  type RemoteTrack,
  type RemoteTrackPublication,
  Track,
  type TrackPublication,
  type LocalTrack
} from 'livekit-client'
import {
  getRtOutputEls,
  setRtOutputApplier,
  type RtcMediaPort,
  type RtcMediaPortCallbacks,
  type RtcMediaPortJoinOptions
} from './rtcMediaPort'

function resolveLiveKitUrl(): string {
  const configured = import.meta.env.VITE_LIVEKIT_URL as string | undefined
  if (configured?.trim()) {
    if (
      typeof window !== 'undefined' &&
      window.location.protocol === 'https:' &&
      configured.startsWith('ws://')
    ) {
      throw new Error('HTTPS 页面必须使用 wss:// 的 VITE_LIVEKIT_URL')
    }
    return configured.trim()
  }
  if (
    typeof window === 'undefined' ||
    ['localhost', '127.0.0.1', '[::1]'].includes(window.location.hostname)
  ) {
    return 'ws://localhost:7880'
  }
  throw new Error('未配置 VITE_LIVEKIT_URL，无法连接生产 LiveKit')
}

const LIVEKIT_URL: string = resolveLiveKitUrl()

const ROOM_OPTIONS = {
  adaptiveStream: true,
  dynacast: true
}

const VIDEO_CAPTURE_OPTIONS = {
  resolution: { width: 1280, height: 720 },
  frameRate: 30,
  facingMode: 'user' as const
}

/** livekit-client 2.x 的 RtcMediaPort 实现 */
class LiveKitMediaPort implements RtcMediaPort {
  private room: Room | null = null
  private cb: RtcMediaPortCallbacks = {
    onRemoteTrack: () => {},
    onRemoteTrackRemoved: () => {},
    onConnectionState: () => {},
    onClose: () => {},
    onRemoteMute: () => {},
    onActiveSpeaker: () => {},
    onLocalTrack: () => {}
  }
  private speakerOn = true
  private currentOutputId = ''
  /** 远端参与者 → 其 MediaStream(同 identity 已发布音轨的合集) */
  private remoteStreams = new Map<string, MediaStream>()
  private localTracks = new Map<string, MediaStreamTrack>()
  private intentionalRooms = new WeakSet<Room>()

  constructor() {
    setRtOutputApplier(() => this.applyOutputToElements())
  }

  setCallbacks(cb: RtcMediaPortCallbacks) {
    this.cb = cb
  }

  async join(opts: RtcMediaPortJoinOptions): Promise<void> {
    this.ensureFreshRoom()
    const room = this.room!
    await room.connect(LIVEKIT_URL, opts.token, { autoSubscribe: true })
    const lp = room.localParticipant
    if (opts.mode === 'video') {
      await this.tryEnableCamera(lp)
    }
    await lp.setMicrophoneEnabled(true)
    this.syncLocalTracks(lp)
  }

  private async tryEnableCamera(lp: LocalParticipant) {
    try {
      await lp.setCameraEnabled(true, VIDEO_CAPTURE_OPTIONS)
    } catch (e) {
      console.warn('[rtc] camera not available, fallback to audio only', e)
    }
  }

  private ensureFreshRoom() {
    if (this.room) {
      this.intentionalRooms.add(this.room)
      this.room.removeAllListeners()
      this.room.disconnect()
      this.room = null
    }
    this.remoteStreams.clear()
    this.localTracks.clear()
    const room = new Room(ROOM_OPTIONS)
    this.room = room
    this.wireEvents(room)
  }

  private wireEvents(room: Room) {
    room.on(RoomEvent.ConnectionStateChanged, (state: ConnectionState) => {
      if (this.intentionalRooms.has(room)) return
      if (state === 'connected') this.cb.onConnectionState('connected')
      else if (state === 'reconnecting') this.cb.onConnectionState('reconnecting')
      else if (state === 'disconnected') this.cb.onConnectionState('disconnected')
    })

    room.on(RoomEvent.Disconnected, () => {
      if (!this.intentionalRooms.has(room)) this.cb.onClose()
    })

    room.on(RoomEvent.TrackSubscribed, this.onTrackSubscribed)
    room.on(RoomEvent.TrackUnsubscribed, this.onTrackUnsubscribed)
    room.on(RoomEvent.TrackMuted, this.onTrackMuted)
    room.on(RoomEvent.TrackUnmuted, this.onTrackUnmuted)
    room.on(RoomEvent.ParticipantDisconnected, this.onParticipantDisconnected)
    room.on(RoomEvent.ActiveSpeakersChanged, this.onActiveSpeakersChanged)

    room.localParticipant.on(ParticipantEvent.LocalTrackPublished, this.onLocalTrackPublished)
    room.localParticipant.on(ParticipantEvent.LocalTrackUnpublished, this.onLocalTrackUnpublished)
  }

  private onLocalTrackPublished = (pub: LocalTrackPublication) => {
    const track: LocalTrack | undefined = pub.track
    if (!track) return
    this.localTracks.set(String(pub.source || pub.kind), track.mediaStreamTrack)
    this.emitLocalStream()
  }

  private onLocalTrackUnpublished = (pub: LocalTrackPublication) => {
    this.localTracks.delete(String(pub.source || pub.kind))
    this.emitLocalStream()
  }

  private syncLocalTracks(lp: LocalParticipant) {
    lp.trackPublications.forEach((pub) => {
      if (pub.track) {
        this.localTracks.set(String(pub.source || pub.kind), pub.track.mediaStreamTrack)
      }
    })
    this.emitLocalStream()
  }

  private emitLocalStream() {
    this.cb.onLocalTrack(new MediaStream(Array.from(this.localTracks.values())))
  }

  private onTrackSubscribed = (
    track: RemoteTrack,
    _publication: RemoteTrackPublication,
    participant: RemoteParticipant
  ) => {
    const identity = participant.identity
    let stream = this.remoteStreams.get(identity)
    if (!stream) {
      stream = new MediaStream()
      this.remoteStreams.set(identity, stream)
    }
    stream.addTrack(track.mediaStreamTrack)
    this.cb.onRemoteTrack(identity, stream)
  }

  private onTrackUnsubscribed = (
    track: Track,
    _publication: RemoteTrackPublication,
    participant: RemoteParticipant
  ) => {
    const identity = participant.identity
    const stream = this.remoteStreams.get(identity)
    if (!stream) return
    stream.removeTrack(track.mediaStreamTrack)
    if (stream.getTracks().length === 0) {
      this.remoteStreams.delete(identity)
      this.cb.onRemoteTrackRemoved(identity)
    } else {
      this.cb.onRemoteTrack(identity, stream)
    }
  }

  private onParticipantDisconnected = (participant: RemoteParticipant) => {
    this.remoteStreams.delete(participant.identity)
    this.cb.onRemoteTrackRemoved(participant.identity)
  }

  private onTrackMuted = (publication: TrackPublication, participant: Participant) => {
    if (participant.isLocal) return
    this.cb.onRemoteMute(
      participant.identity,
      publication.kind === 'audio' ? 'audio' : 'video',
      true
    )
  }

  private onTrackUnmuted = (publication: TrackPublication, participant: Participant) => {
    if (participant.isLocal) return
    this.cb.onRemoteMute(
      participant.identity,
      publication.kind === 'audio' ? 'audio' : 'video',
      false
    )
  }

  private onActiveSpeakersChanged = (speakers: Participant[]) => {
    this.cb.onActiveSpeaker(speakers[0]?.identity ?? null)
  }

  async setMuted(muted: boolean): Promise<void> {
    await this.room?.localParticipant.setMicrophoneEnabled(!muted)
  }

  async setVideoEnabled(enabled: boolean): Promise<void> {
    const room = this.room
    if (!room) return
    if (enabled) {
      await this.tryEnableCamera(room.localParticipant)
    } else {
      await room.localParticipant.setCameraEnabled(false)
    }
  }

  setSpeaker(on: boolean): void {
    this.speakerOn = on
    this.applyOutputToElements()
  }

  private applyOutputToElements() {
    getRtOutputEls().forEach((el) => {
      try {
        el.volume = this.speakerOn ? 1 : 0
        if (this.speakerOn && this.currentOutputId) {
          ;(el as any).setSinkId?.(this.currentOutputId)?.catch?.(() => {})
        }
      } catch {
        /* setSinkId 不支持时忽略 */
      }
    })
  }

  async switchFacing(facing: 'user' | 'environment'): Promise<void> {
    const room = this.room
    if (!room) return
    const lp = room.localParticipant
    try {
      const publication = lp.getTrackPublication(Track.Source.Camera)
      const track = publication?.track as LocalVideoTrack | undefined
      if (track?.restartTrack) {
        await track.restartTrack({ facingMode: facing })
      } else {
        await lp.setCameraEnabled(true, { facingMode: facing })
      }
      this.syncLocalTracks(lp)
    } catch (e) {
      console.warn('[rtc] switch camera failed', e)
      await this.tryEnableCamera(lp)
    }
  }

  async setDevice(kind: 'audioinput' | 'audiooutput', deviceId: string): Promise<void> {
    if (kind === 'audiooutput') {
      this.currentOutputId = deviceId
      this.applyOutputToElements()
      return
    }
    if (!this.room) return
    try {
      await this.room.switchActiveDevice(kind, deviceId)
      this.syncLocalTracks(this.room.localParticipant)
      this.applyOutputToElements()
    } catch (e) {
      console.warn('[rtc] switchActiveDevice failed', kind, e)
    }
  }

  leave(): void {
    if (this.room) {
      this.intentionalRooms.add(this.room)
      this.room.disconnect()
    }
  }

  dispose(): void {
    if (this.room) {
      this.intentionalRooms.add(this.room)
      this.room.removeAllListeners()
      this.room.disconnect()
      this.room = null
    }
    this.remoteStreams.clear()
    this.localTracks.clear()
    setRtOutputApplier(() => this.applyOutputToElements())
  }
}

export const rtcMediaPort: RtcMediaPort = new LiveKitMediaPort()
export { LIVEKIT_URL }
