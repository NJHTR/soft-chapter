<template>
  <!-- ========== 来电通知卡片（被叫方） ========== -->
  <transition name="slide-down">
    <div class="incoming-call" v-if="incoming.show" @click.stop>
      <div class="incoming-header">
        <span>{{ incoming.isGroup ? '群通话' : incoming.isVideo ? '视频通话' : '语音通话' }}</span>
      </div>
      <div class="incoming-body">
        <img class="incoming-avatar" :src="incoming.avatar || defaultAvatar" alt="" />
        <span class="incoming-name">{{ incoming.name }}</span>
        <span class="incoming-hint" v-if="incoming.isGroup">
          与 {{ incoming.groupMembers?.join('、') || '' }} 等人
        </span>
      </div>
      <div class="incoming-actions">
        <div class="action-btn decline" @click="handleReject">
          <div class="btn-circle">
            <img src="@/assets/img/icon/message/chat/call-end.png" alt="" />
          </div>
          <span>挂断</span>
        </div>
        <div class="action-btn accept" @click="handleAccept">
          <div class="btn-circle">
            <img src="@/assets/img/icon/message/chat/call.png" alt="" />
          </div>
          <span>接听</span>
        </div>
      </div>
    </div>
  </transition>

  <!-- ========== 通话界面 ========== -->
  <transition name="scale">
    <div class="call-screen" v-if="state.isActive" :class="{ video: state.isVideo }">
      <!-- ═══ 视频模式 ═══ -->
      <template v-if="state.isVideo">
        <div class="video-top-bar">
          <span class="video-peer-name">{{ state.roomName }}</span>
          <span class="video-status">{{ statusText }}</span>
        </div>

        <!-- 参会者视频网格 (≤9人) -->
        <div class="video-grid" v-if="gridTotal <= 9" :style="gridContainerStyle">
          <!-- 我的视频 -->
          <div class="video-cell" :style="getVideoCellStyle(0)">
            <video
              v-if="!state.isCameraOff"
              class="cell-video"
              ref="localVideo"
              autoplay
              playsinline
              muted
            />
            <div v-else class="cell-off">
              <img :src="myAvatar" alt="" />
            </div>
            <div class="cell-label"><span>我</span></div>
            <div class="cell-mute-badge" v-if="state.isMuted">
              <svg
                class="mute-icon"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
              >
                <path d="M1 1l22 22" />
                <path d="M9 9v3a3 3 0 0 0 5.12 2.12M15 9.34V4a3 3 0 0 0-5.94-.6" />
                <path d="M17 16.95A7 7 0 0 1 5 12v-2m14 0v2a7 7 0 0 1-.11 1.23" />
              </svg>
            </div>
          </div>
          <!-- 远程参会者 -->
          <div
            class="video-cell"
            v-for="(p, i) in visibleParticipants"
            :key="p.userId"
            :style="getVideoCellStyle(i + 1)"
          >
            <video
              v-if="p.stream && p.status === 'connected'"
              class="cell-video"
              :ref="(el) => setRemoteVideoRef(p.userId, el as HTMLVideoElement)"
              autoplay
              playsinline
            />
            <div
              v-else
              class="cell-off"
              :class="{ hungup: p.status === 'hungup' || p.status === 'rejected' }"
            >
              <img :src="_checkImgUrl(p.avatar) || defaultAvatar" alt="" />
              <div class="status-overlay" v-if="p.status !== 'connected'">
                <span>{{ participantStatusText(p) }}</span>
              </div>
            </div>
            <div class="cell-label">
              <span>{{ p.userName }}</span>
            </div>
          </div>
        </div>

        <!-- 参会者视频列表 (>9人) -->
        <div class="video-scroll-list" v-else>
          <div class="list-scroll">
            <!-- 我自己 -->
            <div class="list-row">
              <video
                v-if="!state.isCameraOff"
                class="list-video"
                ref="localVideo"
                autoplay
                playsinline
                muted
              />
              <img v-else :src="myAvatar" class="list-avatar" />
              <span class="list-name">我</span>
              <span class="list-status connected">通话中</span>
            </div>
            <!-- 参会者 -->
            <div class="list-row" v-for="p in participants" :key="p.userId">
              <video
                v-if="p.stream && p.status === 'connected'"
                class="list-video"
                autoplay
                playsinline
              />
              <img v-else :src="_checkImgUrl(p.avatar) || defaultAvatar" class="list-avatar" />
              <span class="list-name">{{ p.userName }}</span>
              <span class="list-status" :class="p.status">{{ participantStatusText(p) }}</span>
            </div>
          </div>
        </div>

        <div class="call-actions video-actions">
          <div class="action-btn" @click="toggleMute">
            <div class="btn-circle" :class="{ off: state.isMuted }">
              <svg
                v-if="state.isMuted"
                class="call-svg-icon"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path d="M1 1l22 22" />
                <path d="M9 9v3a3 3 0 0 0 5.12 2.12M15 9.34V4a3 3 0 0 0-5.94-.6" />
                <path d="M17 16.95A7 7 0 0 1 5 12v-2m14 0v2a7 7 0 0 1-.11 1.23" />
                <line x1="12" y1="19" x2="12" y2="23" />
                <line x1="8" y1="23" x2="16" y2="23" />
              </svg>
              <svg
                v-else
                class="call-svg-icon"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z" />
                <path d="M19 10v2a7 7 0 0 1-14 0v-2" />
                <line x1="12" y1="19" x2="12" y2="23" />
                <line x1="8" y1="23" x2="16" y2="23" />
              </svg>
            </div>
            <span>麦克风</span>
          </div>
          <div class="action-btn" @click="handleHangup">
            <div class="btn-circle hangup">
              <img src="@/assets/img/icon/message/chat/call-end.png" alt="" />
            </div>
            <span>挂断</span>
          </div>
          <div class="action-btn" @click="toggleSpeaker">
            <div class="btn-circle" :class="{ off: !state.isSpeaker }">
              <img :src="state.isSpeaker ? speakerOnIcon : speakerOffIcon" alt="" />
            </div>
            <span>扬声器</span>
          </div>
          <div class="action-btn" @click="toggleCamera">
            <div class="btn-circle" :class="{ off: state.isCameraOff }">
              <img :src="state.isCameraOff ? cameraOffIcon : cameraOnIcon" alt="" />
            </div>
            <span>摄像头</span>
          </div>
        </div>
      </template>

      <!-- ═══ 语音模式 ═══ -->
      <template v-else>
        <div class="call-center">
          <div class="call-room-name">{{ roomDisplayName }}</div>
          <span class="call-status">{{ statusText }}</span>

          <!-- 头像网格 (≤10人) -->
          <div class="audio-grid" v-if="audioGridTotal <= 10" :style="audioGridContainerStyle">
            <!-- 我自己 -->
            <div
              class="audio-cell"
              :class="{ speaking: speakingMap['self'] }"
              :style="getAudioCellStyle(0)"
            >
              <img class="a-avatar" :src="myAvatar" alt="" />
              <span class="a-name">我</span>
              <span class="a-status connected">通话中</span>
            </div>
            <!-- 其他参会者 -->
            <div
              class="audio-cell"
              :class="{ speaking: speakingMap[p.userId] }"
              v-for="(p, i) in visibleParticipants"
              :key="p.userId"
              :style="getAudioCellStyle(i + 1)"
            >
              <img class="a-avatar" :src="_checkImgUrl(p.avatar) || defaultAvatar" alt="" />
              <span class="a-name">{{ p.userName }}</span>
              <span class="a-status" :class="p.status">{{ participantStatusText(p) }}</span>
            </div>
          </div>

          <!-- 列表 (>10人) -->
          <div class="participants-list" v-else>
            <div class="p-row">
              <img class="p-avatar" :src="myAvatar" alt="" />
              <span class="p-name">我</span>
              <span class="p-status connected">通话中</span>
            </div>
            <div class="p-row" v-for="p in visibleParticipants" :key="p.userId">
              <img class="p-avatar" :src="_checkImgUrl(p.avatar) || defaultAvatar" alt="" />
              <span class="p-name">{{ p.userName }}</span>
              <span class="p-status" :class="p.status">{{ participantStatusText(p) }}</span>
            </div>
          </div>
        </div>

        <div class="call-actions voice-actions">
          <div class="action-btn" @click="toggleMute">
            <div class="btn-circle" :class="{ off: state.isMuted }">
              <svg
                v-if="state.isMuted"
                class="call-svg-icon"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path d="M1 1l22 22" />
                <path d="M9 9v3a3 3 0 0 0 5.12 2.12M15 9.34V4a3 3 0 0 0-5.94-.6" />
                <path d="M17 16.95A7 7 0 0 1 5 12v-2m14 0v2a7 7 0 0 1-.11 1.23" />
                <line x1="12" y1="19" x2="12" y2="23" />
                <line x1="8" y1="23" x2="16" y2="23" />
              </svg>
              <svg
                v-else
                class="call-svg-icon"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z" />
                <path d="M19 10v2a7 7 0 0 1-14 0v-2" />
                <line x1="12" y1="19" x2="12" y2="23" />
                <line x1="8" y1="23" x2="16" y2="23" />
              </svg>
            </div>
            <span>{{ state.isMuted ? '已静音' : '麦克风' }}</span>
          </div>
          <div class="action-btn" @click="handleHangup">
            <div class="btn-circle hangup">
              <img src="@/assets/img/icon/message/chat/call-end.png" alt="" />
            </div>
            <span>挂断</span>
          </div>
          <div class="action-btn" @click="toggleSpeaker">
            <div class="btn-circle" :class="{ off: !state.isSpeaker }">
              <img :src="state.isSpeaker ? speakerOnIcon : speakerOffIcon" alt="" />
            </div>
            <span>扬声器</span>
          </div>
        </div>
      </template>
    </div>
  </transition>

  <!-- ========== 通话中浮窗 ========== -->
  <transition name="fade">
    <div v-if="state.isMinimized" class="call-float" @click="state.isMinimized = false">
      <img src="@/assets/img/icon/message/chat/call.png" alt="" />
      <span>通话中</span>
    </div>
  </transition>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted, onUnmounted, watch } from 'vue'
import bus, { EVENT_KEY } from '@/utils/bus'
import { onSocketMsg, sendCallSignal, sendCallSignalToGroup } from '@/utils/socket'
import { useBaseStore } from '@/store/pinia'
import { _checkImgUrl } from '@/utils'
import defaultAvatar from '@/assets/img/icon/people-gray.png'
import cameraOnIcon from '@/assets/img/icon/message/chat/able-camera.png'
import cameraOffIcon from '@/assets/img/icon/message/chat/disabled-camera.png'
import speakerOnIcon from '@/assets/img/icon/message/chat/able-volume.png'
import speakerOffIcon from '@/assets/img/icon/message/chat/disabled-volume.png'

defineOptions({ name: 'Call' })

const store = useBaseStore()
const localVideo = ref<HTMLVideoElement>()
const remoteVideoRefs = new Map<string, HTMLVideoElement>()

function setRemoteVideoRef(userId: string, el: HTMLVideoElement | null) {
  if (el) remoteVideoRefs.set(userId, el)
  else remoteVideoRefs.delete(userId)
}

interface Participant {
  userId: string
  userName: string
  avatar: string
  status: 'ringing' | 'connecting' | 'connected' | 'hungup' | 'rejected'
  stream: MediaStream | null
}

const state = reactive({
  isActive: false,
  isVideo: false,
  isMinimized: false,
  isMuted: false,
  isCameraOff: false,
  isSpeaker: true,
  roomName: '',
  callId: '' as string,
  isInitiator: false
})

const participants = reactive<Participant[]>([])
const incoming = reactive({
  show: false,
  name: '',
  avatar: '',
  isVideo: false,
  isGroup: false,
  callId: '',
  fromUserId: '',
  initiatorId: '',
  groupMembers: [] as string[]
})

const myAvatar = computed(() => store.userinfo.avatar_168x168?.url_list?.[0] || '')
const roomDisplayName = computed(() => {
  const name = state.roomName
  return name.length > 12 ? name.slice(0, 12) + '...' : name
})

// 已结束的通话成员(灰但保留)
const visibleParticipants = computed(() =>
  participants.filter((p) => p.status !== 'hungup' && p.status !== 'rejected')
)
const allParticipants = computed(() => participants)
const connectedCount = computed(() => participants.filter((p) => p.status === 'connected').length)

// ── 语音头像网格 ──
const audioGridTotal = computed(() => visibleParticipants.value.length + 1 /* self */)
const audioGridCols = computed(() => (audioGridTotal.value <= 4 ? 2 : 3))
const audioGridRows = computed(() => Math.ceil(audioGridTotal.value / audioGridCols.value))

const audioGridContainerStyle = computed(() => ({
  display: 'flex',
  flexWrap: 'wrap',
  alignContent: 'center',
  justifyContent: 'flex-start',
  maxWidth: audioGridCols.value === 3 ? '280rem' : '220rem'
}))

function getAudioCellStyle(index: number) {
  const c = audioGridCols.value
  const r = audioGridRows.value
  const w = 100 / c + '%'
  const h = 100 / r + '%'
  const n = audioGridTotal.value

  // 通用：不完整末行居中
  const leftover = n % c
  if (leftover > 0) {
    const lastRowStart = Math.floor(n / c) * c
    const isLastRow = index >= lastRowStart
    if (isLastRow && index === lastRowStart) {
      const offset = ((c - leftover) * (100 / c)) / 2
      return { width: w, height: h, marginLeft: offset + '%' }
    }
  }

  return { width: w, height: h }
}

// ── 视频网格 ──
const gridTotal = computed(() => visibleParticipants.value.length + 1)
const gridCols = computed(() => (gridTotal.value <= 4 ? 2 : 3))
const gridRows = computed(() => Math.ceil(gridTotal.value / gridCols.value))

const gridContainerStyle = computed(() => ({
  display: 'flex',
  flexWrap: 'wrap',
  alignContent: 'center',
  justifyContent: 'flex-start'
}))

function getVideoCellStyle(index: number) {
  const c = gridCols.value
  const r = gridRows.value
  const w = 100 / c + '%'
  const h = 100 / r + '%'
  const n = gridTotal.value

  const leftover = n % c
  if (leftover > 0) {
    const lastRowStart = Math.floor(n / c) * c
    const isLastRow = index >= lastRowStart
    if (isLastRow && index === lastRowStart) {
      const offset = ((c - leftover) * (100 / c)) / 2
      return { width: w, height: h, marginLeft: offset + '%' }
    }
  }

  // 3人特例
  if (n === 3 && index === 0) {
    return { width: w, height: '100%' }
  }

  return { width: w, height: h }
}

const callSeconds = ref(0)
let durationTimer: ReturnType<typeof setInterval> | null = null

const durationText = computed(() => {
  const m = Math.floor(callSeconds.value / 60)
  const s = callSeconds.value % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
})

function totalConnected() {
  return participants.filter((p) => p.status === 'connected').length
}

const statusText = computed(() => {
  if (connectedCount.value === 0) {
    const ringing = participants.filter((p) => p.status === 'ringing' || p.status === 'connecting')
    if (ringing.length) return '等待接听...'
    return '正在连接...'
  }
  return durationText.value
})

function participantStatusText(p: Participant) {
  switch (p.status) {
    case 'ringing':
      return '等待接听...'
    case 'connecting':
      return '连接中...'
    case 'connected':
      return '通话中'
    case 'hungup':
      return '已挂断'
    case 'rejected':
      return '已拒绝'
    default:
      return ''
  }
}

watch(
  () => connectedCount.value,
  (c) => {
    if (c > 0) {
      callSeconds.value = 0
      if (durationTimer) {
        clearInterval(durationTimer)
        durationTimer = null
      }
      durationTimer = setInterval(() => {
        callSeconds.value++
      }, 1000)
    } else {
      if (durationTimer) {
        clearInterval(durationTimer)
        durationTimer = null
      }
    }
  }
)

// --- WebRTC per participant ---
const ICE_SERVERS: RTCConfiguration = {
  iceServers: [{ urls: 'stun:stun.l.google.com:19302' }, { urls: 'stun:stun1.l.google.com:19302' }]
}

const peerConnections = new Map<string, RTCPeerConnection>()
let localStream: MediaStream | null = null
let mixingAudioCtx: AudioContext | null = null
let mixingDestination: MediaStreamAudioDestinationNode | null = null
let unsubSignal: (() => void) | null = null

// ── 说话检测 ──
let speechAudioCtx: AudioContext | null = null
const speechAnalysers = new Map<string, AnalyserNode>()
const speechArrays = new Map<string, Uint8Array>()
const speakingMap = reactive<Record<string, boolean>>({})
let speakingTimer: ReturnType<typeof setInterval> | null = null

function startSpeakingDetection() {
  if (speakingTimer) return
  if (!speechAudioCtx) {
    try {
      speechAudioCtx = new AudioContext()
    } catch {
      return
    }
  }
  speakingTimer = setInterval(() => {
    speechAnalysers.forEach((analyser, uid) => {
      const arr = speechArrays.get(uid)!
      analyser.getByteTimeDomainData(arr)
      let sum = 0
      for (let i = 0; i < arr.length; i++) {
        const v = arr[i] / 128.0 - 1.0
        sum += v * v
      }
      const rms = Math.sqrt(sum / arr.length)
      speakingMap[uid] = rms > 0.03
    })
  }, 150)
}

function addSpeechAnalyser(uid: string, stream: MediaStream) {
  if (!speechAudioCtx) {
    try {
      speechAudioCtx = new AudioContext()
    } catch {
      return
    }
  }
  try {
    const source = speechAudioCtx.createMediaStreamSource(stream)
    const analyser = speechAudioCtx.createAnalyser()
    analyser.fftSize = 256
    analyser.smoothingTimeConstant = 0.3
    source.connect(analyser)
    speechAnalysers.set(uid, analyser)
    speechArrays.set(uid, new Uint8Array(analyser.frequencyBinCount))
  } catch {
    /* permissions issue */
  }
}

function stopSpeakingDetection() {
  if (speakingTimer) {
    clearInterval(speakingTimer)
    speakingTimer = null
  }
  speechAnalysers.clear()
  speechArrays.clear()
  Object.keys(speakingMap).forEach((k) => delete speakingMap[k])
  if (speechAudioCtx) {
    speechAudioCtx.close()
    speechAudioCtx = null
  }
}

function getOrCreatePC(targetId: string) {
  if (peerConnections.has(targetId)) return peerConnections.get(targetId)!
  const pc = new RTCPeerConnection(ICE_SERVERS)

  pc.onicecandidate = (e) => {
    if (e.candidate) {
      sendCallSignal(state.isInitiator ? targetId : getInitiatorId(), 'ice_candidate', {
        ...e.candidate,
        call_id: state.callId,
        target_user: targetId
      })
    }
  }

  pc.ontrack = (e) => {
    const p = participants.find((p) => p.userId === targetId)
    if (p) p.stream = e.streams[0]
    if (e.streams[0]) addSpeechAnalyser(targetId, e.streams[0])
    // 播放远程音频
    const audio = new Audio()
    audio.srcObject = e.streams[0]
    audio.autoplay = true
    audio.play().catch(() => {})
  }

  pc.onconnectionstatechange = () => {
    if (!pc) return
    if (pc.connectionState === 'connected') {
      updateParticipant(targetId, 'connected')
    }
    if (pc.connectionState === 'failed' || pc.connectionState === 'disconnected') {
      updateParticipant(targetId, 'hungup')
    }
  }

  peerConnections.set(targetId, pc)
  return pc
}

function getInitiatorId(): string {
  return incoming.initiatorId || participants[0]?.userId || ''
}

function updateParticipant(userId: string, status: Participant['status']) {
  const p = participants.find((p) => p.userId === userId)
  if (p) p.status = status
}

const AUDIO_CONSTRAINTS = {
  echoCancellation: true,
  noiseSuppression: true,
  autoGainControl: true,
  channelCount: 1,
  sampleRate: 48000
}

async function getLocalMedia() {
  if (localStream) return
  if (state.isVideo) {
    try {
      localStream = await navigator.mediaDevices.getUserMedia({
        audio: AUDIO_CONSTRAINTS,
        video: { width: { ideal: 640 }, height: { ideal: 480 }, facingMode: 'user' }
      })
    } catch {
      console.warn('Video device unavailable, falling back to audio-only')
      state.isVideo = false
      localStream = await navigator.mediaDevices.getUserMedia({
        audio: AUDIO_CONSTRAINTS,
        video: false
      })
    }
  } else {
    localStream = await navigator.mediaDevices.getUserMedia({
      audio: AUDIO_CONSTRAINTS,
      video: false
    })
  }
  if (localVideo.value) localVideo.value.srcObject = localStream
  addSpeechAnalyser('self', localStream)
}

function addLocalTracksToPC(pc: RTCPeerConnection) {
  if (!localStream) return
  localStream.getTracks().forEach((t) => pc.addTrack(t, localStream!))
}

// ==================== 发起通话 ====================
async function startCall(
  targets: { userId: string; userName: string; avatar: string }[],
  isVideo: boolean,
  roomName?: string
) {
  const myUid = store.userinfo.uid
  state.isVideo = isVideo
  state.isActive = true
  state.isMinimized = false
  state.isCameraOff = false
  state.isMuted = false
  state.isSpeaker = true
  state.isInitiator = true
  state.callId = 'call_' + Date.now() + '_' + myUid
  state.roomName = roomName || targets[0]?.userName || '通话'

  // 初始化参会者列表
  participants.length = 0
  targets.forEach((t) => {
    participants.push({
      userId: t.userId,
      userName: t.userName,
      avatar: t.avatar,
      status: 'ringing',
      stream: null
    })
  })

  await getLocalMedia()
  startSpeakingDetection()

  // 群发 call_request
  const targetIds = targets.map((t) => t.userId)
  const userNames = targets.map((t) => t.userName)
  await sendCallSignalToGroup(targetIds, 'call_request', {
    name: store.userinfo.nickname,
    avatar: myAvatar.value,
    isVideo,
    isGroup: targets.length > 1,
    call_id: state.callId,
    initiator_id: myUid,
    group_members: [store.userinfo.nickname, ...userNames].slice(0, 5)
  })
}

// ==================== 被叫接听 ====================
async function handleAccept() {
  incoming.show = false
  const myUid = store.userinfo.uid
  state.isVideo = incoming.isVideo
  state.isActive = true
  state.isCameraOff = false
  state.isMuted = false
  state.isSpeaker = true
  state.isInitiator = false
  state.callId = incoming.callId
  state.roomName = incoming.name
  state.roomName = incoming.name

  // 如果群通话，创建多个参会者条目
  participants.length = 0
  if (incoming.isGroup && incoming.groupMembers?.length) {
    incoming.groupMembers.forEach((name) => {
      if (name && name !== store.userinfo.nickname) {
        participants.push({
          userId: '', // 等 call_accept 后补充
          userName: name,
          avatar: '',
          status: 'ringing',
          stream: null
        })
      }
    })
  }
  participants.push({
    userId: incoming.fromUserId,
    userName: incoming.name,
    avatar: incoming.avatar,
    status: 'connecting',
    stream: null
  })

  await getLocalMedia()
  startSpeakingDetection()

  await sendCallSignal(incoming.fromUserId, 'call_accept', {
    isVideo: incoming.isVideo,
    call_id: incoming.callId
  })
}

async function handleReject() {
  await sendCallSignal(incoming.fromUserId, 'call_reject', {
    isVideo: incoming.isVideo,
    call_id: incoming.callId,
    isGroup: incoming.isGroup
  })
  incoming.show = false
}

// ==================== 挂断 ====================
async function handleHangup() {
  const targetIds = participants
    .filter((p) => p.status === 'connected' || p.status === 'connecting')
    .map((p) => p.userId)
    .filter(Boolean)

  if (state.isInitiator && targetIds.length) {
    await sendCallSignalToGroup(
      targetIds,
      'hangup',
      {
        isVideo: state.isVideo,
        wasAnswered: connectedCount.value > 0,
        duration: callSeconds.value,
        call_id: state.callId
      },
      state.callId
    )
  } else if (!state.isInitiator) {
    await sendCallSignal(getInitiatorId(), 'hangup', {
      isVideo: state.isVideo,
      wasAnswered: connectedCount.value > 0,
      duration: callSeconds.value,
      call_id: state.callId
    })
  }

  cleanup()
}

function cleanup() {
  stopSpeakingDetection()
  peerConnections.forEach((pc) => {
    try {
      pc.close()
    } catch {
      /* ignore */
    }
  })
  peerConnections.clear()
  if (localStream) {
    localStream.getTracks().forEach((t) => t.stop())
    localStream = null
  }
  if (mixingAudioCtx) {
    mixingAudioCtx.close()
    mixingAudioCtx = null
    mixingDestination = null
  }
  if (durationTimer) {
    clearInterval(durationTimer)
    durationTimer = null
  }
  callSeconds.value = 0
  participants.length = 0
  state.isActive = false
  state.isMinimized = false
  incoming.show = false
  state.callId = ''
  bus.emit(EVENT_KEY.CALL_ENDED)
}

// ==================== WebRTC Offer / Answer ====================
async function createOfferForParticipant(targetId: string) {
  try {
    const pc = getOrCreatePC(targetId)
    addLocalTracksToPC(pc)
    const offer = await pc.createOffer()
    await pc.setLocalDescription(offer)
    await sendCallSignal(targetId, 'offer', { ...offer, call_id: state.callId })
  } catch (e) {
    console.error('createOffer failed:', e)
  }
}

async function handleOffer(targetId: string, offer: RTCSessionDescriptionInit) {
  try {
    const pc = getOrCreatePC(targetId)
    await pc.setRemoteDescription(new RTCSessionDescription(offer))
    addLocalTracksToPC(pc)
    const answer = await pc.createAnswer()
    await pc.setLocalDescription(answer)
    await sendCallSignal(targetId, 'answer', { ...answer, call_id: state.callId })
  } catch (e) {
    console.error('handleOffer failed:', e)
  }
}

async function handleAnswer(targetId: string, answer: RTCSessionDescriptionInit) {
  const pc = peerConnections.get(targetId)
  if (!pc) return
  try {
    await pc.setRemoteDescription(new RTCSessionDescription(answer))
    updateParticipant(targetId, 'connected')
  } catch (e) {
    console.error('handleAnswer failed:', e)
  }
}

// ==================== 控制按钮 ====================
function toggleMute() {
  state.isMuted = !state.isMuted
  if (localStream) localStream.getAudioTracks().forEach((t) => (t.enabled = !state.isMuted))
}

function toggleCamera() {
  state.isCameraOff = !state.isCameraOff
  if (localStream) localStream.getVideoTracks().forEach((t) => (t.enabled = !state.isCameraOff))
}

function toggleSpeaker() {
  state.isSpeaker = !state.isSpeaker
}

let facingMode: 'user' | 'environment' = 'user'
async function switchCamera() {
  facingMode = facingMode === 'user' ? 'environment' : 'user'
  if (localStream) localStream.getVideoTracks().forEach((t) => t.stop())
  const newStream = await navigator.mediaDevices.getUserMedia({
    video: { width: { ideal: 640 }, height: { ideal: 480 }, facingMode }
  })
  const NewTrack = newStream.getVideoTracks()[0]
  peerConnections.forEach((pc) => {
    const sender = pc.getSenders().find((s) => s.track?.kind === 'video')
    if (sender) sender.replaceTrack(NewTrack)
  })
  if (localStream) {
    localStream.removeTrack(localStream.getVideoTracks()[0])
    localStream.addTrack(NewTrack)
  }
  if (localVideo.value) localVideo.value.srcObject = localStream
}

// ==================== 信令处理 ====================
function handleSignal(msg: any) {
  const sigType = msg.signal_type
  const data = msg.data
  const fromUserId = msg.from_user_id
  const callId = data?.call_id || msg.call_id

  // 过滤不相关的通话
  if (state.isActive && callId && state.callId && callId !== state.callId) return

  switch (sigType) {
    case 'call_request': {
      incoming.fromUserId = fromUserId
      incoming.name = data?.name || '用户'
      incoming.avatar = data?.avatar || ''
      incoming.isVideo = data?.isVideo || false
      incoming.isGroup = data?.isGroup || false
      incoming.callId = data?.call_id || ''
      incoming.initiatorId = String(fromUserId)
      incoming.groupMembers = data?.group_members || []
      incoming.show = true
      break
    }

    case 'call_accept': {
      if (!state.isInitiator) break
      updateParticipant(fromUserId, 'connecting')
      createOfferForParticipant(fromUserId)
      break
    }

    case 'call_reject': {
      if (data?.isGroup && state.isInitiator) {
        // 群通话里某人拒绝，通知其他人
        updateParticipant(fromUserId, 'rejected')
        const others = participants
          .filter((p) => p.status === 'connected' || p.status === 'connecting')
          .map((p) => p.userId)
        if (others.length) {
          sendCallSignalToGroup(others, 'participant_update', {
            user_id: fromUserId,
            status: 'rejected',
            call_id: state.callId
          })
        }
      } else {
        updateParticipant(fromUserId, 'rejected')
        if (!state.isInitiator) cleanup()
      }
      break
    }

    case 'offer': {
      handleOffer(fromUserId, data)
      break
    }

    case 'answer': {
      handleAnswer(fromUserId, data)
      break
    }

    case 'ice_candidate': {
      const pc = peerConnections.get(fromUserId)
      if (pc && data) {
        pc.addIceCandidate(new RTCIceCandidate(data)).catch(() => {})
      }
      break
    }

    case 'hangup': {
      updateParticipant(fromUserId, 'hungup')
      // 如果是群通话发起者挂了
      if (!state.isInitiator && fromUserId === getInitiatorId()) {
        cleanup()
      }
      // 发起者收到某人挂断 → 广播给其他人
      if (state.isInitiator) {
        const others = participants
          .filter((p) => p.status === 'connected' || p.status === 'connecting')
          .map((p) => p.userId)
        if (others.length) {
          sendCallSignalToGroup(others, 'participant_update', {
            user_id: fromUserId,
            status: 'hungup',
            call_id: state.callId
          })
        }
      }
      // 关闭对应连接
      const pc = peerConnections.get(fromUserId)
      if (pc) {
        pc.close()
        peerConnections.delete(fromUserId)
      }
      break
    }

    case 'participant_update': {
      // 参与者状态变更通知
      const uid = data?.user_id
      const st = data?.status
      if (uid && st) {
        updateParticipant(uid, st as Participant['status'])
      }
      break
    }
  }
}

// ==================== 生命周期 ====================
onMounted(() => {
  unsubSignal = onSocketMsg('call_signal', handleSignal)

  // 1对1 兼容：单人通话通过 bus 触发
  bus.on(EVENT_KEY.SHOW_AUDIO_CALL, (payload: any) => {
    if (payload?.toUserId) {
      startCall(
        [
          {
            userId: String(payload.toUserId),
            userName: payload.name || '',
            avatar: payload.avatar || ''
          }
        ],
        payload.isVideo || false,
        payload.name || ''
      )
    }
  })

  // 群通话通过 bus 触发
  bus.on('SHOW_GROUP_CALL', (payload: { targets: any[]; isVideo: boolean; roomName?: string }) => {
    if (payload?.targets?.length) {
      startCall(
        payload.targets.map((t: any) => ({
          userId: String(t.userId || t.uid || t.id),
          userName: t.userName || t.name || t.nickname || '',
          avatar: t.avatar || t.avatar_168x168?.url_list?.[0] || ''
        })),
        payload.isVideo || false,
        payload.roomName || ''
      )
    }
  })
})

onUnmounted(() => {
  if (unsubSignal) {
    unsubSignal()
    unsubSignal = null
  }
  cleanup()
  bus.off(EVENT_KEY.SHOW_AUDIO_CALL)
  bus.off('SHOW_GROUP_CALL')
})
</script>

<style>
.slide-down-enter-active,
.slide-down-leave-active {
  transition: all 0.35s cubic-bezier(0.21, 1.02, 0.58, 1);
}
.slide-down-enter-from,
.slide-down-leave-to {
  transform: translateY(-120%);
  opacity: 0;
}
.scale-enter-active,
.scale-leave-active {
  transition:
    transform 0.3s ease,
    opacity 0.3s ease;
}
.scale-enter-from,
.scale-leave-to {
  transform: scale(0.85);
  opacity: 0;
}
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>

<style scoped lang="less">
// ═══════════════════════════ 来电通知卡片 ═══════════════════════════
.incoming-call {
  position: fixed;
  z-index: 9999;
  top: 10rem;
  left: 20rem;
  right: 20rem;
  background: rgba(30, 30, 30, 0.97);
  backdrop-filter: blur(20px);
  border-radius: 20rem;
  padding: 24rem 20rem;
  color: white;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20rem;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.5);

  .incoming-header {
    font-size: 14rem;
    color: rgba(255, 255, 255, 0.5);
  }

  .incoming-body {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8rem;

    .incoming-avatar {
      width: 72rem;
      height: 72rem;
      border-radius: 50%;
      object-fit: cover;
      border: 3px solid rgba(255, 255, 255, 0.15);
    }

    .incoming-name {
      font-size: 20rem;
      font-weight: 600;
    }

    .incoming-hint {
      font-size: 13rem;
      color: rgba(255, 255, 255, 0.4);
      max-width: 80vw;
      text-align: center;
      word-break: break-all;
    }
  }

  .incoming-actions {
    display: flex;
    gap: 60rem;

    .action-btn .btn-circle {
      width: 60rem;
      height: 60rem;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      img {
        width: 28rem;
        height: 28rem;
      }
    }

    .decline .btn-circle {
      background: #fe2c55;
    }
    .accept .btn-circle {
      background: #14bf5f;
    }

    span {
      display: block;
      text-align: center;
      margin-top: 6rem;
      font-size: 12rem;
      color: rgba(255, 255, 255, 0.5);
    }
  }
}

// ═══════════════════════════ 通话界面 ═══════════════════════════
.call-screen {
  position: fixed;
  z-index: 9998;
  top: 0;
  left: 0;
  width: 100vw;
  height: calc(var(--vh, 1vh) * 100);
  color: white;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #1a1a2e 0%, #0d1117 100%);

  &.video {
    background: #000;
  }

  // ── 视频顶部信息栏 ──
  .video-top-bar {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    z-index: 2;
    padding: 8% 20rem 20rem;
    background: linear-gradient(to bottom, rgba(0, 0, 0, 0.55), transparent);
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 6rem;

    .video-peer-name {
      font-size: 18rem;
      font-weight: 600;
    }
    .video-status {
      font-size: 13rem;
      color: rgba(255, 255, 255, 0.5);
    }
  }

  // ── 参会者视频网格 ──
  .video-grid {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    padding: 60rem 4rem 150rem;
    box-sizing: border-box;

    .video-cell {
      box-sizing: border-box;
      padding: 3rem;
      position: relative;
      border-radius: 12rem;
      overflow: hidden;

      .cell-video {
        width: 100%;
        height: 100%;
        object-fit: cover;
        border-radius: 10rem;
        background: rgba(0, 0, 0, 0.3);
      }

      .cell-off {
        width: 100%;
        height: 100%;
        display: flex;
        align-items: center;
        justify-content: center;
        background: rgba(0, 0, 0, 0.4);
        border-radius: 10rem;
        flex-direction: column;
        gap: 8rem;

        img {
          width: 50rem;
          height: 50rem;
          border-radius: 50%;
          object-fit: cover;
          opacity: 0.7;
        }

        .status-overlay span {
          font-size: 11rem;
          color: rgba(255, 255, 255, 0.5);
        }

        &.hungup {
          opacity: 0.45;
          img {
            opacity: 0.3;
          }
        }
      }

      .cell-label {
        position: absolute;
        bottom: 6rem;
        left: 8rem;
        right: 8rem;
        font-size: 11rem;
        text-shadow: 0 1px 3px rgba(0, 0, 0, 0.7);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .cell-mute-badge {
        position: absolute;
        bottom: 6rem;
        right: 8rem;
        background: rgba(0, 0, 0, 0.6);
        border-radius: 50%;
        width: 20rem;
        height: 20rem;
        display: flex;
        align-items: center;
        justify-content: center;

        .mute-icon {
          width: 12rem;
          height: 12rem;
          color: #fe2c55;
        }
      }
    }
  }

  // ── 视频滚动列表 (>9人) ──
  .video-scroll-list {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    padding: 60rem 0 150rem;
    overflow-y: auto;

    .list-scroll {
      display: flex;
      flex-direction: column;
      gap: 4rem;
      padding: 0 16rem;
    }

    .list-row {
      display: flex;
      align-items: center;
      gap: 14rem;
      padding: 10rem 14rem;
      background: rgba(255, 255, 255, 0.05);
      border-radius: 12rem;

      .list-video {
        width: 48rem;
        height: 64rem;
        border-radius: 8rem;
        object-fit: cover;
        background: rgba(0, 0, 0, 0.3);
        flex-shrink: 0;
      }

      .list-avatar {
        width: 40rem;
        height: 40rem;
        border-radius: 50%;
        object-fit: cover;
        flex-shrink: 0;
      }

      .list-name {
        flex: 1;
        font-size: 15rem;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .list-status {
        font-size: 12rem;
        color: rgba(255, 255, 255, 0.4);
        flex-shrink: 0;

        &.connected {
          color: #14bf5f;
        }
        &.ringing {
          color: rgba(255, 255, 255, 0.4);
        }
      }
    }
  }

  // ── 语音模式：通话室 ──
  .call-center {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 8rem;
    padding: 40rem 20rem 20rem;

    .call-room-name {
      font-size: 24rem;
      font-weight: 600;
      max-width: 80vw;
      text-align: center;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .call-status {
      font-size: 14rem;
      color: rgba(255, 255, 255, 0.4);
      margin-bottom: 20rem;
    }

    // 音频头像网格 (≤10人)
    .audio-grid {
      .audio-cell {
        box-sizing: border-box;
        padding: 8rem 4rem;
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 4rem;

        .a-avatar {
          width: 58rem;
          height: 58rem;
          border-radius: 50%;
          object-fit: cover;
          border: 2px solid rgba(255, 255, 255, 0.1);
          transition:
            transform 0.15s ease,
            border-color 0.15s ease,
            box-shadow 0.15s ease;
        }

        .a-name {
          font-size: 12rem;
          max-width: 70rem;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
          text-align: center;
        }

        .a-status {
          font-size: 10rem;
          color: rgba(255, 255, 255, 0.4);

          &.connected {
            color: #14bf5f;
          }
          &.ringing {
            color: rgba(255, 255, 255, 0.4);
          }
        }

        &.speaking .a-avatar {
          transform: scale(1.12);
          border-color: #14bf5f;
          box-shadow: 0 0 14rem rgba(20, 191, 95, 0.55);
        }
      }
    }

    // 音频列表 (>10人)
    .participants-list {
      width: 100%;
      max-width: 340rem;
      max-height: 60vh;
      overflow-y: auto;

      .p-row {
        display: flex;
        align-items: center;
        gap: 14rem;
        padding: 12rem 16rem;
        border-radius: 12rem;
        background: rgba(255, 255, 255, 0.05);
        margin-bottom: 8rem;

        .p-avatar {
          width: 40rem;
          height: 40rem;
          border-radius: 50%;
          object-fit: cover;
          flex-shrink: 0;
        }

        .p-name {
          flex: 1;
          font-size: 15rem;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        .p-status {
          font-size: 12rem;
          color: rgba(255, 255, 255, 0.4);
          flex-shrink: 0;

          &.connected {
            color: #14bf5f;
          }
          &.ringing {
            color: rgba(255, 255, 255, 0.4);
          }
        }
      }
    }
  }

  // ── 底部操作栏（语音 + 视频共用） ──
  .call-actions {
    display: flex;
    justify-content: center;
    align-items: flex-start;
    gap: 28rem;
    padding: 40rem 20rem 50rem;
    flex-shrink: 0;

    &.video-actions {
      position: absolute;
      bottom: 0;
      left: 0;
      right: 0;
      z-index: 3;
      padding: 20rem 20rem 50rem;
      background: linear-gradient(to top, rgba(0, 0, 0, 0.55), transparent);
    }

    .action-btn {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 8rem;
      cursor: pointer;

      .btn-circle {
        width: 56rem;
        height: 56rem;
        border-radius: 50%;
        background: rgba(255, 255, 255, 0.15);
        display: flex;
        align-items: center;
        justify-content: center;
        transition: background 0.2s;

        img,
        .call-svg-icon {
          width: 26rem;
          height: 26rem;
        }
        .call-svg-icon {
          color: white;
        }

        &.off {
          background: rgba(255, 255, 255, 0.06);
        }
        &.off .call-svg-icon {
          color: rgba(255, 255, 255, 0.3);
        }

        &:active {
          background: rgba(255, 255, 255, 0.3);
        }

        &.hangup {
          background: #fe2c55;
          width: 64rem;
          height: 64rem;
          img {
            width: 30rem;
            height: 30rem;
          }
          &:active {
            background: #d41e45;
          }
        }
      }

      span {
        font-size: 11rem;
        color: rgba(255, 255, 255, 0.4);
        white-space: nowrap;
      }
    }
  }
}

// ═══════════════════════════ 浮窗 ═══════════════════════════
.call-float {
  position: fixed;
  z-index: 8;
  width: 60rem;
  height: 60rem;
  top: 20vh;
  right: 20rem;
  background: rgba(20, 191, 95, 0.9);
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 10rem;
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.4);
  img {
    width: 22rem;
    height: 22rem;
  }
}
</style>
