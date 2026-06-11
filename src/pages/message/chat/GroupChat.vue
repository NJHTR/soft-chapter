<template>
  <div id="GroupChat" class="group-chat">
    <div class="chat-content" @touchstart="data.tooltipTop = -1">
      <div class="header">
        <div class="left">
          <dy-back @click="router.back" />
          <GroupAvatar :avatars="data.memberAvatars" :size="30" class="header-avatar" />
          <span class="header-name">{{ data.groupName }}</span>
          <span class="member-count">({{ data.memberCount || data.members.length }})</span>
        </div>
        <div class="right">
          <img
            @click="startGroupCall(false)"
            src="../../../assets/img/icon/message/chat/call.png"
            alt=""
          />
          <img
            @click="startGroupCall(true)"
            src="../../../assets/img/icon/message/chat/video-white.png"
            alt=""
          />
          <img src="../../../assets/img/icon/menu-white.png" alt="" @click="goGroupDetail" />
        </div>
      </div>
      <div class="message-wrapper" ref="msgWrapper" @scroll="handleScroll">
        <ChatMessage
          :message="item"
          :key="item.backendId || 'temp_' + i"
          v-for="(item, i) in data.messages"
        />
        <div class="history-tip" v-if="!data.hasMore && data.messages.length">— 没有更多消息 —</div>
      </div>
      <div class="footer">
        <input
          ref="imageInput"
          type="file"
          accept="image/*"
          style="display: none"
          @change="handleImagePicked"
        />
        <div class="toolbar" v-if="!data.recording">
          <img
            @click="pickImage()"
            src="../../../assets/img/icon/message/camera.png"
            alt=""
            class="camera"
          />
          <input
            ref="inputRef"
            v-model="data.inputText"
            @keyup.enter="handleSend"
            type="text"
            placeholder="发送信息..."
          />
          <template v-if="data.inputText">
            <img
              @click="toggleEmoji"
              src="../../../assets/img/icon/message/emoji-white.png"
              alt=""
              class="emoji"
            />
            <div class="send-btn" @click="handleSend">
              <img src="../../../assets/img/icon/message/up.png" alt="" />
            </div>
          </template>
          <template v-else>
            <img
              @click="startVoice"
              src="../../../assets/img/icon/message/voice-white.png"
              alt=""
            />
            <img
              @click="toggleEmoji"
              src="../../../assets/img/icon/message/emoji-white.png"
              alt=""
            />
            <img
              @click="toggleOption"
              src="../../../assets/img/icon/message/add-white.png"
              alt=""
            />
          </template>
        </div>
        <div class="record" v-else>
          <div class="record-hint" :class="{ cancel: voiceCancelled }">
            <span v-if="!data.recordingActive">按住 说话</span>
            <span v-else-if="voiceCancelled">松开 取消</span>
            <span v-else>松开 发送</span>
          </div>
          <div
            class="record-bar"
            @touchstart.prevent="voiceStart"
            @touchmove.prevent="voiceMove"
            @touchend.prevent="voiceEnd"
            @mousedown.prevent="voiceStart"
            @mousemove.prevent="voiceMove"
            @mouseup.prevent="voiceEnd"
            @mouseleave.prevent="voiceEnd"
          >
            <div class="record-wave" :class="{ active: data.recordingActive }">
              <span
                class="bar"
                v-for="i in 6"
                :key="i"
                :style="{ animationDelay: i * 0.12 + 's' }"
              ></span>
            </div>
            <div class="record-duration" v-if="data.recordingActive">{{ voiceDuration }}″</div>
            <img
              @click="cancelVoice"
              src="../../../assets/img/icon/message/keyboard.png"
              alt=""
              class="record-keyboard"
            />
          </div>
        </div>
        <div class="emoji-panel" v-if="data.showEmoji">
          <span class="emoji-item" v-for="e in emojiList" :key="e" @click="insertEmoji(e)">{{
            e
          }}</span>
        </div>
        <div class="options" v-if="data.showOption">
          <div class="option-wrapper">
            <div class="option" @click="pickImage()">
              <img src="../../../assets/img/icon/message/photo.png" alt="" />
              <span>照片</span>
            </div>
            <div class="option" @click="pickImage()">
              <img src="../../../assets/img/icon/message/camera2.png" alt="" />
              <span>拍摄</span>
            </div>
            <div class="option">
              <img src="../../../assets/img/icon/message/redpack.png" alt="" />
              <span>红包</span>
            </div>
            <div class="option" @click="startOptionGroupCall(true)">
              <img src="../../../assets/img/icon/message/video.png" alt="" />
              <span>视频通话</span>
            </div>
            <div class="option" @click="startOptionGroupCall(false)">
              <img src="../../../assets/img/icon/message/audio.png" alt="" />
              <span>语音通话</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import ChatMessage from '../components/ChatMessage.vue'
import { onMounted, onUnmounted, reactive, ref, computed, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useBaseStore } from '@/store/pinia'
import { _notice } from '@/utils'
import { useNav } from '@/utils/hooks/useNav'
import {
  getGroupInfo,
  getGroupMembers,
  getGroupMessageHistory,
  sendGroupMessage,
  markGroupRead
} from '@/api/message'
import { uploadImage } from '@/api/user'
import { connectSocket, onSocketMsg } from '@/utils/socket'
import bus, { EVENT_KEY } from '@/utils/bus'
import GroupAvatar from '@/components/GroupAvatar.vue'
import defaultGroupPng from '@/assets/img/icon/people-gray.png'

defineOptions({ name: 'GroupChat' })

// 前端消息类型（与 ChatMessage 组件对齐）
const MESSAGE_TYPE = {
  TEXT: 0,
  TIME: 1,
  VIDEO: 2,
  DOUYIN_VIDEO: 9,
  AUDIO: 3,
  IMAGE: 6
}

// 后端 msg_type → 前端 MESSAGE_TYPE
const MSG_TYPE_MAP: Record<number, number> = {
  1: MESSAGE_TYPE.TEXT,
  2: MESSAGE_TYPE.IMAGE,
  3: MESSAGE_TYPE.AUDIO,
  4: MESSAGE_TYPE.VIDEO,
  9: MESSAGE_TYPE.DOUYIN_VIDEO
}

const CHINESE_WEEKDAYS = ['日', '一', '二', '三', '四', '五', '六']
const TIME_GAP_THRESHOLD = 5 * 60 * 1000

const route = useRoute()
const router = useRouter()
const nav = useNav()
const store = useBaseStore()

const msgWrapper = ref(null)
const inputRef = ref(null)
const imageInput = ref(null)

const groupId = computed(() => {
  const id = Number(route.query.group_id)
  return isNaN(id) ? -1 : id
})

const data = reactive({
  groupName: (route.query.name as string) || '群聊',
  groupAvatar: (route.query.avatar as string) || '',
  memberCount: 0,
  memberAvatars: [] as string[],
  members: [] as any[],
  messages: [] as any[],
  inputText: '',
  sending: false,
  hasMore: true,
  tooltipTop: -1,
  recording: false,
  recordingActive: false,
  showOption: false,
  showEmoji: false
})

let unsubGroupMsg: (() => void) | null = null
let historyFirstLoad = true

const emojiList = [
  '😀',
  '😂',
  '🤣',
  '😍',
  '🥰',
  '😘',
  '😜',
  '😎',
  '🤩',
  '😊',
  '😏',
  '🥺',
  '😭',
  '😡',
  '🤬',
  '👍',
  '👎',
  '👏',
  '🙌',
  '💪',
  '🤝',
  '❤️',
  '💔',
  '🔥',
  '⭐',
  '🎉',
  '🎊',
  '🙏',
  '🤔',
  '🤗',
  '😱',
  '😴',
  '🤤',
  '🫡',
  '🫠',
  '😶',
  '🤐',
  '😮‍💨',
  '😵',
  '🥴',
  '🤧',
  '💩',
  '👻',
  '💀',
  '👀',
  '👋',
  '🫶',
  '🌹',
  '🌈',
  '🍉',
  '🎂',
  '🍺',
  '💰',
  '🔞'
]

let mediaRecorder: MediaRecorder | null = null
let audioChunks: BlobPart[] = []
let voiceStartTime = 0
let voiceTimer: ReturnType<typeof setInterval> | null = null
const voiceDuration = ref(0)
const voiceCancelled = ref(false)

function toggleEmoji() {
  data.showEmoji = !data.showEmoji
  data.showOption = false
}
function toggleOption() {
  data.showOption = !data.showOption
  data.showEmoji = false
}
function insertEmoji(emoji: string) {
  data.inputText += emoji
  data.showEmoji = false
}

onMounted(() => {
  if (groupId.value < 0) {
    router.back()
    return
  }
  loadGroupInfo()
  loadMembers()
  loadHistory()
  connectSocket()
  unsubGroupMsg = onSocketMsg('group_message', handleGroupSocketMsg)
})

onUnmounted(() => {
  if (unsubGroupMsg) {
    unsubGroupMsg()
    unsubGroupMsg = null
  }
  if (data.messages.length) {
    const lastReal = [...data.messages].reverse().find((m: any) => m.type !== MESSAGE_TYPE.TIME)
    if (lastReal && groupId.value > 0)
      markGroupRead(groupId.value, lastReal.backendId || 0).catch(() => {})
  }
  historyFirstLoad = true
})

function mapMsgToChatItem(msg: any) {
  const myUid = store.userinfo.uid
  const isMyMsg = msg.from_user_id === myUid
  const myAvatar = store.userinfo.avatar_168x168?.url_list?.[0] || defaultGroupPng
  const fromUserAvatar = msg.from_user?.avatar_168x168?.url_list?.[0] || defaultGroupPng

  const frontendType = MSG_TYPE_MAP[msg.msg_type] ?? MESSAGE_TYPE.TEXT

  let msgData: any = msg.content
  if (frontendType === MESSAGE_TYPE.AUDIO) {
    msgData = { url: msg.content, duration: Number(msg.extra) || 0 }
  } else if (frontendType === MESSAGE_TYPE.DOUYIN_VIDEO) {
    try {
      msgData = JSON.parse(msg.content)
    } catch {
      msgData = {}
    }
  }

  return {
    type: frontendType,
    data: msgData,
    time: msg.create_time,
    backendId: msg.id,
    user: {
      id: isMyMsg ? myUid : msg.from_user_id || 0,
      avatar: isMyMsg ? myAvatar : fromUserAvatar || defaultGroupPng
    }
  }
}

function formatChatTime(timeStr: string): string {
  if (!timeStr) return ''
  const d = new Date(timeStr)
  const now = new Date()
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  const time = `${hh}:${mm}`

  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const msgDay = new Date(d.getFullYear(), d.getMonth(), d.getDate())
  const dayDiff = Math.floor((today.getTime() - msgDay.getTime()) / 86400000)

  if (dayDiff === 0) return time
  if (dayDiff === 1) return `昨天 ${time}`
  if (dayDiff < 7) return `周${CHINESE_WEEKDAYS[d.getDay()]} ${time}`

  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  if (d.getFullYear() === now.getFullYear()) return `${month}/${day} ${time}`
  return `${d.getFullYear()}/${month}/${day} ${time}`
}

function insertTimeDividers(msgs: any[]) {
  const realMessages = msgs.filter((m: any) => m.type !== MESSAGE_TYPE.TIME)
  const result: any[] = []

  for (let i = 0; i < realMessages.length; i++) {
    const msg = realMessages[i]
    const msgTime = new Date(msg.time).getTime()

    if (i === 0) {
      result.push({ type: MESSAGE_TYPE.TIME, time: formatChatTime(msg.time), user: {} })
    } else {
      const prevTime = new Date(realMessages[i - 1].time).getTime()
      if (msgTime - prevTime > TIME_GAP_THRESHOLD) {
        result.push({ type: MESSAGE_TYPE.TIME, time: formatChatTime(msg.time), user: {} })
      }
    }
    result.push(msg)
  }
  return result
}

async function loadGroupInfo() {
  try {
    const res = await getGroupInfo(groupId.value)
    if (res.success && res.data) {
      data.groupName = res.data.name || data.groupName
      data.groupAvatar = res.data.avatar || data.groupAvatar
      data.memberCount = res.data.member_count
      data.memberAvatars = res.data.member_avatars || []
    }
  } catch {
    /* ignore */
  }
}

async function loadMembers() {
  try {
    const res = await getGroupMembers(groupId.value)
    if (res.success && res.data) {
      data.members = res.data || []
    }
  } catch {
    /* ignore */
  }
}

async function loadHistory(beforeId?: number) {
  try {
    const res = await getGroupMessageHistory(groupId.value, {
      pageSize: 30,
      beforeId: beforeId || undefined
    })
    if (res.success && res.data) {
      const msgs = (res.data as any[]).map(mapMsgToChatItem) || []

      if (historyFirstLoad) {
        data.messages = insertTimeDividers(msgs)
        historyFirstLoad = false
        scrollToBottom()
        if (msgs.length) {
          const lastReal = [...msgs].reverse().find((m: any) => m.type !== MESSAGE_TYPE.TIME)
          if (lastReal && groupId.value > 0)
            markGroupRead(groupId.value, lastReal.backendId).catch(() => {})
        }
      } else {
        if (msgs.length < 30) data.hasMore = false
        const merged = [...msgs, ...data.messages]
        data.messages = insertTimeDividers(merged)
      }
    } else if (beforeId) {
      data.hasMore = false
    }
  } catch {
    /* ignore */
  }
}

function scrollToBottom() {
  nextTick(() => {
    const el = msgWrapper.value as HTMLElement | null
    if (el) el.scrollTop = el.scrollHeight
  })
}

function handleScroll() {
  const el = msgWrapper.value as HTMLElement | null
  if (!el || !data.hasMore) return
  if (el.scrollTop <= 20) {
    const oldestReal = data.messages.find((m: any) => m.type !== MESSAGE_TYPE.TIME)
    if (oldestReal?.backendId) loadHistory(oldestReal.backendId)
  }
}

async function handleSend() {
  const text = data.inputText.trim()
  if (!text || data.sending) return
  data.sending = true
  data.inputText = ''
  try {
    const res = await sendGroupMessage(groupId.value, { content: text, msg_type: 1 })
    if (res.success && res.data) {
      addLocalMessage(res.data)
    }
  } catch {
    /* ignore */
  } finally {
    data.sending = false
  }
}

function addLocalMessage(msg: any) {
  if (msg.id && data.messages.some((m: any) => m.backendId === msg.id)) return
  const item = mapMsgToChatItem(msg)
  const filtered = data.messages.filter((m: any) => m.type !== MESSAGE_TYPE.TIME)
  filtered.push(item)
  data.messages = insertTimeDividers(filtered)
  scrollToBottom()
}

function handleGroupSocketMsg(msg: any) {
  if (msg.group_id !== groupId.value) {
    if (msg.from_user_id !== store.userinfo.uid) {
      _notice({
        title: msg.from_user?.nickname || '群消息',
        content: msg.content || '',
        avatar: msg.from_user?.avatar_168x168?.url_list?.[0] || '',
        msgType: msg.msg_type
      })
    }
    return
  }
  addLocalMessage(msg)
  if (groupId.value > 0) markGroupRead(groupId.value, msg.id).catch(() => {})
}

function goGroupDetail() {
  nav('/message/group-chat/detail', {
    group_id: groupId.value,
    name: data.groupName,
    avatar: data.groupAvatar
  })
}

function startGroupCall(isVideo: boolean) {
  // 群通话：呼叫所有非己成员
  const myUid = store.userinfo.uid
  const targets = data.members
    .filter((m) => m.user_id !== myUid)
    .map((m) => ({
      userId: String(m.user_id),
      userName: m.user_nickname || m.user?.nickname || '群成员',
      avatar: m.user_avatar || m.user?.avatar_168x168?.url_list?.[0] || ''
    }))
  if (!targets.length) return
  bus.emit('SHOW_GROUP_CALL', {
    targets,
    isVideo,
    roomName: data.groupName
  })
}

function startOptionGroupCall(isVideo: boolean) {
  data.showOption = false
  startGroupCall(isVideo)
}

function pickImage() {
  data.showOption = false(imageInput.value as HTMLInputElement | null)?.click()
}

async function handleImagePicked(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  try {
    data.sending = true
    const upRes = await uploadImage(file)
    if (upRes.success && upRes.data?.url) {
      const res = await sendGroupMessage(groupId.value, {
        content: upRes.data.url,
        msg_type: 2,
        extra: upRes.data.url
      })
      if (res.success && res.data) {
        addLocalMessage(res.data)
      }
    }
  } catch {
    /* ignore */
  } finally {
    data.sending = false
    if (imageInput.value) (imageInput.value as HTMLInputElement).value = ''
  }
}

function startVoice() {
  data.recording = true
  voiceDuration.value = 0
  voiceCancelled.value = false
}

function cancelVoice() {
  if (voiceTimer) {
    clearInterval(voiceTimer)
    voiceTimer = null
  }
  if (mediaRecorder && mediaRecorder.state === 'recording') {
    mediaRecorder.stop()
    audioChunks = []
  }
  data.recording = false
  data.recordingActive = false
  voiceCancelled.value = false
}

function voiceStart() {
  if (!data.recording) return
  data.recordingActive = true
  voiceCancelled.value = false
  audioChunks = []
  voiceStartTime = Date.now()
  voiceDuration.value = 0
  voiceTimer = setInterval(() => {
    voiceDuration.value = Math.round((Date.now() - voiceStartTime) / 1000)
  }, 200)
  navigator.mediaDevices
    .getUserMedia({ audio: true })
    .then((stream) => {
      mediaRecorder = new MediaRecorder(stream, { mimeType: 'audio/webm' })
      mediaRecorder.ondataavailable = (e) => {
        if (e.data.size > 0) audioChunks.push(e.data)
      }
      mediaRecorder.onstop = async () => {
        if (voiceTimer) {
          clearInterval(voiceTimer)
          voiceTimer = null
        }
        stream.getTracks().forEach((t) => t.stop())
        if (audioChunks.length === 0 || voiceCancelled.value) return
        const duration = Math.max(1, Math.round((Date.now() - voiceStartTime) / 1000))
        const blob = new Blob(audioChunks, { type: 'audio/webm' })
        const formData = new FormData()
        formData.append('file', blob, 'voice.webm')
        try {
          const resp = await fetch('/api/upload/image', {
            method: 'POST',
            body: formData,
            headers: { Authorization: 'Bearer ' + store.token }
          })
          const json = await resp.json()
          if (json.code === 200 && json.data?.url) {
            const res = await sendGroupMessage(groupId.value, {
              content: json.data.url,
              msg_type: 3,
              extra: String(duration)
            })
            if (res.success && res.data) addLocalMessage(res.data)
          } else {
            _notice('语音上传失败')
          }
        } catch {
          _notice('语音发送失败')
        }
      }
      mediaRecorder.start()
    })
    .catch(() => {
      _notice('无法访问麦克风')
      data.recording = false
      data.recordingActive = false
    })
}

let _voiceStartY = 0
function voiceMove(e: TouchEvent | MouseEvent) {
  if (!data.recordingActive) return
  const y = 'touches' in e ? e.touches[0].clientY : e.clientY
  if (!_voiceStartY) _voiceStartY = y
  voiceCancelled.value = _voiceStartY - y > 60
}

function voiceEnd() {
  _voiceStartY = 0
  data.recordingActive = false
  if (mediaRecorder && mediaRecorder.state === 'recording') {
    mediaRecorder.stop()
  }
  setTimeout(() => {
    data.recording = false
    voiceCancelled.value = false
  }, 200)
}
</script>

<style scoped lang="less">
@import '../../../assets/less/index';

.group-chat {
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
    height: 100%;
    display: flex;
    flex-direction: column;

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
      flex-shrink: 0;

      .left {
        display: flex;
        align-items: center;
        overflow: hidden;
        flex: 1;
        min-width: 0;

        .header-avatar {
          width: 30rem;
          height: 30rem;
          border-radius: 50%;
          object-fit: cover;
          margin-right: 8rem;
          flex-shrink: 0;
        }

        .header-name {
          font-size: 16rem;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
          max-width: 50%;
        }

        .member-count {
          font-size: 13rem;
          color: var(--second-text-color);
          margin-left: 4rem;
          flex-shrink: 0;
        }
      }

      .right {
        display: flex;
        flex-shrink: 0;

        img {
          height: 20rem;
          margin: 0 10rem;
        }
      }
    }

    .message-wrapper {
      flex: 1;
      overflow-y: auto;
      padding-top: 10rem;

      .history-tip {
        text-align: center;
        padding: 12rem;
        font-size: 12rem;
        color: var(--second-text-color);
      }
    }

    .footer {
      flex-shrink: 0;
      padding: 10rem 0;
      @normal-bg-color: rgb(57, 57, 57);

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
          color: white;
          font-size: 14rem;
          margin-left: 10rem;

          &::placeholder {
            color: var(--second-text-color);
          }
        }

        .camera {
          margin-left: 0;
          margin-right: 5rem;
          width: 14rem;
          padding: 5rem;
          border-radius: 50%;
          background: rgb(105, 143, 244);
        }

        .send-btn {
          width: 28rem;
          height: 28rem;
          border-radius: 50%;
          background: #fe2c55;
          display: flex;
          align-items: center;
          justify-content: center;
          cursor: pointer;
          flex-shrink: 0;
          margin-left: 12rem;

          img {
            width: 16rem;
            height: 16rem;
            margin-left: 0;
          }
        }

        .emoji {
          margin-left: 10rem !important;
        }
      }

      .record {
        display: flex;
        flex-direction: column;
        align-items: center;
        user-select: none;

        .record-hint {
          height: 36rem;
          display: flex;
          align-items: center;
          justify-content: center;
          font-size: 13rem;
          color: var(--second-text-color);
          &.cancel {
            color: #fe2c55;
          }
        }

        .record-bar {
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
          cursor: pointer;
          width: calc(100% - 20rem);

          .record-keyboard {
            right: 5rem;
            position: absolute;
            width: 24rem;
            border-radius: 50%;
            margin-left: 15rem;
          }

          .record-duration {
            font-size: 14rem;
            color: white;
            margin-left: 8rem;
          }
        }

        .record-wave {
          display: flex;
          align-items: flex-end;
          gap: 2rem;
          height: 20rem;

          .bar {
            width: 2.5rem;
            border-radius: 2rem;
            background: rgba(255, 255, 255, 0.3);
            height: 6rem;
            transition: background 0.2s;
          }

          &.active .bar {
            background: #fe2c55;
            animation: g-wave 0.6s ease-in-out infinite alternate;
            &:nth-child(1) {
              height: 10rem;
            }
            &:nth-child(2) {
              height: 16rem;
            }
            &:nth-child(3) {
              height: 20rem;
            }
            &:nth-child(4) {
              height: 16rem;
            }
            &:nth-child(5) {
              height: 10rem;
            }
            &:nth-child(6) {
              height: 6rem;
            }
          }
        }

        @keyframes g-wave {
          0% {
            transform: scaleY(0.4);
          }
          100% {
            transform: scaleY(1);
          }
        }
      }

      .emoji-panel {
        height: calc(var(--vh, 1vh) * 30);
        overflow-y: auto;
        padding: 10rem;
        display: flex;
        flex-wrap: wrap;
        align-content: flex-start;

        .emoji-item {
          width: calc(100vw / 8 - 8rem);
          height: calc(100vw / 8 - 8rem);
          display: flex;
          align-items: center;
          justify-content: center;
          font-size: 24rem;
          cursor: pointer;
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
          color: gray;
          display: grid;
          grid-template-columns: repeat(4, calc((100vw - 30rem) / 4));

          .option {
            display: flex;
            justify-content: center;
            align-items: center;
            flex-direction: column;
            padding: 10rem 0;
            cursor: pointer;

            img {
              width: 44rem;
              height: 44rem;
              border-radius: 12rem;
              background: @normal-bg-color;
              padding: 10rem;
              margin-bottom: 6rem;
            }

            span {
              font-size: 12rem;
              color: var(--second-text-color);
            }
          }
        }
      }
    }
  }
}
</style>
