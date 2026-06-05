<template>
  <div class="text-slide-wrapper" @dblclick="doDoubleTapLike">
    <img :src="textImage" class="text-img" alt="" />

    <div class="float">
      <div class="normal">
        <ItemToolbar v-model:item="localItem" :is-my="isMy" />
        <ItemDesc v-model:item="localItem" />
      </div>
      <transition-group name="comment-status" tag="div" class="loveds">
        <div class="type-loved" :key="i" v-for="i in loveAnimations">
          <img :src="store.userinfo?.avatar_168x168?.url_list?.[0] || ''" class="avatar" alt="" />
          <img src="../../assets/img/icon/love.svg" class="loved" alt="" />
        </div>
      </transition-group>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, provide, ref } from 'vue'
import { _checkImgUrl } from '@/utils'
import { toggleVideoLike, recordWatch } from '@/api/videos'
import { useBaseStore } from '@/store/pinia'
import bus, { EVENT_KEY } from '@/utils/bus'
import ItemToolbar from './ItemToolbar.vue'
import ItemDesc from './ItemDesc.vue'

defineOptions({ name: 'TextSlide' })

const store = useBaseStore()

const props = defineProps({
  item: { type: Object, default: () => ({}) },
  position: { type: Object, default: () => ({}) },
  isPlay: { type: Boolean, default: true }
})

const localItem = ref(props.item)
const loveAnimations = ref<number[]>([])
let loveId = 0

const textImage = computed(() => {
  const cover = props.item?.video?.cover?.url_list?.[0] || props.item?.video?.poster
  return _checkImgUrl(cover || '')
})

const isMy = computed(() => {
  const itemUid = String(props.item?.author?.uid || '')
  const myUid = String(store.userinfo?.uid || '')
  return itemUid !== '' && itemUid === myUid
})

provide('item', computed(() => props.item))
provide('position', computed(() => props.position))
provide('isPlaying', computed(() => props.isPlay))
provide('isMuted', false)

// ====== 观看历史 ======
const videoId = computed(() => props.item?.aweme_id)
const authorUserId = computed(() => String(props.item?.author?.uid || ''))
let watchSec = 0
let watchTimer: ReturnType<typeof setInterval> | null = null

function sendWatchProgress(finished = false) {
  if (!store.userinfo?.uid || !videoId.value) return
  if (String(store.userinfo.uid) === authorUserId.value) return
  recordWatch(videoId.value, {
    watch_duration: watchSec,
    video_duration: 0,
    finished
  }).catch(() => {})
}

onMounted(() => {
  sendWatchProgress(false)
  watchTimer = setInterval(() => {
    watchSec++
    if (watchSec > 0 && watchSec % 5 === 0) sendWatchProgress()
  }, 1000)
})

onUnmounted(() => {
  if (watchTimer) clearInterval(watchTimer)
  sendWatchProgress(true)
  watchSec = 0
})

let liking = false
async function doDoubleTapLike() {
  if (liking || props.item.is_loved) return
  const awemeId = props.item.aweme_id
  if (!awemeId) return

  liking = true
  const prevLoved = props.item.is_loved
  const prevCount = props.item.statistics?.digg_count || 0
  props.item.is_loved = true
  if (props.item.statistics) props.item.statistics.digg_count += 1

  const id = ++loveId
  loveAnimations.value.push(id)
  setTimeout(() => {
    const idx = loveAnimations.value.indexOf(id)
    if (idx > -1) loveAnimations.value.splice(idx, 1)
  }, 800)

  try {
    const res = await toggleVideoLike(awemeId)
    if (res.success) {
      props.item.is_loved = res.data.isLoved
      if (props.item.statistics) props.item.statistics.digg_count = res.data.likeCount
      bus.emit(EVENT_KEY.UPDATE_ITEM, { position: props.position, item: { ...props.item } })
    } else {
      props.item.is_loved = prevLoved
      if (props.item.statistics) props.item.statistics.digg_count = prevCount
    }
  } catch {
    props.item.is_loved = prevLoved
    if (props.item.statistics) props.item.statistics.digg_count = prevCount
  } finally {
    liking = false
  }
}
</script>

<style scoped lang="less">
.text-slide-wrapper {
  position: relative;
  width: 100%;
  height: 100%;
  background: #000;
  overflow: hidden;
}

.text-img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  user-select: none;
  -webkit-user-drag: none;
}

.float {
  position: absolute;
  inset: 0;
  pointer-events: none;

  .normal {
    position: absolute;
    inset: 0;
  }

  :deep(.toolbar) {
    pointer-events: auto;
  }

  :deep(.item-desc) {
    pointer-events: auto;
  }
}

.loveds {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);

  .type-loved {
    position: absolute;
    left: 50%;
    top: 50%;
    transform: translate(-50%, -50%);
    display: flex;
    align-items: center;
    justify-content: center;

    .avatar {
      width: 40px;
      height: 40px;
      border-radius: 50%;
      position: absolute;
      z-index: 1;
      border: 2px solid #fff;
    }

    .loved {
      width: 70px;
      height: 70px;
      position: absolute;
      z-index: 2;
    }
  }
}

.comment-status-enter-active {
  animation: loveIn 0.8s ease forwards;
}
.comment-status-leave-active {
  animation: loveOut 0.3s ease forwards;
}
@keyframes loveIn {
  0% { opacity: 1; transform: translate(-50%, -50%) scale(0); }
  50% { opacity: 1; transform: translate(-50%, -50%) scale(1.2); }
  100% { opacity: 0.8; transform: translate(-50%, -50%) scale(1); }
}
@keyframes loveOut {
  0% { opacity: 0.8; }
  100% { opacity: 0; transform: translate(-50%, -50%) scale(1.5); }
}
</style>
