<template>
  <div class="LivePage" ref="pageRef">
    <!-- 视频画布 -->
    <canvas ref="videoCanvas" class="video-canvas" @click="sendLike"></canvas>

    <!-- 浮动层 -->
    <div class="float">
      <div class="top">
        <div class="left">
          <div class="liver">
            <img class="avatar" :src="hostAvatar" alt="" />
            <div class="desc">
              <div class="desc-wrapper">
                <div class="name">{{ host?.nickname || '主播' }}</div>
                <div class="count">{{ likeCount }} 点赞</div>
              </div>
              <div class="follow-btn" @click.stop="toggleFollow">
                {{ isFollowing ? '已关注' : '关注' }}
              </div>
            </div>
          </div>
          <div class="left-bottom">
            <div class="tag">
              <img src="../../assets/img/icon/home/jin.webp" alt="" />
              <span>{{ host?.nickname || '直播' }}</span>
            </div>
            <div class="tag rank">
              <img src="../../assets/img/icon/home/rank-yellow.png" alt="" />
              <span>直播中</span>
            </div>
          </div>
        </div>
        <div class="right">
          <div class="follower">
            <div class="round count">{{ viewerCount }}</div>
            <div class="round close" @click="$router.back()">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="3">
                <line x1="18" y1="6" x2="6" y2="18" />
                <line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </div>
          </div>
          <div class="more">
            <div class="wrapper">
              <span>更多直播</span>
              <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="3">
                <polyline points="9 18 15 12 9 6" />
              </svg>
            </div>
          </div>
        </div>
      </div>

      <!-- 弹幕漂浮 -->
      <div class="barrage-layer">
        <div
          v-for="b in floatingBarrages"
          :key="b._key"
          class="barrage"
          :style="{ top: b.top + 'px' }"
        >
          <span class="type">{{ b.nickname }}</span>
          <span class="text">{{ b.text }}</span>
        </div>
      </div>

      <!-- 用户加入提示 -->
      <div class="join-layer">
        <transition-group name="join">
          <div
            v-for="j in joinNotifications"
            :key="j._key"
            class="user-joined"
          >
            <span class="name">{{ j.nickname }}</span>
            <span class="text">加入了直播间</span>
          </div>
        </transition-group>
      </div>

      <!-- 底部区域 -->
      <div class="bottom">
        <div class="left">
          <div class="comments" ref="commentsRef">
            <div class="comments-wrapper">
              <div class="comment notice">
                <span class="text"
                  >欢迎来到直播间！SeekFlow严禁未成年人直播或打赏，直播间内严禁出现违法违规、低俗色情、吸烟酗酒等内容。请大家注意财产安全，谨防网络诈骗。</span
                >
              </div>
              <div class="comment" v-for="(m, j) in visibleMessages" :key="m._key">
                <span class="name">{{ m.nickname }}</span>
                <span class="text">{{ m.text }}</span>
              </div>
            </div>
          </div>
          <div class="options">
            <div class="input" @click="focusInput">
              <span>{{ chatText || '说点什么' }}</span>
              <img src="../../assets/img/icon/home/voice.png" alt="" />
            </div>
            <img
              src="../../assets/img/icon/home/love.webp"
              alt=""
              class="more"
              @click="sendLike"
            />
            <img
              src="../../assets/img/icon/home/gift.webp"
              alt=""
              class="gift"
            />
          </div>
        </div>
        <div class="right">
          <div class="avatar-wrapper" :class="{ followed: isFollowing }">
            <img :src="hostAvatar" alt="" class="avatar" />
            <div v-if="!isFollowing" @click.stop="toggleFollow" class="options" ref="attentionOption">
              <img class="no" src="../../assets/img/icon/add-light.png" alt="" />
              <img class="yes" src="../../assets/img/icon/ok-white.png" alt="" />
            </div>
            <img
              v-if="isFollowing"
              src="../../assets/img/icon/home/followed.webp"
              alt=""
              class="follow-img"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- 聊天输入框 -->
    <div v-if="showInput" class="chat-full-input">
      <input
        ref="chatInputRef"
        v-model="chatText"
        class="text-input"
        placeholder="发个弹幕吧..."
        @keyup.enter="sendChat"
      />
      <button class="send-btn" @click="sendChat">发送</button>
    </div>

    <!-- 飘心 -->
    <div class="heart-float-area">
      <transition-group name="float-heart">
        <span
          v-for="h in floatingHearts"
          :key="h.id"
          class="float-heart"
          :style="{ left: h.x + 'px' }"
        >❤️</span>
      </transition-group>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { getFeaturedLive, joinLive, leaveLive, likeLive } from '@/api/live'
import { toggleFollowUser } from '@/api/user'
import { useBaseStore } from '@/store/pinia'
import { _checkImgUrl } from '@/utils'
import defaultAvatarPng from '@/assets/img/icon/people-gray.png'

const store = useBaseStore()

// ---- 直播间状态 ----
const roomId = ref<number>(0)
const host = ref<any>(null)
const viewerCount = ref(0)
const likeCount = ref(0)
const isFollowing = ref(false)
const chatMessages = ref<{ nickname: string; text: string; _key: number }[]>([])
const chatText = ref('')
const showInput = ref(false)
const floatingHearts = ref<{ id: number; x: number }[]>([])
const floatingBarrages = ref<{ nickname: string; text: string; top: number; _key: number }[]>([])
const joinNotifications = ref<{ nickname: string; _key: number }[]>([])

let heartId = 0
let msgKey = 0
let barrageKey = 0

const videoCanvas = ref<HTMLCanvasElement>()
const commentsRef = ref<HTMLDivElement>()
const chatInputRef = ref<HTMLInputElement>()
const attentionOption = ref<HTMLDivElement>()

let liveWs: WebSocket | null = null
let canvasCtx: CanvasRenderingContext2D | null = null
let wsReconnectTimer: ReturnType<typeof setTimeout> | null = null
let wsStopped = false
let reconnectAttempts = 0

const hostAvatar = computed(() =>
  _checkImgUrl(host.value?.avatar_168x168?.url_list?.[0]) ||
  _checkImgUrl(host.value?.avatar) ||
  defaultAvatarPng
)

const visibleMessages = computed(() => chatMessages.value.slice(-20))

// ---- 生命周期 ----
onMounted(async () => {
  if (videoCanvas.value) {
    canvasCtx = videoCanvas.value.getContext('2d')
    resizeCanvas()
    window.addEventListener('resize', resizeCanvas)
  }

  try {
    const res: any = await getFeaturedLive()
    if (res.success && res.data) {
      roomId.value = res.data.id
      host.value = res.data.host
      viewerCount.value = res.data.viewerCount || 0
      likeCount.value = res.data.likeCount || 0
      await joinLive(roomId.value)
      connectWs()
    }
  } catch {
    /* no live available */
  }
})

onBeforeUnmount(() => {
  wsStopped = true
  if (wsReconnectTimer) { clearTimeout(wsReconnectTimer); wsReconnectTimer = null }
  if (liveWs) {
    liveWs.onclose = null
    try { liveWs.close() } catch { /* ignore */ }
  }
  if (roomId.value) leaveLive(roomId.value).catch(() => {})
  window.removeEventListener('resize', resizeCanvas)
})

// ---- Canvas ----
function resizeCanvas() {
  if (!videoCanvas.value) return
  videoCanvas.value.width = window.innerWidth
  videoCanvas.value.height = window.innerHeight
}

// ---- WebSocket ----
function connectWs() {
  if (wsReconnectTimer) { clearTimeout(wsReconnectTimer); wsReconnectTimer = null }
  if (liveWs) {
    liveWs.onopen = null; liveWs.onmessage = null; liveWs.onerror = null; liveWs.onclose = null
    try { liveWs.close() } catch { /* ignore */ }
  }

  const token = localStorage.getItem('token') || ''
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  liveWs = new WebSocket(`${protocol}//${location.host}/ws/live/${roomId.value}?role=viewer&token=${token}`)

  liveWs.onopen = () => {
    reconnectAttempts = 0
  }

  liveWs.onmessage = (e) => {
    try {
      const msg = JSON.parse(e.data)
      switch (msg.type) {
        case 'frame':
          renderFrame(msg.data)
          break
        case 'chat':
          chatMessages.value.push({ nickname: msg.nickname || '观众', text: msg.text, _key: ++msgKey })
          if (chatMessages.value.length > 200) chatMessages.value.splice(0, 100)
          addFloatingBarrage(msg.nickname || '观众', msg.text)
          nextTick(scrollComments)
          break
        case 'like':
          likeCount.value += msg.count || 1
          break
        case 'viewer_count':
          viewerCount.value = msg.count
          break
        case 'end':
          chatMessages.value.push({ nickname: '系统', text: '直播已结束', _key: ++msgKey })
          break
      }
    } catch { /* ignore */ }
  }

  liveWs.onclose = (ev) => {
    if (wsStopped) return
    if (reconnectAttempts < 10) {
      reconnectAttempts++
      wsReconnectTimer = setTimeout(connectWs, 3000)
    }
  }
}

function renderFrame(dataUrl: string) {
  if (!canvasCtx || !videoCanvas.value) return
  const img = new Image()
  img.onload = () => canvasCtx!.drawImage(img, 0, 0, videoCanvas.value!.width, videoCanvas.value!.height)
  img.src = dataUrl
}

// ---- 弹幕漂浮 ----
function addFloatingBarrage(nickname: string, text: string) {
  const top = 150 + Math.random() * 180
  floatingBarrages.value.push({ nickname, text, top, _key: ++barrageKey })
  setTimeout(() => {
    floatingBarrages.value = floatingBarrages.value.filter(b => b._key !== barrageKey)
  }, 6000)
  if (floatingBarrages.value.length > 15) floatingBarrages.value.shift()
}

// ---- 聊天 ----
function focusInput() {
  showInput.value = true
  nextTick(() => chatInputRef.value?.focus())
}

function sendChat() {
  if (!chatText.value.trim() || !liveWs || liveWs.readyState !== WebSocket.OPEN) return
  liveWs.send(JSON.stringify({
    type: 'chat',
    nickname: store.userinfo?.nickname || '观众',
    text: chatText.value.trim()
  }))
  chatText.value = ''
  showInput.value = false
}

function scrollComments() {
  const el = commentsRef.value
  if (el) el.scrollTop = el.scrollHeight
}

// ---- 点赞 ----
function sendLike() {
  if (liveWs && liveWs.readyState === WebSocket.OPEN) {
    liveWs.send(JSON.stringify({ type: 'like', count: 1 }))
  }
  if (roomId.value) likeLive(roomId.value).catch(() => {})
  likeCount.value++
  const h = { id: ++heartId, x: Math.random() * 150 + 20 }
  floatingHearts.value.push(h)
  setTimeout(() => {
    floatingHearts.value = floatingHearts.value.filter(v => v.id !== h.id)
  }, 1200)
}

// ---- 关注 ----
async function toggleFollow() {
  if (!host.value?.uid) return
  try {
    const res: any = await toggleFollowUser(host.value.uid)
    if (res.success) {
      isFollowing.value = res.data?.isAttention ?? !isFollowing.value
    }
  } catch { /* ignore */ }
}
</script>

<style lang="less" scoped>
@import '../../assets/less/index';

.LivePage {
  width: 100%;
  height: calc(var(--vh, 1vh) * 100);
  color: white;
  font-size: 14rem;
  position: relative;
  background: #000;
  overflow: hidden;
}

.video-canvas {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: #000;
  object-fit: contain;
}

.float {
  position: absolute;
  top: 0;
  width: 100%;
  height: calc(var(--vh, 1vh) * 100);
  pointer-events: none;
  z-index: 1;

  > * { pointer-events: auto; }

  @tag-bg: rgba(58, 58, 70, 0.3);

  .top {
    display: flex;
    justify-content: space-between;
    margin-top: max(10rem, env(safe-area-inset-top));

    .left {
      margin-left: var(--page-padding);

      .liver {
        box-sizing: border-box;
        background: var(--second-btn-color-tran);
        display: flex;
        padding: 3rem 4rem 3rem 2rem;
        align-items: center;
        border-radius: 20rem;

        .avatar {
          border-radius: 50%;
          width: 30rem;
          height: 30rem;
          margin-right: 4rem;
          object-fit: cover;
        }

        .desc {
          flex: 1;
          display: flex;
          align-items: center;
          justify-content: space-between;

          .desc-wrapper {
            width: 80rem;

            .name {
              font-size: 12rem;
              white-space: nowrap;
              overflow: hidden;
              text-overflow: ellipsis;
            }

            .count {
              color: gainsboro;
              font-size: 10rem;
            }
          }

          .follow-btn {
            height: 30rem;
            width: 45rem;
            background: var(--primary-btn-color);
            border-radius: 30rem;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 12rem;
            cursor: pointer;
          }
        }
      }

      .left-bottom {
        margin-top: calc(var(--page-padding) / 2);
        display: flex;
        font-size: 12rem;

        .tag {
          display: flex;
          align-items: center;
          padding: 4rem 10rem;
          background: @tag-bg;
          border-radius: 20rem;
          margin-right: 10rem;

          img {
            margin-right: 5rem;
            width: 10rem;
            height: 10rem;
          }
        }
      }
    }

    .right {
      margin-top: 3rem;
      display: flex;
      flex-direction: column;

      .follower {
        @width: 30rem;
        display: flex;

        .round {
          width: @width;
          height: @width;
          border-radius: 50%;
          margin-right: 3rem;
        }

        .count {
          font-size: 12rem;
          background: var(--second-btn-color-tran);
          display: flex;
          align-items: center;
          justify-content: center;
        }

        .close {
          margin-right: 10rem;
          margin-left: 5rem;
          padding: 6rem;
          width: calc(@width - 12rem);
          height: calc(@width - 12rem);
          background: var(--second-btn-color-tran);
          display: flex;
          align-items: center;
          justify-content: center;
          cursor: pointer;
        }
      }

      .more {
        display: flex;
        justify-content: flex-end;

        .wrapper {
          border-radius: 13rem 0 0 13rem;
          padding: 2rem 0 2rem 10rem;
          margin-top: 15rem;
          background: @tag-bg;
          display: flex;
          align-items: center;
          font-size: 10rem;
          gap: 4rem;
        }
      }
    }
  }

  .bottom {
    position: absolute;
    bottom: 0;
    width: 100%;
    box-sizing: border-box;
    padding: var(--page-padding);
    padding-bottom: 10rem;
    display: flex;

    .left {
      width: 87%;

      .comments {
        margin-bottom: 10rem;
        overflow: auto;
        height: 20vh;

        .comments-wrapper {
          min-height: 20vh;
          display: flex;
          flex-direction: column;
          justify-content: flex-end;
        }

        .comment {
          padding: 4rem 5rem;
          border-radius: 10rem;
          background: @tag-bg;
          margin-bottom: 5rem;

          @text-color: rgb(164, 234, 253);

          &.notice {
            .text { color: @text-color; }
          }

          .name {
            margin-right: 5rem;
            font-size: 13rem;
            color: @text-color;
          }

          .text {
            word-break: break-all;
            font-size: 12rem;
          }
        }
      }

      .options {
        display: flex;
        align-items: center;

        .input {
          flex: 1;
          color: #a2a2a2;
          font-size: 12rem;
          border-radius: 15rem;
          padding: 4rem 10rem;
          background: @tag-bg;
          display: flex;
          align-items: center;
          justify-content: space-between;

          img { width: 20rem; }
        }

        .more {
          margin-left: 10rem;
          width: 31rem;
          height: 31rem;
          cursor: pointer;
        }

        .gift {
          margin-left: 10rem;
          width: 31rem;
        }
      }
    }

    .right {
      flex: 1;
      display: flex;
      justify-content: flex-end;
      align-items: flex-end;

      @width: 35rem;

      .avatar-wrapper {
        background: linear-gradient(to bottom, #000000, var(--primary-btn-color));
        border-radius: 20rem;
        width: calc(@width + 2rem);
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;

        &.followed {
          background: linear-gradient(to bottom, rgba(240, 183, 31, 0.2), rgb(240, 183, 31));
        }

        .avatar {
          width: @width;
          height: @width;
          border-radius: 50%;
          background: white;
          padding: 1.5rem;
          object-fit: cover;
        }

        .follow-img {
          width: 32rem;
          margin-top: 5rem;
          margin-bottom: 5rem;
        }

        .options {
          margin-top: 8rem;
          margin-bottom: 5rem;
          display: flex;
          width: 20rem;
          height: 20rem;
          justify-content: center;
          align-items: center;

          img {
            position: absolute;
            width: 18rem;
            transition: all 0.8s;
          }

          .yes {
            opacity: 0;
            transform: rotate(-180deg);
          }

          &.attention {
            .no { opacity: 0; transform: rotate(180deg); }
            .yes { opacity: 1; transform: rotate(0deg); }
          }
        }
      }
    }
  }
}

// ============ 弹幕漂浮 ============
.barrage-layer {
  position: absolute;
  top: 0;
  right: 0;
  width: 70%;
  height: 55%;
  overflow: hidden;
  pointer-events: none;
}

.barrage {
  position: absolute;
  right: 0;
  display: flex;
  align-items: center;
  font-size: 12rem;
  white-space: nowrap;
  padding: 3rem 8rem;
  border-radius: 20rem;
  background: rgba(0, 0, 0, 0.35);
  animation: barrage-slide 5s linear forwards;

  .type {
    padding: 1rem 6rem;
    border: 1px solid rgba(255, 255, 255, 0.5);
    border-radius: 20rem;
    margin-right: 5rem;
    color: #ffd700;
  }

  .text {
    color: #fff;
  }
}

@keyframes barrage-slide {
  from { transform: translateX(100%); opacity: 1; }
  80% { opacity: 1; }
  to { transform: translateX(-120vw); opacity: 0; }
}

// ============ 加入提示 ============
.join-layer {
  position: absolute;
  top: 60%;
  left: 15rem;
}

.user-joined {
  font-size: 12rem;
  padding: 4rem 8rem;
  border-radius: 20rem;
  background: rgba(115, 114, 181, 0.7);
  margin-bottom: 5rem;
  animation: join-slide 3s linear;

  .name {
    margin-right: 5rem;
    font-size: 13rem;
    color: rgb(164, 234, 253);
  }
}

@keyframes join-slide {
  from { opacity: 0; transform: translateX(50%); }
  10% { opacity: 1; transform: translateX(0); }
  80% { opacity: 1; }
  to { opacity: 0; transform: translateX(-30%); }
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

    &::placeholder { color: rgba(255, 255, 255, 0.35); }
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
  }
}

// ============ 飘心 ============
.heart-float-area {
  position: absolute;
  right: 16rem;
  bottom: 160rem;
  z-index: 2;
  pointer-events: none;
}

.float-heart {
  position: absolute;
  font-size: 28rem;
  animation: floatUp 1.2s ease-out forwards;
  filter: drop-shadow(0 0 6rem rgba(254, 44, 85, 0.4));
}

@keyframes floatUp {
  0% { opacity: 1; transform: translateY(0) scale(0.4); }
  30% { opacity: 1; transform: translateY(-30rem) scale(1.2); }
  100% { opacity: 0; transform: translateY(-100rem) scale(0.7); }
}
</style>
