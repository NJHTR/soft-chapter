/**
 * SRS WHIP/WHEP client.
 *
 * Media bytes stay on the browser <-> SRS path. Spring only returns the
 * short-lived room metadata and never proxies SDP or RTP.
 */

export interface SrsMediaUrls {
  whipUrl?: string
  whepUrl?: string
  hlsUrl?: string
  httpFlvUrl?: string
  ingestMode?: 'browser-whip' | 'native'
}

export interface SrsRtcStats {
  bitrateKbps: number
  fps: number
  packetsLost: number
  packetsReceived: number
  rttMs: number
  framesDecoded?: number
}

export interface SrsPeerConnectionOptions {
  /** Called for the active peer only; closed peers never notify their caller. */
  onConnectionStateChange?: (state: RTCPeerConnectionState) => void
}

const DEFAULT_ICE_SERVERS: RTCIceServer[] = []

function iceServers(): RTCIceServer[] {
  const raw = (import.meta.env.VITE_RTC_ICE_SERVERS as string | undefined)?.trim()
  if (!raw) return DEFAULT_ICE_SERVERS
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : DEFAULT_ICE_SERVERS
  } catch {
    console.warn('[srs-rtc] invalid VITE_RTC_ICE_SERVERS; using SRS ICE candidates')
    return DEFAULT_ICE_SERVERS
  }
}

function resolveEndpoint(endpoint: string): string {
  const value = endpoint.trim()
  if (!value) throw new Error('SRS media endpoint is empty')
  const resolved = new URL(
    value,
    typeof window === 'undefined' ? 'http://localhost' : window.location.origin
  )
  if (
    typeof window !== 'undefined' &&
    window.location.protocol === 'https:' &&
    resolved.protocol !== 'https:'
  ) {
    throw new Error('HTTPS 页面必须使用 HTTPS SRS media endpoint')
  }
  return resolved.toString()
}

async function waitForIceGathering(pc: RTCPeerConnection, timeoutMs = 4000): Promise<void> {
  if (pc.iceGatheringState === 'complete') return
  await new Promise<void>((resolve) => {
    let settled = false
    const finish = () => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      pc.removeEventListener('icegatheringstatechange', onState)
      resolve()
    }
    const onState = () => {
      if (pc.iceGatheringState === 'complete') finish()
    }
    const timer = setTimeout(finish, timeoutMs)
    pc.addEventListener('icegatheringstatechange', onState)
  })
}

async function postSdp(
  endpoint: string,
  offer: RTCSessionDescriptionInit
): Promise<{ answer: string; location: string | null }> {
  const response = await fetch(resolveEndpoint(endpoint), {
    method: 'POST',
    headers: { 'Content-Type': 'application/sdp', Accept: 'application/sdp' },
    body: offer.sdp || ''
  })
  if (!response.ok) {
    const detail = (await response.text().catch(() => '')).slice(0, 240)
    throw new Error(`SRS SDP negotiation failed (${response.status})${detail ? `: ${detail}` : ''}`)
  }
  const answer = await response.text()
  if (!answer.trim()) throw new Error('SRS returned an empty SDP answer')
  return { answer, location: response.headers.get('Location') }
}

function resolveSessionLocation(endpoint: string, location: string): string {
  const endpointUrl = new URL(resolveEndpoint(endpoint))
  const locationUrl = new URL(location, endpointUrl)
  // SRS normally returns a root-relative Location. When the browser reached
  // SRS through the Vite/reverse-proxy `/media/srs` prefix, preserve that
  // prefix for DELETE; otherwise the request would bypass the proxy.
  if (location.startsWith('/') && endpointUrl.pathname.startsWith('/media/srs/')) {
    const rtcIndex = endpointUrl.pathname.indexOf('/rtc/v1/')
    const prefix = rtcIndex >= 0 ? endpointUrl.pathname.slice(0, rtcIndex) : ''
    if (prefix && !locationUrl.pathname.startsWith(prefix + '/')) {
      locationUrl.pathname = prefix + locationUrl.pathname
    }
  }
  return locationUrl.toString()
}

async function deleteSession(endpoint: string, location: string | null): Promise<void> {
  if (!location) return
  try {
    await fetch(resolveSessionLocation(endpoint, location), { method: 'DELETE' })
  } catch {
    // The peer connection is still closed locally. SRS will reap the session.
  }
}

function setVideoEncoding(sender: RTCRtpSender, bitrate: number, frameRate: number): void {
  try {
    const params = sender.getParameters()
    params.encodings = params.encodings?.length ? params.encodings : [{}]
    params.encodings[0].maxBitrate = bitrate
    params.encodings[0].maxFramerate = frameRate
    params.encodings[0].scaleResolutionDownBy = 1
    void sender.setParameters(params)
  } catch {
    // Older Safari versions do not expose encoding parameters.
  }
}

function preferBaselineCodecs(pc: RTCPeerConnection): void {
  // SRS's browser-compatible distribution baseline is H.264 + Opus. Keep a
  // browser's native fallback when codec preferences are unavailable instead
  // of advertising a codec that the provider cannot ingest.
  for (const transceiver of pc.getTransceivers()) {
    // recvonly WHEP transceivers have no sender track; the receiver track
    // still carries the media kind before a remote description is applied.
    const kind = transceiver.sender.track?.kind || transceiver.receiver.track?.kind
    if (!kind || typeof transceiver.setCodecPreferences !== 'function') continue
    if (typeof RTCRtpSender.getCapabilities !== 'function') continue
    const capabilities = RTCRtpSender.getCapabilities(kind)
    if (!capabilities?.codecs?.length) continue
    const preferred = capabilities.codecs.filter((codec) =>
      kind === 'video'
        ? codec.mimeType.toLowerCase() === 'video/h264'
        : codec.mimeType.toLowerCase() === 'audio/opus'
    )
    if (preferred.length) transceiver.setCodecPreferences(preferred)
  }
}

export class SrsWhipPublisher {
  private pc: RTCPeerConnection | null = null
  private location: string | null = null
  private startedAt = 0
  private bytesSentAtStart = 0
  private generation = 0

  constructor(
    private readonly stream: MediaStream,
    private readonly endpoint: string,
    private readonly options: {
      bitrate?: number
      frameRate?: number
    } & SrsPeerConnectionOptions = {}
  ) {}

  async start(): Promise<void> {
    this.stop()
    const generation = ++this.generation
    const pc = new RTCPeerConnection({ iceServers: iceServers() })
    this.pc = pc
    const isCurrent = () =>
      this.pc === pc && this.generation === generation && pc.signalingState !== 'closed'
    pc.addEventListener('connectionstatechange', () => {
      if (isCurrent()) this.options.onConnectionStateChange?.(pc.connectionState)
    })
    const bitrate = this.options.bitrate ?? 2_500_000
    const frameRate = this.options.frameRate ?? 30
    try {
      for (const track of this.stream.getTracks()) {
        const sender = pc.addTrack(track, this.stream)
        if (track.kind === 'video') setVideoEncoding(sender, bitrate, frameRate)
      }
      preferBaselineCodecs(pc)
      const offer = await pc.createOffer()
      if (!isCurrent()) throw new DOMException('WHIP publisher was stopped', 'AbortError')
      await pc.setLocalDescription(offer)
      await waitForIceGathering(pc)
      if (!isCurrent()) throw new DOMException('WHIP publisher was stopped', 'AbortError')
      const result = await postSdp(this.endpoint, pc.localDescription || offer)
      if (!isCurrent()) {
        await deleteSession(this.endpoint, result.location)
        throw new DOMException('WHIP publisher was stopped', 'AbortError')
      }
      this.location = result.location
      await pc.setRemoteDescription({ type: 'answer', sdp: result.answer })
      if (!isCurrent()) throw new DOMException('WHIP publisher was stopped', 'AbortError')
      this.startedAt = performance.now()
      this.bytesSentAtStart = 0
    } catch (error) {
      if (isCurrent()) this.stop()
      throw error
    }
  }

  async getStats(): Promise<SrsRtcStats> {
    const pc = this.pc
    if (!pc) return { bitrateKbps: 0, fps: 0, packetsLost: 0, packetsReceived: 0, rttMs: 0 }
    let bytes = 0
    let frames = 0
    let packetsLost = 0
    let packetsReceived = 0
    let rttMs = 0
    const report = await pc.getStats()
    report.forEach((entry) => {
      if (entry.type === 'outbound-rtp' && entry.kind === 'video') {
        bytes += Number(entry.bytesSent || 0)
        frames += Number(entry.framesPerSecond || 0)
      }
      if (entry.type === 'remote-inbound-rtp')
        rttMs = Math.max(rttMs, Number(entry.roundTripTime || 0) * 1000)
      if (entry.type === 'outbound-rtp') {
        packetsLost += Number(entry.packetsLost || 0)
        packetsReceived += Number(entry.packetsSent || 0)
      }
    })
    const elapsed = Math.max(1, (performance.now() - this.startedAt) / 1000)
    return {
      bitrateKbps: Math.max(0, ((bytes - this.bytesSentAtStart) * 8) / elapsed / 1000),
      fps: frames,
      packetsLost,
      packetsReceived,
      rttMs
    }
  }

  stop(): void {
    this.generation++
    const location = this.location
    const pc = this.pc
    this.location = null
    this.pc = null
    void deleteSession(this.endpoint, location)
    if (pc) {
      pc.getSenders().forEach((sender) => sender.replaceTrack(null).catch(() => {}))
      pc.close()
    }
  }
}

export class SrsWhepPlayer {
  private pc: RTCPeerConnection | null = null
  private location: string | null = null
  private remoteStream = new MediaStream()
  private bytesReceivedAtStart = 0
  private startedAt = 0
  private generation = 0
  private cancelPendingStart: (() => void) | null = null

  constructor(
    private readonly endpoint: string,
    private readonly options: SrsPeerConnectionOptions = {}
  ) {}

  async start(video: HTMLVideoElement): Promise<void> {
    this.stop()
    const generation = ++this.generation
    const pc = new RTCPeerConnection({ iceServers: iceServers() })
    this.pc = pc
    const remoteStream = new MediaStream()
    this.remoteStream = remoteStream
    const isCurrent = () =>
      this.pc === pc && this.generation === generation && pc.signalingState !== 'closed'
    pc.addEventListener('connectionstatechange', () => {
      if (isCurrent()) this.options.onConnectionStateChange?.(pc.connectionState)
    })
    pc.addTransceiver('video', { direction: 'recvonly' })
    pc.addTransceiver('audio', { direction: 'recvonly' })
    let cancelTrackReady: (() => void) | null = null
    const trackReady = new Promise<void>((resolve, reject) => {
      let videoTrackSeen = false
      let settled = false
      const timer = setTimeout(() => {
        if (!settled) {
          settled = true
          reject(new Error('SRS WHEP 首帧超时'))
        }
      }, 10_000)
      const finish = () => {
        if (settled || !isCurrent()) return
        settled = true
        clearTimeout(timer)
        resolve()
      }
      cancelTrackReady = () => {
        if (settled) return
        settled = true
        clearTimeout(timer)
        reject(new DOMException('WHEP player was stopped', 'AbortError'))
      }
      this.cancelPendingStart = cancelTrackReady
      const waitForDecodedFrame = () => {
        if (!videoTrackSeen || !isCurrent()) return
        // `ontrack` only proves that a receiver exists. Waiting for a decoded
        // frame avoids reporting a black player as ready when autoplay or the
        // provider has not produced a keyframe yet.
        if ('requestVideoFrameCallback' in video) {
          video.requestVideoFrameCallback(() => finish())
        } else {
          video.addEventListener('loadeddata', finish, { once: true })
        }
      }
      pc.ontrack = (event) => {
        if (!isCurrent()) return
        const tracks = event.streams[0]?.getTracks() || [event.track]
        for (const track of tracks) {
          if (!remoteStream.getTracks().some((item) => item.id === track.id))
            remoteStream.addTrack(track)
          if (track.kind === 'video') videoTrackSeen = true
        }
        video.srcObject = remoteStream
        // The caller keeps the element muted until an explicit user gesture.
        // This satisfies autoplay policy without silently losing the audio
        // track; LiveWatch exposes an unmute control.
        void video
          .play()
          .then(waitForDecodedFrame)
          .catch(() => waitForDecodedFrame())
      }
    })
    try {
      const offer = await pc.createOffer()
      if (!isCurrent()) throw new DOMException('WHEP player was stopped', 'AbortError')
      await pc.setLocalDescription(offer)
      await waitForIceGathering(pc)
      if (!isCurrent()) throw new DOMException('WHEP player was stopped', 'AbortError')
      const result = await postSdp(this.endpoint, pc.localDescription || offer)
      if (!isCurrent()) {
        await deleteSession(this.endpoint, result.location)
        throw new DOMException('WHEP player was stopped', 'AbortError')
      }
      this.location = result.location
      await pc.setRemoteDescription({ type: 'answer', sdp: result.answer })
      if (!isCurrent()) throw new DOMException('WHEP player was stopped', 'AbortError')
      this.startedAt = performance.now()
      await trackReady
    } catch (error) {
      if (isCurrent()) this.stop()
      throw error
    } finally {
      if (this.cancelPendingStart === cancelTrackReady) this.cancelPendingStart = null
    }
  }

  async getStats(): Promise<SrsRtcStats> {
    const pc = this.pc
    if (!pc)
      return {
        bitrateKbps: 0,
        fps: 0,
        packetsLost: 0,
        packetsReceived: 0,
        rttMs: 0,
        framesDecoded: 0
      }
    let bytes = 0
    let frames = 0
    let packetsLost = 0
    let packetsReceived = 0
    let rttMs = 0
    let framesDecoded = 0
    const report = await pc.getStats()
    report.forEach((entry) => {
      if (entry.type === 'inbound-rtp') {
        bytes += Number(entry.bytesReceived || 0)
        frames += Number(entry.framesPerSecond || 0)
        framesDecoded += Number(entry.framesDecoded || 0)
        packetsLost += Number(entry.packetsLost || 0)
        packetsReceived += Number(entry.packetsReceived || 0)
      }
      if (entry.type === 'candidate-pair' && entry.state === 'succeeded') {
        rttMs = Math.max(rttMs, Number(entry.currentRoundTripTime || 0) * 1000)
      }
    })
    const elapsed = Math.max(1, (performance.now() - this.startedAt) / 1000)
    return {
      bitrateKbps: Math.max(0, ((bytes - this.bytesReceivedAtStart) * 8) / elapsed / 1000),
      fps: frames,
      packetsLost,
      packetsReceived,
      rttMs,
      framesDecoded
    }
  }

  stop(): void {
    this.generation++
    this.cancelPendingStart?.()
    this.cancelPendingStart = null
    const location = this.location
    const pc = this.pc
    this.location = null
    this.pc = null
    void deleteSession(this.endpoint, location)
    if (pc) {
      pc.getReceivers().forEach((receiver) => receiver.track.stop())
      pc.close()
    }
    this.remoteStream.getTracks().forEach((track) => track.stop())
    this.remoteStream = new MediaStream()
  }
}

/** Use HLS first, then HTTP-FLV when WebRTC cannot be negotiated. */
export async function playSrsFallback(
  video: HTMLVideoElement,
  urls: SrsMediaUrls
): Promise<() => void> {
  const resetVideo = () => {
    video.pause()
    video.srcObject = null
    video.removeAttribute('src')
    video.load()
  }
  resetVideo()
  let firstError: unknown = null

  if (urls.hlsUrl) {
    try {
      const hlsModule = await import('hls.js')
      const Hls = hlsModule.default
      if (Hls.isSupported()) {
        const hls = new Hls({ lowLatencyMode: true, backBufferLength: 30 })
        const onManifest = () => finish()
        const onError = (_event: unknown, data: { fatal?: boolean }) => {
          if (data.fatal) fail(new Error('HLS 播放失败'))
        }
        let settled = false
        let timeout: ReturnType<typeof setTimeout> | null = null
        let resolveReady: (() => void) | null = null
        let rejectReady: ((reason?: unknown) => void) | null = null
        const finish = () => {
          if (settled) return
          settled = true
          if (timeout) clearTimeout(timeout)
          hls.off(Hls.Events.MANIFEST_PARSED, onManifest)
          hls.off(Hls.Events.ERROR, onError)
          resolveReady?.()
        }
        const fail = (error: Error) => {
          if (settled) return
          settled = true
          if (timeout) clearTimeout(timeout)
          hls.off(Hls.Events.MANIFEST_PARSED, onManifest)
          hls.off(Hls.Events.ERROR, onError)
          rejectReady?.(error)
        }
        hls.on(Hls.Events.MANIFEST_PARSED, onManifest)
        hls.on(Hls.Events.ERROR, onError)
        hls.loadSource(resolveEndpoint(urls.hlsUrl))
        hls.attachMedia(video)
        try {
          await new Promise<void>((resolve, reject) => {
            resolveReady = resolve
            rejectReady = reject
            timeout = setTimeout(() => fail(new Error('HLS 播放超时')), 10_000)
          })
          await video.play()
          return () => {
            hls.destroy()
            resetVideo()
          }
        } catch (error) {
          hls.destroy()
          resetVideo()
          throw error
        }
      }
      if (video.canPlayType('application/vnd.apple.mpegurl')) {
        video.src = resolveEndpoint(urls.hlsUrl)
        try {
          await video.play()
          return resetVideo
        } catch (error) {
          resetVideo()
          throw error
        }
      }
    } catch (error) {
      firstError = error
    }
  }

  if (urls.httpFlvUrl) {
    try {
      const mpegtsModule = await import('mpegts.js')
      const mpegts = mpegtsModule.default
      if (mpegts.isSupported()) {
        const player = mpegts.createPlayer({
          type: 'flv',
          isLive: true,
          url: resolveEndpoint(urls.httpFlvUrl)
        })
        const cleanup = () => {
          try {
            player.pause()
          } catch {
            /* player may already be closed */
          }
          try {
            player.unload()
          } catch {
            /* player may already be closed */
          }
          try {
            player.detachMediaElement()
          } catch {
            /* player may already be detached */
          }
          try {
            player.destroy()
          } catch {
            /* player may already be destroyed */
          }
          resetVideo()
        }
        try {
          player.attachMediaElement(video)
          player.load()
          await Promise.race([
            player.play(),
            new Promise<never>((_, reject) =>
              setTimeout(() => reject(new Error('HTTP-FLV 播放超时')), 10_000)
            )
          ])
          return cleanup
        } catch (error) {
          cleanup()
          throw error
        }
      }
    } catch (error) {
      firstError = firstError || error
    }
  }
  throw firstError instanceof Error ? firstError : new Error('当前浏览器不支持直播播放降级')
}

export function normalizeSrsUrls(urls: SrsMediaUrls): SrsMediaUrls {
  return {
    whipUrl: urls.whipUrl ? resolveEndpoint(urls.whipUrl) : undefined,
    whepUrl: urls.whepUrl ? resolveEndpoint(urls.whepUrl) : undefined,
    hlsUrl: urls.hlsUrl ? resolveEndpoint(urls.hlsUrl) : undefined,
    httpFlvUrl: urls.httpFlvUrl ? resolveEndpoint(urls.httpFlvUrl) : undefined,
    ingestMode: urls.ingestMode
  }
}
