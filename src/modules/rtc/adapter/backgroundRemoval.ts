import { SelfieSegmentation } from '@mediapipe/selfie_segmentation'

/**
 * Runs lightweight person segmentation locally and publishes a canvas-captured
 * video track. The original camera track is never stopped, so disabling the
 * effect can restore it without reopening the permission prompt.
 */
export class BackgroundRemovalProcessor {
  private segmentation: SelfieSegmentation | null = null
  private sourceVideo: HTMLVideoElement | null = null
  private outputTrack: MediaStreamTrack | null = null
  private frameHandle = 0
  private processing = false
  private enabled = false

  async start(inputTrack: MediaStreamTrack): Promise<MediaStreamTrack> {
    await this.stop()
    if (typeof document === 'undefined') throw new Error('背景处理只能在浏览器中运行')
    if (!HTMLCanvasElement.prototype.captureStream) {
      throw new Error('当前浏览器不支持视频背景处理')
    }

    const video = document.createElement('video')
    video.muted = true
    video.playsInline = true
    video.autoplay = true
    video.srcObject = new MediaStream([inputTrack])
    await video.play()

    const canvas = document.createElement('canvas')
    canvas.width = video.videoWidth || 640
    canvas.height = video.videoHeight || 360
    const output = canvas.captureStream(24).getVideoTracks()[0]
    if (!output) throw new Error('无法创建背景处理视频轨道')

    const assetBase = (
      import.meta.env.VITE_MEDIAPIPE_ASSET_BASE ||
      'https://cdn.jsdelivr.net/npm/@mediapipe/selfie_segmentation'
    ).replace(/\/$/, '')
    const segmentation = new SelfieSegmentation({
      locateFile: (file) => `${assetBase}/${file}`
    })
    segmentation.setOptions({ modelSelection: 1 })
    const context = canvas.getContext('2d')
    if (!context) throw new Error('无法创建背景处理画布')

    segmentation.onResults((results) => {
      context.save()
      context.clearRect(0, 0, canvas.width, canvas.height)
      context.drawImage(results.segmentationMask, 0, 0, canvas.width, canvas.height)
      context.globalCompositeOperation = 'source-in'
      context.drawImage(results.image, 0, 0, canvas.width, canvas.height)
      context.globalCompositeOperation = 'destination-over'
      context.fillStyle = '#111111'
      context.fillRect(0, 0, canvas.width, canvas.height)
      context.restore()
    })
    await segmentation.initialize()

    this.segmentation = segmentation
    this.sourceVideo = video
    this.outputTrack = output
    this.enabled = true
    this.scheduleFrame()
    return output
  }

  private scheduleFrame() {
    if (!this.enabled) return
    this.frameHandle = requestAnimationFrame(() => {
      void this.processFrame().finally(() => this.scheduleFrame())
    })
  }

  private async processFrame() {
    if (this.processing || !this.segmentation || !this.sourceVideo) return
    if (this.sourceVideo.readyState < HTMLMediaElement.HAVE_CURRENT_DATA) return
    this.processing = true
    try {
      await this.segmentation.send({ image: this.sourceVideo })
    } finally {
      this.processing = false
    }
  }

  async stop() {
    this.enabled = false
    if (this.frameHandle) cancelAnimationFrame(this.frameHandle)
    this.frameHandle = 0
    this.outputTrack?.stop()
    this.outputTrack = null
    if (this.sourceVideo) {
      this.sourceVideo.pause()
      this.sourceVideo.srcObject = null
    }
    this.sourceVideo = null
    if (this.segmentation) await this.segmentation.close().catch(() => {})
    this.segmentation = null
    this.processing = false
  }
}
