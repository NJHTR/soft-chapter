<template>
  <div class="video-editor">
    <!-- 顶栏 -->
    <div class="editor-top-bar">
      <Icon icon="mingcute:close-line" @click="goBack" />
      <span class="editor-title">剪辑</span>
      <span class="done-btn" @click="confirmEdit">保存</span>
    </div>

    <!-- 视频预览 -->
    <div class="player-area" @click="togglePlay">
      <video ref="videoEl" class="player-video" :src="videoSrc"
        @timeupdate="onTimeUpdate" @loadedmetadata="onVideoMeta"
        @ended="onVideoEnded" preload="auto" crossorigin="anonymous" />
      <div v-if="!isPlaying" class="play-overlay">
        <Icon icon="mingcute:play-fill" />
      </div>
      <div class="trim-shades">
        <div class="shade left" :style="{ width: trimLeftPercent + '%' }"></div>
        <div class="shade right" :style="{ width: (100 - trimRightPercent) + '%' }"></div>
      </div>
    </div>

    <!-- 底部编辑区 -->
    <div class="editor-bottom">
      <!-- 工具栏 -->
      <div class="toolbar">
        <div class="tool-group">
          <button class="tool-btn" @click="undoSplit" :disabled="segments.length <= 1">
            <Icon icon="mingcute:back-line" /><span>撤销分割</span>
          </button>
          <button class="tool-btn" @click="splitAtPlayhead">
            <Icon icon="mingcute:scissors-line" /><span>分割</span>
          </button>
          <button class="tool-btn" @click="resetSegments">
            <Icon icon="mingcute:refresh-line" /><span>重置</span>
          </button>
        </div>
        <div class="tool-group">
          <select class="speed-select" :value="currentSegSpeed" @change="onSpeedChange">
            <option value="0.5">0.5x</option>
            <option value="0.75">0.75x</option>
            <option value="1" selected>1x</option>
            <option value="1.5">1.5x</option>
            <option value="2">2x</option>
            <option value="3">3x</option>
          </select>
        </div>
      </div>

      <!-- 时间轴 -->
      <div class="timeline-panel" ref="timelinePanel">
        <!-- 轨道标签列 -->
        <div class="track-labels">
          <span class="label video-label">视频</span>
          <span class="label audio-label">原声</span>
          <span class="label music-label">音乐</span>
        </div>

        <!-- 轨道内容 -->
        <div class="tracks-area" ref="tracksArea">
          <!-- 视频轨 -->
          <div class="track video-track" ref="videoTrackEl" @pointerdown="onTrackSeek">
            <div class="frame-strip">
              <div v-for="(thumb, i) in thumbnails" :key="i" class="frame-thumb"
                :style="{ backgroundImage: thumb ? 'url(' + thumb + ')' : 'none' }"></div>
            </div>
            <!-- 片段分割线 -->
            <div v-for="(seg, i) in innerSegs" :key="'seg-'+i"
              class="segment-boundary"
              :style="{ left: seg.percent + '%' }"
              v-show="i < segments.length - 1"></div>
            <!-- 裁剪手柄 -->
            <div class="trim-handle left" :style="{ left: trimLeftPercent + '%' }"
              @pointerdown.stop.prevent="startTrimDrag('left', $event)">
              <div class="handle-bar"></div>
            </div>
            <div class="trim-handle right" :style="{ left: trimRightPercent + '%' }"
              @pointerdown.stop.prevent="startTrimDrag('right', $event)">
              <div class="handle-bar"></div>
            </div>
            <!-- 选中片段高亮 -->
            <div v-if="activeSegIdx >= 0 && segments[activeSegIdx]"
              class="active-segment"
              :style="activeSegStyle"></div>
            <!-- 播放头 -->
            <div class="playhead" :style="{ left: playheadPercent + '%' }"></div>
          </div>

          <!-- 原声音频轨 -->
          <div class="track audio-track" ref="audioTrackEl">
            <canvas ref="audioWaveCanvas" class="wave-canvas"></canvas>
            <div class="playhead" :style="{ left: playheadPercent + '%' }"></div>
          </div>

          <!-- 音乐轨 -->
          <div class="track music-track" ref="musicTrackEl">
            <canvas ref="musicWaveCanvas" class="wave-canvas"></canvas>
            <div v-if="music" class="music-indicator" :style="musicBarStyle">
              <Icon icon="vaadin:music" /><span>{{ music.name }}</span>
            </div>
            <span v-else class="no-music-tag">未选音乐</span>
            <div class="playhead" :style="{ left: playheadPercent + '%' }"></div>
          </div>
        </div>
      </div>

      <!-- 底部控制 -->
      <div class="control-bar">
        <Icon class="play-btn" :icon="isPlaying ? 'mingcute:pause-fill' : 'mingcute:play-fill'"
          @click.stop="togglePlay" />
        <span class="time-info">{{ formatTime(currentTime) }} / {{ formatTime(trimEnd - trimStart) }}</span>
        <div class="seg-info" v-if="segments.length > 1">
          <span>{{ activeSegIdx + 1 }}/{{ segments.length }} 段</span>
          <template v-if="activeSegIdx >= 0 && segments[activeSegIdx]">
            <span class="seg-speed">×{{ segments[activeSegIdx].speed }}</span>
          </template>
        </div>
        <div v-if="music" class="vol-wrap">
          <Icon icon="mingcute:volume-line" />
          <input type="range" min="0" max="100" :value="bgmVolume * 100"
            @input="onVolumeChange" class="vol-slider" />
        </div>
      </div>
    </div>

    <canvas ref="thumbCanvas" style="display:none"></canvas>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'

defineOptions({ name: 'VideoEditor' })

interface Segment {
  startTime: number
  endTime: number
  speed: number
}

const router = useRouter()
const videoEl = ref<HTMLVideoElement | null>(null)
const videoTrackEl = ref<HTMLDivElement | null>(null)
const audioTrackEl = ref<HTMLDivElement | null>(null)
const musicTrackEl = ref<HTMLDivElement | null>(null)
const audioWaveCanvas = ref<HTMLCanvasElement | null>(null)
const musicWaveCanvas = ref<HTMLCanvasElement | null>(null)
const thumbCanvas = ref<HTMLCanvasElement | null>(null)
const tracksArea = ref<HTMLDivElement | null>(null)

const videoSrc = ref('')
const videoDuration = ref(0)
const currentTime = ref(0)
const isPlaying = ref(false)
const bgmStartOffset = ref(0)
const bgmVolume = ref(0.7)
const music = ref<{ id: number; name: string; artist?: string; play_url?: string; duration?: number } | null>(null)
const thumbnails = ref<string[]>([])
const videoWavePeaks = ref<number[]>([])
const musicWavePeaks = ref<number[]>([])
const activeSegIdx = ref(0)

const segments = reactive<Segment[]>([{ startTime: 0, endTime: 0, speed: 1 }])
const FRAME_COUNT = 15
const WAVEFORM_PEAKS = 100

let musicAudio: HTMLAudioElement | null = null
let syncRafId = 0
let audioCtx: AudioContext | null = null
let resizeObserver: ResizeObserver | null = null

// 内部片段（包含裁剪边界）
const innerSegs = computed(() => {
  const dur = videoDuration.value || 1
  return segments.map(s => ({
    ...s,
    percent: (s.startTime / dur) * 100
  }))
})

const activeSegStyle = computed(() => {
  const dur = videoDuration.value || 1
  const s = segments[activeSegIdx.value]
  if (!s) return {}
  return {
    left: (s.startTime / dur) * 100 + '%',
    width: ((s.endTime - s.startTime) / dur) * 100 + '%'
  }
})

const currentSegSpeed = computed(() => {
  const s = segments[activeSegIdx.value]
  return s ? String(s.speed) : '1'
})

const trimStart = computed(() => segments[0]?.startTime ?? 0)
const trimEnd = computed(() => segments[segments.length - 1]?.endTime ?? videoDuration.value)

const playheadPercent = computed(() => {
  if (!videoDuration.value) return 0
  return (currentTime.value / videoDuration.value) * 100
})
const trimLeftPercent = computed(() => {
  if (!videoDuration.value) return 0
  return (trimStart.value / videoDuration.value) * 100
})
const trimRightPercent = computed(() => {
  if (!videoDuration.value) return 100
  return (trimEnd.value / videoDuration.value) * 100
})

const musicBarStyle = computed(() => {
  if (!music.value || !videoDuration.value) return { display: 'none' }
  const tW = tracksArea.value?.clientWidth || 1
  const pxPerSec = tW / videoDuration.value
  const left = bgmStartOffset.value * pxPerSec
  const w = Math.max((music.value.duration || 15) * pxPerSec, 50)
  return { left: left + 'px', width: w + 'px' }
})

// ======= 挂载 =======
onMounted(async () => {
  videoSrc.value = sessionStorage.getItem('video_editor_video_src') || ''
  bgmStartOffset.value = Number(sessionStorage.getItem('video_editor_bgm_start_offset') || 0)
  bgmVolume.value = Number(sessionStorage.getItem('video_editor_bgm_volume') || 0.7)
  const prevDur = Number(sessionStorage.getItem('video_editor_duration') || 0)

  const raw = sessionStorage.getItem('video_editor_music')
  if (raw) { try { music.value = JSON.parse(raw) } catch { music.value = null } }
  if (music.value) initMusicAudio()

  if (prevDur) {
    videoDuration.value = prevDur
    segments[0].endTime = prevDur
    nextTick(() => {
      extractThumbnails()
      extractAudioWaveform()
      if (music.value?.play_url) extractMusicWaveform()
    })
  }

  if (tracksArea.value) {
    resizeObserver = new ResizeObserver(() => { drawAllWaveforms() })
    resizeObserver.observe(tracksArea.value)
  }
})

onBeforeUnmount(() => {
  stopAll()
  cancelAnimationFrame(syncRafId)
  if (resizeObserver) { resizeObserver.disconnect(); resizeObserver = null }
  if (audioCtx) { audioCtx.close(); audioCtx = null }
})

// ======= 片段操作 =======
function findActiveSegment() {
  const ct = videoEl.value?.currentTime ?? currentTime.value
  for (let i = 0; i < segments.length; i++) {
    if (ct >= segments[i].startTime && ct < segments[i].endTime) return i
  }
  return segments.length - 1
}

function splitAtPlayhead() {
  const ct = videoEl.value?.currentTime ?? currentTime.value
  const idx = findActiveSegment()
  const seg = segments[idx]
  if (!seg || ct - seg.startTime < 0.3 || seg.endTime - ct < 0.3) return

  const newSeg: Segment = { startTime: ct, endTime: seg.endTime, speed: seg.speed }
  seg.endTime = ct
  segments.splice(idx + 1, 0, newSeg)
  activeSegIdx.value = idx + 1
}

function undoSplit() {
  if (segments.length <= 1) return
  const idx = Math.min(activeSegIdx.value, segments.length - 2)
  segments[idx].endTime = segments[idx + 1].endTime
  segments.splice(idx + 1, 1)
  activeSegIdx.value = idx
}

function resetSegments() {
  segments.length = 0
  segments.push({ startTime: 0, endTime: videoDuration.value, speed: 1 })
  activeSegIdx.value = 0
}

function onSpeedChange(e: Event) {
  const v = Number((e.target as HTMLSelectElement).value)
  if (segments[activeSegIdx.value]) {
    segments[activeSegIdx.value].speed = v
  }
}

watch(currentTime, () => {
  activeSegIdx.value = findActiveSegment()
})

// ======= 视频元数据 / 缩略图 =======
function onVideoMeta() {
  if (!videoEl.value) return
  videoDuration.value = videoEl.value.duration
  segments[0].endTime = videoEl.value.duration
  sessionStorage.setItem('video_editor_duration', String(videoDuration.value))
  nextTick(() => {
    extractThumbnails()
    extractAudioWaveform()
  })
}

async function extractThumbnails() {
  const v = videoEl.value; const cvs = thumbCanvas.value
  if (!v || !cvs || !videoDuration.value) return
  cvs.width = 120; cvs.height = 200
  const ctx = cvs.getContext('2d'); if (!ctx) return
  const dur = videoDuration.value
  const wasPlaying = !v.paused; v.pause()
  const thumbs: string[] = []

  for (let i = 0; i < FRAME_COUNT; i++) {
    v.currentTime = (i / (FRAME_COUNT - 1)) * dur
    await new Promise<void>(resolve => {
      const cb = () => { v.removeEventListener('seeked', cb); try { ctx.drawImage(v, 0, 0, cvs.width, cvs.height); thumbs.push(cvs.toDataURL('image/jpeg', 0.5)) } catch { thumbs.push('') }; resolve() }
      v.addEventListener('seeked', cb)
    })
  }
  thumbnails.value = thumbs
  v.currentTime = 0
  if (wasPlaying) v.play()
}

// ======= 波形提取 =======
async function extractAudioWaveform() {
  try {
    const resp = await fetch(videoSrc.value)
    const buf = await resp.arrayBuffer()
    audioCtx = audioCtx || new AudioContext()
    const audioBuf = await audioCtx.decodeAudioData(buf)
    videoWavePeaks.value = computePeaks(audioBuf, WAVEFORM_PEAKS)
  } catch { videoWavePeaks.value = Array.from({ length: WAVEFORM_PEAKS }, () => Math.random() * 0.6 + 0.2) }
  nextTick(() => drawAllWaveforms())
}

async function extractMusicWaveform() {
  if (!music.value?.play_url) return
  try {
    const resp = await fetch(music.value.play_url)
    const buf = await resp.arrayBuffer()
    audioCtx = audioCtx || new AudioContext()
    const audioBuf = await audioCtx.decodeAudioData(buf)
    musicWavePeaks.value = computePeaks(audioBuf, WAVEFORM_PEAKS)
  } catch { musicWavePeaks.value = Array.from({ length: WAVEFORM_PEAKS }, () => Math.random() * 0.4 + 0.3) }
  nextTick(() => drawAllWaveforms())
}

function computePeaks(buf: AudioBuffer, n: number): number[] {
  const d = buf.getChannelData(0); const step = Math.floor(d.length / n)
  const peaks: number[] = []
  for (let i = 0; i < n; i++) { let max = 0; for (let j = i * step; j < (i + 1) * step && j < d.length; j++) { const v = Math.abs(d[j]); if (v > max) max = v }; peaks.push(max) }
  return peaks
}

function drawAllWaveforms() {
  drawWaveform(audioWaveCanvas.value, audioTrackEl.value, videoWavePeaks.value, 'rgba(255,255,255,0.25)')
  drawWaveform(musicWaveCanvas.value, musicTrackEl.value, musicWavePeaks.value, 'rgba(255,107,107,0.5)')
}

function drawWaveform(canvas: HTMLCanvasElement | null, container: HTMLDivElement | null, peaks: number[], color: string) {
  if (!canvas || !container || !peaks.length) return
  const w = container.clientWidth; const h = container.clientHeight
  if (w <= 0 || h <= 0) return
  const dpr = window.devicePixelRatio || 1
  canvas.width = w * dpr; canvas.height = h * dpr
  canvas.style.width = w + 'px'; canvas.style.height = h + 'px'
  const ctx = canvas.getContext('2d'); if (!ctx) return
  ctx.scale(dpr, dpr)
  ctx.clearRect(0, 0, w, h)
  const barW = w / peaks.length; const midY = h / 2
  ctx.fillStyle = color
  for (let i = 0; i < peaks.length; i++) {
    const bh = Math.max(1.5, peaks[i] * h * 0.65)
    ctx.fillRect(i * barW, midY - bh / 2, Math.max(1, barW - 1), bh)
  }
}

// ======= 播放控制 =======
function togglePlay() {
  const v = videoEl.value; if (!v) return
  if (v.paused) {
    const s = segments[findActiveSegment()]
    if (s && (v.currentTime < s.startTime || v.currentTime >= s.endTime)) {
      v.currentTime = s.startTime
    }
    v.playbackRate = segments[activeSegIdx.value]?.speed ?? 1
    v.play(); isPlaying.value = true
    startMusicSync()
    syncRafId = requestAnimationFrame(syncLoop)
  } else { pauseAll() }
}

function pauseAll() {
  if (videoEl.value) videoEl.value.pause()
  if (musicAudio) musicAudio.pause()
  isPlaying.value = false; cancelAnimationFrame(syncRafId)
}

function stopAll() { pauseAll(); if (musicAudio) { musicAudio.pause(); musicAudio.currentTime = 0 } }

function onVideoEnded() {
  pauseAll()
  if (videoEl.value) { videoEl.value.currentTime = trimStart.value; currentTime.value = trimStart.value }
}

function onTimeUpdate() {
  if (!videoEl.value) return
  currentTime.value = videoEl.value.currentTime
  // 检查片段边界：播放到片段末尾时跳到下一片段或循环
  const idx = findActiveSegment()
  const seg = segments[idx]
  if (seg && currentTime.value >= seg.endTime) {
    if (idx < segments.length - 1) {
      videoEl.value.currentTime = segments[idx + 1].startTime
      videoEl.value.playbackRate = segments[idx + 1].speed
    } else {
      videoEl.value.currentTime = trimStart.value
      videoEl.value.playbackRate = segments[0].speed
      startMusicSync()
    }
  }
}

// ======= 音乐同步 =======
function startMusicSync() {
  if (!musicAudio || !videoEl.value || !music.value) return
  const vt = videoEl.value.currentTime
  const mt = vt - bgmStartOffset.value
  const dur = videoDuration.value
  if (mt >= 0 && vt < bgmStartOffset.value + dur) {
    musicAudio.currentTime = mt
    musicAudio.play().catch(() => {})
  }
}

let lastSyncVt = 0
let editorLoopHandled = false

function syncLoop() {
  if (!isPlaying.value) {
    syncRafId = requestAnimationFrame(syncLoop)
    return
  }
  const v = videoEl.value
  if (!v) {
    syncRafId = requestAnimationFrame(syncLoop)
    return
  }

  const audio = musicAudio
  if (!audio || !music.value) {
    syncRafId = requestAnimationFrame(syncLoop)
    return
  }

  const vt = v.currentTime
  const dur = v.duration || videoDuration.value
  const mt = vt - bgmStartOffset.value

  const timeJumpedBack = vt < lastSyncVt - 0.5
  lastSyncVt = vt

  if (timeJumpedBack && !editorLoopHandled) {
    audio.pause()
    audio.currentTime = 0
    audio.play().catch(() => {})
    editorLoopHandled = true
  } else if (!timeJumpedBack) {
    editorLoopHandled = false
  }

  if (!timeJumpedBack && mt >= 0 && vt < bgmStartOffset.value + dur) {
    if (audio.paused) {
      audio.currentTime = mt
      audio.play().catch(() => {})
    } else if (Math.abs(audio.currentTime - mt) > 0.3) {
      audio.currentTime = mt
    }
  }

  syncRafId = requestAnimationFrame(syncLoop)
}

function initMusicAudio() {
  if (!music.value?.play_url) return
  musicAudio = new Audio(music.value.play_url)
  musicAudio.volume = bgmVolume.value
  musicAudio.preload = 'auto'
}

// ======= 轨道交互 =======
function onTrackSeek(e: PointerEvent) {
  const track = videoTrackEl.value
  if (!track || !videoEl.value || !videoDuration.value) return
  const r = track.getBoundingClientRect()
  const t = Math.max(0, Math.min(videoDuration.value, ((e.clientX - r.left) / r.width) * videoDuration.value))
  videoEl.value.currentTime = t; currentTime.value = t
  if (isPlaying.value) startMusicSync()
}

function startTrimDrag(side: 'left' | 'right', e: PointerEvent) {
  const track = videoTrackEl.value; if (!track || !videoDuration.value) return
  const r = track.getBoundingClientRect(); const pps = r.width / videoDuration.value
  const startX = e.clientX

  function onMove(ev: PointerEvent) {
    const dx = ev.clientX - startX; const dt = dx / pps
    if (side === 'left') {
      const v = Math.max(0, Math.min(trimEnd.value - 0.5, trimStart.value + dt))
      segments[0].startTime = Math.round(v * 10) / 10
    } else {
      const v = Math.max(trimStart.value + 0.5, Math.min(videoDuration.value, trimEnd.value + dt))
      segments[segments.length - 1].endTime = Math.round(v * 10) / 10
    }
    if (videoEl.value && !isPlaying.value) {
      const st = side === 'left' ? trimStart.value : trimEnd.value
      videoEl.value.currentTime = st; currentTime.value = st
    }
  }
  function onUp() { document.removeEventListener('pointermove', onMove); document.removeEventListener('pointerup', onUp) }
  document.addEventListener('pointermove', onMove); document.addEventListener('pointerup', onUp)
}

function onVolumeChange(e: Event) {
  bgmVolume.value = Number((e.target as HTMLInputElement).value) / 100
  if (musicAudio) musicAudio.volume = bgmVolume.value
}

// ======= 工具 =======
function formatTime(s: number) { if (isNaN(s) || s < 0) s = 0; const m = Math.floor(s / 60); const sec = Math.floor(s % 60); return `${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}` }

function goBack() { stopAll(); router.back() }

function confirmEdit() {
  stopAll()
  sessionStorage.setItem('video_editor_trim_start', String(trimStart.value))
  sessionStorage.setItem('video_editor_trim_end', String(trimEnd.value))
  sessionStorage.setItem('video_editor_segments', JSON.stringify(segments.map(s => ({ startTime: s.startTime, endTime: s.endTime, speed: s.speed }))))
  if (music.value) {
    sessionStorage.setItem('video_editor_bgm_start_offset', String(bgmStartOffset.value))
    sessionStorage.setItem('video_editor_bgm_volume', String(bgmVolume.value))
    sessionStorage.setItem('video_editor_music_id', String(music.value.id))
  } else {
    sessionStorage.setItem('video_editor_music_id', '')
    sessionStorage.setItem('video_editor_bgm_start_offset', '0')
    sessionStorage.setItem('video_editor_bgm_volume', '0.7')
  }
  router.back()
}
</script>

<style scoped lang="less">
.video-editor {
  position: fixed; inset: 0; background: #000; color: #fff;
  display: flex; flex-direction: column; z-index: 100;
}

// ====== 顶栏 ======
.editor-top-bar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12rem 16rem 8rem; font-size: 26rem; flex-shrink: 0; z-index: 10;
  .editor-title { font-size: 17rem; font-weight: 600; }
  .done-btn { font-size: 15rem; color: #ffcc00; font-weight: 600; cursor: pointer; }
}

// ====== 预览 ======
.player-area {
  flex: 1; position: relative; background: #111; min-height: 0;
  .player-video { width: 100%; height: 100%; object-fit: contain; }
  .play-overlay { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; background: rgba(0,0,0,0.2); svg { font-size: 60rem; } }
  .trim-shades { position: absolute; inset: 0; display: flex; pointer-events: none; .shade { height: 100%; background: rgba(0,0,0,0.5); } }
}

// ====== 底部面板 ======
.editor-bottom { flex-shrink: 0; background: #121212; }

// ====== 工具栏 ======
.toolbar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 8rem 16rem; gap: 8rem;
  .tool-group { display: flex; gap: 4rem; align-items: center; }
  .tool-btn {
    display: flex; align-items: center; gap: 3rem;
    background: rgba(255,255,255,0.08); border: none; color: #ccc;
    padding: 6rem 10rem; border-radius: 4rem; font-size: 12rem; cursor: pointer;
    &:disabled { opacity: 0.3; cursor: default; }
    &:not(:disabled):active { background: rgba(255,255,255,0.15); }
    svg { font-size: 16rem; }
  }
  .speed-select {
    background: rgba(255,255,255,0.08); border: none; color: #ffcc00;
    padding: 6rem 8rem; border-radius: 4rem; font-size: 13rem; font-weight: 600;
    cursor: pointer; outline: none;
  }
}

// ====== 时间轴面板 ======
.timeline-panel {
  display: flex; padding: 0 12rem; gap: 6rem;
}

.track-labels {
  display: flex; flex-direction: column; gap: 2rem; flex-shrink: 0; padding-top: 2rem;
  .label {
    font-size: 11rem; color: #888; height: 44rem; display: flex; align-items: center; width: 32rem;
    &.video-label { height: 44rem; }
    &.audio-label { height: 36rem; }
    &.music-label { height: 36rem; }
  }
}

.tracks-area {
  flex: 1; display: flex; flex-direction: column; gap: 2rem; position: relative;
}

// ====== 轨道 ======
.track {
  position: relative; border-radius: 3rem; overflow: hidden;
}

.video-track {
  height: 44rem; background: #1a1a1a; cursor: pointer;
  .frame-strip { display: flex; height: 100%; position: absolute; inset: 0; z-index: 1; }
  .frame-thumb { flex: 1; height: 100%; background-size: cover; background-position: center; }
  .segment-boundary {
    position: absolute; top: 0; bottom: 0; width: 2rem; background: #ff4444;
    z-index: 4; pointer-events: none; box-shadow: 0 0 4rem rgba(255,68,68,0.6);
  }
  .active-segment {
    position: absolute; top: 0; bottom: 0; border: 2rem solid #ffcc00;
    z-index: 3; pointer-events: none; border-radius: 2rem;
  }
  .trim-handle {
    position: absolute; top: 0; bottom: 0; width: 22rem; z-index: 5;
    cursor: ew-resize; display: flex; align-items: center; justify-content: center;
    touch-action: none; margin-left: -11rem;
    .handle-bar { width: 3rem; height: 26rem; background: #fff; border-radius: 2rem; box-shadow: 0 0 6rem rgba(0,0,0,0.6); }
  }
  .playhead { position: absolute; top: -4rem; bottom: -4rem; width: 2rem; background: #ffcc00; z-index: 8; pointer-events: none; margin-left: -1rem; }
}

.audio-track {
  height: 36rem; background: #1a1a1a; position: relative;
  .wave-canvas { position: absolute; inset: 0; width: 100%; height: 100%; pointer-events: none; }
  .playhead { position: absolute; top: -4rem; bottom: -4rem; width: 2rem; background: #ffcc00; z-index: 8; pointer-events: none; margin-left: -1rem; }
}

.music-track {
  height: 36rem; background: #1a1a1a; position: relative;
  .wave-canvas { position: absolute; inset: 0; width: 100%; height: 100%; pointer-events: none; }
  .music-indicator {
    position: absolute; top: 4rem; bottom: 4rem; z-index: 2;
    background: linear-gradient(90deg, rgba(255,107,107,0.5), rgba(255,204,0,0.5));
    border-radius: 3rem; display: flex; align-items: center; gap: 3rem; padding: 0 6rem;
    overflow: hidden; pointer-events: none;
    svg { font-size: 12rem; flex-shrink: 0; }
    span { font-size: 10rem; color: #fff; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  }
  .no-music-tag { font-size: 11rem; color: #555; position: relative; z-index: 2; margin-left: 8rem; line-height: 36rem; }
  .playhead { position: absolute; top: -4rem; bottom: -4rem; width: 2rem; background: #ffcc00; z-index: 8; pointer-events: none; margin-left: -1rem; }
}

// ====== 控制栏 ======
.control-bar {
  display: flex; align-items: center; gap: 10rem; padding: 10rem 16rem 18rem;
  .play-btn { font-size: 30rem; cursor: pointer; flex-shrink: 0; }
  .time-info { font-size: 13rem; color: #aaa; font-variant-numeric: tabular-nums; flex-shrink: 0; }
  .seg-info { display: flex; align-items: center; gap: 6rem; font-size: 12rem; color: #888; flex: 1; .seg-speed { color: #ffcc00; font-weight: 600; } }
  .vol-wrap { display: flex; align-items: center; gap: 4rem; flex-shrink: 0; svg { font-size: 16rem; color: #888; } }
  .vol-slider { width: 56rem; -webkit-appearance: none; appearance: none; height: 3rem; background: rgba(255,255,255,0.2); border-radius: 2rem; outline: none; &::-webkit-slider-thumb { -webkit-appearance: none; appearance: none; width: 14rem; height: 14rem; border-radius: 50%; background: #fff; cursor: pointer; } }
}
</style>
