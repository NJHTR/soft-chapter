<template>
  <div class="Chat">
    <div class="chat-content" @touchstart="data.tooltipTop = -1">
      <div class="header">
        <div class="left">
          <dy-back @click="router.back"></dy-back>
          <span>{{ targetUserName }}</span>
        </div>
        <div class="right">
          <img
            @click="bus.emit(EVENT_KEY.SHOW_AUDIO_CALL)"
            src="../../../assets/img/icon/message/chat/call.png"
            alt=""
          />
          <img @click="_no" src="../../../assets/img/icon/message/chat/video-white.png" alt="" />
          <img
            src="../../../assets/img/icon/menu-white.png"
            alt=""
            @click="nav('/message/chat/detail')"
          />
        </div>
      </div>
      <div class="message-wrapper" ref="msgWrapper" :class="isExpand ? 'expand' : ''">
        <ChatMessage
          @itemClick="clickItem"
          v-longpress="showTooltip"
          :message="item"
          :key="index"
          v-for="(item, index) in data.messages"
        ></ChatMessage>
      </div>
      <div class="footer">
        <div class="toolbar" v-if="!data.recording">
          <img src="../../../assets/img/icon/message/camera.png" alt="" class="camera" />
          <input
            v-model="data.inputText"
            @keyup.enter="handleSend"
            type="text"
            placeholder="发送信息..."
          />
          <img @click="handleClick" src="../../../assets/img/icon/message/voice-white.png" alt="" />
          <img src="../../../assets/img/icon/message/emoji-white.png" alt="" />
          <img
            @click="data.showOption = !data.showOption"
            src="../../../assets/img/icon/message/add-white.png"
            alt=""
          />
        </div>
        <div class="record" v-else>
          <span>按住 说话</span>
          <img
            @click="data.recording = false"
            src="../../../assets/img/icon/message/keyboard.png"
            alt=""
          />
        </div>
        <div class="options" v-if="data.showOption">
          <div class="option-wrapper">
            <div class="option">
              <img src="../../../assets/img/icon/message/photo.png" alt="" />
              <span>照片</span>
            </div>
            <div class="option">
              <img src="../../../assets/img/icon/message/camera2.png" alt="" />
              <span>拍摄</span>
            </div>
            <div class="option">
              <img src="../../../assets/img/icon/message/redpack.png" alt="" />
              <span>红包</span>
            </div>
            <div class="option">
              <img src="../../../assets/img/icon/message/video.png" alt="" />
              <span>视频通话</span>
            </div>
            <div class="option">
              <img src="../../../assets/img/icon/message/audio.png" alt="" />
              <span>语音通话</span>
            </div>
            <div class="option">
              <img src="../../../assets/img/icon/message/come-on.png" alt="" />
              <span>一起看视频</span>
            </div>
            <div class="option">
              <img src="../../../assets/img/icon/message/come-chang.png" alt="" />
              <span>一起唱</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!--  长按工具栏  -->
    <transition name="tooltip">
      <!--      TODO 定位也有问题-->
      <div class="tooltip" :style="{ top: data.tooltipTop + 'px' }" v-if="data.tooltipTop !== -1">
        <div class="options">
          <img src="../../../assets/img/icon/message/chat/like.png" alt="" />
          <span>点赞</span>
        </div>
        <div class="options">
          <img src="../../../assets/img/icon/message/chat/copy.png" alt="" />
          <span>复制</span>
        </div>
        <div class="options">
          <img src="../../../assets/img/icon/message/chat/send.png" alt="" />
          <span>转发</span>
        </div>
        <div class="options">
          <img src="../../../assets/img/icon/message/chat/comment.png" alt="" />
          <span>回复</span>
        </div>
        <div class="options">
          <img src="../../../assets/img/icon/message/chat/return.png" alt="" />
          <span>回复</span>
        </div>
        <div class="options">
          <img src="../../../assets/img/icon/message/chat/del.png" alt="" />
          <span>删除</span>
        </div>
        <!--      TODO 官方的三角头会随着点击位置变动，先注释掉-->
        <!--      <div class="arrow" :class="tooltipTopLocation"></div>-->
      </div>
    </transition>

    <div class="preview-img" v-if="false">
      <div class="header">
        <dy-back mode="light" />
        <img src="../../../assets/img/icon/search-light.png" alt="" />
      </div>
      <img :src="data.previewImg" alt="" class="img-src" />
      <div class="footer"></div>
    </div>

    <!--  红包  -->
    <transition name="scale">
      <div class="red-packet" v-if="data.isShowOpenRedPacket">
        <BaseMask @click="data.isShowOpenRedPacket = false" />
        <div class="content">
          <template v-if="data.isOpened">
            <img src="../../../assets/img/icon/message/chat/bg-open.png" alt="" class="bg" />
            <div class="wrapper">
              <div class="top">
                <div class="money">0.01元</div>
                <div class="belong">{{ store.userinfo.nickname }}的红包</div>
                <div class="password">大吉大利</div>
              </div>
              <div class="notice" @click="nav('/message/chat/red-packet-detail')">
                查看红包详情>
              </div>
            </div>
          </template>
          <template v-else>
            <img src="../../../assets/img/icon/message/chat/bg-close.png" alt="" class="bg" />
            <div class="wrapper">
              <div class="top">
                <img
                  :src="_checkImgUrl(store.userinfo.cover_url[0].url_list[0])"
                  alt=""
                  class="avatar"
                />
                <div class="belong">{{ store.userinfo.nickname }}的红包</div>
                <div class="password">大吉大利</div>
              </div>

              <div class="l-button" :class="{ opening: data.opening }" @click="openRedPacket">
                <template v-if="data.opening">
                  <img src="../../../assets/img/icon/loading-white.png" alt="" />
                  正在打开
                </template>
                <span v-else>开红包</span>
              </div>
            </div>
          </template>
          <img
            src="../../../assets/img/icon/message/chat/close.png"
            alt=""
            class="close"
            @click="data.isShowOpenRedPacket = false"
          />
        </div>
      </div>
    </transition>

    <Loading v-if="data.loading" />
  </div>
</template>
<script setup lang="ts">
import ChatMessage from '../components/ChatMessage.vue'
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import Loading from '@/components/Loading.vue'
import { useBaseStore } from '@/store/pinia'
import { _checkImgUrl, _no, _sleep } from '@/utils'
import { useRouter, useRoute } from 'vue-router'
import { useNav } from '@/utils/hooks/useNav'
import bus, { EVENT_KEY } from '@/utils/bus'
import { getChatHistory, sendMessage } from '@/api/message'
import { connectSocket, disconnectSocket, onSocketMsg } from '@/utils/socket'

let CALL_STATE = {
  REJECT: 0,
  RESOLVE: 1,
  NONE: 2
}
let VIDEO_STATE = {
  VALID: 0,
  INVALID: 1
}
let AUDIO_STATE = {
  NORMAL: 0,
  SENDING: 1
}
let READ_STATE = {
  SENDING: 0,
  ARRIVED: 1,
  READ: 1
}
let MESSAGE_TYPE = {
  TEXT: 0,
  TIME: 1,
  VIDEO: 2,
  DOUYIN_VIDEO: 9,
  AUDIO: 3,
  IMAGE: 6,
  VIDEO_CALL: 4,
  AUDIO_CALL: 5,
  MEME: 7, //表情包
  RED_PACKET: 8 //红包
}
let RED_PACKET_MODE = {
  SINGLE: 1,
  MULTIPLE: 2
}

defineOptions({
  name: 'Chat'
})

const router = useRouter()
const route = useRoute()
const nav = useNav()
const store = useBaseStore()
const msgWrapper = ref<HTMLDivElement>()
const inputRef = ref<HTMLInputElement>()

// 聊天对象信息
const targetUserId = Number(route.query.user_id || '0')
const targetUserName = (route.query.name as string) || '聊天'
const targetUserAvatar = (route.query.avatar as string) || ''

const data = reactive({
  previewImg: new URL('../../../assets/img/poster/3.jpg', import.meta.url).href,
  videoCall: [],
  MESSAGE_TYPE,
  messages: [] as any[],
  typing: false,
  loading: false,
  opening: false,
  isOpened: false,
  recording: false,
  showOption: false,
  isShowOpenRedPacket: false,
  tooltipTop: -1,
  tooltipTopLocation: '',
  inputText: ''
})

let unsubChat: (() => void) | null = null

onMounted(async () => {
  await loadHistory()
  connectSocket()
  unsubChat = onSocketMsg('chat', handleWsMessage)
})

onUnmounted(() => {
  if (unsubChat) { unsubChat(); unsubChat = null }
})

const isExpand = computed(() => {
  return data.showOption
})

// 后端 msg_type → 前端 MESSAGE_TYPE 映射
const MSG_TYPE_MAP: Record<number, number> = {
  1: MESSAGE_TYPE.TEXT,
  2: MESSAGE_TYPE.IMAGE,
  3: MESSAGE_TYPE.AUDIO,
  4: MESSAGE_TYPE.VIDEO,
  5: MESSAGE_TYPE.RED_PACKET
}

// 映射后端消息到前端组件格式
function mapMsgToChatItem(msg: any) {
  const myUid = store.userinfo.uid
  return {
    type: MSG_TYPE_MAP[msg.msg_type] ?? MESSAGE_TYPE.TEXT,
    data: msg.content,
    time: msg.create_time,
    backendId: msg.id, // 用于去重
    user: {
      id: msg.from_user_id === myUid ? myUid : targetUserId,
      avatar: msg.from_user?.avatar_168x168?.url_list?.[0] || ''
    }
  }
}

async function loadHistory() {
  if (!targetUserId) return
  try {
    const res = await getChatHistory({ with_user_id: targetUserId })
    if (res.success && res.data) {
      data.messages = res.data.map(mapMsgToChatItem)
      scrollBottom()
    }
  } catch { /* ignore */ }
}

function handleWsMessage(msg: any) {
  // 去重：已经存在的消息不重复添加
  if (msg.id && data.messages.some(m => m.backendId === msg.id)) return
  // 检查是否是当前对话的消息
  if (msg.from_user_id === targetUserId || (msg.to_user_id === targetUserId && msg.from_user_id === store.userinfo.uid)) {
    data.messages.push(mapMsgToChatItem(msg))
    scrollBottom()
  }
}

async function handleSend() {
  const text = data.inputText?.trim()
  if (!text || !targetUserId) return

  // 通过 REST API 发送，后端会自动通过 WebSocket 推送给接收方
  try {
    const res = await sendMessage({ to_user_id: targetUserId, content: text })
    if (res.success && res.data) {
      data.messages.push(mapMsgToChatItem(res.data))
      data.inputText = ''
      scrollBottom()
    }
  } catch { /* ignore */ }
}

function handleClick() {
  data.recording = true
  data.showOption = false
}

function scrollBottom() {
  nextTick(() => {
    const wrapper = msgWrapper.value
    if (wrapper) {
      wrapper.scrollTo({ top: wrapper.scrollHeight - wrapper.clientHeight })
    }
  })
}

function openRedPacket() {
  data.opening = true
  setTimeout(() => {
    data.opening = false
    nav('/message/chat/red-packet-detail')
  }, 1000)
}

async function clickItem(e) {
  if (e.type === data.MESSAGE_TYPE.RED_PACKET) {
    data.loading = true
    await _sleep(500)
    data.loading = false
    data.isOpened = e.data.state === '已过期'
    data.isShowOpenRedPacket = true
  }
}

function showTooltip(e) {
  console.log(e)
  let wrapper = null
  e.path.map((v: any) => {
    if (v && v.classList) {
      if (v.classList.value === 'chat-wrapper') {
        wrapper = v
      }
    }
  })
  if (wrapper) {
    if (wrapper.getBoundingClientRect().y - 61 > 70) {
      data.tooltipTopLocation = 'top'
      data.tooltipTop = wrapper.getBoundingClientRect().y - 70
    } else {
      data.tooltipTopLocation = 'bottom'
      data.tooltipTop =
        wrapper.getBoundingClientRect().y + wrapper.getBoundingClientRect().height + 10
    }
  }
}
</script>

<style>
.scale-enter-active,
.scale-leave-active {
  transition: transform 0.2s ease;
}

.scale-enter-from,
.scale-leave-to {
  transform: scale(0);
}
</style>
<style scoped lang="less">
@import '../../../assets/less/index';

.Chat {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  top: 0;
  overflow: auto;
  color: white;
  font-size: 14rem;
  background: var(--color-message);

  .chat-content {
    > .header {
      z-index: 2;
      width: 100%;
      box-sizing: border-box;
      height: var(--common-header-height);
      padding: 0 10rem;
      display: flex;
      justify-content: space-between;
      align-items: center;
      border-bottom: 1px solid var(--line-color);

      img {
        height: 20rem;
        margin: 0 10rem;
      }

      .left {
        max-width: 60%;
        overflow: hidden;
        display: flex;
        align-items: center;

        .badge {
          margin-right: 10rem;
          font-size: 12rem;
          display: block;
          padding: 1px 6px;
          border-radius: 10px;
          color: white;
          background: var(--second-btn-color);
        }
      }

      .right {
        display: flex;
      }
    }

    .message-wrapper {
      height: calc(var(--vh, 1vh) * 100 - 125rem);
      overflow: auto;

      &.expand {
        height: calc(var(--vh, 1vh) * 100 - (125rem + var(--vh, 1vh) * 30));
      }
    }

    .footer {
      @chat-bg-color: rgb(105, 143, 244);
      @normal-bg-color: rgb(57, 57, 57);
      padding: 10rem 0;

      .toolbar {
        box-sizing: border-box;
        height: 44rem;
        margin: 0 10rem;
        padding: 5rem;
        background: @normal-bg-color;
        border-radius: 20rem;
        display: flex;
        align-items: center;

        img {
          width: 24rem;
          border-radius: 50%;
          margin-left: 15rem;
        }

        input {
          flex: 1;
          outline: none;
          border: none;
          background: @normal-bg-color;
        }

        .camera {
          margin-left: 0;
          margin-right: 5rem;
          width: 14rem;
          padding: 5rem;
          border-radius: 50%;
          background: @chat-bg-color;
        }
      }

      .record {
        box-sizing: border-box;
        height: 44rem;
        margin: 0 10rem;
        padding: 10rem 5rem;
        background: @normal-bg-color;
        border-radius: 20rem;
        display: flex;
        align-items: center;
        justify-content: center;
        position: relative;

        img {
          right: 5rem;
          position: absolute;
          width: 24rem;
          border-radius: 50%;
          margin-left: 15rem;
        }
      }

      .options {
        font-size: 14rem;
        width: 100vw;
        padding: 15rem;
        height: calc(var(--vh, 1vh) * 30);
        box-sizing: border-box;

        .option-wrapper {
          box-sizing: border-box;
          @grid-width: calc((100vw - 30rem) / 4);
          color: gray;
          display: grid;
          grid-template-columns: @grid-width @grid-width @grid-width @grid-width;

          .option {
            display: flex;
            justify-content: center;
            align-items: center;
            flex-direction: column;
            margin-bottom: 10rem;

            img {
              border-radius: 4rem;
              background: @normal-bg-color;
              padding: 10rem;
              width: 30rem;
              margin-bottom: 10rem;
            }
          }
        }
      }
    }
  }

  .preview-img {
    position: fixed;
    z-index: 9;
    top: 0;
    background: black;
    width: 100vw;
    height: calc(var(--vh, 1vh) * 100);

    .header {
      position: fixed;
      width: 100vw;
      box-sizing: border-box;
      padding: var(--page-padding);
      display: flex;
      justify-content: space-between;

      img {
        width: 22rem;
      }
    }
  }

  .tooltip {
    z-index: 9;
    left: 50%;
    margin-left: -33%;
    position: fixed;
    font-size: 12rem;
    border-radius: 6rem;
    //padding: 1rem;
    background: rgb(55, 58, 67);
    display: flex;

    .options {
      width: 45rem;
      height: 60rem;
      display: flex;
      justify-content: center;
      align-items: center;
      flex-direction: column;

      img {
        margin-bottom: 4rem;
        width: 18rem;
      }
    }

    .arrow {
      width: 0;
      height: 0;
      position: absolute;
      left: 50%;
      transform: translateX(-50%);

      &.bottom {
        top: -14rem;
        border: 7rem solid transparent;
        border-bottom: 7rem solid var(--second-btn-color);
      }

      &.top {
        bottom: -14rem;
        border: 7rem solid transparent;
        border-top: 7rem solid var(--second-btn-color);
      }
    }
  }

  .red-packet {
    z-index: 9;
    top: 0;
    position: fixed;
    width: 100vw;
    height: calc(var(--vh, 1vh) * 100);
    display: flex;
    align-items: center;
    justify-content: center;

    .content {
      width: 75vw;
      height: 55vh;
      z-index: 10;
      position: fixed;
      display: flex;
      align-items: center;
      flex-direction: column;

      .bg {
        z-index: 9;
        position: absolute;
        width: 100%;
        height: 100%;
      }

      .wrapper {
        width: 100%;
        height: 100%;
        box-sizing: border-box;
        padding: 20rem;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: space-between;
        color: #fdd9b3;
        z-index: 10;
        position: relative;

        .top {
          display: flex;
          flex-direction: column;
          align-items: center;
        }

        .avatar {
          margin-top: 60rem;
          width: 55rem;
          height: 55rem;
          border-radius: 50%;
          margin-bottom: 20rem;
        }

        .money {
          color: rgb(193, 135, 79);
          font-size: 40rem;
          font-weight: bold;
          margin-top: 15rem;
          margin-bottom: 65rem;
        }

        .belong {
          font-size: 12rem;
          margin-bottom: 30rem;
        }

        .password {
          font-size: 16rem;
        }

        .notice {
          margin-top: 150rem;
          font-size: 12rem;
        }

        .l-button {
          font-size: 16rem;
          border-radius: 0.5rem;
          margin-bottom: 30rem;
          padding: 12rem 0;
          display: flex;
          align-items: center;
          justify-content: center;
          color: rgb(120, 48, 45);
          width: 65vw;
          background: rgb(255, 217, 132);
          box-shadow: 0 0 1px;

          &.opening {
            background: rgb(228, 77, 58);

            img {
              width: 18rem;
              margin-right: 10rem;
              animation: animal 0.8s infinite linear;

              @keyframes animal {
                0% {
                  transform: rotate(-360deg);
                }
                100% {
                  transform: rotate(0deg);
                }
              }
            }
          }
        }
      }

      .close {
        bottom: -8vh;
        position: absolute;
        width: 30rem;
      }
    }
  }
}
</style>
