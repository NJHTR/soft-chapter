<template>
  <div class="live-create">
    <!-- ========== 设置阶段 ========== -->
    <div v-if="step === 'setup'" class="setup-page">
      <BaseHeader mode="dark" backImg="back" @back="$router.back()">
        <template v-slot:center><span class="header-title">开直播</span></template>
      </BaseHeader>

      <div class="setup-body">
        <!-- 摄像头预览 + 装饰光圈 -->
        <div class="camera-preview">
          <div class="camera-ring">
            <div class="ring-inner"></div>
          </div>
          <video ref="previewVideo" autoplay muted playsinline class="preview-video"></video>
          <div class="camera-label">
            <span class="dot-live"></span>
            相机预览
          </div>
        </div>

        <div class="setup-form">
          <div class="input-wrap">
            <svg
              class="input-icon"
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="rgba(255,255,255,0.5)"
              stroke-width="2"
            >
              <path d="M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7" />
              <path d="M18.5 2.5a2.121 2.121 0 013 3L12 15l-4 1 1-4 9.5-9.5z" />
            </svg>
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
            <svg
              width="20"
              height="20"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <circle cx="12" cy="12" r="10" />
              <polygon points="10 8 16 12 10 16 10 8" fill="currentColor" stroke="none" />
            </svg>
          </button>
        </div>
      </div>
    </div>

    <!-- ========== 直播中 ========== -->
    <div v-if="step === 'live'" class="live-page">
      <!-- 全屏摄像头预览 -->
      <video ref="liveVideo" autoplay muted playsinline class="live-video"></video>

      <!-- 顶部渐变条 -->
      <div class="top-gradient"></div>

      <!-- 顶部左侧: 主播信息 + 点赞 -->
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

      <!-- 顶部右侧: 观看人数 + 关播开关 -->
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
        <div class="btn-end-live" @click="endBroadcast">
          <svg
            width="14"
            height="14"
            viewBox="0 0 24 24"
            fill="none"
            stroke="#fff"
            stroke-width="2.5"
          >
            <rect x="4" y="4" width="16" height="16" rx="3" />
          </svg>
          <span>关播</span>
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

      <!-- 底部输入栏 -->
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
          <span class="placeholder">说点什么...</span>
        </div>
      </div>

      <!-- 全屏聊天输入 -->
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

      <!-- 底部渐变 -->
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
import defaultAvatarPng from '@/assets/img/icon/people-gray.png'

// ============================================================
// 模块级变量：离开 LiveCreate 页面后推流不中断
// ============================================================
let globalStream: MediaStream | null = null
let globalRoomId = 0
let liveWs: WebSocket | null = null
let frameTimer: ReturnType<typeof setInterval> | null = null
let offscreenVideo: HTMLVideoElement | null = null
let offscreenCanvas: HTMLCanvasElement | null = null
let offscreenCtx: CanvasRenderingContext2D | null = null
let wsReconnectAttempts = 0
let wsReconnectTimer: ReturnType<typeof setTimeout> | null = null
let wsStopped = false
let globalMsgKey = 0

const router = useRouter()
const store = useBaseStore()
const step = ref<'setup' | 'live'>('setup')
const title = ref('')
const roomId = ref<number>(globalRoomId)
const viewerCount = ref(1)
const likeCount = ref(0)
const chatMessages = ref<{ nickname: string; text: string; _key: number }[]>([])
const chatText = ref('')
const showChatInput = ref(false)

const previewVideo = ref<HTMLVideoElement>()
const liveVideo = ref<HTMLVideoElement>()
const chatInputEl = ref<HTMLInputElement>()

const quickTags = ['唱歌聊天', '游戏实况', '户外直播', '美食制作', '日常唠嗑']
const visibleComments = computed(() => chatMessages.value.slice(-8))

let resumedSession = false

onMounted(async () => {
  // 已有进行中的直播 → 恢复 UI 状态
  if (globalStream && globalRoomId > 0 && liveWs && liveWs.readyState === WebSocket.OPEN) {
    resumedSession = true
    roomId.value = globalRoomId
    step.value = 'live'
    await nextTick()
    if (liveVideo.value) {
      liveVideo.value.srcObject = globalStream
    }
    return
  }

  // 新开播：获取摄像头
  try {
    const s = await navigator.mediaDevices.getUserMedia({
      video: { width: 480, height: 640, facingMode: 'user' },
      audio: true
    })
    globalStream = s
    if (previewVideo.value) previewVideo.value.srcObject = s
  } catch (e) {
    _notice('无法访问摄像头，请检查权限')
  }
})

// 离开页面时提示（直播中）
onBeforeRouteLeave((_to, _from, next) => {
  if (step.value === 'live' && !wsStopped) {
    const ok = window.confirm('离开页面不会中断直播，确定要返回吗？')
    if (!ok) return next(false)
  }
  next()
})

onBeforeUnmount(() => {
  // 不关播、不断 WebSocket，推流在后台继续
  // 只是把组件 ref 对应的 video 清掉
  if (liveVideo.value) {
    liveVideo.value.srcObject = null
  }
})

async function startBroadcast() {
  if (!title.value.trim()) return
  try {
    const res: any = await createLiveRoom({ title: title.value.trim() })
    if (!res.success) {
      _notice(res.msg || '创建失败')
      return
    }
    globalRoomId = res.data.id
    roomId.value = globalRoomId
    const startRes: any = await startLive(globalRoomId)
    if (!startRes.success) {
      _notice(startRes.msg || '开播失败')
      return
    }
    step.value = 'live'

    await nextTick()
    if (liveVideo.value && globalStream) {
      liveVideo.value.srcObject = globalStream
      liveVideo.value.play().catch(() => {})
    }
    setupOffscreenCapture()
    connectLiveWs()
    startStreaming()
  } catch (e) {
    _notice('开播失败')
  }
}

function setupOffscreenCapture() {
  if (!globalStream) return
  offscreenVideo = document.createElement('video')
  offscreenVideo.setAttribute('playsinline', '')
  offscreenVideo.muted = true
  offscreenVideo.srcObject = globalStream
  // 必须挂到 DOM 上，浏览器才会解码帧；用最小可见度
  offscreenVideo.style.cssText =
    'position:fixed;top:-10px;left:-10px;width:1px;height:1px;opacity:0.01;pointer-events:none;'
  document.body.appendChild(offscreenVideo)
  offscreenVideo.play().catch(() => {})

  offscreenCanvas = document.createElement('canvas')
  offscreenCanvas.width = 200
  offscreenCanvas.height = 266
  offscreenCtx = offscreenCanvas.getContext('2d')
}

function startStreaming() {
  if (!offscreenVideo || !offscreenCanvas || !offscreenCtx) return

  frameTimer = setInterval(() => {
    if (
      !offscreenCtx ||
      !offscreenCanvas ||
      !offscreenVideo ||
      !liveWs ||
      liveWs.readyState !== WebSocket.OPEN
    )
      return
    // 确保视频有画面数据再抓帧
    if (offscreenVideo.readyState < 2) return
    try {
      offscreenCtx.drawImage(offscreenVideo, 0, 0, 200, 266)
      const dataUrl = offscreenCanvas.toDataURL('image/jpeg', 0.4)
      liveWs.send(JSON.stringify({ type: 'frame', data: dataUrl }))
    } catch (_) {
      /* ignore */
    }
  }, 200)
}

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
    } catch (_) {
      /* ignore */
    }
  }

  const token = localStorage.getItem('token') || ''
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
    } catch (_) {
      /* ignore */
    }
  }
  liveWs.onerror = () => {}
  liveWs.onclose = () => {
    if (wsStopped) return
    if (wsReconnectAttempts < 10) {
      wsReconnectAttempts++
      wsReconnectTimer = setTimeout(connectLiveWs, 3000)
    }
  }
}

async function endBroadcast() {
  wsStopped = true
  stopStreaming()
  if (wsReconnectTimer) {
    clearTimeout(wsReconnectTimer)
    wsReconnectTimer = null
  }
  if (liveWs) {
    liveWs.onclose = null
    liveWs.close()
    liveWs = null
  }
  // 停止 offscreen video
  if (offscreenVideo) {
    offscreenVideo.pause()
    offscreenVideo.srcObject = null
    offscreenVideo.remove()
    offscreenVideo = null
  }
  offscreenCanvas = null
  offscreenCtx = null
  // 停止摄像头
  if (globalStream) {
    globalStream.getTracks().forEach((t) => t.stop())
    globalStream = null
  }
  if (globalRoomId > 0) {
    await endLive(globalRoomId)
  }
  globalRoomId = 0
  roomId.value = 0
  router.back()
}

function stopStreaming() {
  if (frameTimer) {
    clearInterval(frameTimer)
    frameTimer = null
  }
}

function focusInput() {
  showChatInput.value = true
  nextTick(() => chatInputEl.value?.focus())
}

function sendChat() {
  if (!chatText.value.trim() || !liveWs) return
  const nick = store.userinfo?.nickname || '主播'
  const text = chatText.value.trim()
  liveWs.send(JSON.stringify({ type: 'chat', nickname: nick, text }))
  chatMessages.value.push({ nickname: nick, text, _key: ++globalMsgKey })
  if (chatMessages.value.length > 100) chatMessages.value.shift()
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

// =============================================
// 设置阶段
// =============================================
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

// 摄像头预览
.camera-preview {
  width: 100%;
  max-width: 320rem;
  aspect-ratio: 3/4;
  border-radius: 20rem;
  overflow: visible;
  position: relative;
  margin-top: 4rem;

  .camera-ring {
    position: absolute;
    inset: -4rem;
    border-radius: 24rem;
    border: 2rem solid rgba(254, 44, 85, 0.2);
    animation: ringPulse 2s ease-in-out infinite;
    pointer-events: none;

    .ring-inner {
      position: absolute;
      inset: 3rem;
      border-radius: 20rem;
      border: 1.5rem solid rgba(254, 44, 85, 0.12);
      animation: ringPulse 2s ease-in-out 0.5s infinite;
    }
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
  -webkit-backdrop-filter: blur(8px);
  border-radius: 12rem;

  .dot-live {
    width: 7rem;
    height: 7rem;
    border-radius: 50%;
    background: #fe2c55;
    animation: dotBlink 1.2s ease-in-out infinite;
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
}

// 设置表单
.setup-form {
  width: 100%;
  max-width: 360rem;
  margin-top: 20rem;
}

.input-wrap {
  display: flex;
  align-items: center;
  background: rgba(255, 255, 255, 0.06);
  border: 1.5rem solid rgba(255, 255, 255, 0.08);
  border-radius: 14rem;
  padding: 0 14rem;
  transition: border-color 0.3s;

  &:focus-within {
    border-color: rgba(254, 44, 85, 0.4);
  }

  .input-icon {
    flex-shrink: 0;
    margin-right: 10rem;
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
    flex-shrink: 0;
    color: rgba(255, 255, 255, 0.25);
    font-size: 11rem;
  }
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

  .btn-text {
    letter-spacing: 2rem;
  }

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

// =============================================
// 直播中
// =============================================
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

// 顶部 / 底部渐变
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

// ============ 顶部左侧：主播信息 ============
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
  -webkit-backdrop-filter: blur(16px);
  border-radius: 26rem;
  border: 1rem solid rgba(255, 255, 255, 0.08);
  z-index: 10;

  .avatar-wrap {
    position: relative;
    flex-shrink: 0;

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
    max-width: 90rem;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .like-info {
    color: rgba(255, 255, 255, 0.8);
    font-size: 10rem;
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
    font-size: 12rem;
    font-weight: 600;
  }

  .viewer-count {
    font-size: 14rem;
    font-weight: 700;
  }

  .btn-end-live {
    display: flex;
    align-items: center;
    gap: 4rem;
    padding: 6rem 14rem;
    background: rgba(254, 44, 85, 0.25);
    backdrop-filter: blur(16px);
    -webkit-backdrop-filter: blur(16px);
    border: 1.5rem solid rgba(254, 44, 85, 0.4);
    border-radius: 26rem;
    color: #ff6b7a;
    font-size: 12rem;
    font-weight: 600;
    cursor: pointer;
    transition: all 0.25s;

    &:active {
      background: rgba(254, 44, 85, 0.45);
      transform: scale(0.95);
    }
  }
}

// ============ 评论气泡 ============
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

// ============ 全屏聊天输入 ============
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

// ============ 动画关键帧 ============
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
