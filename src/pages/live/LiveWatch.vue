<template>
  <div class="live-watch">
    <div class="video-area" ref="videoArea">
      <canvas ref="videoCanvas" class="video-canvas" @click="toggleLike"></canvas>
      <div class="top-bar">
        <img class="back-btn" src="@/assets/img/icon/common/back-w.png" @click="$router.back()" />
        <div class="host-info" v-if="host">
          <span class="host-name">{{ host.nickname }}</span>
          <span class="viewer-count">{{ viewerCount }} 人观看</span>
        </div>
      </div>
      <div class="live-tag">LIVE</div>
      <div class="like-btn" @click="sendLike">
        <span class="heart">❤️</span>
        <span class="like-count">{{ likeCount }}</span>
      </div>
      <!-- 点赞飘心动画 -->
      <transition-group name="float-heart" tag="div" class="heart-float-area">
        <span
          v-for="h in floatingHearts"
          :key="h.id"
          class="float-heart"
          :style="{ left: h.x + 'px' }"
        >
          ❤️
        </span>
      </transition-group>
    </div>

    <div class="chat-panel">
      <div class="chat-messages" ref="chatMsgs">
        <div v-if="chatMessages.length === 0" class="chat-hint">欢迎来到直播间～</div>
        <div v-for="(m, i) in chatMessages" :key="i" class="chat-msg">
          <span class="chat-user">{{ m.nickname }}: </span>
          <span>{{ m.text }}</span>
        </div>
      </div>
      <div class="chat-input-row">
        <input
          v-model="chatText"
          class="chat-input"
          placeholder="发个弹幕吧..."
          @keyup.enter="sendChat"
        />
        <button class="btn-send" @click="sendChat">发送</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick, reactive } from 'vue'
import { useRoute } from 'vue-router'
import { getLiveDetail, joinLive, leaveLive, likeLive } from '@/api/live'
import { useBaseStore } from '@/store/pinia'
import { _notice } from '@/utils'

const route = useRoute()
const roomId = ref<number>(Number(route.params.id))
const host = ref<any>(null)
const viewerCount = ref(0)
const likeCount = ref(0)
const chatMessages = ref<{ nickname: string; text: string }[]>([])
const chatText = ref('')
const floatingHearts = ref<{ id: number; x: number }[]>([])
let heartId = 0

const videoCanvas = ref<HTMLCanvasElement>()
let liveWs: WebSocket | null = null
let canvasCtx: CanvasRenderingContext2D | null = null
const imgEl = document.createElement('img')

onMounted(async () => {
  // Load room info
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

  // Join room
  try {
    await joinLive(roomId.value)
  } catch (e) {
    /* ignore */
  }

  // Connect live WebSocket as viewer
  connectWs()

  // Setup canvas
  if (videoCanvas.value) {
    canvasCtx = videoCanvas.value.getContext('2d')
  }
})

onBeforeUnmount(() => {
  if (liveWs) liveWs.close()
  leaveLive(roomId.value).catch(() => {})
})

function connectWs() {
  const token = localStorage.getItem('token') || ''
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  liveWs = new WebSocket(
    `${protocol}//${location.host}/ws/live/${roomId.value}?role=viewer&token=${token}`
  )

  liveWs.onmessage = (e) => {
    try {
      const msg = JSON.parse(e.data)
      switch (msg.type) {
        case 'frame':
          if (imgEl && canvasCtx && videoCanvas.value) {
            imgEl.onload = () => {
              canvasCtx!.drawImage(imgEl, 0, 0, videoCanvas.value!.width, videoCanvas.value!.height)
            }
            imgEl.src = msg.data
          }
          break
        case 'chat':
          chatMessages.value.push({ nickname: msg.nickname, text: msg.text })
          if (chatMessages.value.length > 100) chatMessages.value.shift()
          scrollChat()
          break
        case 'like':
          likeCount.value += msg.count || 1
          break
        case 'viewer_count':
          viewerCount.value = msg.count
          break
      }
    } catch (e) {
      console.error(e)
    }
  }

  liveWs.onclose = () => {
    // Auto reconnect after 3s
    setTimeout(connectWs, 3000)
  }
}

function sendChat() {
  if (!chatText.value.trim() || !liveWs || liveWs.readyState !== WebSocket.OPEN) return
  const store = useBaseStore()
  liveWs.send(
    JSON.stringify({
      type: 'chat',
      nickname: store.userinfo.nickname || '观众',
      text: chatText.value.trim()
    })
  )
  chatText.value = ''
}

function sendLike() {
  if (liveWs && liveWs.readyState === WebSocket.OPEN) {
    liveWs.send(JSON.stringify({ type: 'like', count: 1 }))
  }
  likeLive(roomId.value).catch(() => {})
  likeCount.value++
  // Floating heart animation
  const h = { id: ++heartId, x: Math.random() * 200 + 50 }
  floatingHearts.value.push(h)
  setTimeout(() => {
    floatingHearts.value = floatingHearts.value.filter((v) => v.id !== h.id)
  }, 1000)
}

function toggleLike() {
  sendLike()
}

function scrollChat() {
  nextTick(() => {
    const el = document.querySelector('.chat-messages')
    if (el) el.scrollTop = el.scrollHeight
  })
}
</script>

<style scoped lang="less">
.live-watch {
  width: 100%;
  height: 100vh;
  background: #000;
  display: flex;
  flex-direction: column;
}

.video-area {
  flex: 1;
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}
.video-canvas {
  width: 100%;
  height: 100%;
  object-fit: contain;
  background: #111;
}

.top-bar {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  padding: 12rem 16rem;
  padding-top: max(12rem, env(safe-area-inset-top));
  background: linear-gradient(to bottom, rgba(0, 0, 0, 0.6), transparent);
  z-index: 2;
}
.back-btn {
  width: 24rem;
  height: 24rem;
  cursor: pointer;
  margin-right: 12rem;
}
.host-info {
  display: flex;
  flex-direction: column;
}
.host-name {
  color: #fff;
  font-size: 15rem;
  font-weight: 600;
}
.viewer-count {
  color: #ccc;
  font-size: 11rem;
}

.live-tag {
  position: absolute;
  top: 60rem;
  left: 16rem;
  background: #fe2c55;
  color: #fff;
  font-size: 11rem;
  padding: 2rem 8rem;
  border-radius: 4rem;
  font-weight: 700;
  z-index: 2;
}

.like-btn {
  position: absolute;
  right: 16rem;
  bottom: 120rem;
  display: flex;
  flex-direction: column;
  align-items: center;
  z-index: 2;
  cursor: pointer;
}
.heart {
  font-size: 36rem;
  filter: drop-shadow(0 2rem 4rem rgba(0, 0, 0, 0.3));
}
.like-count {
  color: #fff;
  font-size: 12rem;
  font-weight: 600;
  margin-top: 2rem;
}

.heart-float-area {
  position: absolute;
  right: 20rem;
  bottom: 120rem;
  z-index: 3;
  pointer-events: none;
}
.float-heart {
  position: absolute;
  font-size: 24rem;
  animation: floatUp 1s ease-out forwards;
}
@keyframes floatUp {
  0% {
    opacity: 1;
    transform: translateY(0) scale(0.5);
  }
  50% {
    opacity: 1;
    transform: translateY(-40rem) scale(1);
  }
  100% {
    opacity: 0;
    transform: translateY(-80rem) scale(0.8);
  }
}

.chat-panel {
  height: 35vh;
  display: flex;
  flex-direction: column;
  background: rgba(0, 0, 0, 0.8);
  border-top: 1rem solid #333;
}
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 8rem 12rem;
}
.chat-hint {
  text-align: center;
  color: #666;
  font-size: 13rem;
  padding: 20rem;
}
.chat-msg {
  padding: 2rem 0;
  font-size: 12rem;
  color: #ddd;
  line-height: 1.6;
}
.chat-user {
  color: #ffd700;
  font-weight: 600;
}
.chat-input-row {
  display: flex;
  padding: 8rem 12rem;
  gap: 8rem;
  border-top: 1rem solid #333;
  background: #1a1a1a;
}
.chat-input {
  flex: 1;
  padding: 8rem 12rem;
  border-radius: 20rem;
  border: none;
  font-size: 13rem;
  outline: none;
  background: #333;
  color: #fff;
}
.btn-send {
  padding: 8rem 16rem;
  background: #fe2c55;
  color: #fff;
  border: none;
  border-radius: 20rem;
  font-size: 13rem;
  cursor: pointer;
  white-space: nowrap;
}
</style>
