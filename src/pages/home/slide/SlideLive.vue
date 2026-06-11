<template>
  <SlideItem class="slide-live">
    <div
      class="live-feed"
      ref="feedEl"
      @touchstart="onTouchStart"
      @touchmove.prevent="onTouchMove"
      @touchend="onTouchEnd"
    >
      <div
        class="live-track"
        ref="trackEl"
        :style="{
          transform: `translate3d(0, ${translateY}px, 0)`,
          transition: animating ? 'transform 0.35s cubic-bezier(0.25, 0.46, 0.45, 0.94)' : 'none'
        }"
      >
        <div
          class="live-page-item"
          v-for="(room, i) in rooms"
          :key="room.id"
          :style="{ transform: `translateY(${i * 100}%)` }"
        >
          <LiveFeedItem
            v-if="i >= activeIndex - 1 && i <= activeIndex + 1"
            :room="room"
            :isActive="i === activeIndex && props.active"
          />
          <div v-else class="placeholder-item"></div>
        </div>
      </div>

      <!-- 指示器 -->
      <div class="indicator-dots" v-if="rooms.length > 1">
        <span
          v-for="(_, i) in rooms"
          :key="i"
          class="dot"
          :class="{ active: i === activeIndex }"
        ></span>
      </div>
    </div>

    <div v-if="loading" class="loading-overlay">
      <span>加载中...</span>
    </div>
  </SlideItem>
</template>

<script setup lang="ts">
import { ref, onMounted, onActivated, onDeactivated, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import SlideItem from '@/components/slide/SlideItem.vue'
import LiveFeedItem from './LiveFeedItem.vue'
import { getLiveRooms } from '@/api/live'

const props = defineProps({
  active: { type: Boolean, default: false }
})

const router = useRouter()
const feedEl = ref<HTMLDivElement>()
const trackEl = ref<HTMLDivElement>()
const rooms = ref<any[]>([])
const loading = ref(false)
const activeIndex = ref(0)
const translateY = ref(0)
const animating = ref(false)

let startY = 0
let startTranslate = 0
let itemHeight = 0
let moved = false

function getItemHeight(): number {
  if (feedEl.value) return feedEl.value.clientHeight
  return window.innerHeight
}

function onTouchStart(e: TouchEvent) {
  itemHeight = getItemHeight()
  startY = e.touches[0].clientY
  startTranslate = -activeIndex.value * itemHeight
  animating.value = false
  moved = false
}

function onTouchMove(e: TouchEvent) {
  const dy = e.touches[0].clientY - startY
  if (Math.abs(dy) > 5) moved = true

  const maxUp = -(rooms.value.length - 1) * itemHeight
  let newY = startTranslate + dy
  if (newY > 0) {
    newY = newY * 0.3
  } else if (newY < maxUp) {
    const over = newY - maxUp
    newY = maxUp + over * 0.3
  }
  translateY.value = newY
}

function onTouchEnd(e: TouchEvent) {
  const dy = e.changedTouches[0].clientY - startY
  const threshold = itemHeight * 0.2

  if (!moved && Math.abs(dy) < 10) {
    // 纯点击，没滑动 → 进入直播间
    const room = rooms.value[activeIndex.value]
    if (room?.id) {
      router.push('/live/' + room.id)
      return
    }
  }

  if (Math.abs(dy) > threshold) {
    if (dy > 0 && activeIndex.value > 0) {
      activeIndex.value--
    } else if (dy < 0 && activeIndex.value < rooms.value.length - 1) {
      activeIndex.value++
    }
  }

  if (activeIndex.value >= rooms.value.length - 2) {
    loadMore()
  }

  animating.value = true
  translateY.value = -activeIndex.value * itemHeight
}

let pollTimer: ReturnType<typeof setInterval> | null = null

async function fetchRooms() {
  loading.value = true
  try {
    const res: any = await getLiveRooms({ pageNo: 1, pageSize: 10 })
    if (res.success && res.data?.list) {
      const wasEmpty = rooms.value.length === 0
      rooms.value = res.data.list
      if (wasEmpty && rooms.value.length > 0) {
        activeIndex.value = 0
        await nextTick()
        translateY.value = 0
      }
    }
  } catch (e) {
    /* ignore */
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  // 暂时一次性加载所有，后续可以加分页
}

function onResize() {
  itemHeight = getItemHeight()
  translateY.value = -activeIndex.value * itemHeight
  animating.value = false
}

function startPolling() {
  stopPolling()
  pollTimer = setInterval(fetchRooms, 8000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

onMounted(() => {
  fetchRooms()
  startPolling()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  stopPolling()
  window.removeEventListener('resize', onResize)
})

onActivated(() => {
  fetchRooms()
  startPolling()
})

onDeactivated(() => {
  stopPolling()
})
</script>

<style scoped lang="less">
.slide-live {
  background: #000;
}

.live-feed {
  width: 100%;
  height: 100%;
  position: relative;
  overflow: hidden;
}

.live-track {
  width: 100%;
  height: 100%;
  position: relative;
}

.live-page-item {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

.placeholder-item {
  width: 100%;
  height: 100%;
  background: #111;
}

.indicator-dots {
  position: absolute;
  right: 10rem;
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  flex-direction: column;
  gap: 8rem;
  z-index: 5;
  pointer-events: none;

  .dot {
    width: 6rem;
    height: 6rem;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.3);
    transition: all 0.3s;

    &.active {
      background: #fe2c55;
      height: 18rem;
      border-radius: 3rem;
    }
  }
}

.loading-overlay {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  color: rgba(255, 255, 255, 0.5);
  font-size: 13rem;
  pointer-events: none;
}
</style>
