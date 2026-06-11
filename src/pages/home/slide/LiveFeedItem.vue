<template>
  <div class="live-feed-item">
    <!-- 背景：直播画面或模糊头像 -->
    <canvas v-if="isActive && wsConnected" ref="liveCanvas" class="live-canvas"></canvas>
    <img
      v-else
      class="cover-bg"
      :src="
        _checkImgUrl(room.host?.avatar_168x168?.url_list?.[0]) ||
        _checkImgUrl(room.host?.avatar) ||
        defaultAvatarPng
      "
    />

    <!-- 信息覆盖层 -->
    <div class="overlay">
      <!-- 左上：主播信息 -->
      <div class="top-left">
        <img
          class="avatar"
          :src="
            _checkImgUrl(room.host?.avatar_168x168?.url_list?.[0]) ||
            _checkImgUrl(room.host?.avatar) ||
            defaultAvatarPng
          "
        />
        <div class="host-detail">
          <span class="host-name">{{ room.host?.nickname || '主播' }}</span>
          <span class="host-title">{{ room.title || '直播间' }}</span>
        </div>
      </div>

      <!-- 右上：观看人数 + LIVE标签 -->
      <div class="top-right">
        <div class="live-tag">LIVE</div>
        <div class="viewer-count">{{ formatCount(room.viewerCount) }}人观看</div>
      </div>

      <!-- 底部提示 -->
      <div class="bottom-hint">
        <span>点击进入直播间</span>
      </div>
    </div>

    <!-- 加载中 -->
    <div v-if="isActive && !wsConnected" class="connecting">
      <span>连接中...</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onBeforeUnmount, watch, nextTick } from 'vue'
import { _checkImgUrl } from '@/utils'
import defaultAvatarPng from '@/assets/img/icon/people-gray.png'

const props = defineProps<{
  room: any
  isActive: boolean
}>()

const liveCanvas = ref<HTMLCanvasElement>()
const wsConnected = ref(false)
let liveWs: WebSocket | null = null
let canvasCtx: CanvasRenderingContext2D | null = null

function formatCount(n: number): string {
  if (!n) return '0'
  if (n >= 10000) return (n / 10000).toFixed(1) + '万'
  return String(n)
}

function connectWs() {
  if (!props.room?.id) return
  if (liveWs) disconnectWs()

  const token = localStorage.getItem('token') || ''
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  liveWs = new WebSocket(
    `${protocol}//${location.host}/ws/live/${props.room.id}?role=viewer&token=${token}`
  )

  liveWs.onopen = () => {
    wsConnected.value = true
    if (liveCanvas.value) {
      canvasCtx = liveCanvas.value.getContext('2d')
      liveCanvas.value.width = liveCanvas.value.offsetWidth || window.innerWidth
      liveCanvas.value.height = liveCanvas.value.offsetHeight || window.innerHeight
    }
  }

  liveWs.onmessage = (e) => {
    try {
      const msg = JSON.parse(e.data)
      if (msg.type === 'frame') {
        renderFrame(msg.data)
      }
    } catch (_) {
      /* ignore */
    }
  }

  liveWs.onerror = () => {
    wsConnected.value = false
  }

  liveWs.onclose = () => {
    wsConnected.value = false
  }
}

function renderFrame(dataUrl: string) {
  if (!canvasCtx || !liveCanvas.value) return
  const img = new Image()
  img.onload = () => {
    if (!canvasCtx || !liveCanvas.value) return
    canvasCtx.drawImage(img, 0, 0, liveCanvas.value.width, liveCanvas.value.height)
  }
  img.src = dataUrl
}

function disconnectWs() {
  if (liveWs) {
    liveWs.onclose = null
    try {
      liveWs.close()
    } catch (_) {
      /* ignore */
    }
    liveWs = null
  }
  wsConnected.value = false
  canvasCtx = null
}

watch(
  () => props.isActive,
  (active) => {
    if (active) {
      nextTick(() => connectWs())
    } else {
      disconnectWs()
    }
  }
)

onBeforeUnmount(() => {
  disconnectWs()
})
</script>

<style scoped lang="less">
.live-feed-item {
  width: 100%;
  height: 100%;
  position: relative;
  background: #000;
  overflow: hidden;
}

.live-canvas {
  width: 100%;
  height: 100%;
  object-fit: contain;
  background: #111;
}

.cover-bg {
  width: 100%;
  height: 100%;
  object-fit: cover;
  filter: blur(30px) brightness(0.4);
  transform: scale(1.3);
}

.overlay {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

// 左上
.top-left {
  position: absolute;
  top: max(50rem, env(safe-area-inset-top));
  left: 12rem;
  display: flex;
  align-items: center;
  gap: 8rem;
  padding: 5rem 12rem 5rem 5rem;
  background: rgba(0, 0, 0, 0.35);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-radius: 24rem;

  .avatar {
    width: 34rem;
    height: 34rem;
    border-radius: 50%;
    object-fit: cover;
    border: 1.5rem solid rgba(255, 255, 255, 0.3);
  }

  .host-detail {
    display: flex;
    flex-direction: column;
    gap: 1rem;
  }

  .host-name {
    color: #fff;
    font-size: 13rem;
    font-weight: 600;
    max-width: 100rem;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .host-title {
    color: rgba(255, 255, 255, 0.7);
    font-size: 10rem;
    max-width: 100rem;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

// 右上
.top-right {
  position: absolute;
  top: max(50rem, env(safe-area-inset-top));
  right: 12rem;
  display: flex;
  align-items: center;
  gap: 8rem;

  .live-tag {
    padding: 3rem 8rem;
    background: #fe2c55;
    color: #fff;
    font-size: 10rem;
    font-weight: 700;
    border-radius: 4rem;
  }

  .viewer-count {
    color: #fff;
    font-size: 11rem;
    padding: 4rem 10rem;
    background: rgba(0, 0, 0, 0.35);
    backdrop-filter: blur(12px);
    -webkit-backdrop-filter: blur(12px);
    border-radius: 24rem;
  }
}

// 底部提示
.bottom-hint {
  position: absolute;
  bottom: 60rem;
  left: 50%;
  transform: translateX(-50%);
  padding: 8rem 20rem;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border-radius: 20rem;
  color: rgba(255, 255, 255, 0.8);
  font-size: 12rem;
}

.connecting {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  color: rgba(255, 255, 255, 0.5);
  font-size: 13rem;
}
</style>
