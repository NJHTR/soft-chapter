<template>
  <div class="live-create">
    <div v-if="step === 'setup'" class="setup-page">
      <BaseHeader mode="dark" backImg="back" @back="$router.back()">
        <template v-slot:center><span class="header-title">开直播</span></template>
      </BaseHeader>

      <div class="setup-body">
        <div class="camera-preview">
          <div class="camera-ring">
            <div class="ring-inner"></div>
          </div>
          <video ref="previewVideo" autoplay muted playsinline class="preview-video"></video>
          <div class="camera-label">
            <span class="dot-live"></span>
            {{ previewQuality }}
          </div>
        </div>

        <!-- Streaming quality selector -->
        <div class="quality-selector">
          <div class="quality-label">画质</div>
          <div class="quality-options">
            <div
              v-for="q in qualityOptions"
              :key="q.value"
              class="quality-option"
              :class="{ active: selectedQuality === q.value }"
              @click="selectedQuality = q.value"
            >
              <span class="q-name">{{ q.label }}</span>
              <span class="q-desc">{{ q.desc }}</span>
            </div>
          </div>
        </div>

        <!-- Beauty filter toggle -->
        <div class="beauty-toggle">
          <div class="toggle-label">
            <span>美颜滤镜</span>
            <span class="toggle-hint">原生引擎启用时生效</span>
          </div>
          <label class="switch">
            <input type="checkbox" v-model="enableBeauty" />
            <span class="slider"></span>
          </label>
        </div>

        <div class="setup-form">
          <div class="input-wrap">
            <input
              v-model="title"
              class="title-input"
              placeholder="给你的直播取个吸引人的标题..."
              maxlength="50"
            />
            <span class="char-count">{{ title.length }}/50</span>
          </div>

          <div class="setup-tags">
            <span class="tag" v-for="t in quickTags" :key="t" @click="title = t">{{ t }}</span>
          </div>

          <button class="btn-start" :disabled="!title.trim()" @click="startBroadcast">
            <span class="btn-text">开始直播</span>
          </button>
        </div>
      </div>
    </div>

    <div v-if="step === 'live'" class="live-page">
      <video ref="liveVideo" autoplay muted playsinline class="live-video"></video>

      <div class="top-gradient"></div>

      <div class="top-left">
        <div class="avatar-wrap">
          <img
            class="avatar"
            :src="_checkImgUrl(store.userinfo?.avatar_168x168?.url_list?.[0]) || defaultAvatarPng"
          />
          <span class="live-dot"></span>
        </div>
        <div class="host-detail">
          <span class="host-name">{{ store.userinfo?.nickname || '主播' }}</span>
          <span class="like-info">❤️ {{ likeCount }}本场点赞</span>
        </div>
      </div>

      <div class="top-right">
        <div class="viewer-info">
          <span class="viewer-count">{{ viewerCount }}</span>
          <span class="viewer-label">观看</span>
        </div>
        <!-- Quality indicator -->
        <div class="quality-badge" :title="`码率: ${(currentBitrate / 1000000).toFixed(1)}Mbps`">
          {{ qualityBadge }}
        </div>
        <div class="btn-end-live" @click="endBroadcast">
          <span>关播</span>
        </div>
      </div>

      <div class="comment-bubbles">
        <transition-group name="bubble">
          <div v-for="m in visibleComments" :key="m._key" class="comment-bubble">
            <span class="cb-user">{{ m.nickname }}</span>
            <span class="cb-text">{{ m.text }}</span>
          </div>
        </transition-group>
      </div>

      <div class="bottom-bar">
        <div class="chat-input-box" @click="focusInput">
          <span class="placeholder">说点什么...</span>
        </div>
        <!-- Stream stats -->
        <div class="stats-badge">
          <span class="stat-item">{{ streamFps }}fps</span>
          <span class="stat-divider">|</span>
          <span class="stat-item">{{ codecLabel }}</span>
        </div>
      </div>

      <div v-if="showChatInput" class="chat-full-input">
        <input
          ref="chatInputEl"
          v-model="chatText"
          class="text-input"
          placeholder="和观众说点什么..."
          @keyup.enter="sendChat"
          @blur="showChatInput = false"
        />
        <button class="send-btn" @click="sendChat">发送</button>
      </div>

      <div class="bottom-gradient"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick, computed } from 'vue'
import { useRouter, onBeforeRouteLeave } from 'vue-router'
import { _notice, _checkImgUrl } from '@/utils'
import { createLiveRoom, startLive, endLive } from '@/api/live'
import { useBaseStore } from '@/store/pinia'
import BaseHeader from '@/components/BaseHeader.vue'
import { SrsWhipPublisher, normalizeSrsUrls, type SrsMediaUrls } from '@/utils/streaming/srs_rtc'
import defaultAvatarPng from '@/assets/img/icon/people-gray.png'

// ===== Quality Options =====
const qualityOptions = [
  {
    value: '1080p',
    label: '高清',
    desc: '4Mbps 1080P30 H.264',
    bitrate: 4000000,
    width: 1920,
    height: 1080,
    fps: 30
  },
  {
    value: '720p',
    label: '均衡',
    desc: '2.5Mbps 720P30 H.264',
    bitrate: 2500000,
    width: 1280,
    height: 720,
    fps: 30
  },
  {
    value: '480p',
    label: '流畅',
    desc: '1.2Mbps 480P30 H.264',
    bitrate: 1200000,
    width: 854,
    height: 480,
    fps: 30
  }
]

// ===== Refs =====
const router = useRouter()
const store = useBaseStore()
const step = ref<'setup' | 'live'>('setup')
const title = ref('')
const selectedQuality = ref('720p')
const enableBeauty = ref(true)
const roomId = ref(0)
const viewerCount = ref(1)
const likeCount = ref(0)
const chatMessages = ref<{ nickname: string; text: string; _key: number }[]>([])
const chatText = ref('')
const showChatInput = ref(false)
const currentBitrate = ref(2500000)
const streamFps = ref(0)
const codecLabel = ref('H.264')

const previewVideo = ref<HTMLVideoElement>()
const liveVideo = ref<HTMLVideoElement>()
const chatInputEl = ref<HTMLInputElement>()

const quickTags = ['唱歌聊天', '游戏实况', '户外直播', '美食制作', '日常唠嗑']
const visibleComments = computed(() => chatMessages.value.slice(-8))
const previewQuality = computed(
  () => `${qualityOptions.find((q) => q.value === selectedQuality.value)?.label || '均衡'} 预览`
)

const qualityBadge = computed(() => {
  const q = qualityOptions.find((q) => q.bitrate === currentBitrate.value)
  return q ? q.label : '高清'
})

// Module-level variables
let mediaStream: MediaStream | null = null
let publisher: SrsWhipPublisher | null = null
let globalRoomId = 0
let liveWs: WebSocket | null = null
let wsReconnectAttempts = 0
let wsReconnectTimer: ReturnType<typeof setTimeout> | null = null
let wsStopped = false
let globalMsgKey = 0
let statsInterval: ReturnType<typeof setInterval> | null = null
let mediaUrls: SrsMediaUrls = {}
let starting = false
let lifecycleGeneration = 0

// ===== Lifecycle =====
onMounted(async () => {
  if (globalRoomId > 0 && liveWs && liveWs.readyState === WebSocket.OPEN) {
    roomId.value = globalRoomId
    step.value = 'live'
    await nextTick()
    return
  }

  try {
    const s = await navigator.mediaDevices.getUserMedia({
      video: {
        width: { ideal: 1920 },
        height: { ideal: 1080 },
        frameRate: { ideal: 30 },
        facingMode: 'user'
      },
      audio: true
    })
    mediaStream = s
    if (previewVideo.value) previewVideo.value.srcObject = s
  } catch (e) {
    try {
      const s = await navigator.mediaDevices.getUserMedia({
        video: { width: 1280, height: 720, frameRate: 30, facingMode: 'user' },
        audio: true
      })
      mediaStream = s
      if (previewVideo.value) previewVideo.value.srcObject = s
    } catch (e2) {
      _notice('无法访问摄像头，请检查权限')
    }
  }
})

onBeforeRouteLeave((_to, _from, next) => {
  if (step.value === 'live' && !wsStopped) {
    const ok = window.confirm('离开页面将结束直播，确定要返回吗？')
    if (!ok) return next(false)
    void cleanupLiveSession().then(() => next())
    return
  }
  next()
})

onBeforeUnmount(() => {
  lifecycleGeneration++
  if (statsInterval) clearInterval(statsInterval)
  if (!wsStopped) void cleanupLiveSession()
})

// ===== Start Broadcast =====
async function startBroadcast() {
  if (!title.value.trim() || starting) return
  starting = true
  const generation = ++lifecycleGeneration
  const isCurrent = () => generation === lifecycleGeneration
  const quality = qualityOptions.find((q) => q.value === selectedQuality.value)!
  wsStopped = false

  try {
    const res: any = await createLiveRoom({ title: title.value.trim() })
    if (!isCurrent()) {
      if (res?.success && res.data?.id) await endLive(res.data.id).catch(() => {})
      return
    }
    if (!res.success) {
      _notice(res.msg || '创建失败')
      return
    }

    globalRoomId = res.data.id
    roomId.value = globalRoomId

    const startRes: any = await startLive(globalRoomId)
    if (!isCurrent()) {
      if (globalRoomId > 0) await endLive(globalRoomId).catch(() => {})
      return
    }
    if (!startRes.success) {
      _notice(startRes.msg || '开播失败')
      await endLive(globalRoomId).catch(() => {})
      globalRoomId = 0
      roomId.value = 0
      return
    }

    step.value = 'live'
    await nextTick()
    if (!isCurrent()) return

    const detail: any = await (await import('@/api/live')).getLiveDetail(globalRoomId)
    if (!isCurrent()) return
    mediaUrls = normalizeSrsUrls((detail?.data?.media || {}) as SrsMediaUrls)
    if (mediaUrls.ingestMode === 'native') {
      // A native ingest deployment is the sole producer for this room. Do not
      // open a second browser WHIP publisher against the same SRS stream.
      codecLabel.value = 'Native'
      connectLiveWs()
      startStatsReporting()
      return
    }
    if (!mediaStream || !mediaUrls.whipUrl) throw new Error('直播媒体服务未就绪')
    const track = mediaStream.getVideoTracks()[0]
    try {
      // Use ideal constraints for the selected tier. An older/mobile camera
      // may not expose the exact size; that must degrade to its native mode,
      // not abort an otherwise valid broadcast.
      await track?.applyConstraints({
        width: { ideal: quality.width },
        height: { ideal: quality.height },
        frameRate: { ideal: quality.fps, max: quality.fps }
      })
      if (!isCurrent()) return
    } catch (constraintError) {
      console.warn(
        '[live] selected camera tier unavailable; using native track settings',
        constraintError
      )
    }
    if (liveVideo.value) liveVideo.value.srcObject = mediaStream
    publisher = new SrsWhipPublisher(mediaStream, mediaUrls.whipUrl, {
      bitrate: quality.bitrate,
      frameRate: quality.fps
    })
    await publisher.start()
    if (!isCurrent()) return
    currentBitrate.value = quality.bitrate

    connectLiveWs()
    startStatsReporting()
  } catch (e) {
    console.error('[live] WHIP publish failed', e)
    step.value = 'setup'
    publisher?.stop()
    publisher = null
    if (globalRoomId > 0) await endLive(globalRoomId).catch(() => {})
    globalRoomId = 0
    _notice(e instanceof Error ? e.message : '开播失败')
  } finally {
    starting = false
  }
}

// ===== WebSocket Signaling =====
function connectLiveWs() {
  if (wsReconnectTimer) {
    clearTimeout(wsReconnectTimer)
    wsReconnectTimer = null
  }
  if (liveWs) {
    liveWs.onopen = null
    liveWs.onmessage = null
    liveWs.onerror = null
    liveWs.onclose = null
    try {
      liveWs.close()
    } catch {
      /* socket may already be closed */
    }
  }

  const token = encodeURIComponent(localStorage.getItem('token') || '')
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  liveWs = new WebSocket(
    `${protocol}//${location.host}/ws/live/${globalRoomId}?role=host&token=${token}`
  )
  liveWs.onopen = () => {
    wsReconnectAttempts = 0
  }
  liveWs.onmessage = (e) => {
    try {
      const msg = JSON.parse(e.data)
      if (msg.type === 'chat') {
        chatMessages.value.push({ nickname: msg.nickname, text: msg.text, _key: ++globalMsgKey })
        if (chatMessages.value.length > 100) chatMessages.value.shift()
      }
      if (msg.type === 'viewer_count') {
        viewerCount.value = msg.count + 1
      }
      if (msg.type === 'like') {
        likeCount.value += msg.count || 1
      }
    } catch {
      /* ignore malformed control event */
    }
  }
  liveWs.onerror = () => {
    /* close handler owns reconnect */
  }
  liveWs.onclose = () => {
    if (wsStopped) return
    if (wsReconnectAttempts < 10) {
      wsReconnectAttempts++
      wsReconnectTimer = setTimeout(connectLiveWs, 3000)
    } else {
      // Without a control-plane session we cannot observe host ownership or
      // deliver an explicit end event. Stop the producer after bounded retry.
      void cleanupLiveSession()
    }
  }
}

// ===== Stats =====
function startStatsReporting() {
  statsInterval = setInterval(() => {
    if (publisher) {
      publisher
        .getStats()
        .then((s) => {
          streamFps.value = Math.round(s.fps)
          if (s.bitrateKbps > 0) currentBitrate.value = Math.round(s.bitrateKbps * 1000)
        })
        .catch(() => {})
    }
  }, 2000)
}

// ===== End Broadcast =====
async function endBroadcast() {
  await cleanupLiveSession()
  router.back()
}

async function cleanupLiveSession() {
  lifecycleGeneration++
  wsStopped = true
  const roomToEnd = globalRoomId
  if (wsReconnectTimer) {
    clearTimeout(wsReconnectTimer)
    wsReconnectTimer = null
  }
  if (liveWs) {
    liveWs.onclose = null
    liveWs.close()
    liveWs = null
  }
  if (statsInterval) {
    clearInterval(statsInterval)
    statsInterval = null
  }

  publisher?.stop()
  publisher = null

  if (mediaStream) {
    mediaStream.getTracks().forEach((t) => t.stop())
    mediaStream = null
  }
  try {
    if (roomToEnd > 0) await endLive(roomToEnd)
  } catch (error) {
    // Local media is already stopped. Keep route cleanup deterministic even
    // when the control plane is temporarily unavailable; reconciliation can
    // close the provider room later instead of trapping navigation forever.
    console.warn('[live] failed to end room during cleanup', roomToEnd, error)
  } finally {
    globalRoomId = 0
    roomId.value = 0
    step.value = 'setup'
  }
}

function focusInput() {
  showChatInput.value = true
  nextTick(() => chatInputEl.value?.focus())
}

function sendChat() {
  if (!chatText.value.trim() || !liveWs || liveWs.readyState !== WebSocket.OPEN) return
  const nick = store.userinfo?.nickname || '主播'
  const text = chatText.value.trim()
  liveWs.send(JSON.stringify({ type: 'chat', nickname: nick, text }))
  chatText.value = ''
  showChatInput.value = false
}
</script>

<script lang="ts">
export default { name: 'LiveCreate' }
</script>

<style scoped lang="less">
.live-create {
  width: 100%;
  height: 100vh;
  background: #000;
}

.setup-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #0a0a0a 0%, #1a1a2e 60%, #16213e 100%);
}

.header-title {
  font-size: 17rem;
  font-weight: 700;
  color: #fff;
  letter-spacing: 1rem;
}

.setup-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16rem 24rem;
  overflow-y: auto;
}

.camera-preview {
  width: 100%;
  max-width: 320rem;
  aspect-ratio: 3/4;
  border-radius: 20rem;
  overflow: visible;
  position: relative;
  margin-top: 4rem;
}
.camera-ring {
  position: absolute;
  inset: -4rem;
  border-radius: 24rem;
  border: 2rem solid rgba(254, 44, 85, 0.2);
  animation: ringPulse 2s ease-in-out infinite;
  pointer-events: none;
}
.ring-inner {
  position: absolute;
  inset: 3rem;
  border-radius: 20rem;
  border: 1.5rem solid rgba(254, 44, 85, 0.12);
  animation: ringPulse 2s ease-in-out 0.5s infinite;
}
@keyframes ringPulse {
  0%,
  100% {
    opacity: 0.5;
    transform: scale(1);
  }
  50% {
    opacity: 1;
    transform: scale(1.015);
  }
}

.preview-video {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 20rem;
  border: 2rem solid rgba(255, 255, 255, 0.08);
  position: relative;
  z-index: 1;
}

.camera-label {
  position: absolute;
  bottom: 10rem;
  left: 50%;
  transform: translateX(-50%);
  z-index: 2;
  color: rgba(255, 255, 255, 0.7);
  font-size: 11rem;
  display: flex;
  align-items: center;
  gap: 6rem;
  padding: 4rem 12rem;
  background: rgba(0, 0, 0, 0.45);
  backdrop-filter: blur(8px);
  border-radius: 12rem;
  .dot-live {
    width: 7rem;
    height: 7rem;
    border-radius: 50%;
    background: #fe2c55;
    animation: dotBlink 1.2s ease-in-out infinite;
  }
}

.quality-selector {
  width: 100%;
  max-width: 360rem;
  margin-top: 16rem;
}
.quality-label {
  color: rgba(255, 255, 255, 0.6);
  font-size: 12rem;
  margin-bottom: 8rem;
}
.quality-options {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8rem;
}
.quality-option {
  padding: 10rem;
  border-radius: 12rem;
  background: rgba(255, 255, 255, 0.04);
  border: 1.5rem solid rgba(255, 255, 255, 0.08);
  cursor: pointer;
  transition: all 0.25s;
  display: flex;
  flex-direction: column;
  gap: 2rem;
  &.active {
    border-color: #fe2c55;
    background: rgba(254, 44, 85, 0.1);
  }
}
.q-name {
  color: #fff;
  font-size: 13rem;
  font-weight: 600;
}
.q-desc {
  color: rgba(255, 255, 255, 0.4);
  font-size: 10rem;
}

.beauty-toggle {
  width: 100%;
  max-width: 360rem;
  margin-top: 14rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.toggle-label {
  display: flex;
  flex-direction: column;
  gap: 2rem;
  span {
    color: rgba(255, 255, 255, 0.7);
    font-size: 13rem;
  }
  .toggle-hint {
    color: rgba(255, 255, 255, 0.3);
    font-size: 10rem;
  }
}
.switch {
  position: relative;
  width: 44rem;
  height: 24rem;
  input {
    opacity: 0;
    width: 0;
    height: 0;
  }
  .slider {
    position: absolute;
    cursor: pointer;
    inset: 0;
    background: rgba(255, 255, 255, 0.15);
    border-radius: 24rem;
    transition: 0.3s;
    &::before {
      content: '';
      position: absolute;
      width: 18rem;
      height: 18rem;
      left: 3rem;
      bottom: 3rem;
      background: #fff;
      border-radius: 50%;
      transition: 0.3s;
    }
  }
  input:checked + .slider {
    background: #fe2c55;
  }
  input:checked + .slider::before {
    transform: translateX(20rem);
  }
}

.setup-form {
  width: 100%;
  max-width: 360rem;
  margin-top: 16rem;
}
.input-wrap {
  display: flex;
  align-items: center;
  background: rgba(255, 255, 255, 0.06);
  border: 1.5rem solid rgba(255, 255, 255, 0.08);
  border-radius: 14rem;
  padding: 0 14rem;
  &:focus-within {
    border-color: rgba(254, 44, 85, 0.4);
  }
}
.title-input {
  flex: 1;
  padding: 14rem 0;
  border: none;
  font-size: 14rem;
  outline: none;
  background: transparent;
  color: #fff;
  &::placeholder {
    color: rgba(255, 255, 255, 0.3);
  }
}
.char-count {
  color: rgba(255, 255, 255, 0.25);
  font-size: 11rem;
}

.setup-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8rem;
  margin-top: 14rem;
  .tag {
    padding: 6rem 14rem;
    border-radius: 16rem;
    background: rgba(255, 255, 255, 0.06);
    border: 1rem solid rgba(255, 255, 255, 0.08);
    color: rgba(255, 255, 255, 0.7);
    font-size: 12rem;
    cursor: pointer;
    transition: all 0.25s;
    &:active {
      background: rgba(254, 44, 85, 0.2);
      border-color: rgba(254, 44, 85, 0.4);
      color: #fe2c55;
    }
  }
}

.btn-start {
  width: 100%;
  margin-top: 22rem;
  padding: 14rem;
  border: none;
  border-radius: 26rem;
  background: linear-gradient(135deg, #fe2c55 0%, #ff4470 100%);
  color: #fff;
  font-size: 16rem;
  font-weight: 700;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rem;
  transition: all 0.3s;
  box-shadow: 0 4rem 20rem rgba(254, 44, 85, 0.35);
  &:not(:disabled):active {
    transform: scale(0.97);
    box-shadow: 0 2rem 10rem rgba(254, 44, 85, 0.2);
  }
}
.btn-start:disabled {
  background: #3a3a3a;
  box-shadow: none;
  cursor: not-allowed;
  color: rgba(255, 255, 255, 0.35);
}

.live-page {
  width: 100%;
  height: 100vh;
  position: relative;
  overflow: hidden;
}
.live-video {
  width: 100%;
  height: 100%;
  object-fit: cover;
  background: #000;
}

.top-gradient {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 120rem;
  background: linear-gradient(to bottom, rgba(0, 0, 0, 0.55), transparent);
  z-index: 1;
  pointer-events: none;
}
.bottom-gradient {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 160rem;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.55), transparent);
  z-index: 1;
  pointer-events: none;
}

.top-left {
  position: absolute;
  top: max(14rem, env(safe-area-inset-top));
  left: 14rem;
  display: flex;
  align-items: center;
  gap: 8rem;
  padding: 5rem 14rem 5rem 5rem;
  background: rgba(0, 0, 0, 0.3);
  backdrop-filter: blur(16px);
  border-radius: 26rem;
  border: 1rem solid rgba(255, 255, 255, 0.08);
  z-index: 10;
}
.avatar-wrap {
  position: relative;
  flex-shrink: 0;
}
.avatar {
  width: 36rem;
  height: 36rem;
  border-radius: 50%;
  object-fit: cover;
  border: 2rem solid rgba(255, 255, 255, 0.25);
}
.host-detail {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
.host-name {
  color: #fff;
  font-size: 13rem;
  font-weight: 700;
}
.like-info {
  color: rgba(255, 255, 255, 0.8);
  font-size: 10rem;
}

.top-right {
  position: absolute;
  top: max(14rem, env(safe-area-inset-top));
  right: 14rem;
  display: flex;
  align-items: center;
  gap: 8rem;
  z-index: 10;
}
.viewer-info {
  display: flex;
  align-items: center;
  gap: 4rem;
  padding: 6rem 12rem;
  background: rgba(0, 0, 0, 0.3);
  backdrop-filter: blur(16px);
  border-radius: 26rem;
  border: 1rem solid rgba(255, 255, 255, 0.08);
  color: #fff;
  font-size: 12rem;
}
.viewer-count {
  font-weight: 700;
  font-size: 14rem;
}
.viewer-label {
  color: rgba(255, 255, 255, 0.6);
}

.quality-badge {
  padding: 6rem 10rem;
  background: rgba(0, 0, 0, 0.3);
  backdrop-filter: blur(16px);
  border-radius: 12rem;
  border: 1rem solid rgba(254, 44, 85, 0.3);
  color: #ff6b7a;
  font-size: 10rem;
  font-weight: 700;
}

.btn-end-live {
  display: flex;
  align-items: center;
  gap: 4rem;
  padding: 6rem 14rem;
  background: rgba(254, 44, 85, 0.25);
  backdrop-filter: blur(16px);
  border: 1.5rem solid rgba(254, 44, 85, 0.4);
  border-radius: 26rem;
  color: #ff6b7a;
  font-size: 12rem;
  font-weight: 600;
  cursor: pointer;
  &:active {
    background: rgba(254, 44, 85, 0.45);
    transform: scale(0.95);
  }
}

.comment-bubbles {
  position: absolute;
  bottom: 80rem;
  left: 14rem;
  right: 14rem;
  z-index: 10;
  pointer-events: none;
  display: flex;
  flex-direction: column;
  gap: 8rem;
}
.comment-bubble {
  align-self: flex-start;
  max-width: 75%;
  padding: 6rem 12rem;
  background: rgba(0, 0, 0, 0.3);
  backdrop-filter: blur(14px);
  border-radius: 14rem;
  font-size: 12rem;
}
.cb-user {
  color: #ffd700;
  font-weight: 700;
  margin-right: 5rem;
}
.cb-text {
  color: rgba(255, 255, 255, 0.9);
}
.bubble-enter-active {
  transition: all 0.35s ease;
}
.bubble-enter-from {
  opacity: 0;
  transform: translateY(12rem);
}

.bottom-bar {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 10rem 14rem;
  padding-bottom: max(10rem, env(safe-area-inset-bottom));
  display: flex;
  align-items: center;
  gap: 8rem;
  z-index: 10;
}
.chat-input-box {
  flex: 1;
  padding: 11rem 16rem;
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(12px);
  border-radius: 24rem;
  border: 1rem solid rgba(255, 255, 255, 0.08);
  cursor: pointer;
}
.placeholder {
  color: rgba(255, 255, 255, 0.4);
  font-size: 13rem;
}

.stats-badge {
  display: flex;
  align-items: center;
  gap: 4rem;
  padding: 6rem 10rem;
  background: rgba(0, 0, 0, 0.3);
  backdrop-filter: blur(8px);
  border-radius: 12rem;
}
.stat-item {
  color: rgba(255, 255, 255, 0.6);
  font-size: 10rem;
}
.stat-divider {
  color: rgba(255, 255, 255, 0.2);
  font-size: 10rem;
}

.chat-full-input {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  padding: 12rem 14rem;
  padding-bottom: max(12rem, env(safe-area-inset-bottom));
  gap: 10rem;
  z-index: 20;
}
.text-input {
  flex: 1;
  padding: 12rem 16rem;
  border-radius: 24rem;
  border: 1.5rem solid rgba(255, 255, 255, 0.1);
  font-size: 14rem;
  outline: none;
  background: rgba(30, 30, 30, 0.95);
  backdrop-filter: blur(16px);
  color: #fff;
  &::placeholder {
    color: rgba(255, 255, 255, 0.35);
  }
}
.send-btn {
  padding: 12rem 22rem;
  background: linear-gradient(135deg, #fe2c55, #ff4470);
  color: #fff;
  border: none;
  border-radius: 24rem;
  font-size: 14rem;
  font-weight: 600;
  cursor: pointer;
}

@keyframes dotBlink {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.3;
  }
}
</style>
