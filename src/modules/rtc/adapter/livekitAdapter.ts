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
  VideoQuality,
  type TrackPublication,
  type LocalTrack
} from 'livekit-client'
import {
  resolveSubscriptionPolicy,
  type RtcRemoteVideoQuality
} from '../quality/subscriptionPolicy'
import {
  getRtOutputEls,
  setRtOutputApplier,
  type RtcMediaPort,
  type RtcMediaPortCallbacks,
  type RtcMediaPortJoinOptions,
  type RtcQoeSnapshot,
  type RtcQoeTrackSnapshot
} from './rtcMediaPort'
import { BackgroundRemovalProcessor } from './backgroundRemoval'

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

let cachedLiveKitUrl: string | null = null
function getLiveKitUrl(): string {
  if (!cachedLiveKitUrl) cachedLiveKitUrl = resolveLiveKitUrl()
  return cachedLiveKitUrl
}

const ROOM_OPTIONS = {
  // The provider port currently exposes MediaStream rather than LiveKit's
  // attach/detach element lifecycle. Keep the baseline deterministic until
  // that contract is explicit; otherwise adaptiveStream can pause an
  // un-attached remote video track and present a blurry/black frame.
  adaptiveStream: false,
  // Dynacast only pauses local simulcast layers that have no subscribers. It
  // does not change the MediaStream binding used by the current UI, so it is
  // safe to enable before the later adaptiveStream/visibility migration.
  dynacast: true,
  publishDefaults: {
    // Make the sender contract explicit instead of relying on SDK defaults.
    simulcast: true
  }
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
  private remoteTracks = new Map<
    string,
    {
      identity: string
      kind: 'audio' | 'video'
      source: RtcQoeTrackSnapshot['source']
      track: RemoteTrack
    }
  >()
  private localTracks = new Map<string, MediaStreamTrack>()
  private intentionalRooms = new WeakSet<Room>()
  private backgroundProcessor = new BackgroundRemovalProcessor()
  private originalCameraTrack: MediaStreamTrack | null = null
  private processedCameraTrack: MediaStreamTrack | null = null
  private cameraOperation: Promise<boolean> = Promise.resolve(true)
  private remoteVideoQuality = new Map<string, RtcRemoteVideoQuality>()
  private remoteSubscription = new Map<
    string,
    { audio?: boolean; video?: boolean; quality?: RtcRemoteVideoQuality }
  >()
  private visibleParticipants: ReadonlySet<string> | null = null
  private activeSpeakerId: string | null = null

  constructor() {
    setRtOutputApplier(() => this.applyOutputToElements())
  }

  setCallbacks(cb: RtcMediaPortCallbacks) {
    this.cb = cb
  }

  async join(opts: RtcMediaPortJoinOptions): Promise<void> {
    this.ensureFreshRoom()
    const room = this.room!
    await room.connect(getLiveKitUrl(), opts.token, { autoSubscribe: true })
    const lp = room.localParticipant
    if (opts.mode === 'video') {
      await this.tryEnableCamera(lp)
      this.originalCameraTrack =
        lp.getTrackPublication(Track.Source.Camera)?.track?.mediaStreamTrack || null
    }
    await lp.setMicrophoneEnabled(true)
    this.syncLocalTracks(lp)
  }

  private async tryEnableCamera(lp: LocalParticipant): Promise<boolean> {
    try {
      await lp.setCameraEnabled(true, VIDEO_CAPTURE_OPTIONS)
      return true
    } catch (e) {
      console.warn('[rtc] camera not available, fallback to audio only', e)
      return false
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
    this.remoteTracks.clear()
    this.localTracks.clear()
    this.remoteVideoQuality.clear()
    this.remoteSubscription.clear()
    this.visibleParticipants = null
    this.activeSpeakerId = null
    void this.resetBackgroundRemoval()
    const room = new Room(ROOM_OPTIONS)
    this.room = room
    this.wireEvents(room)
  }

  private wireEvents(room: Room) {
    room.on(RoomEvent.ConnectionStateChanged, (state: ConnectionState) => {
      if (this.intentionalRooms.has(room)) return
      if (state === 'connected') this.cb.onConnectionState('connected')
      else if (state === 'reconnecting' || state === 'signalReconnecting') {
        this.cb.onConnectionState('reconnecting')
      } else if (state === 'disconnected') this.cb.onConnectionState('disconnected')
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
    console.info('[rtc] local track published', {
      source: pub.source,
      kind: pub.kind,
      trackId: track.mediaStreamTrack.id,
      readyState: track.mediaStreamTrack.readyState,
      enabled: track.mediaStreamTrack.enabled
    })
    this.localTracks.set(String(pub.source || pub.kind), track.mediaStreamTrack)
    this.emitLocalStream()
  }

  private onLocalTrackUnpublished = (pub: LocalTrackPublication) => {
    console.info('[rtc] local track unpublished', { source: pub.source, kind: pub.kind })
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
    publication: RemoteTrackPublication,
    participant: RemoteParticipant
  ) => {
    const identity = participant.identity
    console.info('[rtc] remote track subscribed', {
      identity,
      kind: track.kind,
      trackId: track.mediaStreamTrack.id,
      readyState: track.mediaStreamTrack.readyState,
      enabled: track.mediaStreamTrack.enabled
    })
    if (track.kind === Track.Kind.Video) {
      this.applyRemoteSubscriptionPolicy()
    }
    const source = this.toQoeTrackSource(publication.source)
    this.remoteTracks.forEach((entry, trackId) => {
      if (
        entry.identity === identity &&
        entry.source === source &&
        entry.track.mediaStreamTrack.id !== track.mediaStreamTrack.id
      ) {
        this.remoteTracks.delete(trackId)
      }
    })
    this.remoteTracks.set(publication.trackSid || track.mediaStreamTrack.id, {
      identity,
      kind: track.kind === Track.Kind.Audio ? 'audio' : 'video',
      source,
      track
    })
    let stream = this.remoteStreams.get(identity)
    if (!stream) {
      stream = new MediaStream()
      this.remoteStreams.set(identity, stream)
    }
    // A participant can publish a fresh camera track before the browser has
    // delivered TrackUnsubscribed for the old one. Keep one live track per
    // media kind so a stale ended track cannot win the preview binding.
    stream.getTracks().forEach((existing) => {
      if (existing.kind === track.kind && existing.id !== track.mediaStreamTrack.id) {
        stream?.removeTrack(existing)
      }
    })
    if (!stream.getTracks().some((existing) => existing.id === track.mediaStreamTrack.id)) {
      stream.addTrack(track.mediaStreamTrack)
    }
    this.cb.onRemoteMute(identity, track.kind === 'audio' ? 'audio' : 'video', false)
    this.cb.onRemoteTrack(identity, stream)
  }

  private onTrackUnsubscribed = (
    track: Track,
    publication: RemoteTrackPublication,
    participant: RemoteParticipant
  ) => {
    const identity = participant.identity
    console.info('[rtc] remote track unsubscribed', {
      identity,
      kind: track.kind,
      trackId: track.mediaStreamTrack.id
    })
    const stream = this.remoteStreams.get(identity)
    const trackKey = publication.trackSid || track.mediaStreamTrack.id
    const tracked = this.remoteTracks.get(trackKey)
    if (tracked?.track.mediaStreamTrack.id === track.mediaStreamTrack.id) {
      this.remoteTracks.delete(trackKey)
    } else if (!tracked) {
      this.remoteTracks.forEach((entry, candidateKey) => {
        if (entry.track.mediaStreamTrack.id === track.mediaStreamTrack.id) {
          this.remoteTracks.delete(candidateKey)
        }
      })
    }
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
    this.remoteTracks.forEach((entry, trackId) => {
      if (entry.identity === participant.identity) this.remoteTracks.delete(trackId)
    })
    this.remoteVideoQuality.delete(participant.identity)
    this.remoteSubscription.delete(participant.identity)
    this.cb.onRemoteTrackRemoved(participant.identity)
  }

  private onTrackMuted = (publication: TrackPublication, participant: Participant) => {
    if (participant.isLocal) return
    const currentStream = this.remoteStreams.get(participant.identity)
    const publicationTrack = publication.track?.mediaStreamTrack
    if (
      currentStream &&
      publicationTrack &&
      !currentStream.getTracks().some((track) => track.id === publicationTrack.id)
    ) {
      // Ignore a late mute event from a camera track that was already replaced.
      return
    }
    this.cb.onRemoteMute(
      participant.identity,
      publication.kind === 'audio' ? 'audio' : 'video',
      true
    )
  }

  private onTrackUnmuted = (publication: TrackPublication, participant: Participant) => {
    if (participant.isLocal) return
    const currentStream = this.remoteStreams.get(participant.identity)
    const publicationTrack = publication.track?.mediaStreamTrack
    if (
      currentStream &&
      publicationTrack &&
      !currentStream.getTracks().some((track) => track.id === publicationTrack.id)
    ) {
      return
    }
    this.cb.onRemoteMute(
      participant.identity,
      publication.kind === 'audio' ? 'audio' : 'video',
      false
    )
  }

  private onActiveSpeakersChanged = (speakers: Participant[]) => {
    this.setActiveSpeaker(speakers[0]?.identity ?? null)
    this.cb.onActiveSpeaker(this.activeSpeakerId)
  }

  /**
   * Keep group calls audio-first without changing the 1:1 quality baseline.
   * The full visibility/attach-driven policy belongs to RTC-012; this small
   * policy is safe with the current MediaStream adapter and reduces SFU egress
   * as soon as a room has more than one remote participant.
   */
  private applyRemoteSubscriptionPolicy() {
    const room = this.room
    if (!room) return
    room.remoteParticipants.forEach((participant) => {
      participant.trackPublications.forEach((publication) => {
        const requested = this.remoteSubscription.get(participant.identity) ?? {}
        if (publication.kind === Track.Kind.Audio) {
          publication.setSubscribed(requested.audio ?? true)
          return
        }
        if (publication.kind !== Track.Kind.Video) return
        const decision = resolveSubscriptionPolicy({
          participantId: participant.identity,
          visibleParticipantIds:
            room.remoteParticipants.size <= 1 ? null : this.visibleParticipants,
          activeSpeakerId: this.activeSpeakerId,
          screenShare: publication.source === Track.Source.ScreenShare,
          requestedVideoQuality:
            requested.quality ?? this.remoteVideoQuality.get(participant.identity),
          requestedVideo: requested.video
        })
        publication.setSubscribed(decision.video)
        publication.setVideoQuality(this.toLiveKitVideoQuality(decision.quality))
      })
    })
  }

  private toLiveKitVideoQuality(quality: RtcRemoteVideoQuality): VideoQuality {
    if (quality === 'high') return VideoQuality.HIGH
    if (quality === 'medium') return VideoQuality.MEDIUM
    return VideoQuality.LOW
  }

  setRemoteVideoQuality(identity: string, quality: RtcRemoteVideoQuality): void {
    this.remoteVideoQuality.set(identity, quality)
    this.applyRemoteSubscriptionPolicy()
  }

  setParticipantSubscription(
    identity: string,
    options: { audio?: boolean; video?: boolean; quality?: RtcRemoteVideoQuality }
  ): void {
    const previous = this.remoteSubscription.get(identity) ?? {}
    this.remoteSubscription.set(identity, { ...previous, ...options })
    this.applyRemoteSubscriptionPolicy()
  }

  setVisibleParticipants(identities: ReadonlySet<string> | readonly string[] | null): void {
    this.visibleParticipants = identities === null ? null : new Set(identities)
    this.applyRemoteSubscriptionPolicy()
  }

  setActiveSpeaker(identity: string | null): void {
    this.activeSpeakerId = identity
    this.applyRemoteSubscriptionPolicy()
  }

  async getQoeSnapshot(): Promise<RtcQoeSnapshot> {
    const tracks: RtcQoeTrackSnapshot[] = []
    for (const [trackId, entry] of Array.from(this.remoteTracks.entries())) {
      const report = await entry.track.getRTCStatsReport()
      if (!report) continue
      const aggregate: RtcQoeTrackSnapshot = {
        trackId,
        identity: entry.identity,
        kind: entry.kind,
        source: entry.source,
        packetsLost: 0,
        packetsReceived: 0,
        jitterMs: 0,
        bytesReceived: 0,
        framesDecoded: 0,
        framesDropped: 0,
        framesPerSecond: 0,
        frameWidth: 0,
        frameHeight: 0
      }
      report.forEach((statsEntry) => {
        if (statsEntry.type !== 'inbound-rtp') return
        aggregate.packetsLost += Number(statsEntry.packetsLost || 0)
        aggregate.packetsReceived += Number(statsEntry.packetsReceived || 0)
        aggregate.jitterMs = Math.max(aggregate.jitterMs, Number(statsEntry.jitter || 0) * 1000)
        aggregate.bytesReceived += Number(statsEntry.bytesReceived || 0)
        aggregate.framesDecoded += Number(statsEntry.framesDecoded || 0)
        aggregate.framesDropped += Number(statsEntry.framesDropped || 0)
        aggregate.framesPerSecond = Math.max(
          aggregate.framesPerSecond,
          Number(statsEntry.framesPerSecond || 0)
        )
        aggregate.frameWidth = Math.max(aggregate.frameWidth, Number(statsEntry.frameWidth || 0))
        aggregate.frameHeight = Math.max(aggregate.frameHeight, Number(statsEntry.frameHeight || 0))
      })
      tracks.push(aggregate)
    }
    return {
      sampledAt: new Date().toISOString(),
      connectionState: this.toQoeConnectionState(this.room?.state),
      tracks
    }
  }

  private toQoeConnectionState(state?: ConnectionState): RtcQoeSnapshot['connectionState'] {
    if (state === 'connecting') return 'connecting'
    if (state === 'connected') return 'connected'
    if (state === 'reconnecting' || state === 'signalReconnecting') return 'reconnecting'
    if (state === 'disconnected') return 'disconnected'
    return 'unknown'
  }

  private toQoeTrackSource(source: Track.Source): RtcQoeTrackSnapshot['source'] {
    if (source === Track.Source.Microphone) return 'microphone'
    if (source === Track.Source.Camera) return 'camera'
    if (source === Track.Source.ScreenShare) return 'screen_share'
    if (source === Track.Source.ScreenShareAudio) return 'screen_share_audio'
    return 'unknown'
  }

  async setMuted(muted: boolean): Promise<void> {
    await this.room?.localParticipant.setMicrophoneEnabled(!muted)
  }

  async setVideoEnabled(enabled: boolean): Promise<boolean> {
    const operation = this.cameraOperation
      .then(() => this.applyVideoEnabled(enabled))
      .catch((error) => {
        console.warn('[rtc] camera toggle failed', error)
        return false
      })
    this.cameraOperation = operation
    return operation
  }

  private async applyVideoEnabled(enabled: boolean): Promise<boolean> {
    const room = this.room
    if (!room) return false
    if (enabled) {
      const enabledResult = await this.tryEnableCamera(room.localParticipant)
      if (!enabledResult) return false
      const cameraTrack =
        room.localParticipant.getTrackPublication(Track.Source.Camera)?.track?.mediaStreamTrack ||
        null
      if (!cameraTrack || cameraTrack.readyState === 'ended') {
        console.warn('[rtc] camera enabled without a live media track')
        return false
      }
      this.originalCameraTrack = cameraTrack
      this.syncLocalTracks(room.localParticipant)
      return true
    } else {
      await this.resetBackgroundRemoval()
      await room.localParticipant.setCameraEnabled(false)
      this.syncLocalTracks(room.localParticipant)
      return true
    }
  }

  async setBackgroundRemoval(enabled: boolean): Promise<boolean> {
    const room = this.room
    if (!room) return false
    const publication = room.localParticipant.getTrackPublication(Track.Source.Camera)
    const localTrack = publication?.track as LocalVideoTrack | undefined
    if (!localTrack) return false
    if (!this.originalCameraTrack) this.originalCameraTrack = localTrack.mediaStreamTrack

    if (!enabled) {
      if (this.originalCameraTrack) await localTrack.replaceTrack(this.originalCameraTrack)
      await this.backgroundProcessor.stop()
      this.processedCameraTrack = null
      this.syncLocalTracks(room.localParticipant)
      return true
    }

    try {
      this.processedCameraTrack = await this.backgroundProcessor.start(this.originalCameraTrack)
      await localTrack.replaceTrack(this.processedCameraTrack)
      this.syncLocalTracks(room.localParticipant)
      return true
    } catch (error) {
      await this.backgroundProcessor.stop()
      this.processedCameraTrack = null
      console.warn('[rtc] background removal unavailable', error)
      return false
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
    const restoreBackgroundRemoval = Boolean(this.processedCameraTrack)
    if (restoreBackgroundRemoval) await this.resetBackgroundRemoval()
    try {
      const publication = lp.getTrackPublication(Track.Source.Camera)
      const track = publication?.track as LocalVideoTrack | undefined
      if (track?.restartTrack) {
        await track.restartTrack({ facingMode: facing })
      } else {
        await lp.setCameraEnabled(true, { facingMode: facing })
      }
      this.syncLocalTracks(lp)
      if (restoreBackgroundRemoval) {
        const enabled = await this.setBackgroundRemoval(true)
        if (!enabled)
          console.warn('[rtc] background removal could not be restored after camera switch')
      }
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
    void this.resetBackgroundRemoval()
    if (this.room) {
      this.intentionalRooms.add(this.room)
      this.room.removeAllListeners()
      this.room.disconnect()
      this.room = null
    }
    this.remoteStreams.clear()
    this.remoteTracks.clear()
    this.localTracks.clear()
    this.remoteVideoQuality.clear()
    this.remoteSubscription.clear()
    this.visibleParticipants = null
    this.activeSpeakerId = null
    setRtOutputApplier(() => this.applyOutputToElements())
  }

  private async resetBackgroundRemoval() {
    const room = this.room
    const publication = room?.localParticipant.getTrackPublication(Track.Source.Camera)
    const localTrack = publication?.track as LocalVideoTrack | undefined
    if (localTrack && this.originalCameraTrack) {
      try {
        await localTrack.replaceTrack(this.originalCameraTrack)
      } catch {
        /* camera may already be unpublished while leaving a room */
      }
    }
    await this.backgroundProcessor.stop()
    this.processedCameraTrack = null
    this.originalCameraTrack = null
  }
}

export const rtcMediaPort: RtcMediaPort = new LiveKitMediaPort()
