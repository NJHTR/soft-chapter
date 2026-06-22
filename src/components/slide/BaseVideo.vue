<template>
  <div class="video-wrapper" ref="videoWrapper" :class="positionName">
    <Loading v-if="state.loading" style="position: absolute" />
    <!--    <video :src="item.video + '?v=123'"-->
    <video
      :poster="poster"
      ref="videoEl"
      :muted="state.isMuted"
      preload="auto"
      loop
      x5-video-player-type="h5-page"
      :x5-video-player-fullscreen="false"
      :webkit-playsinline="true"
      :x5-playsinline="true"
      :playsinline="true"
      :fullscreen="false"
      :autoplay="isPlay"
    >
      <source
        v-for="(urlItem, index) in item.video.play_addr.url_list"
        :key="index"
        :src="_checkImgUrl(urlItem)"
        :type="(urlItem || '').split('?')[0].endsWith('.webm') ? 'video/webm' : 'video/mp4'"
      />
      <p>您的浏览器不支持 video 标签。</p>
    </video>
    <Icon icon="fluent:play-28-filled" class="pause-icon" v-if="!isPlaying" />
    <div class="float">
      <template v-if="isLive">
        <div class="living">点击进入直播间</div>
        <ItemDesc :is-live="true" v-model:item="state.localItem" :position="position" />
      </template>
      <template v-else>
        <div :style="{ opacity: state.isMove ? 0 : 1 }" class="normal">
          <template v-if="!state.commentVisible">
            <ItemToolbar v-model:item="state.localItem" :is-my="isMy" />
            <ItemDesc
              v-model:item="state.localItem"
              :video-id="item.aweme_id"
              :show-hints="isPlaying"
              @searchHint="onSearchHint"
            />
          </template>
          <transition-group name="comment-status" tag="div" class="loveds">
            <div class="type-loved" :key="i" v-for="i in state.test">
              <img
                :src="_checkImgUrl(store.userinfo?.avatar_168x168?.url_list?.[0] || '')"
                alt=""
                class="avatar"
              />
              <img src="../../assets/img/icon/love.svg" alt="" class="loved" />
            </div>
          </transition-group>
        </div>
        <div
          class="progress"
          :class="progressClass"
          ref="progressEl"
          @click="null"
          @touchstart="touchstart"
          @touchmove="touchmove"
          @touchend="touchend"
        >
          <div class="time" v-if="state.isMove">
            <span class="currentTime">{{ _duration(state.currentTime) }}</span>
            <span class="duration"> / {{ _duration(state.duration) }}</span>
          </div>
          <template v-if="state.duration > 15 || state.isMove || !isPlaying">
            <div class="bg"></div>
            <div class="progress-line" :style="durationStyle"></div>
            <div class="point"></div>
          </template>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { _checkImgUrl, _duration, _stopPropagation } from '@/utils'
import { recordWatch, toggleVideoLike } from '@/api/videos'
import { getBrowsingSessionId } from '@/utils/session'
import { getResumePosition, persistPosition } from '@/utils/watchPosition'
import Loading from '../Loading.vue'
import ItemToolbar from './ItemToolbar.vue'
import ItemDesc from './ItemDesc.vue'
import bus, { EVENT_KEY } from '../../utils/bus'
import { SlideItemPlayStatus } from '@/utils/const_var'
import { computed, onMounted, onUnmounted, provide, reactive } from 'vue'
import { Icon } from '@iconify/vue'
import { _css } from '@/utils/dom'
import { useBaseStore } from '@/store/pinia'

defineOptions({
  name: 'BaseVideo'
})

const store = useBaseStore()

function onSearchHint(keyword: string) {
  console.log('[BaseVideo] searchHint:', keyword)
  ;(window as any)?.$router?.push(`/home/search?q=${encodeURIComponent(keyword)}`)
}

const props = defineProps({
  item: {
    type: Object,
    default: () => {
      return {}
    }
  },
  position: {
    type: Object,
    default: () => {
      return {}
    }
  },
  //用于第一条数据，自动播放，如果都用事件去触发播放，有延迟
  isPlay: {
    type: Boolean,
    default: () => {
      return true
    }
  },
  isLive: {
    type: Boolean,
    default: () => {
      return false
    }
  }
})

provide(
  'isPlaying',
  computed(() => isPlaying)
)
provide(
  'isMuted',
  computed(() => state.isMuted)
)
provide(
  'position',
  computed(() => props.position)
)
provide(
  'item',
  computed(() => props.item)
)

const videoEl = $ref<HTMLVideoElement>()
const progressEl = $ref<HTMLDivElement>()
let state = reactive({
  loading: false,
  paused: false,
  isMuted: window.isMuted,
  status: props.isPlay ? SlideItemPlayStatus.Play : SlideItemPlayStatus.Pause,
  duration: 0,
  step: 0,
  currentTime: -1,
  playX: 0,
  start: { x: 0 },
  last: { x: 0, time: 0 },
  height: 0,
  width: 0,
  isMove: false,
  ignoreWaiting: false, //忽略waiting事件。因为改变进度会触发waiting事件，烦的一批
  test: [],
  loveId: 0,
  localItem: props.item,
  progressBarRect: {
    height: 0,
    width: 0
  },
  videoScreenHeight: 0,
  commentVisible: false
})
// 断点续播: 会话级位置缓存 (同 session 内秒恢复, 不依赖网络)
const sessionPositions = new Map<string, number>()

// 观看时长跟踪
let watchSec = 0
let watchTimer: any = null
let watchReported = false
let positionResumed = false // 本次播放是否已从断点恢复
let waitingSince = 0 // buffer 卡顿开始时间戳
let stallRecoveryTimer: any = null
const videoId = computed(() => (props.item?.aweme_id ? String(props.item.aweme_id) : ''))
const authorUserId = computed(() => (props.item?.author?.uid ? String(props.item.author.uid) : ''))
const videoDuration = computed(() => state.duration || 0)

function tickWatch() {
  watchSec++
  // 每 5 秒: 已登录上报后端, 未登录存 localStorage
  if (watchSec > 0 && watchSec % 5 === 0 && !watchReported) {
    if (store.userinfo?.uid) {
      sendWatchProgress()
    } else if (videoId.value) {
      const pos = videoEl?.currentTime || 0
      if (pos > 1) persistPosition(videoId.value, pos, false)
    }
  }
}

function sendWatchProgress(finished = false) {
  if (!store.userinfo?.uid || !videoId.value) return
  if (String(store.userinfo.uid) === authorUserId.value) return // 不看自己的
  const dur = watchSec
  const currentPos = videoEl?.currentTime || 0
  sessionPositions.set(videoId.value, currentPos)
  // 双写: localStorage 保底 (已登录时后端也由 recordWatch 写入)
  if (currentPos > 1) {
    persistPosition(videoId.value, currentPos, !!store.userinfo?.uid)
  }
  recordWatch(videoId.value, {
    watch_duration: dur,
    video_duration: videoDuration.value,
    finished,
    session_id: getBrowsingSessionId(),
    swipe_seconds: dur,
    traffic_source: 'HOME_RECOMMEND',
    last_position: Math.floor(currentPos)
  }).catch(() => {})
  if (finished) watchReported = true
}

const poster = $computed(() => {
  return _checkImgUrl(props.item.video.poster ?? props.item.video.cover.url_list[0])
})
const durationStyle = $computed(() => {
  return { width: state.playX + 'px' }
})
const isPlaying = $computed(() => {
  return state.status === SlideItemPlayStatus.Play
})
const positionName = $computed(() => {
  return 'item-' + Object.values(props.position).join('-')
})
const progressClass = $computed(() => {
  if (state.isMove) {
    return 'move'
  } else {
    return isPlaying ? '' : 'stop'
  }
})

const isMy = $computed(() => {
  const itemUid = String(props.item?.author?.uid || '')
  const myUid = String(store.userinfo?.uid || '')
  return itemUid !== '' && itemUid === myUid
})

function showLoveAnimation() {
  const id = ++state.loveId
  state.test.push(id)
  setTimeout(() => {
    const idx = state.test.indexOf(id)
    if (idx > -1) state.test.splice(idx, 1)
  }, 800)
}

let likingDoubleTap = false

async function doDoubleTapLike() {
  if (likingDoubleTap) return
  if (props.item.is_loved) return
  const awemeId = props.item.aweme_id
  if (!awemeId) return

  likingDoubleTap = true
  const prevLoved = props.item.is_loved
  const prevCount = props.item.statistics.digg_count
  // eslint-disable-next-line vue/no-mutating-props
  props.item.is_loved = true
  // eslint-disable-next-line vue/no-mutating-props
  props.item.statistics.digg_count += 1

  try {
    const res = await toggleVideoLike(awemeId)
    if (res.success) {
      // eslint-disable-next-line vue/no-mutating-props
      props.item.is_loved = res.data.isLoved
      // eslint-disable-next-line vue/no-mutating-props
      props.item.statistics.digg_count = res.data.likeCount
      bus.emit(EVENT_KEY.UPDATE_ITEM, { position: props.position, item: { ...props.item } })
      bus.emit(EVENT_KEY.LIKE_UPDATED)
    } else {
      // eslint-disable-next-line vue/no-mutating-props
      props.item.is_loved = prevLoved
      // eslint-disable-next-line vue/no-mutating-props
      props.item.statistics.digg_count = prevCount
    }
  } catch {
    // eslint-disable-next-line vue/no-mutating-props
    props.item.is_loved = prevLoved
    // eslint-disable-next-line vue/no-mutating-props
    props.item.statistics.digg_count = prevCount
  } finally {
    likingDoubleTap = false
  }
}

onMounted(() => {
  // console.log('video', this.localItem.aweme_id)
  // console.log(this.commentVisible)
  state.height = document.body.clientHeight
  state.width = document.body.clientWidth
  videoEl.currentTime = 0
  let fun = (e) => {
    state.currentTime = Math.ceil(e.target.currentTime)
    state.playX = (state.currentTime - 1) * state.step
  }
  videoEl.addEventListener('loadedmetadata', () => {
    state.videoScreenHeight = videoEl.videoHeight / (videoEl.videoWidth / state.width)
    state.duration = videoEl.duration
    state.progressBarRect = progressEl.getBoundingClientRect()
    state.step = state.progressBarRect.width / Math.floor(state.duration)
    videoEl.addEventListener('timeupdate', fun)
  })

  let eventTester = (e, t: string) => {
    videoEl.addEventListener(
      e,
      () => {
        // console.log('eventTester', e, state.item.aweme_id)
        if (e === 'playing') {
          state.loading = false
          waitingSince = 0
          // 恢复后清理定时器
          if (stallRecoveryTimer) {
            clearInterval(stallRecoveryTimer)
            stallRecoveryTimer = null
          }
        }
        if (e === 'waiting') {
          if (!state.paused && !state.ignoreWaiting) {
            state.loading = true
            if (!waitingSince) waitingSince = Date.now()
            // 启动卡顿恢复定时器
            if (!stallRecoveryTimer) stallRecoveryTimer = setInterval(tryRecoverFromStall, 2000)
          }
        }
        let s = false
        if (s) {
          console.log(e, t)
        }
      },
      false
    )
  }

  // eventTester("loadstart", '客户端开始请求数据'); //客户端开始请求数据
  // eventTester("abort", '客户端主动终止下载（不是因为错误引起）'); //客户端主动终止下载（不是因为错误引起）
  // eventTester("loadstart", '客户端开始请求数据'); //客户端开始请求数据
  // eventTester("progress", '客户端正在请求数据'); //客户端正在请求数据
  // // eventTester("suspend", '延迟下载'); //延迟下载
  // eventTester("abort", '客户端主动终止下载（不是因为错误引起），'); //客户端主动终止下载（不是因为错误引起），
  // eventTester("error", '请求数据时遇到错误'); //请求数据时遇到错误
  // eventTester("stalled", '网速失速'); //网速失速
  // eventTester("play", 'play()和autoplay开始播放时触发'); //play()和autoplay开始播放时触发
  // eventTester("pause", 'pause()触发'); //pause()触发
  // eventTester("loadedmetadata", '成功获取资源长度'); //成功获取资源长度
  // eventTester("loadeddata"); //
  eventTester('waiting', '等待数据，并非错误') //等待数据，并非错误
  eventTester('playing', '开始回放') //开始回放
  // eventTester("canplay", '/可以播放，但中途可能因为加载而暂停'); //可以播放，但中途可能因为加载而暂停
  // eventTester("canplaythrough", '可以播放，歌曲全部加载完毕'); //可以播放，歌曲全部加载完毕
  // eventTester("seeking", '寻找中'); //寻找中
  // eventTester("seeked", '寻找完毕'); //寻找完毕
  // // eventTester("timeupdate",'播放时间改变'); //播放时间改变
  // eventTester("ended", '播放结束'); //播放结束
  // eventTester("ratechange", '播放速率改变'); //播放速率改变
  // eventTester("durationchange", '资源长度改变'); //资源长度改变
  // eventTester("volumechange", '音量改变'); //音量改变

  // 观看时长跟踪 — 播放时启动计时器，暂停/结束时上报
  videoEl.addEventListener('playing', () => {
    if (watchTimer) clearInterval(watchTimer)
    watchTimer = setInterval(tickWatch, 1000)
  })
  videoEl.addEventListener('pause', () => {
    if (watchTimer) {
      clearInterval(watchTimer)
      watchTimer = null
    }
    sendWatchProgress()
  })
  videoEl.addEventListener('ended', () => {
    if (watchTimer) {
      clearInterval(watchTimer)
      watchTimer = null
    }
    sendWatchProgress(true)
  })

  // console.log('mounted')
  // bus.off('singleClickBroadcast')
  bus.on(EVENT_KEY.SINGLE_CLICK_BROADCAST, click)
  bus.on(EVENT_KEY.DIALOG_MOVE, onDialogMove)
  bus.on(EVENT_KEY.DIALOG_END, onDialogEnd)
  bus.on(EVENT_KEY.OPEN_COMMENTS, onOpenComments)
  bus.on(EVENT_KEY.CLOSE_COMMENTS, onCloseComments)
  bus.on(EVENT_KEY.OPEN_SUB_TYPE, onOpenSubType)
  bus.on(EVENT_KEY.CLOSE_SUB_TYPE, onCloseSubType)

  bus.on(EVENT_KEY.REMOVE_MUTED, removeMuted)
  bus.on(EVENT_KEY.LIKE_UPDATED, onLikeUpdated)
  bus.on(EVENT_KEY.DOUBLE_TAP, onDoubleTap)
})

onUnmounted(() => {
  // console.log('unmounted')
  if (watchTimer) {
    clearInterval(watchTimer)
    watchTimer = null
  }
  if (stallRecoveryTimer) {
    clearInterval(stallRecoveryTimer)
    stallRecoveryTimer = null
  }
  sendWatchProgress()
  watchSec = 0
  bus.off(EVENT_KEY.SINGLE_CLICK_BROADCAST, click)
  bus.off(EVENT_KEY.DIALOG_MOVE, onDialogMove)
  bus.off(EVENT_KEY.DIALOG_END, onDialogEnd)
  bus.off(EVENT_KEY.OPEN_COMMENTS, onOpenComments)
  bus.off(EVENT_KEY.CLOSE_COMMENTS, onCloseComments)
  bus.off(EVENT_KEY.OPEN_SUB_TYPE, onOpenSubType)
  bus.off(EVENT_KEY.CLOSE_SUB_TYPE, onCloseSubType)
  bus.off(EVENT_KEY.REMOVE_MUTED, removeMuted)
  bus.off(EVENT_KEY.LIKE_UPDATED, onLikeUpdated)
  bus.off(EVENT_KEY.DOUBLE_TAP, onDoubleTap)
})

function removeMuted() {
  state.isMuted = false
}

function onLikeUpdated() {
  if (props.item.is_loved) {
    showLoveAnimation()
  }
}

function onDoubleTap() {
  doDoubleTapLike()
}

function onOpenSubType() {
  state.commentVisible = true
}

function onCloseSubType() {
  state.commentVisible = false
}

function onDialogMove({ tag, e }) {
  if (state.commentVisible && tag === 'comment') {
    _css(videoEl, 'transition-duration', `0ms`)
    _css(videoEl, 'height', `calc(var(--vh, 1vh) * 30 + ${e}px)`)
  }
}

function onDialogEnd({ tag, isClose }) {
  if (state.commentVisible && tag === 'comment') {
    console.log('isClose', isClose)
    _css(videoEl, 'transition-duration', `300ms`)
    if (isClose) {
      state.commentVisible = false
      _css(videoEl, 'height', '100%')
    } else {
      _css(videoEl, 'height', 'calc(var(--vh, 1vh) * 30)')
    }
  }
}

function onOpenComments(id) {
  if (id === props.item.aweme_id) {
    _css(videoEl, 'transition-duration', `300ms`)
    _css(videoEl, 'height', 'calc(var(--vh, 1vh) * 30)')
    state.commentVisible = true
  }
}

function onCloseComments() {
  if (state.commentVisible) {
    _css(videoEl, 'transition-duration', `300ms`)
    _css(videoEl, 'height', '100%')
    state.commentVisible = false
  }
}

function click({ uniqueId, index, type }) {
  if (props.position.uniqueId === uniqueId && props.position.index === index) {
    if (type === EVENT_KEY.ITEM_TOGGLE) {
      if (props.isLive) {
        pause()
        bus.emit(EVENT_KEY.NAV, {
          path: '/live/list'
        })
      } else {
        if (state.status === SlideItemPlayStatus.Play) {
          pause()
        } else {
          play()
        }
      }
    }
    if (type === EVENT_KEY.ITEM_STOP) {
      // 保存当前播放位置 (会话缓存 + localStorage/后端双持久化)
      const pos = videoEl.currentTime
      if (pos > 1 && videoId.value) {
        sessionPositions.set(videoId.value, pos)
        const loggedIn = !!store.userinfo?.uid
        persistPosition(videoId.value, pos, loggedIn)
      }
      videoEl.currentTime = 0
      positionResumed = false
      state.ignoreWaiting = true
      pause()
      setTimeout(() => (state.ignoreWaiting = false), 300)
    }
    if (type === EVENT_KEY.ITEM_PLAY) {
      state.ignoreWaiting = true
      // 断点续播: 1) 会话缓存(瞬时) → 2) localStorage/后端(跨session)
      const savedPos = videoId.value ? sessionPositions.get(videoId.value) : undefined
      if (savedPos && savedPos > 1 && savedPos < videoEl.duration - 2) {
        videoEl.currentTime = savedPos
        positionResumed = true
      } else {
        videoEl.currentTime = 0
        resumeFromStore()
      }
      play()
      setTimeout(() => (state.ignoreWaiting = false), 300)
    }
  }
}

function play() {
  state.status = SlideItemPlayStatus.Play
  videoEl.volume = 1
  videoEl.play()
}

function pause() {
  state.status = SlideItemPlayStatus.Pause
  videoEl.pause()
}

/** 跨 session 断点恢复: 已登录→后端, 未登录→localStorage */
async function resumeFromStore() {
  if (!videoId.value) return
  const loggedIn = !!store.userinfo?.uid
  const pos = await getResumePosition(videoId.value, loggedIn)
  if (pos > 1 && pos < videoEl.duration - 2) {
    videoEl.currentTime = pos
    sessionPositions.set(videoId.value, pos)
    positionResumed = true
  }
}

/** Buffer 卡顿自动恢复 */
function tryRecoverFromStall() {
  if (!videoEl || videoEl.paused || videoEl.ended) return
  const now = Date.now()
  // 卡顿超过 5 秒触发恢复
  if (waitingSince > 0 && now - waitingSince > 5000) {
    waitingSince = 0
    const cur = videoEl.currentTime
    // 往后跳 0.1 秒, 触发新的 Range 请求 (有时卡在某字节位置)
    videoEl.currentTime = Math.min(cur + 0.1, videoEl.duration || Infinity)
    videoEl.play().catch(() => {})
  }
}

function touchstart(e) {
  _stopPropagation(e)
  state.start.x = e.touches[0].pageX
  state.last.x = state.playX
  state.last.time = state.currentTime
}

function touchmove(e) {
  // console.log('move',e)
  _stopPropagation(e)
  state.isMove = true
  pause()
  let dx = e.touches[0].pageX - state.start.x
  state.playX = state.last.x + dx
  state.currentTime = state.last.time + Math.ceil(Math.ceil(dx) / state.step)
  if (state.currentTime <= 0) state.currentTime = 0
  if (state.currentTime >= state.duration) state.currentTime = state.duration
}

function touchend(e) {
  // console.log('end', e)
  _stopPropagation(e)
  if (isPlaying) return
  setTimeout(() => (state.isMove = false), 1000)
  videoEl.currentTime = state.currentTime
  play()
}
</script>

<style scoped lang="less">
.video-wrapper {
  position: relative;
  font-size: 14rem;
  width: 100%;
  height: 100%;
  text-align: center;

  video {
    max-width: 100%;
    height: 100%;
    transition:
      height,
      margin-top 0.3s;
    //background: black;
    /*position: absolute;*/
  }

  .float {
    position: absolute;
    left: 0;
    top: 0;
    height: 100%;
    width: 100%;

    .normal {
      position: absolute;
      bottom: 0;
      width: 100%;
      transition: all 0.3s;

      .loveds {
        position: absolute;
        bottom: 0;
        left: 15rem;

        .type-loved {
          width: 40px;
          height: 40px;
          position: relative;
          margin-bottom: 20px;
          animation: loveFloat 1s ease-out forwards;

          .avatar {
            width: 36px;
            height: 36px;
            border-radius: 50%;
          }

          .loved {
            position: absolute;
            bottom: 0;
            left: 20px;
            width: 10px;
            height: 10px;
            background: red;
            padding: 3px;
            border-radius: 50%;
            border: 2px solid white;
          }
        }

        @keyframes loveFloat {
          from {
            opacity: 1;
            transform: translate3d(0, 0, 0) scale(1);
          }
          to {
            opacity: 0;
            transform: translate3d(0, -80px, 0) scale(0.5);
          }
        }
      }
    }

    .progress {
      z-index: 10;
      @w: 90%;
      position: absolute;
      bottom: -1rem;
      height: 10rem;
      left: calc((100% - @w) / 2);
      width: @w;
      display: flex;
      align-items: flex-end;
      margin-bottom: 2rem;

      .time {
        position: absolute;
        z-index: 9;
        font-size: 24px;
        bottom: 50px;
        left: 0;
        right: 0;
        color: white;
        text-align: center;

        .duration {
          color: darkgray;
        }
      }

      @radius: 10rem;

      @h: 2rem;
      @tr: height 0.3s;

      .bg {
        transition: @tr;
        position: absolute;
        width: 100%;
        height: @h;
        background: #4f4f4f;
        border-radius: @radius;
      }

      @p: 50px;

      .progress-line {
        transition: @tr;
        height: calc(@h + 0.5rem);
        width: @p;
        border-radius: @radius 0 0 @radius;
        background: #777777;
        z-index: 1;
      }

      .point {
        transition: all 0.2s;
        width: @h+2;
        height: @h+2;
        border-radius: 50%;
        background: gray;
        z-index: 2;
        transform: translate(-1rem, 1rem);
      }
    }

    & .move {
      @h: 10rem;

      .bg {
        height: @h;
        background: var(--active-main-bg);
      }

      .progress-line {
        height: @h;
        background: var(--second-text-color);
      }

      .point {
        width: @h+2;
        height: @h+2;
        background: white;
      }
    }

    & .stop {
      @h: 4rem;

      .bg {
        height: @h;
      }

      .progress-line {
        height: @h;
        background: white;
      }

      .point {
        width: @h+2;
        height: @h+2;
        background: white;
      }
    }
  }
}

.living {
  position: absolute;
  left: 50%;
  font-size: 18rem;
  border-radius: 50rem;
  border: 1px solid #e0e0e0;
  padding: 15rem 20rem;
  line-height: 1;
  color: white;
  top: 70%;
  transform: translate(-50%, -50%);
}
</style>
