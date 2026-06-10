<template>
  <div class="live-create">
    <BaseHeader mode="dark" backImg="back" @back="$router.back()">
      <template v-slot:center><span class="header-title">开直播</span></template>
    </BaseHeader>

    <div class="body">
      <!-- 设置阶段 -->
      <div v-if="step === 'setup'" class="setup">
        <div class="camera-preview" ref="previewWrap">
          <video ref="previewVideo" autoplay muted playsinline class="preview-video"></video>
        </div>
        <div class="form">
          <input v-model="title" class="title-input" placeholder="输入直播标题..." maxlength="50" />
          <button class="btn-start" :disabled="!title.trim()" @click="startBroadcast">
            开始直播
          </button>
        </div>
      </div>

      <!-- 直播中 -->
      <div v-if="step === 'live'" class="live">
        <div class="camera-preview live-preview" ref="livePreviewWrap">
          <video ref="liveVideo" autoplay muted playsinline class="preview-video"></video>
          <div class="live-badge">LIVE</div>
          <div class="viewer-badge">{{ viewerCount }} 人观看</div>
          <button class="btn-end" @click="endBroadcast">结束直播</button>
        </div>
        <div class="chat-panel">
          <div class="chat-messages" ref="chatMsgs">
            <div v-for="(m, i) in chatMessages" :key="i" class="chat-msg">
              <span class="chat-user">{{ m.nickname }}: </span>
              <span>{{ m.text }}</span>
            </div>
          </div>
          <div class="chat-input-row">
            <input
              v-model="chatText"
              class="chat-input"
              placeholder="说点什么..."
              @keyup.enter="sendChat"
            />
            <button class="btn-send" @click="sendChat">发送</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { _notice } from '@/utils'
import { createLiveRoom, startLive, endLive } from '@/api/live'
import BaseHeader from '@/components/BaseHeader.vue'

const router = useRouter()
const step = ref<'setup' | 'live'>('setup')
const title = ref('')
const roomId = ref<number>(0)
const viewerCount = ref(1)
const chatMessages = ref<{ nickname: string; text: string }[]>([])
const chatText = ref('')

const previewVideo = ref<HTMLVideoElement>()
const liveVideo = ref<HTMLVideoElement>()
const stream: Ref<MediaStream | null> = ref(null)
let liveWs: WebSocket | null = null
let frameTimer: ReturnType<typeof setInterval> | null = null
let canvas: HTMLCanvasElement | null = null
let ctx: CanvasRenderingContext2D | null = null

onMounted(async () => {
  try {
    const s = await navigator.mediaDevices.getUserMedia({
      video: { width: 480, height: 640, facingMode: 'user' },
      audio: true
    })
    stream.value = s
    if (previewVideo.value) previewVideo.value.srcObject = s
    if (liveVideo.value) liveVideo.value.srcObject = s
  } catch (e) {
    _notice('无法访问摄像头，请检查权限')
  }
})

onBeforeUnmount(() => {
  stopStreaming()
  if (stream.value) {
    stream.value.getTracks().forEach((t) => t.stop())
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
    roomId.value = res.data.id
    await startLive(roomId.value)
    step.value = 'live'

    await nextTick()
    startStreaming()
    connectLiveWs()
  } catch (e) {
    _notice('开播失败')
  }
}

function startStreaming() {
  if (!stream.value || !liveVideo.value) return
  if (liveVideo.value) liveVideo.value.srcObject = stream.value

  canvas = document.createElement('canvas')
  canvas.width = 320
  canvas.height = 480
  ctx = canvas.getContext('2d')

  frameTimer = setInterval(() => {
    if (!ctx || !canvas || !liveVideo.value || !liveWs || liveWs.readyState !== WebSocket.OPEN)
      return
    ctx.drawImage(liveVideo.value, 0, 0, 320, 480)
    const dataUrl = canvas.toDataURL('image/jpeg', 0.5)
    liveWs.send(JSON.stringify({ type: 'frame', data: dataUrl }))
  }, 200) // 5fps
}

function connectLiveWs() {
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  liveWs = new WebSocket(`${protocol}//${location.host}/ws/live/${roomId.value}?role=host`)
  liveWs.onmessage = (e) => {
    try {
      const msg = JSON.parse(e.data)
      if (msg.type === 'chat') {
        chatMessages.value.push({ nickname: msg.nickname, text: msg.text })
        if (chatMessages.value.length > 50) chatMessages.value.shift()
        scrollChat()
      }
      if (msg.type === 'viewer_count') {
        viewerCount.value = msg.count + 1
      }
    } catch (e) {
      console.error(e)
    }
  }
}

async function endBroadcast() {
  stopStreaming()
  if (liveWs) {
    liveWs.close()
    liveWs = null
  }
  if (roomId.value > 0) {
    await endLive(roomId.value)
  }
  router.back()
}

function stopStreaming() {
  if (frameTimer) {
    clearInterval(frameTimer)
    frameTimer = null
  }
}

function sendChat() {
  if (!chatText.value.trim() || !liveWs) return
  liveWs.send(
    JSON.stringify({
      type: 'chat',
      userId: 0,
      nickname: '主播',
      text: chatText.value.trim()
    })
  )
  chatText.value = ''
}

function scrollChat() {
  nextTick(() => {
    const el = document.querySelector('.chat-messages')
    if (el) el.scrollTop = el.scrollHeight
  })
}
</script>

<script lang="ts">
import { Ref } from 'vue'
export default { name: 'LiveCreate' }
</script>

<style scoped lang="less">
.live-create {
  width: 100%;
  height: 100vh;
  background: #000;
  display: flex;
  flex-direction: column;
}
.header-title {
  font-size: 16rem;
  font-weight: 600;
  color: #fff;
}

.body {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.setup {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20rem;
}

.camera-preview {
  width: 100%;
  max-width: 360rem;
  aspect-ratio: 3/4;
  background: #222;
  border-radius: 12rem;
  overflow: hidden;
  position: relative;
}
.preview-video {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.live-badge {
  position: absolute;
  top: 10rem;
  left: 10rem;
  background: #fe2c55;
  color: #fff;
  font-size: 12rem;
  padding: 2rem 8rem;
  border-radius: 4rem;
  font-weight: 700;
}
.viewer-badge {
  position: absolute;
  top: 10rem;
  right: 10rem;
  background: rgba(0, 0, 0, 0.6);
  color: #fff;
  font-size: 12rem;
  padding: 2rem 8rem;
  border-radius: 4rem;
}
.btn-end {
  position: absolute;
  bottom: 16rem;
  right: 16rem;
  background: #fe2c55;
  color: #fff;
  border: none;
  border-radius: 20rem;
  padding: 8rem 20rem;
  font-size: 14rem;
  cursor: pointer;
}

.form {
  width: 100%;
  max-width: 360rem;
  margin-top: 20rem;
}
.title-input {
  width: 100%;
  padding: 12rem;
  border-radius: 8rem;
  border: none;
  font-size: 14rem;
  outline: none;
  box-sizing: border-box;
  background: #333;
  color: #fff;
}
.btn-start {
  width: 100%;
  margin-top: 16rem;
  padding: 12rem;
  border: none;
  border-radius: 24rem;
  background: #fe2c55;
  color: #fff;
  font-size: 16rem;
  font-weight: 600;
  cursor: pointer;
}
.btn-start:disabled {
  background: #666;
  cursor: not-allowed;
}

.live {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.live-preview {
  max-width: 100%;
  aspect-ratio: auto;
  flex: 1;
  max-height: 60vh;
}

.chat-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #1a1a1a;
  border-top: 1rem solid #333;
}
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 8rem 12rem;
}
.chat-msg {
  padding: 3rem 0;
  font-size: 12rem;
  color: #ccc;
  line-height: 1.5;
}
.chat-user {
  color: #fe2c55;
  font-weight: 600;
}
.chat-input-row {
  display: flex;
  padding: 8rem 12rem;
  gap: 8rem;
  border-top: 1rem solid #333;
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
