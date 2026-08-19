<template>
  <div class="live-watch">
    <!-- SRS WHEP video; HLS/HTTP-FLV attaches to this element on fallback. -->
    <video
      ref="videoEl"
      class="live-video"
      autoplay
      muted
      playsinline
      :controls="false"
      @click="handleCanvasClick"
    ></video>

    <!-- Loading overlay -->
    <div v-if="loading" class="loading-overlay">
      <div class="loading-spinner"></div>
      <span class="loading-text">连接直播中...</span>
      <span v-if="streamStats" class="loading-detail">
        {{ streamStats.codec }} | {{ Math.round(streamStats.bitrateKbps) }}kbps
      </span>
    </div>
    <div v-else-if="playbackError" class="playback-error">
      <span>{{ playbackError }}</span>
      <button type="button" @click="retryPlayback">重试</button>
    </div>

    <!-- Quality indicator -->
    <div class="quality-indicator" v-if="!loading && streamStats">
      <span class="qi-dot" :class="qualityDotClass"></span>
      <span>{{ streamStats.fps }}fps</span>
      <span class="qi-divider">|</span>
      <span>{{ qualityLabel }}</span>
      <span class="qi-divider">|</span>
      <span>{{ Math.round(streamStats.latencyMs) }}ms</span>
    </div>

    <div class="top-gradient"></div>

    <div class="top-left">
      <div class="avatar-wrap" @click.stop="goHostProfile">
        <img
          class="avatar"
          :src="
            _checkImgUrl(host?.avatar_168x168?.url_list?.[0]) ||
            _checkImgUrl(host?.avatar) ||
            defaultAvatarPng
          "
        />
        <span class="live-dot"></span>
      </div>
      <div class="host-detail">
        <span class="host-name">{{ host?.nickname || '主播' }}</span>
        <span class="like-info">❤️ {{ likeCount }} 点赞</span>
      </div>
      <div class="follow-btn" v-if="host?.uid" @click.stop="toggleFollow">
        <span>{{ isFollowing ? '已关注' : '+ 关注' }}</span>
      </div>
    </div>

    <div class="top-right">
      <div class="viewer-info">
        <span class="viewer-count">{{ viewerCount }}</span>
        <span class="viewer-label">观看</span>
      </div>
      <div class="btn-close" @click="$router.back()">
        <svg
          width="16"
          height="16"
          viewBox="0 0 24 24"
          fill="none"
          stroke="#fff"
          stroke-width="2.5"
        >
          <line x1="18" y1="6" x2="6" y2="18" />
          <line x1="6" y1="6" x2="18" y2="18" />
        </svg>
      </div>
    </div>

    <button
      class="audio-toggle"
      type="button"
      :aria-label="audioMuted ? '开启声音' : '静音'"
      :title="audioMuted ? '开启声音' : '静音'"
      @click.stop="toggleAudio"
    >
      <svg
        v-if="audioMuted"
        width="18"
        height="18"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
      >
        <path d="M11 5 6 9H3v6h3l5 4V5Z" />
        <path d="m23 9-6 6m0-6 6 6" />
      </svg>
      <svg
        v-else
        width="18"
        height="18"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
      >
        <path d="M11 5 6 9H3v6h3l5 4V5Z" />
        <path d="M15.5 8.5a5 5 0 0 1 0 7m3-10a9 9 0 0 1 0 13" />
      </svg>
    </button>

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
        <span class="placeholder">发个弹幕...</span>
      </div>
      <div class="bottom-actions">
        <div class="action-btn" @click="sendLike">
          <svg
            width="22"
            height="22"
            viewBox="0 0 24 24"
            fill="none"
            stroke="#fff"
            stroke-width="1.8"
          >
            <path
              d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"
            />
          </svg>
        </div>
      </div>
    </div>

    <div v-if="showChatInput" class="chat-full-input">
      <input
        ref="chatInputEl"
        v-model="chatText"
        class="text-input"
        placeholder="发个弹幕吧..."
        @keyup.enter="sendChat"
        @blur="showChatInput = false"
      />
      <button class="send-btn" @click="sendChat">发送</button>
    </div>

    <transition-group name="float-heart" tag="div" class="heart-float-area">
      <span
        v-for="h in floatingHearts"
        :key="h.id"
        class="float-heart"
        :style="{ left: h.x + 'px' }"
        >❤️</span
      >
    </transition-group>

    <div class="bottom-gradient"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick, computed } from 'vue'
import { useRoute } from 'vue-router'
import { getLiveDetail, joinLive, leaveLive, likeLive } from '@/api/live'
import { toggleFollowUser } from '@/api/user'
import { useBaseStore } from '@/store/pinia'
import { _checkImgUrl } from '@/utils'
import {
  SrsWhepPlayer,
  normalizeSrsUrls,
  playSrsFallback,
  type SrsMediaUrls
} from '@/utils/streaming/srs_rtc'
import defaultAvatarPng from '@/assets/img/icon/people-gray.png'

const route = useRoute()
const store = useBaseStore()
const idNum = Number(route.params.id)
const roomId = ref<number>(isNaN(idNum) ? 0 : idNum)
const host = ref<any>(null)
const viewerCount = ref(0)
const likeCount = ref(0)
const chatMessages = ref<{ nickname: string; text: string; _key: number }[]>([])
const chatText = ref('')
const showChatInput = ref(false)
const floatingHearts = ref<{ id: number; x: number }[]>([])
const isFollowing = ref(false)
const loading = ref(true)
const streamStats = ref<any>(null)
const audioMuted = ref(true)
const playbackError = ref('')

const videoEl = ref<HTMLVideoElement>()
const chatInputEl = ref<HTMLInputElement>()
let player: SrsWhepPlayer | null = null
let fallbackStop: (() => void) | null = null
let mediaUrls: SrsMediaUrls = {}
let liveWs: WebSocket | null = null
let reconnectAttempts = 0
let wsReconnectTimer: ReturnType<typeof setTimeout> | null = null
let wsStopped = false
let msgKey = 0
let heartId = 0
let statsInterval: ReturnType<typeof setInterval> | null = null
let mounted = false
let playbackGeneration = 0
let mediaRecoveryTimer: ReturnType<typeof setTimeout> | null = null
let mediaRecoveryAttempts = 0
const presenceSessionId =
  typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `live-${Date.now()}-${Math.random().toString(36).slice(2)}`
let presenceTimer: ReturnType<typeof setInterval> | null = null

const visibleComments = computed(() => chatMessages.value.slice(-8))

const qualityDotClass = computed(() => {
  if (!streamStats.value) return 'yellow'
  const fps = streamStats.value.fps
  if (fps >= 55) return 'green'
  if (fps >= 30) return 'yellow'
  return 'red'
})

const qualityLabel = computed(() => {
  if (!streamStats.value) return ''
  const codec = streamStats.value.codec || ''
  if (codec.startsWith('av01')) return 'AV1'
  if (codec.startsWith('hev1')) return 'H.265'
  if (codec.startsWith('avc1')) return 'H.264'
  if (codec.toLowerCase().includes('h.264') || codec.toLowerCase().includes('h264')) return 'H.264'
  return codec.substring(0, 4)
})

onMounted(async () => {
  mounted = true
  const generation = ++playbackGeneration
  try {
    const res: any = await getLiveDetail(roomId.value)
    if (!mounted || generation !== playbackGeneration) return
    if (res.success) {
      host.value = res.data.host
      viewerCount.value = res.data.viewerCount || 0
      likeCount.value = res.data.likeCount || 0
      mediaUrls = normalizeSrsUrls((res.data.media || {}) as SrsMediaUrls)
    }
  } catch {
    /* detail request is best effort */
  }

  try {
    const joined: any = await joinLive(roomId.value, presenceSessionId)
    if (!mounted || generation !== playbackGeneration) return
    // The join response is authoritative when the detail request raced the
    // broadcaster's transition to LIVE or returned before media was ready.
    if (joined?.success && joined.data?.media) {
      mediaUrls = normalizeSrsUrls(joined.data.media as SrsMediaUrls)
    }
    if (joined?.success && Number.isFinite(joined.data?.viewerCount)) {
      viewerCount.value = joined.data.viewerCount
    }
  } catch {
    /* join failure is surfaced by the playback state */
  }

  await initPlayer(generation)
  if (!mounted || generation !== playbackGeneration) return

  connectWs()
  startStatsMonitor()
})

onBeforeUnmount(() => {
  mounted = false
  playbackGeneration++
  wsStopped = true
  stopPresenceHeartbeat()
  if (wsReconnectTimer) {
    clearTimeout(wsReconnectTimer)
    wsReconnectTimer = null
  }
  if (mediaRecoveryTimer) {
    clearTimeout(mediaRecoveryTimer)
    mediaRecoveryTimer = null
  }
  if (liveWs) {
    liveWs.onclose = null
    try {
      liveWs.close()
    } catch {
      /* socket may already be closed */
    }
  }
  if (statsInterval) {
    clearInterval(statsInterval)
  }
  player?.stop()
  player = null
  fallbackStop?.()
  fallbackStop = null
  leaveLive(roomId.value, presenceSessionId).catch(() => {})
})

async function initPlayer(generation = ++playbackGeneration) {
  if (!videoEl.value || !mounted || generation !== playbackGeneration) return
  loading.value = true
  playbackError.value = ''
  fallbackStop?.()
  fallbackStop = null
  player?.stop()
  player = null
  videoEl.value.muted = true
  let whepPlayer: SrsWhepPlayer | null = null
  try {
    if (!mediaUrls.whepUrl) throw new Error('WHEP 地址不可用')
    whepPlayer = new SrsWhepPlayer(mediaUrls.whepUrl, {
      onConnectionStateChange: (state) => {
        if (state === 'disconnected' || state === 'failed') schedulePlaybackRecovery()
      }
    })
    player = whepPlayer
    await whepPlayer.start(videoEl.value)
    if (!mounted || generation !== playbackGeneration || player !== whepPlayer) {
      whepPlayer.stop()
      if (player === whepPlayer) player = null
      return
    }
    mediaRecoveryAttempts = 0
    loading.value = false
  } catch (e) {
    console.warn('[live] WHEP failed, trying HLS/HTTP-FLV fallback', e)
    if (whepPlayer && player === whepPlayer) {
      whepPlayer.stop()
      player = null
    }
    if (!mounted || generation !== playbackGeneration) return
    try {
      fallbackStop = await playSrsFallback(videoEl.value, mediaUrls)
      if (!mounted || generation !== playbackGeneration) {
        fallbackStop?.()
        fallbackStop = null
        return
      }
      loading.value = false
    } catch (fallbackError) {
      console.error('[live] playback fallback failed', fallbackError)
      loading.value = false
      playbackError.value = '直播暂时无法播放'
    }
  }
}

function schedulePlaybackRecovery() {
  if (!mounted || wsStopped || mediaRecoveryTimer || mediaRecoveryAttempts >= 5) return
  const attempt = mediaRecoveryAttempts++
  const delay = Math.min(30_000, 1_000 * 2 ** attempt)
  mediaRecoveryTimer = setTimeout(() => {
    mediaRecoveryTimer = null
    if (!mounted || wsStopped) return
    void initPlayer(++playbackGeneration)
  }, delay)
}

async function retryPlayback() {
  if (mediaRecoveryTimer) {
    clearTimeout(mediaRecoveryTimer)
    mediaRecoveryTimer = null
  }
  mediaRecoveryAttempts = 0
  await initPlayer(++playbackGeneration)
}

function startStatsMonitor() {
  statsInterval = setInterval(() => {
    if (player) {
      player
        .getStats()
        .then((stats) => {
          streamStats.value = { ...stats, codec: 'H.264', latencyMs: stats.rttMs }
        })
        .catch(() => {
          /* stats are best effort */
        })
    }
  }, 2000)
}

function connectWs() {
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
    `${protocol}//${location.host}/ws/live/${roomId.value}?role=viewer&token=${token}&sessionId=${encodeURIComponent(presenceSessionId)}`
  )

  liveWs.onopen = () => {
    reconnectAttempts = 0
    startPresenceHeartbeat()
  }
  liveWs.onmessage = (e) => {
    try {
      const msg = JSON.parse(e.data)
      switch (msg.type) {
        case 'chat':
          chatMessages.value.push({ nickname: msg.nickname, text: msg.text, _key: ++msgKey })
          if (chatMessages.value.length > 100) chatMessages.value.shift()
          break
        case 'like':
          likeCount.value += msg.count || 1
          break
        case 'viewer_count':
          viewerCount.value = msg.count
          break
        case 'end':
          if (mediaRecoveryTimer) {
            clearTimeout(mediaRecoveryTimer)
            mediaRecoveryTimer = null
          }
          mediaRecoveryAttempts = 5
          player?.stop()
          player = null
          fallbackStop?.()
          fallbackStop = null
          playbackError.value = '直播已结束'
          break
      }
    } catch {
      /* ignore malformed control event */
    }
  }
  liveWs.onerror = () => {
    /* close handler owns reconnect */
  }
  liveWs.onclose = () => {
    stopPresenceHeartbeat()
    if (wsStopped) return
    if (reconnectAttempts < 10) {
      reconnectAttempts++
      wsReconnectTimer = setTimeout(connectWs, 3000)
    }
  }
}

function startPresenceHeartbeat() {
  stopPresenceHeartbeat()
  presenceTimer = setInterval(() => {
    if (liveWs?.readyState === WebSocket.OPEN) liveWs.send(JSON.stringify({ type: 'presence' }))
  }, 15_000)
}

function stopPresenceHeartbeat() {
  if (presenceTimer) {
    clearInterval(presenceTimer)
    presenceTimer = null
  }
}

function focusInput() {
  showChatInput.value = true
  nextTick(() => chatInputEl.value?.focus())
}

function sendChat() {
  if (!chatText.value.trim() || !liveWs || liveWs.readyState !== WebSocket.OPEN) return
  liveWs.send(
    JSON.stringify({
      type: 'chat',
      nickname: store.userinfo.nickname || '观众',
      text: chatText.value.trim()
    })
  )
  chatText.value = ''
  showChatInput.value = false
}

function sendLike() {
  const wsOpen = !!liveWs && liveWs.readyState === WebSocket.OPEN
  if (wsOpen) liveWs!.send(JSON.stringify({ type: 'like', count: 1 }))
  likeLive(roomId.value).catch(() => {
    /* persistence is best effort */
  })
  // The control WS echoes likes to every viewer, including the sender. Avoid
  // incrementing twice locally when that echo is available.
  if (!wsOpen) likeCount.value++
  const h = { id: ++heartId, x: Math.random() * 150 + 20 }
  floatingHearts.value.push(h)
  setTimeout(() => {
    floatingHearts.value = floatingHearts.value.filter((v) => v.id !== h.id)
  }, 1000)
}

function toggleAudio() {
  if (!videoEl.value) return
  audioMuted.value = !audioMuted.value
  videoEl.value.muted = audioMuted.value
  if (!audioMuted.value)
    videoEl.value.play().catch(() => {
      audioMuted.value = true
      videoEl.value!.muted = true
    })
}

function handleCanvasClick() {
  sendLike()
}
function goHostProfile() {}
async function toggleFollow() {
  if (!host.value?.uid) return
  try {
    const res = await toggleFollowUser(host.value.uid)
    if (res.success) {
      isFollowing.value = res.data?.isAttention ?? !isFollowing.value
    }
  } catch {
    /* follow state is best effort */
  }
}
</script>

<style scoped lang="less">
.live-watch {
  width: 100%;
  height: 100vh;
  background: #000;
  position: relative;
  overflow: hidden;
}
.live-video {
  width: 100%;
  height: 100%;
  object-fit: contain;
  background: #111;
}
.audio-toggle {
  position: absolute;
  top: max(64rem, calc(env(safe-area-inset-top) + 48rem));
  right: 14rem;
  z-index: 20;
  width: 34rem;
  height: 34rem;
  border: 1rem solid rgba(255, 255, 255, 0.14);
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.36);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.playback-error {
  position: absolute;
  inset: 0;
  z-index: 100;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14rem;
  color: rgba(255, 255, 255, 0.8);
  background: rgba(0, 0, 0, 0.65);
}
.playback-error button {
  min-width: 72rem;
  padding: 8rem 16rem;
  border: 0;
  border-radius: 18rem;
  background: #fe2c55;
  color: #fff;
  cursor: pointer;
}

.loading-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12rem;
  z-index: 100;
  background: rgba(0, 0, 0, 0.6);
}
.loading-spinner {
  width: 40rem;
  height: 40rem;
  border: 3rem solid rgba(255, 255, 255, 0.1);
  border-top-color: #fe2c55;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
.loading-text {
  color: rgba(255, 255, 255, 0.8);
  font-size: 14rem;
}
.loading-detail {
  color: rgba(255, 255, 255, 0.4);
  font-size: 11rem;
}

.quality-indicator {
  position: absolute;
  top: max(14rem, env(safe-area-inset-top));
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 4rem;
  padding: 4rem 10rem;
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(8px);
  border-radius: 12rem;
  z-index: 20;
  color: rgba(255, 255, 255, 0.7);
  font-size: 10rem;
}
.qi-dot {
  width: 6rem;
  height: 6rem;
  border-radius: 50%;
}
.qi-dot.green {
  background: #4caf50;
  box-shadow: 0 0 6rem rgba(76, 175, 80, 0.5);
}
.qi-dot.yellow {
  background: #ffc107;
  box-shadow: 0 0 6rem rgba(255, 193, 7, 0.5);
}
.qi-dot.red {
  background: #f44336;
  box-shadow: 0 0 6rem rgba(244, 67, 54, 0.5);
}
.qi-divider {
  color: rgba(255, 255, 255, 0.2);
}

.top-gradient {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 130rem;
  background: linear-gradient(to bottom, rgba(0, 0, 0, 0.5), transparent);
  z-index: 1;
  pointer-events: none;
}
.bottom-gradient {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 180rem;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.5), transparent);
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
  padding: 5rem 10rem 5rem 5rem;
  background: rgba(0, 0, 0, 0.3);
  backdrop-filter: blur(16px);
  border-radius: 26rem;
  border: 1rem solid rgba(255, 255, 255, 0.08);
  z-index: 10;
}
.avatar-wrap {
  position: relative;
  flex-shrink: 0;
  cursor: pointer;
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
  max-width: 80rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.like-info {
  color: rgba(255, 255, 255, 0.8);
  font-size: 10rem;
}

.follow-btn {
  flex-shrink: 0;
  padding: 6rem 14rem;
  border-radius: 20rem;
  background: linear-gradient(135deg, #fe2c55, #ff4470);
  color: #fff;
  font-size: 11rem;
  font-weight: 700;
  cursor: pointer;
  &:active {
    transform: scale(0.95);
    opacity: 0.85;
  }
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
.btn-close {
  width: 34rem;
  height: 34rem;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.3);
  backdrop-filter: blur(16px);
  border: 1rem solid rgba(255, 255, 255, 0.08);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  &:active {
    background: rgba(255, 255, 255, 0.15);
  }
}

.comment-bubbles {
  position: absolute;
  bottom: 90rem;
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
  max-width: 72%;
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
  gap: 10rem;
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
.bottom-actions {
  display: flex;
  gap: 8rem;
}
.action-btn {
  width: 42rem;
  height: 42rem;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(12px);
  border: 1rem solid rgba(255, 255, 255, 0.08);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  &:active {
    background: rgba(254, 44, 85, 0.25);
    transform: scale(0.9);
    svg {
      stroke: #fe2c55;
    }
  }
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

.heart-float-area {
  position: absolute;
  right: 16rem;
  bottom: 160rem;
  z-index: 11;
  pointer-events: none;
}
.float-heart {
  position: absolute;
  font-size: 28rem;
  animation: floatUp 1.2s ease-out forwards;
  filter: drop-shadow(0 0 6rem rgba(254, 44, 85, 0.4));
}
@keyframes floatUp {
  0% {
    opacity: 1;
    transform: translateY(0) scale(0.4);
  }
  30% {
    opacity: 1;
    transform: translateY(-30rem) scale(1.2);
  }
  100% {
    opacity: 0;
    transform: translateY(-100rem) scale(0.7);
  }
}
</style>
