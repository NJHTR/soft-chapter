<template>
  <div class="live-watch">
    <!-- 全屏视频画布 -->
    <canvas ref="videoCanvas" class="video-canvas" @click="handleCanvasClick"></canvas>

    <!-- 顶部渐变条 -->
    <div class="top-gradient"></div>

    <!-- 顶部左侧: 主播信息 -->
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

    <!-- 顶部右侧: 观看人数 + 关闭 -->
    <div class="top-right">
      <div class="viewer-info">
        <svg
          width="14"
          height="14"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
        >
          <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
          <circle cx="12" cy="12" r="3" />
        </svg>
        <span class="viewer-count">{{ viewerCount }}</span>
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

    <!-- 底部评论浮动气泡 -->
    <div class="comment-bubbles">
      <transition-group name="bubble">
        <div v-for="m in visibleComments" :key="m._key" class="comment-bubble">
          <span class="cb-user">{{ m.nickname }}</span>
          <span class="cb-text">{{ m.text }}</span>
        </div>
      </transition-group>
    </div>

    <!-- 底部输入栏 + 操作按钮 -->
    <div class="bottom-bar">
      <div class="chat-input-box" @click="focusInput">
        <svg
          width="16"
          height="16"
          viewBox="0 0 24 24"
          fill="none"
          stroke="rgba(255,255,255,0.4)"
          stroke-width="2"
        >
          <path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z" />
        </svg>
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

    <!-- 聊天输入框 -->
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

    <!-- 飘心动画 -->
    <transition-group name="float-heart" tag="div" class="heart-float-area">
      <span
        v-for="h in floatingHearts"
        :key="h.id"
        class="float-heart"
        :style="{ left: h.x + 'px' }"
        >❤️</span
      >
    </transition-group>

    <!-- 底部渐变 -->
    <div class="bottom-gradient"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick, computed } from 'vue'
import { useRoute } from 'vue-router'
import { getLiveDetail, joinLive, leaveLive, likeLive } from '@/api/live'
import { useBaseStore } from '@/store/pinia'
import { _notice, _checkImgUrl } from '@/utils'
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
let heartId = 0
let msgKey = 0

const videoCanvas = ref<HTMLCanvasElement>()
const chatInputEl = ref<HTMLInputElement>()
let liveWs: WebSocket | null = null
let canvasCtx: CanvasRenderingContext2D | null = null
let reconnectAttempts = 0
let wsReconnectTimer: ReturnType<typeof setTimeout> | null = null
let wsStopped = false
let frameCount = 0

// 只显示最近 8 条评论
const visibleComments = computed(() => {
  return chatMessages.value.slice(-8)
})

onMounted(async () => {
  // 先初始化 canvas，再连 WebSocket，避免首帧到达时 canvasCtx 为 null
  if (videoCanvas.value) {
    canvasCtx = videoCanvas.value.getContext('2d')
    resizeCanvas()
    window.addEventListener('resize', resizeCanvas)
  }

  // 加载直播间信息
  try {
    const res: any = await getLiveDetail(roomId.value)
    if (res.success) {
      host.value = res.data.host
      viewerCount.value = res.data.viewerCount || 0
      likeCount.value = res.data.likeCount || 0
    }
  } catch (e) {
    /* ignore */
  }

  // 加入直播间
  try {
    await joinLive(roomId.value)
  } catch (e) {
    /* ignore */
  }

  connectWs()
})

onBeforeUnmount(() => {
  wsStopped = true
  if (wsReconnectTimer) {
    clearTimeout(wsReconnectTimer)
    wsReconnectTimer = null
  }
  if (liveWs) {
    liveWs.onclose = null
    try {
      liveWs.close()
    } catch (_) {
      /* ignore */
    }
  }
  leaveLive(roomId.value).catch(() => {})
  window.removeEventListener('resize', resizeCanvas)
})

function resizeCanvas() {
  if (!videoCanvas.value) return
  videoCanvas.value.width = window.innerWidth
  videoCanvas.value.height = window.innerHeight
}

function connectWs() {
  // 清理旧连接，避免级联重连
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
    } catch (_) {
      /* ignore */
    }
  }

  const token = localStorage.getItem('token') || ''
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  liveWs = new WebSocket(
    `${protocol}//${location.host}/ws/live/${roomId.value}?role=viewer&token=${token}`
  )

  liveWs.onopen = () => {
    reconnectAttempts = 0
    console.log('[Viewer] WS connected')
  }

  liveWs.onmessage = (e) => {
    try {
      const msg = JSON.parse(e.data)
      switch (msg.type) {
        case 'frame':
          renderFrame(msg.data)
          break
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
      }
    } catch (e) {
      /* ignore malformed messages */
    }
  }

  liveWs.onerror = (ev) => {
    console.error('[Viewer] WS error', ev)
  }

  liveWs.onclose = (ev) => {
    console.log('[Viewer] WS closed, code:', ev.code)
    if (wsStopped) return
    if (reconnectAttempts < 10) {
      reconnectAttempts++
      wsReconnectTimer = setTimeout(connectWs, 3000)
    }
  }
}

// 每帧用独立的 Image 对象，避免共用 img 导致的竞态黑屏
function renderFrame(dataUrl: string) {
  if (!canvasCtx || !videoCanvas.value) return
  const img = new Image()
  img.onload = () => {
    canvasCtx!.drawImage(img, 0, 0, videoCanvas.value!.width, videoCanvas.value!.height)
  }
  img.onerror = () => {
    // 解码失败，忽略这一帧
  }
  img.src = dataUrl
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
  if (liveWs && liveWs.readyState === WebSocket.OPEN) {
    liveWs.send(JSON.stringify({ type: 'like', count: 1 }))
  }
  likeLive(roomId.value).catch(() => {})
  likeCount.value++
  // 飘心动画
  const h = { id: ++heartId, x: Math.random() * 150 + 20 }
  floatingHearts.value.push(h)
  setTimeout(() => {
    floatingHearts.value = floatingHearts.value.filter((v) => v.id !== h.id)
  }, 1000)
}

function handleCanvasClick() {
  sendLike()
}

function goHostProfile() {
  if (host.value?.uid) {
    // 可跳转主播主页
  }
}

function toggleFollow() {
  isFollowing.value = !isFollowing.value
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

.video-canvas {
  width: 100%;
  height: 100%;
  object-fit: contain;
  background: #111;
}

// 顶部 / 底部渐变
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

// ============ 顶部左侧：主播信息 ============
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
  -webkit-backdrop-filter: blur(16px);
  border-radius: 26rem;
  border: 1rem solid rgba(255, 255, 255, 0.08);
  z-index: 10;

  .avatar-wrap {
    position: relative;
    flex-shrink: 0;
    cursor: pointer;

    .avatar {
      width: 36rem;
      height: 36rem;
      border-radius: 50%;
      object-fit: cover;
      border: 2rem solid rgba(255, 255, 255, 0.25);
    }

    .live-dot {
      position: absolute;
      bottom: -1rem;
      right: -1rem;
      width: 12rem;
      height: 12rem;
      border-radius: 50%;
      background: #fe2c55;
      border: 2rem solid rgba(0, 0, 0, 0.6);
      animation: dotBlink 1.2s ease-in-out infinite;
    }
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
    transition: all 0.25s;
    margin-left: 2rem;

    &:active {
      transform: scale(0.95);
      opacity: 0.85;
    }
  }
}

// ============ 顶部右侧 ============
.top-right {
  position: absolute;
  top: max(14rem, env(safe-area-inset-top));
  right: 14rem;
  display: flex;
  align-items: center;
  gap: 8rem;
  z-index: 10;

  .viewer-info {
    display: flex;
    align-items: center;
    gap: 5rem;
    padding: 6rem 14rem;
    background: rgba(0, 0, 0, 0.3);
    backdrop-filter: blur(16px);
    -webkit-backdrop-filter: blur(16px);
    border-radius: 26rem;
    border: 1rem solid rgba(255, 255, 255, 0.08);
    color: #fff;
    font-size: 13rem;
    font-weight: 600;
  }

  .viewer-count {
    font-size: 14rem;
    font-weight: 700;
  }

  .btn-close {
    width: 34rem;
    height: 34rem;
    border-radius: 50%;
    background: rgba(0, 0, 0, 0.3);
    backdrop-filter: blur(16px);
    -webkit-backdrop-filter: blur(16px);
    border: 1rem solid rgba(255, 255, 255, 0.08);
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    transition: all 0.2s;

    &:active {
      background: rgba(255, 255, 255, 0.15);
    }
  }
}

// ============ 评论气泡 ============
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
  -webkit-backdrop-filter: blur(14px);
  border-radius: 14rem;
  font-size: 12rem;
  line-height: 1.6;

  .cb-user {
    color: #ffd700;
    font-weight: 700;
    margin-right: 5rem;
  }

  .cb-text {
    color: rgba(255, 255, 255, 0.9);
    word-break: break-all;
  }
}

.bubble-enter-active {
  transition: all 0.35s ease;
}
.bubble-leave-active {
  transition: all 0.5s ease;
}
.bubble-enter-from {
  opacity: 0;
  transform: translateY(12rem);
}
.bubble-leave-to {
  opacity: 0;
  transform: translateY(-12rem);
}

// ============ 底部输入栏 ============
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
  -webkit-backdrop-filter: blur(12px);
  border-radius: 24rem;
  border: 1rem solid rgba(255, 255, 255, 0.08);
  display: flex;
  align-items: center;
  gap: 8rem;

  .placeholder {
    color: rgba(255, 255, 255, 0.4);
    font-size: 13rem;
  }
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
  -webkit-backdrop-filter: blur(12px);
  border: 1rem solid rgba(255, 255, 255, 0.08);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;

  &:active {
    background: rgba(254, 44, 85, 0.25);
    border-color: rgba(254, 44, 85, 0.4);
    transform: scale(0.9);

    svg {
      stroke: #fe2c55;
    }
  }
}

// ============ 聊天输入 ============
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

  .text-input {
    flex: 1;
    padding: 12rem 16rem;
    border-radius: 24rem;
    border: 1.5rem solid rgba(255, 255, 255, 0.1);
    font-size: 14rem;
    outline: none;
    background: rgba(30, 30, 30, 0.95);
    backdrop-filter: blur(16px);
    -webkit-backdrop-filter: blur(16px);
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
    white-space: nowrap;
    box-shadow: 0 2rem 12rem rgba(254, 44, 85, 0.3);
  }
}

// ============ 飘心动画 ============
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

@keyframes dotBlink {
  0%,
  100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.4;
    transform: scale(0.8);
  }
}
</style>
