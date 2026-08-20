import { beforeEach, describe, expect, it, vi } from 'vitest'
import { rtcMediaPort } from '../adapter/livekitAdapter'
import type { RtcMediaPortCallbacks } from '../adapter/rtcMediaPort'

class FakeMediaStream {
  private tracks: any[] = []
  addTrack(track: any) {
    if (!this.tracks.some((existing) => existing.id === track.id)) this.tracks.push(track)
  }
  removeTrack(track: any) {
    this.tracks = this.tracks.filter((existing) => existing.id !== track.id)
  }
  getTracks() {
    return this.tracks
  }
  getAudioTracks() {
    return this.tracks.filter((track) => track.kind === 'audio')
  }
  getVideoTracks() {
    return this.tracks.filter((track) => track.kind === 'video')
  }
}

vi.mock('livekit-client', () => {
  const Track = {
    Kind: { Audio: 'audio', Video: 'video' },
    Source: {
      Microphone: 'microphone',
      Camera: 'camera',
      ScreenShare: 'screen_share',
      ScreenShareAudio: 'screen_share_audio'
    }
  }
  const VideoQuality = { HIGH: 'high', MEDIUM: 'medium', LOW: 'low' }
  const RoomEvent = {
    ConnectionStateChanged: 'ConnectionStateChanged',
    Disconnected: 'Disconnected',
    TrackSubscribed: 'TrackSubscribed',
    TrackUnsubscribed: 'TrackUnsubscribed',
    TrackMuted: 'TrackMuted',
    TrackUnmuted: 'TrackUnmuted',
    ParticipantDisconnected: 'ParticipantDisconnected',
    ActiveSpeakersChanged: 'ActiveSpeakersChanged',
    TrackPublished: 'TrackPublished',
    TrackUnpublished: 'TrackUnpublished'
  }
  const ParticipantEvent = {
    LocalTrackPublished: 'LocalTrackPublished',
    LocalTrackUnpublished: 'LocalTrackUnpublished'
  }
  const createdRooms: any[] = []

  class Room {
    handlers = new Map<string, ((...args: any[]) => void)[]>()
    remoteParticipants = new Map<string, any>()
    localParticipant: any
    state = 'disconnected'
    url = ''
    token = ''
    constructor() {
      createdRooms.push(this)
      this.localParticipant = {
        on: () => {},
        off: () => {},
        trackPublications: new Map(),
        setMicrophoneEnabled: vi.fn(async () => {}),
        setCameraEnabled: vi.fn(async () => true),
        setScreenShareEnabled: vi.fn(async () => {}),
        getTrackPublication: () => ({ track: undefined })
      }
    }
    on(event: string, fn: (...args: any[]) => void) {
      const list = this.handlers.get(event)
      if (list) list.push(fn)
      else this.handlers.set(event, [fn])
    }
    off() {}
    removeAllListeners() {
      this.handlers.clear()
    }
    async connect(url: string, token: string) {
      this.url = url
      this.token = token
      this.state = 'connected'
    }
    disconnect() {
      this.state = 'disconnected'
    }
    emit(event: string, ...args: any[]) {
      ;(this.handlers.get(event) ?? []).slice().forEach((fn) => fn(...args))
    }
  }

  return {
    Room,
    Track,
    VideoQuality,
    RoomEvent,
    ParticipantEvent,
    __createdRooms: createdRooms,
    __reset: () => {
      createdRooms.length = 0
    }
  }
})

// 测试用具：与 livekit-client mock 同名的工厂（运行期对象来自 mock 工厂）。
import { RoomEvent, Track, VideoQuality, __createdRooms, __reset } from 'livekit-client'

function makeTrack(id: string, kind: 'audio' | 'video') {
  return {
    kind,
    mediaStreamTrack: {
      id,
      kind,
      readyState: 'live',
      enabled: true
    }
  }
}

function makePublication(sid: string, kind: 'audio' | 'video', source: string) {
  return {
    trackSid: sid,
    kind,
    source,
    track: makeTrack(`track-${sid}`, kind),
    isLocal: false,
    setSubscribed: vi.fn(),
    setVideoQuality: vi.fn()
  }
}

function makeParticipant(identity: string, publications: Map<string, any>) {
  return {
    identity,
    isLocal: false,
    trackPublications: publications
  }
}

function spyCallbacks(): { events: string[]; streams: Array<{ identity: string; ids: string[] }> } {
  const state = {
    events: [] as string[],
    streams: [] as Array<{ identity: string; ids: string[] }>
  }
  const callbacks: RtcMediaPortCallbacks = {
    onRemoteTrack(identity, stream) {
      state.events.push('onRemoteTrack')
      state.streams.push({ identity, ids: stream.getVideoTracks().map((t) => t.id) })
    },
    onRemoteTrackRemoved: () => state.events.push('onRemoteTrackRemoved'),
    onConnectionState: () => state.events.push('onConnectionState'),
    onClose: () => state.events.push('onClose'),
    onRemoteMute: () => state.events.push('onRemoteMute'),
    onActiveSpeaker: () => state.events.push('onActiveSpeaker'),
    onLocalTrack: () => state.events.push('onLocalTrack')
  }
  rtcMediaPort.setCallbacks(callbacks)
  return state
}

const JOIN = {
  token: 'tok',
  roomName: 'room-1',
  identity: 'me',
  mode: 'audio',
  scope: 'group'
} as const

describe('LiveKitMediaPort selective subscription (RTC-012)', () => {
  beforeEach(() => {
    __reset()
    ;(globalThis as any).MediaStream = FakeMediaStream
  })

  it('join passes url/token and exposes explicit scope via snapshot topology', async () => {
    await rtcMediaPort.join({ ...JOIN, scope: 'group', token: 'tok9' })
    const room = __createdRooms[0]

    expect(room.url).toBe('ws://localhost:7880')
    expect(room.token).toBe('tok9')
    const snapshot = await rtcMediaPort.getQoeSnapshot()
    expect(snapshot.topology).toBe('group')
  })

  it('calls setSubscribed/setVideoQuality only when the desired value changes', async () => {
    await rtcMediaPort.join(JOIN)
    const room = __createdRooms[0]
    const p1 = makeParticipant(
      'p1',
      new Map([['cam-1', makePublication('cam-1', 'video', Track.Source.Camera)]])
    )
    const p2 = makeParticipant(
      'p2',
      new Map([['cam-2', makePublication('cam-2', 'video', Track.Source.Camera)]])
    )
    room.remoteParticipants.set('p1', p1)
    room.remoteParticipants.set('p2', p2)
    const cam1 = p1.trackPublications.get('cam-1')
    const cam2 = p2.trackPublications.get('cam-2')

    rtcMediaPort.setVisibleParticipants(['p1', 'p2'])
    rtcMediaPort.setVisibleParticipants(['p1', 'p2'])
    rtcMediaPort.setVisibleParticipants(['p1', 'p2'])

    expect(cam1.setSubscribed).toHaveBeenCalledTimes(1)
    expect(cam1.setSubscribed).toHaveBeenLastCalledWith(true)
    expect(cam1.setVideoQuality).toHaveBeenCalledTimes(1)
    expect(cam1.setVideoQuality).toHaveBeenLastCalledWith(VideoQuality.MEDIUM)
    expect(cam2.setSubscribed).toHaveBeenCalledTimes(1)

    rtcMediaPort.setVisibleParticipants(['p1'])

    expect(cam2.setSubscribed).toHaveBeenCalledTimes(2)
    expect(cam2.setSubscribed).toHaveBeenLastCalledWith(false)
    expect(cam1.setSubscribed).toHaveBeenCalledTimes(1)

    rtcMediaPort.setActiveSpeaker('p2')

    expect(cam2.setSubscribed).toHaveBeenCalledTimes(3)
    expect(cam2.setSubscribed).toHaveBeenLastCalledWith(true)
    expect(cam2.setVideoQuality).toHaveBeenLastCalledWith(VideoQuality.HIGH)
  })

  it('pauses ordinary camera video but keeps audio and screen share when hidden/minimized', async () => {
    await rtcMediaPort.join(JOIN)
    const room = __createdRooms[0]
    const p1 = makeParticipant(
      'p1',
      new Map([['cam-1', makePublication('cam-1', 'video', Track.Source.Camera)]])
    )
    const p2 = makeParticipant(
      'p2',
      new Map([['mic-2', makePublication('mic-2', 'audio', Track.Source.Microphone)]])
    )
    const p3 = makeParticipant(
      'p3',
      new Map([['ss-3', makePublication('ss-3', 'video', Track.Source.ScreenShare)]])
    )
    room.remoteParticipants.set('p1', p1)
    room.remoteParticipants.set('p2', p2)
    room.remoteParticipants.set('p3', p3)
    const cam1 = p1.trackPublications.get('cam-1')
    const mic2 = p2.trackPublications.get('mic-2')
    const ss3 = p3.trackPublications.get('ss-3')

    rtcMediaPort.setVisibleParticipants(['p1', 'p2', 'p3'])
    rtcMediaPort.setActiveSpeaker('p2')
    expect(cam1.setSubscribed).toHaveBeenLastCalledWith(true)

    rtcMediaPort.setMediaAttention({ hidden: true, minimized: false })

    expect(cam1.setSubscribed).toHaveBeenLastCalledWith(false)
    expect(mic2.setSubscribed).toHaveBeenLastCalledWith(true)
    expect(ss3.setSubscribed).toHaveBeenLastCalledWith(true)
    expect(ss3.setVideoQuality).toHaveBeenLastCalledWith(VideoQuality.HIGH)

    rtcMediaPort.setMediaAttention({ hidden: false, minimized: true })
    expect(cam1.setSubscribed).toHaveBeenLastCalledWith(false)
    expect(mic2.setSubscribed).toHaveBeenLastCalledWith(true)

    rtcMediaPort.setMediaAttention({ hidden: false, minimized: false })
    expect(cam1.setSubscribed).toHaveBeenLastCalledWith(true)
    expect(cam1.setVideoQuality).toHaveBeenLastCalledWith(VideoQuality.MEDIUM)
  })

  it('registry uses composite (identity, source) key and never overrides a newer track', async () => {
    const state = spyCallbacks()
    await rtcMediaPort.join(JOIN)
    const room = __createdRooms[0]
    const participant = makeParticipant('remote-1', new Map())

    const oldCamera = makePublication('cam-old', 'video', Track.Source.Camera)
    const newCamera = makePublication('cam-new', 'video', Track.Source.Camera)
    room.emit(RoomEvent.TrackSubscribed, oldCamera.track, oldCamera, participant)
    assertStream(state, { identity: 'remote-1', ids: ['track-cam-old'] })

    room.emit(RoomEvent.TrackSubscribed, newCamera.track, newCamera, participant)
    assertStream(state, { identity: 'remote-1', ids: ['track-cam-new'] })

    room.emit(RoomEvent.TrackUnpublished, newCamera, participant)
    expect(state.events).toContain('onRemoteTrackRemoved')

    // 离开时按 generation 幂等清理，不再下发旧轨道
    const screen = makePublication('ss-1', 'video', Track.Source.ScreenShare)
    room.emit(RoomEvent.TrackSubscribed, screen.track, screen, participant)
    assertStream(state, { identity: 'remote-1', ids: ['track-ss-1'] })

    room.emit(RoomEvent.ParticipantDisconnected, participant)
    expect(state.events.filter((e) => e === 'onRemoteTrackRemoved').length).toBe(2)
  })

  it('mute events from an already replaced camera track are ignored', async () => {
    spyCallbacks()
    await rtcMediaPort.join(JOIN)
    const room = __createdRooms[0]
    const participant = makeParticipant('remote-1', new Map())
    const oldCamera = makePublication('cam-old', 'video', Track.Source.Camera)
    const newCamera = makePublication('cam-new', 'video', Track.Source.Camera)

    room.emit(RoomEvent.TrackSubscribed, oldCamera.track, oldCamera, participant)
    room.emit(RoomEvent.TrackSubscribed, newCamera.track, newCamera, participant)

    // 旧轨道已不在流中：迟到的 mute 不触发远端静音
    expect(() =>
      room.emit(RoomEvent.TrackMuted, { ...oldCamera, kind: 'video' }, participant)
    ).not.toThrow()
  })
})

function assertStream(
  state: { streams: Array<{ identity: string; ids: string[] }> },
  expected: { identity: string; ids: string[] }
) {
  const last = state.streams[state.streams.length - 1]
  expect(last).toEqual(expected)
}
