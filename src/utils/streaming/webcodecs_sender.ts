/**
 * WebCodecs-based video stream sender (broadcaster).
 * 
 * Captures camera at 4K, processes frames through GPU via WebGL,
 * encodes with hardware codecs (AV1/H.265/H.264 via WebCodecs),
 * and streams via WebSocket/WebRTC.
 * 
 * Pipeline:
 * Camera (4K) → VideoFrame → WebGL processing → EncodedVideoChunk → Stream
 */

export type EncoderCodec = 'av1' | 'h265' | 'h264'
export type StreamProtocol = 'websocket' | 'webrtc' | 'srt-relay'

export interface SenderConfig {
  codec: EncoderCodec
  protocol: StreamProtocol
  streamUrl: string
  streamKey: string
  width: number
  height: number
  fps: number
  bitrate: number
  maxBitrate?: number
  enableBeauty?: boolean
  enableDenoise?: boolean
}

export interface SenderStats {
  encodeFps: number
  captureFps: number
  bitrateKbps: number
  bytesSent: number
  framesEncoded: number
  framesDropped: number
  codec: string
  latencyMs: number
}

export class WebCodecsSender {
  private mediaStream: MediaStream | null = null
  private videoTrack: MediaStreamTrack | null = null
  private encoder: VideoEncoder | null = null
  private ws: WebSocket | null = null
  private peerConnection: RTCPeerConnection | null = null
  private canvas: OffscreenCanvas | null = null
  private gl: WebGL2RenderingContext | null = null

  private config: SenderConfig
  private stats: SenderStats = {
    encodeFps: 0,
    captureFps: 0,
    bitrateKbps: 0,
    bytesSent: 0,
    framesEncoded: 0,
    framesDropped: 0,
    codec: '',
    latencyMs: 0,
  }

  private running = false
  private frameCount = 0
  private encodeTimestamps: number[] = []
  private captureTimestamps: number[] = []
  private totalBytes = 0
  private statsInterval: ReturnType<typeof setInterval> | null = null
  private captureLoopId: number | null = null
  private bitrateController: BitrateController | null = null

  private static readonly LEGACY_WEBRTC_ERROR =
    'Legacy WebCodecs WebRTC sender is retired; use SrsWhipPublisher for live ingest.'

  constructor(config: SenderConfig) {
    this.config = {
      maxBitrate: config.bitrate * 2,
      enableBeauty: true,
      enableDenoise: true,
      ...config,
    }
    this.bitrateController = new BitrateController(config.bitrate)
  }

  async start(): Promise<void> {
    if (this.config.protocol === 'webrtc') {
      throw new Error(WebCodecsSender.LEGACY_WEBRTC_ERROR)
    }
    this.running = true

    // 1. Initialize camera at 4K
    await this.initCamera()

    // 2. Initialize GPU processing pipeline
    await this.initGPUPipeline()

    // 3. Initialize encoder
    await this.initEncoder()

    // 4. Connect stream output
    await this.connectStream()

    // 5. Start capture loop
    this.startCaptureLoop()

    // 6. Start stats reporting
    this.startStatsReporting()
  }

  stop(): void {
    this.running = false
    if (this.captureLoopId) cancelAnimationFrame(this.captureLoopId)
    this.encoder?.close()
    this.ws?.close()
    this.peerConnection?.close()
    this.mediaStream?.getTracks().forEach(t => t.stop())
    if (this.statsInterval) clearInterval(this.statsInterval)
  }

  getStats(): SenderStats {
    return { ...this.stats }
  }

  setBitrate(bitrate: number): void {
    if (this.encoder) {
      this.encoder.configure({
        codec: this.getCodecString(),
        width: this.config.width,
        height: this.config.height,
        bitrate,
        framerate: this.config.fps,
      })
    }
  }

  // ===== Camera Initialization (4K) =====
  private async initCamera(): Promise<void> {
    try {
      this.mediaStream = await navigator.mediaDevices.getUserMedia({
        video: {
          width: { ideal: 3840 },
          height: { ideal: 2160 },
          frameRate: { ideal: 60 },
          facingMode: 'user',
        },
        audio: true,
      })

      this.videoTrack = this.mediaStream.getVideoTrack()
      const settings = this.videoTrack.getSettings()
      console.log(`Camera initialized: ${settings.width}x${settings.height} @ ${settings.frameRate}fps`)
    } catch (e) {
      console.error('Camera init failed, falling back to 1080P', e)
      this.mediaStream = await navigator.mediaDevices.getUserMedia({
        video: { width: 1920, height: 1080, frameRate: 30 },
        audio: true,
      })
      this.videoTrack = this.mediaStream.getVideoTrack()
    }
  }

  // ===== GPU Pipeline (WebGL2 for oversampling & beauty) =====
  private async initGPUPipeline(): Promise<void> {
    this.canvas = new OffscreenCanvas(this.config.width, this.config.height)
    this.gl = this.canvas.getContext('webgl2', {
      alpha: false,
      desynchronized: true,
      premultipliedAlpha: false,
    })

    if (!this.gl) {
      console.warn('WebGL2 not available, skipping GPU processing')
    }
  }

  // ===== Encoder Initialization =====
  private async initEncoder(): Promise<void> {
    if (typeof VideoEncoder === 'undefined') {
      throw new Error('WebCodecs VideoEncoder not supported')
    }

    const codecString = this.getCodecString()
    this.stats.codec = codecString

    // Check support
    const support = await VideoEncoder.isConfigSupported({
      codec: codecString,
      width: this.config.width,
      height: this.config.height,
      bitrate: this.config.bitrate,
      framerate: this.config.fps,
    })

    if (!support.supported) {
      console.warn(`${codecString} not supported, falling back to h264`)
      this.config.codec = 'h264'
    }

    this.encoder = new VideoEncoder({
      output: (chunk: EncodedVideoChunk, metadata: EncodedVideoChunkMetadata | undefined) => {
        if (!this.running) return

        const data = chunk.byteLength
        this.totalBytes += data
        this.frameCount++
        this.encodeTimestamps.push(performance.now())
        if (this.encodeTimestamps.length > 120) this.encodeTimestamps.shift()

        // Build frame packet: [4B timestamp][1B keyframe][1B codec][payload]
        const buffer = new ArrayBuffer(6 + data)
        const view = new DataView(buffer)
        view.setUint32(0, chunk.timestamp / 1000, true) // ms timestamp
        view.setUint8(4, chunk.type === 'key' ? 1 : 0)
        view.setUint8(5, this.getCodecId())
        new Uint8Array(buffer, 6).set(new Uint8Array(chunk.byteLength))

        this.sendPacket(new Uint8Array(buffer))
      },
      error: (e: Error) => {
        console.error('VideoEncoder error:', e)
      },
    })

    this.encoder.configure({
      codec: this.getCodecString(),
      width: this.config.width,
      height: this.config.height,
      bitrate: this.config.bitrate,
      framerate: this.config.fps,
      avc: { format: 'annexb' },
    })
  }

  // ===== Capture + Encode Loop =====
  private startCaptureLoop(): void {
    const video = document.createElement('video')
    video.srcObject = this.mediaStream!
    video.autoplay = true
    video.muted = true
    video.playsInline = true

    video.onloadedmetadata = () => {
      video.play()

      const processFrame = async () => {
        if (!this.running || !this.encoder) return

        this.captureTimestamps.push(performance.now())
        if (this.captureTimestamps.length > 120) this.captureTimestamps.shift()

        try {
          // Create VideoFrame from video element
          let frame = new VideoFrame(video, {
            timestamp: performance.now() * 1000, // microseconds
          })

          // If WebGL processing needed, process through canvas
          if (this.gl && this.config.enableBeauty) {
            // Upload to WebGL texture, process with shaders, read back
            // (simplified - in production use proper GPU pipeline)
            const processedFrame = new VideoFrame(this.canvas!, {
              timestamp: frame.timestamp,
            })
            frame.close()
            frame = processedFrame
          }

          // Encode
          this.encoder.encode(frame, { keyFrameInterval: 120 })
          frame.close()
        } catch (e) {
          console.error('Frame processing error:', e)
        }

        // Control encode rate based on FPS
        const interval = 1000 / this.config.fps
        setTimeout(() => {
          this.captureLoopId = requestAnimationFrame(processFrame)
        }, interval)
      }

      this.captureLoopId = requestAnimationFrame(processFrame)
    }
  }

  // ===== Stream Output =====
  private async connectStream(): Promise<void> {
    switch (this.config.protocol) {
      case 'websocket':
        this.connectWebSocket()
        break
      case 'webrtc':
        await this.connectWebRTC()
        break
    }
  }

  private connectWebSocket(): void {
    const ws = new WebSocket(this.config.streamUrl)
    this.ws = ws
    ws.binaryType = 'arraybuffer'

    ws.onopen = () => console.log('Stream WebSocket connected')
    ws.onclose = () => {
      if (this.running) setTimeout(() => this.connectWebSocket(), 3000)
    }
    ws.onerror = () => console.error('Stream WebSocket error')
  }

  private async connectWebRTC(): Promise<void> {
    throw new Error(WebCodecsSender.LEGACY_WEBRTC_ERROR)
  }

  private sendPacket(data: Uint8Array): void {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(data)
    }
    // WebRTC sends via track, not here
  }

  private getCodecString(): string {
    switch (this.config.codec) {
      case 'av1': return 'av01.0.05M.08'
      case 'h265': return 'hev1.1.6.L153.B0'
      case 'h264': return 'avc1.64001F'
    }
  }

  private getCodecId(): number {
    switch (this.config.codec) {
      case 'av1': return 1
      case 'h265': return 2
      case 'h264': return 3
    }
  }

  private startStatsReporting(): void {
    this.statsInterval = setInterval(() => {
      const now = performance.now()

      // Encode FPS
      const recentEncode = this.encodeTimestamps.filter(t => now - t < 2000)
      this.stats.encodeFps = recentEncode.length / 2

      // Capture FPS
      const recentCapture = this.captureTimestamps.filter(t => now - t < 2000)
      this.stats.captureFps = recentCapture.length / 2

      // Bitrate
      this.stats.bitrateKbps = (this.totalBytes * 8) / 1000
      this.stats.bytesSent += this.totalBytes
      this.totalBytes = 0

      this.stats.framesEncoded = this.frameCount
    }, 2000)
  }
}

// ===== Simple Bitrate Controller =====
class BitrateController {
  private currentBitrate: number
  private minBitrate = 500000
  private maxBitrate: number
  private lastChange = 0

  constructor(initialBitrate: number, maxBitrate?: number) {
    this.currentBitrate = initialBitrate
    this.maxBitrate = maxBitrate || initialBitrate * 2
  }

  update(rtt: number, packetLoss: number): number {
    const now = Date.now()
    if (now - this.lastChange < 10000) return this.currentBitrate

    if (packetLoss > 0.05 || rtt > 500) {
      // Bad network - reduce bitrate
      this.currentBitrate = Math.max(
        this.minBitrate,
        Math.floor(this.currentBitrate * 0.75)
      )
    } else if (packetLoss < 0.01 && rtt < 100) {
      // Good network - increase bitrate
      this.currentBitrate = Math.min(
        this.maxBitrate,
        Math.floor(this.currentBitrate * 1.25)
      )
    }

    this.lastChange = now
    return this.currentBitrate
  }
}
