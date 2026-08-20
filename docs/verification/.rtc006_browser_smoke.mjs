import { createRequire } from 'node:module'
import http from 'node:http'
import assert from 'node:assert/strict'

const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT_PACKAGE || 'playwright')

const srsRtc = 'http://127.0.0.1:1985/rtc/v1'
const srsHttp = 'http://127.0.0.1:8080/live'
const stream = `rtc006_smoke_${Date.now()}`
const pageServer = http.createServer((_req, res) => {
  res.writeHead(200, { 'content-type': 'text/html; charset=utf-8' })
  res.end('<!doctype html><html><body></body></html>')
})

const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms))
const waitIce = (pc) => new Promise((resolve) => {
  if (pc.iceGatheringState === 'complete') return resolve()
  const timer = setTimeout(resolve, 5000)
  pc.addEventListener('icegatheringstatechange', () => {
    if (pc.iceGatheringState === 'complete') {
      clearTimeout(timer)
      resolve()
    }
  })
})

async function negotiate(page, endpoint, mode) {
  return page.evaluate(async ({ endpoint, mode }) => {
    const pc = new RTCPeerConnection()
    const preferH264 = (transceiver) => {
      const codecs = RTCRtpSender.getCapabilities('video')?.codecs || []
      const h264 = codecs.filter((codec) => codec.mimeType?.toLowerCase() === 'video/h264')
      if (!h264.length) throw new Error('browser has no H.264 RTP capability')
      transceiver.setCodecPreferences(h264)
    }
    let media = null
    let location = null
    let remote = null
    const state = () => pc.connectionState
    if (mode === 'whip') {
      media = await navigator.mediaDevices.getUserMedia({ video: true, audio: true })
      for (const track of media.getTracks()) pc.addTrack(track, media)
      const videoTransceiver = pc.getTransceivers().find((item) => item.sender.track?.kind === 'video')
      preferH264(videoTransceiver)
    } else {
      const videoTransceiver = pc.addTransceiver('video', { direction: 'recvonly' })
      preferH264(videoTransceiver)
      pc.addTransceiver('audio', { direction: 'recvonly' })
      remote = new MediaStream()
      pc.ontrack = (event) => {
        for (const track of event.streams[0]?.getTracks() || [event.track]) {
          if (!remote.getTracks().some((item) => item.id === track.id)) remote.addTrack(track)
        }
        window.__rtc006Video.srcObject = remote
        void window.__rtc006Video.play().catch(() => {})
      }
    }
    const offer = await pc.createOffer()
    await pc.setLocalDescription(offer)
    await new Promise((resolve) => {
      if (pc.iceGatheringState === 'complete') return resolve()
      const timer = setTimeout(resolve, 5000)
      pc.addEventListener('icegatheringstatechange', () => {
        if (pc.iceGatheringState === 'complete') {
          clearTimeout(timer)
          resolve()
        }
      })
    })
    const response = await fetch(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/sdp', Accept: 'application/sdp' },
      body: pc.localDescription.sdp
    })
    if (!response.ok) throw new Error(`${mode} SDP request failed with status ${response.status}`)
    location = response.headers.get('Location')
    await pc.setRemoteDescription({ type: 'answer', sdp: await response.text() })
    // SRS can receive RTP before Chromium exposes non-zero RTP counters.
    // For WHIP, the WHEP subscriber and decoded first-frame assertion below
    // provide the end-to-end media proof, so do not fail on a publisher-only
    // browser stats timing race.
    if (mode === 'whip') await new Promise((resolve) => setTimeout(resolve, 3000))
    const mediaStats = {
      connectionState: state(),
      iceConnectionState: pc.iceConnectionState,
    }
    window.__rtc006 = { pc, media, location }
    return { ...mediaStats, sessionCreated: Boolean(location) }
  }, { endpoint, mode })
}

async function cleanup(page) {
  await page.evaluate(async () => {
    const session = window.__rtc006
    if (!session) return
    for (const track of session.media?.getTracks() || []) track.stop()
    session.pc.close()
    if (session.location) {
      try { await fetch(new URL(session.location, location.href), { method: 'DELETE' }) } catch {}
    }
  })
}

async function readFirstBytes(url, maxBytes = 64 * 1024) {
  for (let attempt = 0; attempt < 10; attempt++) {
    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), 5000)
    try {
      const response = await fetch(url, { signal: controller.signal })
      if (!response.ok) {
        if (response.status !== 404) return { status: response.status, bytes: 0 }
        await wait(500)
        continue
      }
      const reader = response.body?.getReader()
      let bytes = 0
      while (reader && bytes < maxBytes) {
        const chunk = await reader.read()
        if (chunk.done) break
        bytes += chunk.value?.byteLength || 0
      }
      return { status: response.status, bytes }
    } catch (error) {
      if (attempt === 9) return { status: 0, bytes: 0, error: error.name }
      await wait(500)
    } finally {
      clearTimeout(timer)
    }
  }
  return { status: 0, bytes: 0, error: 'unavailable' }
}

async function readText(url, ready = () => true) {
  for (let attempt = 0; attempt < 30; attempt++) {
    try {
      const response = await fetch(url)
      if (response.ok) {
        const body = await response.text()
        if (ready(body)) return { status: response.status, body }
      } else if (response.status !== 404) {
        return { status: response.status, body: '' }
      }
    } catch {}
    await wait(1000)
  }
  return { status: 404, body: '' }
}

await new Promise((resolve) => pageServer.listen(8765, '127.0.0.1', resolve))
const browser = await chromium.launch({
  headless: true,
  executablePath: process.env.BROWSER_EXECUTABLE_PATH || undefined,
  args: ['--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream', '--autoplay-policy=no-user-gesture-required', '--disable-gpu', '--use-angle=swiftshader']
})
const context = await browser.newContext({ permissions: ['camera', 'microphone'] })
const publisher = await context.newPage()
const viewer = await context.newPage()
await publisher.goto('http://127.0.0.1:8765')
await viewer.goto('http://127.0.0.1:8765')
await viewer.evaluate(() => {
  const video = document.createElement('video')
  video.autoplay = true
  video.muted = true
  video.playsInline = true
  document.body.append(video)
  window.__rtc006Video = video
})

try {
  const whip = await negotiate(publisher, `${srsRtc}/whip/?app=live&stream=${stream}`, 'whip')
  // Allow the publisher to emit an H.264 keyframe before WHEP joins.
  // SRS writes the first HLS playlist after the initial fragment boundary.
  await wait(12000)
  const whep = await negotiate(viewer, `${srsRtc}/whep/?app=live&stream=${stream}`, 'whep')
  const firstFrame = await viewer.evaluate(() => new Promise((resolve) => {
    const video = window.__rtc006Video
    const timer = setTimeout(async () => {
      const stats = []
      const report = await window.__rtc006?.pc?.getStats()
      for (const item of report?.values() || []) {
        if (item.type === 'inbound-rtp' || item.type === 'candidate-pair' || item.type === 'track') {
          stats.push({ type: item.type, kind: item.kind, bytesReceived: item.bytesReceived || 0, framesDecoded: item.framesDecoded || 0, framesReceived: item.framesReceived || 0, state: item.state || '' })
        }
      }
      resolve({
        decoded: false,
        error: 'WHEP first frame timeout',
        readyState: video.readyState,
        currentTime: video.currentTime,
        width: video.videoWidth,
        height: video.videoHeight,
        paused: video.paused,
        tracks: [...(video.srcObject?.getTracks() || [])].map((track) => ({ kind: track.kind, readyState: track.readyState })),
        stats,
      })
    }, 10000)
    const done = () => {
      clearTimeout(timer)
      resolve({ decoded: true, currentTime: video.currentTime, width: video.videoWidth, height: video.videoHeight })
    }
    if ('requestVideoFrameCallback' in video) video.requestVideoFrameCallback(done)
    else video.addEventListener('loadeddata', done, { once: true })
  }))
  Object.assign(whep, await viewer.evaluate(() => ({
    connectionState: window.__rtc006.pc.connectionState,
    iceConnectionState: window.__rtc006.pc.iceConnectionState,
  })))
  await wait(5000)
  const flv = await readFirstBytes(`${srsHttp}/${stream}.flv`)
  await wait(3000)
  const hlsUrl = `${srsHttp}/${stream}.m3u8`
  const hlsMaster = await readText(hlsUrl, (body) => body.includes('.m3u8'))
  const hlsMediaName = hlsMaster.body.match(/^\s*(\/[^\r\n]+\.m3u8[^\r\n]*)\s*$/m)?.[1]
  const hlsMediaUrl = hlsMediaName ? new URL(hlsMediaName, hlsUrl).toString() : hlsUrl
  const hlsPlaylist = await readText(hlsMediaUrl, (body) => body.includes('.ts'))
  const hlsSegmentName = hlsPlaylist.body.match(/([^\r\n]+\.ts)/)?.[1]
  const hlsSegment = hlsSegmentName
    ? await readFirstBytes(new URL(hlsSegmentName, hlsMediaUrl).toString())
    : { status: 0, bytes: 0, error: 'playlist has no ts segment' }
  const hls = {
    status: hlsMaster.status,
    bytes: hlsMaster.body.length,
    media: { status: hlsPlaylist.status, bytes: hlsPlaylist.body.length },
    segment: hlsSegment
  }
  assert.equal(whip.connectionState, 'connected', 'WHIP peer connection did not reach connected')
  assert.equal(whip.sessionCreated, true, 'WHIP response did not create a provider session')
  assert.equal(whep.connectionState, 'connected', 'WHEP peer connection did not reach connected')
  assert.equal(whep.sessionCreated, true, 'WHEP response did not create a provider session')
  assert.equal(firstFrame.decoded, true, 'WHEP did not decode a first video frame')
  assert.ok(firstFrame.width > 0 && firstFrame.height > 0, 'WHEP first frame has invalid dimensions')
  assert.equal(hls.status, 200, 'HLS master playlist request failed')
  assert.ok(hls.bytes > 0, 'HLS master playlist was empty')
  assert.equal(hls.media.status, 200, 'HLS media playlist request failed')
  assert.ok(hls.media.bytes > 0, 'HLS media playlist was empty')
  assert.equal(hls.segment.status, 200, 'HLS segment request failed')
  assert.ok(hls.segment.bytes > 0, 'HLS segment was empty')
  assert.equal(flv.status, 200, 'HTTP-FLV request failed')
  assert.ok(flv.bytes > 0, 'HTTP-FLV response was empty')
  console.log(JSON.stringify({ stream, whip, whep, firstFrame, hls, flv }))
} finally {
  await cleanup(viewer)
  await cleanup(publisher)
  await browser.close()
  await new Promise((resolve) => pageServer.close(resolve))
}
