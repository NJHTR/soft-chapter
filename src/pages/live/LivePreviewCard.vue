<template>
  <div class="room-card" ref="cardRef" @click="enterRoom">
    <div class="cover" ref="coverRef">
      <canvas ref="previewCanvas" class="preview-canvas" />
      <div class="cover-fallback" v-if="!wsReady">
        <img v-if="room.host" class="cover-img" :src="hostAvatar" />
        <div class="cover-placeholder" v-else></div>
      </div>
      <div class="live-tag">LIVE</div>
      <div class="viewer-badge">{{ formatCount(room.viewerCount) }}人观看</div>
    </div>
    <div class="info">
      <span class="room-title">{{ room.title || '直播间' }}</span>
      <span class="host-name">@{{ room.host?.nickname || '主播' }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { _checkImgUrl } from '@/utils'
import defaultAvatarPng from '@/assets/img/icon/people-gray.png'

const props = defineProps<{ room: any }>()
const router = useRouter()

const cardRef = ref<HTMLElement>()
const coverRef = ref<HTMLElement>()
const previewCanvas = ref<HTMLCanvasElement>()
const wsReady = ref(false)

const hostAvatar = computed(() => {
  return (
    _checkImgUrl(props.room.host?.avatar_168x168?.url_list?.[0]) ||
    _checkImgUrl(props.room.host?.avatar) ||
    defaultAvatarPng
  )
})

let ws: WebSocket | null = null
let ctx: CanvasRenderingContext2D | null = null
let stopped = false
let shouldConnect = false
let resizeObserver: ResizeObserver | null = null
let visibilityObserver: IntersectionObserver | null = null

function initCanvas() {
  if (!previewCanvas.value || !coverRef.value) return
  const rect = coverRef.value.getBoundingClientRect()
  const w = Math.round(rect.width)
  const h = Math.round(rect.height)
  if (w <= 0 || h <= 0) return
  if (previewCanvas.value.width === w && previewCanvas.value.height === h) return
  previewCanvas.value.width = w
  previewCanvas.value.height = h
  ctx = previewCanvas.value.getContext('2d')
}

function connectWs() {
  if (stopped || !shouldConnect) return
  if (ws) {
    ws.onclose = null
    ws.close()
    ws = null
  }
  const token = localStorage.getItem('token') || ''
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  ws = new WebSocket(
    `${protocol}//${location.host}/ws/live/${props.room.id}?role=viewer&token=${token}`
  )

  ws.onopen = () => {
    if (!stopped && shouldConnect) wsReady.value = true
  }

  ws.onmessage = (e) => {
    try {
      const msg = JSON.parse(e.data)
      if (msg.type === 'frame') {
        renderFrame(msg.data)
      }
    } catch (_) {
      /* ignore */
    }
  }

  ws.onclose = () => {
    wsReady.value = false
    ws = null
    if (stopped || !shouldConnect) return
    setTimeout(() => {
      if (!stopped && shouldConnect) connectWs()
    }, 5000)
  }
}

function renderFrame(dataUrl: string) {
  if (!ctx || !previewCanvas.value) return
  const cw = previewCanvas.value.width
  const ch = previewCanvas.value.height
  const img = new Image()
  img.onload = () => {
    if (
      ctx &&
      previewCanvas.value &&
      previewCanvas.value.width === cw &&
      previewCanvas.value.height === ch
    ) {
      ctx!.drawImage(img, 0, 0, cw, ch)
    }
  }
  img.src = dataUrl
}

function enterRoom() {
  router.push('/live/' + props.room.id)
}

function formatCount(n: number): string {
  if (!n) return '0'
  if (n >= 10000) return (n / 10000).toFixed(1) + '万'
  return String(n)
}

onMounted(async () => {
  await nextTick()
  initCanvas()

  resizeObserver = new ResizeObserver(() => initCanvas())
  if (coverRef.value) resizeObserver.observe(coverRef.value)

  visibilityObserver = new IntersectionObserver(
    (entries) => {
      if (entries[0].isIntersecting) {
        shouldConnect = true
        if (!ws || ws.readyState > WebSocket.OPEN) connectWs()
      } else {
        shouldConnect = false
        if (ws) {
          ws.onclose = null
          ws.close()
          ws = null
          wsReady.value = false
        }
      }
    },
    { rootMargin: '200px' }
  )
  if (cardRef.value) visibilityObserver.observe(cardRef.value)
})

onBeforeUnmount(() => {
  stopped = true
  shouldConnect = false
  visibilityObserver?.disconnect()
  resizeObserver?.disconnect()
  if (ws) {
    ws.onclose = null
    ws.close()
    ws = null
  }
})
</script>

<style scoped lang="less">
.room-card {
  cursor: pointer;
  border-radius: 10rem;
  overflow: hidden;
}

.cover {
  position: relative;
  aspect-ratio: 3/4;
  background: #222;
  overflow: hidden;
  border-radius: 10rem;

  .preview-canvas {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    object-fit: cover;
    z-index: 1;
  }

  .cover-fallback {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    z-index: 0;

    .cover-img {
      width: 100%;
      height: 100%;
      object-fit: cover;
      filter: blur(20px) brightness(0.6);
      transform: scale(1.2);
    }

    .cover-placeholder {
      width: 100%;
      height: 100%;
      background: linear-gradient(135deg, #1a1a2e, #16213e, #0f3460);
    }
  }

  .live-tag {
    position: absolute;
    top: 8rem;
    left: 8rem;
    padding: 2rem 6rem;
    background: #fe2c55;
    color: #fff;
    font-size: 10rem;
    font-weight: 700;
    border-radius: 4rem;
    z-index: 2;
  }

  .viewer-badge {
    position: absolute;
    bottom: 8rem;
    right: 8rem;
    padding: 2rem 8rem;
    background: rgba(0, 0, 0, 0.5);
    color: #fff;
    font-size: 10rem;
    border-radius: 10rem;
    z-index: 2;
  }
}

.info {
  padding: 6rem 2rem;

  .room-title {
    color: #fff;
    font-size: 13rem;
    font-weight: 500;
    display: block;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .host-name {
    color: #888;
    font-size: 11rem;
    display: block;
    margin-top: 2rem;
  }
}
</style>
