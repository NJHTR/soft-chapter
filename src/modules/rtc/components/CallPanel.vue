<template>
  <Transition name="rtc-fade">
    <div v-if="store.phase !== 'idle'" class="rtc-panel" @click.stop>
      <!-- 最小化胶囊(connected 时) -->
      <div
        v-if="store.minimized && store.phase === 'connected'"
        class="rtc-capsule"
        @click="store.toggleMinimize()"
      >
        <span class="capsule-name">{{ peerName }}</span>
        <span class="capsule-time">{{ store.durationText }}</span>
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
              <div class="btn-circle hangup"><img :src="iconCallEnd" alt="" /></div>
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
              <div class="btn-circle red"><img :src="iconCallEnd" alt="" /></div>
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
              <div class="btn-circle hangup"><img :src="iconCallEnd" alt="" /></div>
              <span>挂断</span>
            </div>
          </div>
        </div>

        <!-- ═══ 通话中 ═══ -->
        <div v-else-if="store.phase === 'connected'" class="rtc-connected">
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
                    v-if="store.mode === 'video' && !entry.muted.video && entry.stream"
                    :ref="(el) => setGroupVideoEl(entry.identity, el as any)"
                    class="rtc-group-tile-video"
                    autoplay
                    playsinline
                  />
                  <audio
                    v-if="store.mode === 'audio' && entry.stream"
                    :ref="(el) => setGroupVideoEl(`aud-${entry.identity}`, el as any)"
                    autoplay
                    playsinline
                    class="rtc-hidden-audio"
                  />
                  <div
                    v-if="store.mode === 'audio' || entry.muted.video || !entry.stream"
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
            <div class="rtc-video-stage">
              <!-- 远端 -->
              <video
                v-if="peerVideoEnable"
                class="rtc-video-remote"
                :ref="setRemoteVideoEl"
                autoplay
                playsinline
              />
              <div v-else class="rtc-video-placeholder">
                <img :src="peerAvatar || defaultAvatar" alt="" />
                <span>视频已关闭</span>
              </div>
              <!-- 本地小窗 -->
              <div v-if="localVideoEnable" class="rtc-video-local">
                <video
                  ref="localVideoEl"
                  autoplay
                  playsinline
                  muted
                  class="rtc-video-local-stream"
                />
              </div>
              <div v-else-if="store.localStream" class="rtc-video-local rtc-avatar-only">
                <img :src="myAvatar || defaultAvatar" alt="" />
              </div>
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
            <div class="rtc-action-btn" @click="store.hangup()">
              <div class="btn-circle hangup">
                <img :src="iconCallEnd" alt="" />
              </div>
              <span>挂断</span>
            </div>
            <template v-if="store.mode === 'video'">
              <div class="rtc-action-btn" @click="store.toggleCamera()">
                <div class="btn-circle" :class="{ off: store.devices.videoOff }">
                  <img :src="store.devices.videoOff ? iconCameraOff : iconCameraOn" alt="" />
                </div>
                <span>摄像头</span>
              </div>
              <div class="rtc-action-btn" @click="store.switchCamera()">
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
                    <path
                      d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"
                    />
                  </svg>
                </div>
                <span>翻转</span>
              </div>
            </template>
            <div class="rtc-action-btn" @click="store.toggleMinimize()">
              <div class="btn-circle">
                <svg
                  class="rtc-svg-icon"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                >
                  <line x1="5" y1="12" x2="19" y2="12" />
                </svg>
              </div>
              <span>最小化</span>
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
import iconCallEnd from '@/assets/img/icon/message/chat/call-end.png'
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

const peerStream = computed(() => {
  const id = store.peerId
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
const peerVideoEnable = computed(
  () =>
    !!peerStream.value?.getVideoTracks().some((track) => track.readyState !== 'ended') &&
    !store.remoteMuted[store.peerId]?.video
)
const localVideoEnable = computed(
  () =>
    !!store.localStream?.getVideoTracks().some((track) => track.readyState !== 'ended') &&
    !store.devices.videoOff
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
    return {
      identity,
      stream,
      name: member?.name || identity,
      avatar: resolveAvatar(member?.avatar),
      muted: store.remoteMuted[identity] || { audio: false, video: false }
    }
  })
)
const groupMediaEls = new Map<string, HTMLVideoElement | HTMLAudioElement>()
function setGroupVideoEl(key: string, el: HTMLVideoElement | HTMLAudioElement | null) {
  if (el) {
    groupMediaEls.set(key, el)
    const identity = key.startsWith('aud-') ? key.slice(4) : key
    const stream = store.remoteStreams[identity]
    if (stream) bindStream(el as HTMLMediaElement, stream)
  } else {
    groupMediaEls.delete(key)
  }
}
watch(
  () => store.remoteStreams,
  async (streams) => {
    if (!isGroup.value) return
    await nextTick()
    for (const [identity, stream] of Object.entries(streams)) {
      const videoEl = groupMediaEls.get(identity)
      if (videoEl) await bindStream(videoEl as HTMLMediaElement, stream)
      const audioEl = groupMediaEls.get(`aud-${identity}`)
      if (audioEl) await bindStream(audioEl as HTMLMediaElement, stream)
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

async function bindStream(el: HTMLMediaElement | null, stream: MediaStream | null) {
  if (!el) return
  if (el.srcObject !== stream) el.srcObject = stream
  if (stream) await el.play().catch(() => {})
}

watch(
  [peerStream, () => store.mode, () => store.phase],
  async () => {
    await nextTick()
    await bindStream(remoteVideoEl.value, store.mode === 'video' ? peerStream.value : null)
    await bindStream(remoteAudioEl.value, store.mode === 'audio' ? peerStream.value : null)
  },
  { immediate: true }
)

watch(
  () => store.localStream,
  async (stream) => {
    await nextTick()
    await bindStream(localVideoEl.value, stream)
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  registerRtOutputEl('remote-video', null)
  registerRtOutputEl('remote-audio', null)
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
    background: linear-gradient(to top, rgba(0, 0, 0, 0.6), transparent);
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
        img {
          width: 30rem;
          height: 30rem;
        }
      }
    }

    span {
      font-size: 11rem;
      color: rgba(255, 255, 255, 0.5);
      white-space: nowrap;
    }
  }
}

.rtc-connected {
  flex: 1;
  position: relative;
}

.rtc-video-stage {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;

  .rtc-video-remote {
    width: 100%;
    height: 100%;
    object-fit: cover;
    background: #000;
  }

  .rtc-video-placeholder {
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

  .rtc-video-local {
    position: absolute;
    top: 60rem;
    right: 16rem;
    width: 100rem;
    height: 136rem;
    border-radius: 12rem;
    overflow: hidden;
    background: rgba(0, 0, 0, 0.5);
    border: 1px solid rgba(255, 255, 255, 0.2);
    z-index: 2;

    .rtc-video-local-stream {
      width: 100%;
      height: 100%;
      object-fit: cover;
      transform: scaleX(-1);
    }

    &.rtc-avatar-only {
      display: flex;
      align-items: center;
      justify-content: center;

      img {
        width: 56rem;
        height: 56rem;
        border-radius: 50%;
        object-fit: cover;
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
  right: 24rem;
  bottom: 120rem;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2rem;
  padding: 10rem 16rem;
  border-radius: 14rem;
  background: rgba(20, 191, 95, 0.92);
  box-shadow: 0 4px 16rem rgba(0, 0, 0, 0.4);

  .capsule-name {
    font-size: 11rem;
    max-width: 90rem;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .capsule-time {
    font-size: 13rem;
    font-weight: 600;
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
