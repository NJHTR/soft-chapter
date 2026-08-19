<template>
  <Transition name="rtc-fade">
    <div
      v-if="store.phase !== 'idle'"
      class="rtc-panel"
      :class="{ 'rtc-panel-minimized': store.minimized }"
      @click.stop
    >
      <!-- 最小化胶囊(connected 时) -->
      <div
        v-if="store.minimized && store.phase === 'connected'"
        class="rtc-capsule"
        :style="capsuleStyle"
        @pointerdown="beginCapsuleDrag"
        @click.stop="restoreFromCapsule"
      >
        <div class="rtc-capsule-media">
          <video
            v-if="store.mode === 'video' && peerVideoEnable"
            class="rtc-capsule-video"
            :ref="setMinimizedRemoteVideoEl"
            autoplay
            playsinline
            muted
          />
          <img v-else :src="peerAvatar || defaultAvatar" alt="" />
        </div>
        <div class="rtc-capsule-info">
          <span class="capsule-name">{{ peerName }}</span>
          <span class="capsule-time">{{ store.durationText }}</span>
        </div>
      </div>

      <template v-else>
        <!-- ═══ 拨号中 ═══ -->
        <div v-if="store.phase === 'dialing'" class="rtc-center">
          <img class="rtc-avatar big" :src="isGroup ? defaultAvatar : peerAvatar" alt="" />
          <div class="rtc-name">{{ isGroup ? groupDisplayName : peerName }}</div>
          <div class="rtc-status">正在呼叫…</div>
          <div v-if="store.error" class="rtc-error">{{ store.error }}</div>
          <div class="rtc-actions dialing-actions">
            <div class="rtc-action-btn hangup" @click="store.cancel()">
              <div class="btn-circle hangup">
                <svg class="rtc-svg-icon rtc-hangup-icon" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                  <path d="M6.62 10.79a15.05 15.05 0 0 0 6.59 6.59l2.2-2.2c.27-.27.67-.36 1.02-.24 1.12.37 2.33.57 3.57.57.55 0 1 .45 1 1V20c0 .55-.45 1-1 1C10.61 21 3 13.39 3 4c0-.55.45-1 1-1h3.5c.55 0 1 .45 1 1 0 1.25.2 2.45.57 3.57.11.35.03.74-.25 1.02z" transform="rotate(135 12 12)" />
                </svg>
              </div>
              <span>取消</span>
            </div>
          </div>
        </div>

        <!-- ═══ 来电 ═══ -->
        <div v-else-if="store.phase === 'ringingIn'" class="rtc-center">
          <img class="rtc-avatar big" :src="peerAvatar" alt="" />
          <div class="rtc-name">{{ store.incoming?.name || '来电' }}</div>
          <div class="rtc-status">
            {{
              store.incoming?.isGroup
                ? '群通话来电'
                : store.incoming?.mode === 'video'
                  ? '视频通话来电'
                  : '语音通话来电'
            }}
          </div>
          <div class="rtc-actions incoming-actions">
            <div class="rtc-action-btn" @click="store.reject()">
              <div class="btn-circle red">
                <svg class="rtc-svg-icon rtc-hangup-icon" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                  <path d="M6.62 10.79a15.05 15.05 0 0 0 6.59 6.59l2.2-2.2c.27-.27.67-.36 1.02-.24 1.12.37 2.33.57 3.57.57.55 0 1 .45 1 1V20c0 .55-.45 1-1 1C10.61 21 3 13.39 3 4c0-.55.45-1 1-1h3.5c.55 0 1 .45 1 1 0 1.25.2 2.45.57 3.57.11.35.03.74-.25 1.02z" transform="rotate(135 12 12)" />
                </svg>
              </div>
              <span>拒绝</span>
            </div>
            <div class="rtc-action-btn" @click="store.accept()">
              <div class="btn-circle green"><img :src="iconCall" alt="" /></div>
              <span>接听</span>
            </div>
          </div>
        </div>

        <!-- ═══ 连接中 ═══ -->
        <div v-else-if="store.phase === 'connecting'" class="rtc-center">
          <img class="rtc-avatar big" :src="isGroup ? defaultAvatar : peerAvatar" alt="" />
          <div class="rtc-name">{{ isGroup ? groupDisplayName : peerName }}</div>
          <div class="rtc-status">正在连接…</div>
          <div v-if="store.error" class="rtc-error">{{ store.error }}</div>
          <div class="rtc-actions dialing-actions">
            <div class="rtc-action-btn hangup" @click="store.hangup()">
              <div class="btn-circle hangup">
                <svg class="rtc-svg-icon rtc-hangup-icon" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                  <path d="M6.62 10.79a15.05 15.05 0 0 0 6.59 6.59l2.2-2.2c.27-.27.67-.36 1.02-.24 1.12.37 2.33.57 3.57.57.55 0 1 .45 1 1V20c0 .55-.45 1-1 1C10.61 21 3 13.39 3 4c0-.55.45-1 1-1h3.5c.55 0 1 .45 1 1 0 1.25.2 2.45.57 3.57.11.35.03.74-.25 1.02z" transform="rotate(135 12 12)" />
                </svg>
              </div>
              <span>挂断</span>
            </div>
          </div>
        </div>

        <!-- ═══ 通话中 ═══ -->
        <div v-else-if="store.phase === 'connected'" class="rtc-connected">
          <button
            type="button"
            class="rtc-minimize-btn"
            aria-label="最小化通话"
            title="最小化通话"
            @click="store.toggleMinimize()"
          >
            <svg
              class="rtc-svg-icon"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              aria-hidden="true"
            >
              <line x1="5" y1="12" x2="19" y2="12" />
            </svg>
          </button>
          <!-- 群通话: 多参与者网格 -->
          <template v-if="isGroup">
            <div class="rtc-group-stage">
              <div class="rtc-top-bar rtc-top-bar-group">
                <span class="rtc-peer-name">{{ groupDisplayName }}</span>
                <span class="rtc-timer">{{ store.durationText }}</span>
              </div>
              <div class="rtc-group-grid">
                <div class="rtc-group-tile rtc-group-tile-me">
                  <video
                    v-if="store.mode === 'video' && localVideoEnable"
                    ref="localVideoEl"
                    class="rtc-group-tile-video"
                    autoplay
                    playsinline
                    muted
                  />
                  <div v-else class="rtc-group-tile-off">
                    <img :src="myAvatar || defaultAvatar" alt="" />
                  </div>
                  <span class="rtc-group-tile-name">我</span>
                  <span v-if="store.devices.audioMuted" class="rtc-group-mute-icon">
                    <svg
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="2"
                      class="rtc-svg-icon-sm"
                    >
                      <path d="M1 1l22 22" />
                      <path d="M9 9v3a3 3 0 0 0 5.12 2.12M15 9.34V4a3 3 0 0 0-5.94-.6" />
                    </svg>
                  </span>
                </div>
                <div
                  v-for="entry in remoteParticipantEntries"
                  :key="entry.identity"
                  class="rtc-group-tile"
                  :class="{ speaking: store.activeSpeaker === entry.identity }"
                >
                  <video
                    v-if="store.mode === 'video' && entry.videoEnabled && entry.stream"
                    :ref="(el) => setGroupVideoEl(entry.identity, el as any)"
                    class="rtc-group-tile-video"
                    autoplay
                    playsinline
                    muted
                  />
                  <audio
                    v-if="entry.stream"
                    :ref="(el) => setGroupVideoEl(`aud-${entry.identity}`, el as any)"
                    autoplay
                    playsinline
                    class="rtc-hidden-audio"
                  />
                  <div
                  v-if="store.mode === 'audio' || !entry.videoEnabled || !entry.stream"
                  class="rtc-group-tile-off"
                  >
                    <img :src="entry.avatar || defaultAvatar" alt="" />
                  </div>
                  <span class="rtc-group-tile-name">{{ entry.name }}</span>
                  <span v-if="entry.muted.audio" class="rtc-group-mute-icon">
                    <svg
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="2"
                      class="rtc-svg-icon-sm"
                    >
                      <path d="M1 1l22 22" />
                      <path d="M9 9v3a3 3 0 0 0 5.12 2.12M15 9.34V4a3 3 0 0 0-5.94-.6" />
                    </svg>
                  </span>
                </div>
              </div>
            </div>
          </template>
          <template v-else-if="store.mode === 'video'">
            <div class="rtc-video-stage" :class="{ 'rtc-video-stage-remote-focused': videoLayoutSwapped }">
              <!-- 本机主画面 -->
              <div
                class="rtc-video-self-layer"
                :class="{ 'is-clickable': videoLayoutSwapped }"
                :role="videoLayoutSwapped ? 'button' : undefined"
                :tabindex="videoLayoutSwapped ? 0 : -1"
                aria-label="切换主画面"
                @click.stop="videoLayoutSwapped && toggleVideoLayout()"
                @keydown.enter.prevent="videoLayoutSwapped && toggleVideoLayout()"
                @keydown.space.prevent="videoLayoutSwapped && toggleVideoLayout()"
              >
                <video
                  v-if="localVideoEnable"
                  class="rtc-video-self"
                  ref="localVideoEl"
                  autoplay
                  playsinline
                  muted
                  @loadedmetadata="logLocalVideoEvent('loadedmetadata', $event)"
                  @playing="logLocalVideoEvent('playing', $event)"
                  @error="logLocalVideoEvent('error', $event)"
                />
                <div v-else class="rtc-video-self-placeholder">
                  <img :src="myAvatar || defaultAvatar" alt="" />
                  <span>我的摄像头已关闭</span>
                </div>
              </div>
              <!-- 对端右上角小窗 -->
              <div
                class="rtc-video-remote-preview"
                :class="{ 'is-clickable': !videoLayoutSwapped }"
                :role="!videoLayoutSwapped ? 'button' : undefined"
                :tabindex="!videoLayoutSwapped ? 0 : -1"
                aria-label="切换主画面"
                @click.stop="!videoLayoutSwapped && toggleVideoLayout()"
                @keydown.enter.prevent="!videoLayoutSwapped && toggleVideoLayout()"
                @keydown.space.prevent="!videoLayoutSwapped && toggleVideoLayout()"
              >
                <video
                  v-if="store.mode === 'video'"
                  class="rtc-video-remote-preview-stream"
                  :class="{ 'rtc-video-remote-preview-hidden': !peerVideoEnable }"
                  :ref="setRemoteVideoEl"
                  autoplay
                  playsinline
                  muted
                  @loadedmetadata="logRemoteVideoEvent('loadedmetadata', $event)"
                  @playing="logRemoteVideoEvent('playing', $event)"
                  @error="logRemoteVideoEvent('error', $event)"
                />
                <div v-if="!peerVideoEnable" class="rtc-video-remote-preview-placeholder">
                  <img :src="peerAvatar || defaultAvatar" alt="" />
                </div>
              </div>
              <audio
                v-if="peerStream"
                :ref="setRemoteAudioEl"
                autoplay
                playsinline
                class="rtc-hidden-audio"
              />
              <!-- 顶部信息 -->
              <div class="rtc-top-bar">
                <span class="rtc-peer-name">{{ peerName }}</span>
                <span class="rtc-timer">{{ store.durationText }}</span>
                <span v-if="store.peerLeft" class="rtc-peer-left">对方已挂断</span>
              </div>
            </div>
          </template>

          <template v-else>
            <div class="rtc-center audio-mid">
              <img
                class="rtc-avatar big"
                :class="{ speaking: store.activeSpeaker === store.peerId && peerStream }"
                :src="peerAvatar || defaultAvatar"
                alt=""
              />
              <div class="rtc-name">{{ peerName }}</div>
              <div class="rtc-status">
                {{ store.durationText }}
                <span v-if="store.peerLeft"> · 对方已挂断</span>
              </div>
              <!-- 隐藏的远端音频承载元素 -->
              <audio
                v-if="peerStream"
                :ref="setRemoteAudioEl"
                autoplay
                playsinline
                class="rtc-hidden-audio"
              />
            </div>
          </template>

          <div class="rtc-actions bottom-actions">
            <div
              class="rtc-action-row rtc-primary-row"
              :class="{ 'audio-primary-row': store.mode !== 'video' }"
            >
              <div class="rtc-action-btn" @click="store.toggleMute()">
                <div class="btn-circle" :class="{ off: store.devices.audioMuted }">
                  <svg
                    v-if="store.devices.audioMuted"
                    class="rtc-svg-icon"
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
                  </svg>
                  <svg
                    v-else
                    class="rtc-svg-icon"
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
                <span>{{ store.devices.audioMuted ? '已静音' : '麦克风' }}</span>
              </div>
              <div class="rtc-action-btn" @click="store.toggleSpeaker()">
                <div class="btn-circle" :class="{ off: !store.devices.speakerOn }">
                  <img :src="store.devices.speakerOn ? iconSpeakerOn : iconSpeakerOff" alt="" />
                </div>
                <span>扬声器</span>
              </div>
              <div v-if="store.mode === 'video'" class="rtc-action-btn" @click="store.toggleCamera()">
                <div class="btn-circle" :class="{ off: store.devices.videoOff }">
                  <img :src="store.devices.videoOff ? iconCameraOff : iconCameraOn" alt="" />
                </div>
                <span>{{ store.devices.videoOff ? '摄像头已关' : '摄像头' }}</span>
              </div>
            </div>
            <div class="rtc-action-row rtc-secondary-row">
              <div
                v-if="store.mode === 'video'"
                class="rtc-action-btn secondary-background"
                :class="{ active: store.devices.backgroundRemoved }"
                @click="store.toggleBackgroundRemoval()"
              >
                <div class="btn-circle">
                  <svg
                    class="rtc-svg-icon"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  >
                    <path d="M4 4h16v16H4z" />
                    <path d="m8 15 2.5-3 2 2 2.5-3L19 16" />
                    <circle cx="9" cy="9" r="1" />
                  </svg>
                </div>
                <span>{{ store.devices.backgroundRemoved ? '已去背景' : '去背景' }}</span>
              </div>
              <div class="rtc-action-btn secondary-hangup" @click="store.hangup()">
                <div class="btn-circle hangup">
                  <svg class="rtc-svg-icon rtc-hangup-icon" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                    <path d="M6.62 10.79a15.05 15.05 0 0 0 6.59 6.59l2.2-2.2c.27-.27.67-.36 1.02-.24 1.12.37 2.33.57 3.57.57.55 0 1 .45 1 1V20c0 .55-.45 1-1 1C10.61 21 3 13.39 3 4c0-.55.45-1 1-1h3.5c.55 0 1 .45 1 1 0 1.25.2 2.45.57 3.57.11.35.03.74-.25 1.02z" transform="rotate(135 12 12)" />
                  </svg>
                </div>
                <span>挂断</span>
              </div>
              <div v-if="store.mode === 'video'" class="rtc-action-btn secondary-flip" @click="store.switchCamera()">
                <div class="btn-circle">
                  <svg
                    class="rtc-svg-icon"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  >
                    <polyline points="23 4 23 10 17 10" />
                    <polyline points="1 20 1 14 7 14" />
                    <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15" />
                  </svg>
                </div>
                <span>翻转</span>
              </div>
            </div>
          </div>
        </div>

        <!-- ═══ 重连中 ═══ -->
        <div v-else-if="store.phase === 'reconnecting'" class="rtc-reconnecting">
          <div class="rtc-spinner"></div>
          <div class="rtc-status">连接恢复中…({{ store.reconnectCount }}/3)</div>
        </div>

        <!-- ═══ 已结束 ═══ -->
        <div v-else-if="store.phase === 'ended'" class="rtc-center">
          <img
            class="rtc-avatar big grey"
            :src="isGroup ? defaultAvatar : peerAvatar || defaultAvatar"
            alt=""
          />
          <div class="rtc-name">{{ isGroup ? groupDisplayName : peerName || '通话' }}</div>
          <div class="rtc-status">{{ endedText }}</div>
          <div v-if="store.error" class="rtc-error">{{ store.error }}</div>
        </div>
      </template>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRtcStore } from '@/modules/rtc/store/useRtcStore'
import { registerRtOutputEl } from '@/modules/rtc/adapter/rtcMediaPort'
import { useBaseStore } from '@/store/pinia'
import { _checkImgUrl } from '@/utils'
import defaultAvatar from '@/assets/img/icon/people-gray.png'
import iconCall from '@/assets/img/icon/message/chat/call.png'
import iconCameraOn from '@/assets/img/icon/message/chat/able-camera.png'
import iconCameraOff from '@/assets/img/icon/message/chat/disabled-camera.png'
import iconSpeakerOn from '@/assets/img/icon/message/chat/able-volume.png'
import iconSpeakerOff from '@/assets/img/icon/message/chat/disabled-volume.png'

defineOptions({ name: 'CallPanel' })

const store = useRtcStore()
const baseStore = useBaseStore()
const remoteVideoEl = ref<HTMLVideoElement | null>(null)
const remoteAudioEl = ref<HTMLAudioElement | null>(null)
const localVideoEl = ref<HTMLVideoElement | null>(null)
const minimizedRemoteVideoEl = ref<HTMLVideoElement | null>(null)
const videoLayoutSwapped = ref(false)
const minimizedPosition = ref({ left: 16, top: 16 })
const capsuleStyle = computed(() => ({
  left: `${minimizedPosition.value.left}px`,
  top: `${minimizedPosition.value.top}px`
}))

let capsuleDrag: {
  pointerId: number
  startX: number
  startY: number
  originLeft: number
  originTop: number
  moved: boolean
} | null = null
let capsuleClickSuppressed = false

function toggleVideoLayout() {
  videoLayoutSwapped.value = !videoLayoutSwapped.value
}

function beginCapsuleDrag(event: PointerEvent) {
  const target = event.currentTarget as HTMLElement | null
  if (!target) return
  capsuleDrag = {
    pointerId: event.pointerId,
    startX: event.clientX,
    startY: event.clientY,
    originLeft: minimizedPosition.value.left,
    originTop: minimizedPosition.value.top,
    moved: false
  }
  target.setPointerCapture?.(event.pointerId)
  window.addEventListener('pointermove', moveCapsule)
  window.addEventListener('pointerup', endCapsuleDrag, { once: true })
}

function moveCapsule(event: PointerEvent) {
  if (!capsuleDrag || event.pointerId !== capsuleDrag.pointerId) return
  const dx = event.clientX - capsuleDrag.startX
  const dy = event.clientY - capsuleDrag.startY
  if (Math.abs(dx) + Math.abs(dy) > 4) capsuleDrag.moved = true
  if (!capsuleDrag.moved) return
  const width = 164
  const height = 104
  minimizedPosition.value = {
    left: Math.max(8, Math.min(window.innerWidth - width - 8, capsuleDrag.originLeft + dx)),
    top: Math.max(8, Math.min(window.innerHeight - height - 8, capsuleDrag.originTop + dy))
  }
}

function endCapsuleDrag() {
  if (capsuleDrag?.moved) capsuleClickSuppressed = true
  capsuleDrag = null
  window.removeEventListener('pointermove', moveCapsule)
}

function restoreFromCapsule() {
  if (capsuleClickSuppressed) {
    capsuleClickSuppressed = false
    return
  }
  store.toggleMinimize()
}

const peerIdentity = computed(() => {
  const expected = store.peerId
  if (expected && store.remoteStreams[expected]) return expected
  const fallback = Object.keys(store.remoteStreams).find((identity) => identity !== store.myId)
  return fallback || expected
})
const peerStream = computed(() => {
  const id = peerIdentity.value
  return id ? (store.remoteStreams[id] ?? null) : null
})
const peerName = computed(() => store.outgoingMeta?.name || store.incoming?.name || '对方')
function resolveAvatar(url?: string | null): string {
  return _checkImgUrl(url || '') || defaultAvatar
}

const peerAvatar = computed(() =>
  resolveAvatar(store.outgoingMeta?.avatar || store.incoming?.avatar)
)
const myAvatar = computed(() => resolveAvatar(baseStore.userinfo.avatar_168x168?.url_list?.[0]))
// MediaStreamTrack.enabled is mutated by the browser and is not reactive by
// itself. Read the store signal so LiveKit mute/unmute callbacks invalidate
// this computed value, then derive visibility from the current track.
const peerVideoMuteSignal = computed(() => store.remoteMuted[peerIdentity.value]?.video ?? false)
const peerVideoEnable = computed(
  () => {
    void peerVideoMuteSignal.value
    return !!peerStream.value?.getVideoTracks().some(
      (track) => track.readyState !== 'ended' && track.enabled
    )
  }
)
const peerVideoSignature = computed(() =>
  peerStream.value?.getVideoTracks().map((track) => `${track.id}:${track.readyState}:${track.enabled}`).join('|') || ''
)
const localVideoEnable = computed(
  () =>
    !!store.localStream?.getVideoTracks().some(
      (track) => track.readyState !== 'ended' && track.enabled
    ) &&
    !store.devices.videoOff
)
const localVideoSignature = computed(
  () =>
    store.localStream?.getVideoTracks()
      .map((track) => `${track.id}:${track.readyState}:${track.enabled}`)
      .join('|') || ''
)

const END_REASON_TEXT: Record<string, string> = {
  REJECTED: '对方已拒绝',
  CANCELLED: '通话已取消',
  EXPIRED: '对方未接听',
  FAILED: '通话失败',
  ENDED: '通话已结束'
}
const endedText = computed(
  () => (store.endReason && END_REASON_TEXT[store.endReason]) || '通话已结束'
)

const isGroup = computed(() => !!store.groupMeta || store.session?.scope === 'group')
const groupDisplayName = computed(() => {
  const count = store.participants.length
  return count > 1 ? `群通话(${count}人)` : '群通话'
})
const remoteParticipantEntries = computed(() =>
  Object.entries(store.remoteStreams).map(([identity, stream]) => {
    const member = store.groupMeta?.members.find((m) => m.userId === identity)
    const videoMuteSignal = store.remoteMuted[identity]?.video ?? false
    void videoMuteSignal
    return {
      identity,
      stream,
      name: member?.name || identity,
      avatar: resolveAvatar(member?.avatar),
      muted: store.remoteMuted[identity] || { audio: false, video: false },
      videoEnabled: stream.getVideoTracks().some(
        (track) => track.readyState !== 'ended' && track.enabled
      )
    }
  })
)
const groupMediaEls = new Map<string, HTMLVideoElement | HTMLAudioElement>()
function setGroupVideoEl(key: string, el: HTMLVideoElement | HTMLAudioElement | null) {
  if (el) {
    groupMediaEls.set(key, el)
    registerRtOutputEl(`group-${key}`, el)
    const identity = key.startsWith('aud-') ? key.slice(4) : key
    const stream = store.remoteStreams[identity]
    if (stream) bindStream(el as HTMLMediaElement, stream, key.startsWith('aud-') ? 'audio' : 'video')
  } else {
    groupMediaEls.delete(key)
    registerRtOutputEl(`group-${key}`, null)
  }
}
watch(
  () => store.remoteStreams,
  async (streams) => {
    if (!isGroup.value) return
    await nextTick()
    for (const [identity, stream] of Object.entries(streams)) {
      const videoEl = groupMediaEls.get(identity)
      if (videoEl) await bindStream(videoEl as HTMLMediaElement, stream, 'video')
      const audioEl = groupMediaEls.get(`aud-${identity}`)
      if (audioEl) await bindStream(audioEl as HTMLMediaElement, stream, 'audio')
    }
  },
  { deep: true }
)

function setRemoteVideoEl(el: any) {
  remoteVideoEl.value = el
  registerRtOutputEl('remote-video', el || null)
}
function setRemoteAudioEl(el: any) {
  remoteAudioEl.value = el
  registerRtOutputEl('remote-audio', el || null)
}
function setMinimizedRemoteVideoEl(el: HTMLVideoElement | null) {
  minimizedRemoteVideoEl.value = el
  registerRtOutputEl('remote-video-minimized', el)
}

function logRemoteVideoEvent(name: string, event: Event) {
  const el = event.currentTarget as HTMLVideoElement | null
  console.info('[rtc] remote video element', {
    event: name,
    videoWidth: el?.videoWidth || 0,
    videoHeight: el?.videoHeight || 0,
    readyState: el?.readyState ?? -1,
    networkState: el?.networkState ?? -1,
    currentTime: el?.currentTime ?? 0,
    tracks: (el?.srcObject as MediaStream | null)?.getTracks().map((track) => ({
      kind: track.kind,
      readyState: track.readyState,
      enabled: track.enabled
    })) || []
  })
}

function logLocalVideoEvent(name: string, event: Event) {
  const el = event.currentTarget as HTMLVideoElement | null
  console.info('[rtc] local video element', {
    event: name,
    videoWidth: el?.videoWidth || 0,
    videoHeight: el?.videoHeight || 0,
    readyState: el?.readyState ?? -1,
    tracks: (el?.srcObject as MediaStream | null)?.getTracks().map((track) => ({
      kind: track.kind,
      readyState: track.readyState,
      enabled: track.enabled
    })) || []
  })
}

async function bindStream(
  el: HTMLMediaElement | null,
  stream: MediaStream | null,
  kind?: 'audio' | 'video'
) {
  if (!el) return
  const tracks = stream
    ? kind === 'video'
      ? stream.getVideoTracks()
      : kind === 'audio'
        ? stream.getAudioTracks()
        : stream.getTracks()
    : []
  const mediaStream = stream && tracks.length > 0 ? new MediaStream(tracks) : null
  const currentTracks = el.srcObject instanceof MediaStream ? el.srcObject.getTracks() : []
  const sameTracks =
    currentTracks.length === tracks.length &&
    currentTracks.every((track) => tracks.some((nextTrack) => nextTrack.id === track.id))
  if (!sameTracks) {
    el.srcObject = mediaStream
    el.load()
  }
  if (mediaStream) {
    await el.play().catch((error) => {
      console.warn('[rtc] media element play failed', {
        kind: kind || (el instanceof HTMLVideoElement ? 'video' : 'audio'),
        readyState: el.readyState,
        error: error instanceof Error ? error.message : String(error)
      })
    })
  }
}

watch(
  [peerStream, peerVideoSignature, () => store.mode, () => store.phase],
  async () => {
    await nextTick()
    await bindStream(remoteVideoEl.value, store.mode === 'video' ? peerStream.value : null, 'video')
    // Video calls still carry a remote Opus track. Keep it on a dedicated
    // audio element while the video element stays muted to avoid double play.
    await bindStream(remoteAudioEl.value, peerStream.value, 'audio')
  },
  { immediate: true }
)

watch(
  [() => store.localStream, localVideoSignature, () => store.phase, () => store.mode, () => store.devices.videoOff],
  async ([stream]) => {
    await nextTick()
    await bindStream(
      localVideoEl.value,
      store.mode === 'video' && !store.devices.videoOff ? stream : null,
      'video'
    )
  },
  { immediate: true }
)

watch(
  [peerStream, peerVideoSignature, () => store.minimized],
  async () => {
    await nextTick()
    await bindStream(
      minimizedRemoteVideoEl.value,
      store.mode === 'video' && store.minimized ? peerStream.value : null,
      'video'
    )
  },
  { immediate: true }
)

watch(
  () => store.phase,
  (phase) => {
    if (phase !== 'connected') videoLayoutSwapped.value = false
  }
)

watch(
  [peerStream, peerVideoSignature, () => store.remoteMuted[peerIdentity.value]?.video],
  ([stream, signature, muted]) => {
    console.info('[rtc] remote video state', {
      peerId: peerIdentity.value,
      hasStream: !!stream,
      videoTracks: stream?.getVideoTracks().length || 0,
      liveVideoTracks:
        stream?.getVideoTracks().filter((track) => track.readyState !== 'ended').length || 0,
      enabledVideoTracks:
        stream?.getVideoTracks().filter((track) => track.readyState !== 'ended' && track.enabled)
          .length || 0,
      signature,
      muted: !!muted
    })
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  window.removeEventListener('pointermove', moveCapsule)
  window.removeEventListener('pointerup', endCapsuleDrag)
  registerRtOutputEl('remote-video', null)
  registerRtOutputEl('remote-audio', null)
  registerRtOutputEl('remote-video-minimized', null)
  for (const key of groupMediaEls.keys()) registerRtOutputEl(`group-${key}`, null)
  groupMediaEls.clear()
})

onMounted(() => {
  store.init()
})
</script>

<style scoped lang="less">
.rtc-panel {
  position: fixed;
  z-index: 9997;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: #000;
  color: #fff;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.rtc-panel-minimized {
  background: transparent;
  pointer-events: none;

  .rtc-capsule {
    pointer-events: auto;
  }
}

.rtc-center {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10rem;
  padding-bottom: 120rem;

  .rtc-avatar.big {
    width: 96rem;
    height: 96rem;
    border-radius: 50%;
    object-fit: cover;
    &.grey {
      opacity: 0.6;
      filter: grayscale(1);
    }
  }

  .rtc-name {
    font-size: 22rem;
    font-weight: 600;
    max-width: 80vw;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    text-align: center;
  }

  .rtc-status {
    font-size: 14rem;
    color: rgba(255, 255, 255, 0.45);
  }

  .rtc-error {
    font-size: 13rem;
    color: #ff6b81;
  }
}

.rtc-actions {
  display: flex;
  justify-content: center;
  gap: 30rem;
  padding: 30rem 20rem 60rem;

  &.bottom-actions {
    position: absolute;
    bottom: 0;
    left: 0;
    right: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 16rem;
    padding: 20rem 16rem 30rem;
    z-index: 6;
    background: linear-gradient(to top, rgba(0, 0, 0, 0.6), transparent);
  }

  .rtc-action-row {
    width: min(100%, 360rem);
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 12rem;
  }

  .rtc-primary-row {
    .rtc-action-btn .btn-circle {
      width: 64rem;
      height: 64rem;

      img,
      .rtc-svg-icon {
        width: 30rem;
        height: 30rem;
      }
    }
  }

  .audio-primary-row {
    width: min(100%, 240rem);
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .rtc-secondary-row {
    width: min(100%, 300rem);
    min-height: 70rem;

    .rtc-action-btn {
      .btn-circle {
        width: 48rem;
        height: 48rem;

        img,
        .rtc-svg-icon {
          width: 24rem;
          height: 24rem;
        }
      }

      .btn-circle.hangup {
        width: 58rem;
        height: 58rem;
      }
    }
  }

  .secondary-hangup {
    grid-column: 2;
    justify-self: center;
  }

  .secondary-background {
    grid-column: 1;
    justify-self: center;

    &.active .btn-circle {
      background: rgba(20, 191, 95, 0.72);
    }
  }

  .secondary-flip {
    grid-column: 3;
    justify-self: center;
  }

  .rtc-action-btn {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8rem;

    .btn-circle {
      width: 54rem;
      height: 54rem;
      border-radius: 50%;
      background: rgba(255, 255, 255, 0.16);
      display: flex;
      align-items: center;
      justify-content: center;

      img {
        width: 26rem;
        height: 26rem;
      }

      .rtc-svg-icon {
        width: 26rem;
        height: 26rem;
        color: #fff;
      }

      &.off {
        background: rgba(255, 255, 255, 0.07);
        opacity: 0.5;
      }

      &.red {
        background: #fe2c55;
      }

      &.green {
        background: #14bf5f;
      }

      &.hangup {
        background: #fe2c55;
        width: 62rem;
        height: 62rem;
        box-shadow: none;
      }
    }

    span {
      font-size: 11rem;
      color: rgba(255, 255, 255, 0.5);
      white-space: nowrap;
    }
  }
}

.rtc-hangup-icon {
  width: 32rem;
  height: 32rem;
  color: #fff;
}

.rtc-connected {
  flex: 1;
  position: relative;
}

.rtc-minimize-btn {
  position: absolute;
  top: 16rem;
  left: 16rem;
  z-index: 10;
  width: 42rem;
  height: 42rem;
  padding: 0;
  border: 0;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: transparent;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;

  &:active {
    transform: scale(0.94);
  }

  .rtc-svg-icon {
    width: 22rem;
    height: 22rem;
  }
}

.rtc-video-stage {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;

  .rtc-video-self-layer {
    position: absolute;
    inset: 0;
    z-index: 1;
    transition: top 0.22s ease, right 0.22s ease, bottom 0.22s ease, left 0.22s ease,
      width 0.22s ease, height 0.22s ease, border-radius 0.22s ease;
  }

  .rtc-video-self {
    width: 100%;
    height: 100%;
    object-fit: cover;
    background: #000;
    transform: scaleX(-1);
  }

  .rtc-video-self-placeholder {
    width: 100%;
    height: 100%;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 12rem;
    background: #111;

    img {
      width: 110rem;
      height: 110rem;
      border-radius: 50%;
      object-fit: cover;
    }

    span {
      font-size: 13rem;
      color: rgba(255, 255, 255, 0.4);
    }
  }

  .rtc-video-remote-preview {
    position: absolute;
    top: 60rem;
    right: 16rem;
    width: 112rem;
    height: 150rem;
    border-radius: 12rem;
    overflow: hidden;
    background: rgba(0, 0, 0, 0.5);
    border: 1px solid rgba(255, 255, 255, 0.2);
    z-index: 2;
    transition: top 0.22s ease, right 0.22s ease, bottom 0.22s ease, left 0.22s ease,
      width 0.22s ease, height 0.22s ease, border-radius 0.22s ease;

  }

  .rtc-video-self-layer.is-clickable,
  .rtc-video-remote-preview.is-clickable {
    cursor: pointer;
  }

  .rtc-video-remote-preview-stream {
    width: 100%;
    height: 100%;
    object-fit: cover;

    &.rtc-video-remote-preview-hidden {
      display: none;
    }
  }

  .rtc-video-remote-preview-placeholder {
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    background: #1a1a1a;

    img {
      width: 56rem;
      height: 56rem;
      border-radius: 50%;
      object-fit: cover;
    }
  }

  &.rtc-video-stage-remote-focused {
    .rtc-video-remote-preview {
      top: 0;
      right: 0;
      bottom: 0;
      left: 0;
      width: auto;
      height: auto;
      border: 0;
      border-radius: 0;
      background: #000;
      z-index: 1;
    }

    .rtc-video-self-layer {
      top: 60rem;
      right: 16rem;
      bottom: auto;
      left: auto;
      width: 112rem;
      height: 150rem;
      border-radius: 12rem;
      overflow: hidden;
      background: rgba(0, 0, 0, 0.5);
      border: 1px solid rgba(255, 255, 255, 0.2);
      z-index: 2;
    }

    .rtc-video-self-placeholder {
      gap: 0;

      img {
        width: 56rem;
        height: 56rem;
      }

      span {
        display: none;
      }
    }
  }

  .rtc-top-bar {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    z-index: 3;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 4rem;
    padding: 36rem 16rem 20rem;
    background: linear-gradient(to bottom, rgba(0, 0, 0, 0.55), transparent);

    .rtc-peer-name {
      font-size: 18rem;
      font-weight: 600;
    }

    .rtc-timer {
      font-size: 13rem;
      color: rgba(255, 255, 255, 0.5);
    }

    .rtc-peer-left {
      font-size: 12rem;
      color: #ffb84d;
    }
  }
}

.audio-mid {
  .rtc-avatar.speaking {
    box-shadow: 0 0 18rem rgba(20, 191, 95, 0.55);
  }
}

.rtc-hidden-audio {
  display: none;
}

.rtc-reconnecting {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16rem;
  background: rgba(0, 0, 0, 0.88);

  .rtc-spinner {
    width: 44rem;
    height: 44rem;
    border-radius: 50%;
    border: 4rem solid rgba(255, 255, 255, 0.15);
    border-top-color: #fff;
    animation: rtc-spin 0.9s linear infinite;
  }
}

@keyframes rtc-spin {
  to {
    transform: rotate(360deg);
  }
}

.rtc-capsule {
  position: fixed;
  z-index: 9998;
  width: 164px;
  height: 104px;
  box-sizing: border-box;
  left: 16px;
  top: 16px;
  right: auto;
  bottom: auto;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px;
  border-radius: 12px;
  overflow: hidden;
  background: rgba(20, 20, 20, 0.94);
  border: 1px solid rgba(255, 255, 255, 0.22);
  box-shadow: 0 4px 16rem rgba(0, 0, 0, 0.4);
  touch-action: none;
  cursor: grab;

  &:active {
    cursor: grabbing;
  }

  .rtc-capsule-media {
    width: 74px;
    height: 92px;
    flex: 0 0 74px;
    border-radius: 8px;
    overflow: hidden;
    background: #111;
    display: flex;
    align-items: center;
    justify-content: center;

    img,
    .rtc-capsule-video {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }

    img {
      width: 46px;
      height: 46px;
      border-radius: 50%;
    }
  }

  .rtc-capsule-info {
    min-width: 0;
    display: flex;
    flex: 1;
    flex-direction: column;
    align-items: flex-start;
    gap: 2px;
  }

  .capsule-name {
    font-size: 11rem;
    max-width: 74px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .capsule-time {
    font-size: 13rem;
    font-weight: 600;
    color: rgba(255, 255, 255, 0.82);
  }
}

.rtc-fade-enter-active,
.rtc-fade-leave-active {
  transition: opacity 0.25s ease;
}
.rtc-fade-enter-from,
.rtc-fade-leave-to {
  opacity: 0;
}

.rtc-group-stage {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
}

.rtc-top-bar-group {
  position: relative;
  padding: 14rem 16rem 10rem;
  display: flex;
  align-items: center;
  gap: 10rem;
  background: linear-gradient(to bottom, rgba(0, 0, 0, 0.55), transparent);
  z-index: 2;
}

.rtc-group-grid {
  flex: 1;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  grid-auto-rows: 1fr;
  gap: 3rem;
  padding: 3rem;
  padding-bottom: 160rem;
}

.rtc-group-tile {
  position: relative;
  background: #1a1a1a;
  border-radius: 8rem;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2rem solid transparent;
  transition: border-color 0.2s;

  &.speaking {
    border-color: #14bf5f;
  }
}

.rtc-group-tile-video {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.rtc-group-tile-off {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;

  img {
    width: 52rem;
    height: 52rem;
    border-radius: 50%;
    object-fit: cover;
  }
}

.rtc-group-tile-name {
  position: absolute;
  bottom: 4rem;
  left: 6rem;
  font-size: 11rem;
  color: rgba(255, 255, 255, 0.8);
  background: rgba(0, 0, 0, 0.4);
  border-radius: 4rem;
  padding: 1rem 4rem;
  max-width: 80%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rtc-group-mute-icon {
  position: absolute;
  top: 4rem;
  right: 4rem;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.5);
  border-radius: 50%;
  width: 20rem;
  height: 20rem;
}

.rtc-svg-icon-sm {
  width: 12rem;
  height: 12rem;
  color: #ff6b81;
}
</style>
