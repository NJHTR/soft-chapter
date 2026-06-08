<template>
  <div class="Publish">
    <!-- ============================================ -->
    <!-- 拍摄态：摄像头 + 录制按钮                         -->
    <!-- ============================================ -->
    <template v-if="!showPreview && !showPostEdit && curTab !== 'text'">
      <video ref="videoEl" autoplay playsinline muted class="camera-preview"></video>

      <div class="top-bar" v-show="!isRecording">
        <Icon class="close-btn" icon="mingcute:close-line" @click="close" />
        <div class="music-btn">
          <Icon icon="vaadin:music" />
          <span>选择音乐</span>
        </div>
        <div class="right-tools">
          <div class="tool-item" @click="switchCamera">
            <Icon icon="tabler:refresh" />
          </div>
          <div class="tool-item" @click="toggleFlash">
            <Icon icon="pepicons-pop:electricity-off" />
          </div>
          <div class="tool-item">
            <Icon icon="mdi:gif" />
            <span class="tool-label">动图</span>
          </div>
          <div class="tool-item">
            <Icon icon="mdi:timer-outline" />
            <span class="tool-label">倒计时</span>
          </div>
        </div>
      </div>

      <!-- 分段拍进度指示器 -->
      <div class="segment-indicators" v-if="curMode === 'segment' && segmentList.length > 0">
        <div
          v-for="(seg, i) in segmentList"
          :key="i"
          class="seg-dot"
          :class="{ active: i === segmentList.length - 1 && isRecording }"
          :style="{ width: (seg.duration / maxRecordSeconds) * 100 + '%' }"
        ></div>
      </div>

      <div class="record-timer" v-if="isRecording">
        <span class="timer-dot"></span>
        {{ formatTime(recordSeconds) }}
        <span v-if="curMode === 'segment'" class="seg-count"> {{ segmentList.length + 1 }}段 </span>
      </div>

      <div class="bottom-zone" v-show="!isRecording">
        <div class="mode-tabs">
          <span
            v-for="m in modes"
            :key="m.key"
            :class="{ active: curMode === m.key }"
            @click="switchMode(m.key)"
            >{{ m.label }}</span
          >
        </div>
        <div class="action-row">
          <div class="side-action">
            <div class="side-icon-wrap"><Icon icon="mdi:magic" /></div>
            <span>特效</span>
          </div>
          <div
            class="record-btn-wrap"
            @click="curMode === 'photo' ? capturePhoto() : null"
            @pointerdown="curMode !== 'photo' ? startRecord() : null"
          >
            <div class="record-ring"></div>
            <div class="record-core" :class="coreClass">
              <!-- 拍照模式图标 -->
              <svg v-if="curMode === 'photo'" class="photo-icon" viewBox="0 0 24 24">
                <circle cx="12" cy="13" r="3" fill="white" />
                <path
                  d="M20 5h-3.17L15 3H9L7.17 5H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2z"
                  fill="white"
                />
              </svg>
              <!-- 分段拍图标 -->
              <svg v-else-if="curMode === 'segment'" class="segments-svg" viewBox="0 0 100 100">
                <circle
                  cx="50"
                  cy="50"
                  r="38"
                  fill="none"
                  stroke="#ff3b30"
                  stroke-width="18"
                  stroke-dasharray="45 195"
                  stroke-linecap="round"
                  transform="rotate(-90 50 50)"
                />
              </svg>
              <!-- 视频模式图标 -->
              <div v-else-if="curMode === 'video'" class="photo-dot"></div>
            </div>
          </div>
          <div class="side-action" @click="selectFile">
            <div class="album-thumb">
              <img v-if="albumPreview" :src="albumPreview" />
              <Icon v-else icon="mdi:image-outline" style="font-size: 24rem" />
            </div>
            <span>相册</span>
          </div>
        </div>
        <div class="footer-tabs">
          <span
            v-for="t in footerTabs"
            :key="t.key"
            :class="{ active: curTab === t.key }"
            @click="switchTab(t.key)"
            >{{ t.label }}</span
          >
        </div>
      </div>

      <!-- 录完分段拍的撤销按钮 -->
      <div
        class="undo-seg-bar"
        v-if="curMode === 'segment' && segmentList.length > 0 && !isRecording"
      >
        <span class="seg-summary"
          >{{ segmentList.length }}段 · {{ formatTime(totalSegDuration) }}</span
        >
        <button class="undo-seg-btn" @click="removeLastSegment">
          <Icon icon="mingcute:back-line" />撤销上段
        </button>
        <button class="done-seg-btn" @click="finishSegmentRecording">完成</button>
      </div>

      <!-- 录像中UI -->
      <div
        class="recording-ui"
        v-if="isRecording"
        @click="curMode === 'segment' ? stopRecord() : null"
        @pointerup="curMode !== 'segment' ? stopRecord() : null"
      >
        <svg class="progress-ring" viewBox="0 0 120 120">
          <circle class="ring-bg" cx="60" cy="60" r="54" />
          <circle
            class="ring-progress"
            cx="60"
            cy="60"
            r="54"
            :style="{ strokeDashoffset: dashOffset }"
          />
        </svg>
        <div class="recording-core">
          <div class="recording-pause"></div>
        </div>
      </div>
    </template>

    <!-- ============================================ -->
    <!-- 文字模式：编辑器                                   -->
    <!-- ============================================ -->
    <template v-if="!showPreview && !showPostEdit && curTab === 'text'">
      <div class="text-editor">
        <div class="text-top-bar">
          <Icon class="close-btn" icon="mingcute:close-line" @click="close" />
          <span class="text-editor-title">文字</span>
          <span class="done-btn" @click="finishTextEdit" :class="{ disabled: !textContent.trim() }"
            >完成</span
          >
        </div>
        <div class="text-preview-area" :style="{ background: textBgGradient }">
          <textarea
            ref="textInputEl"
            v-model="textContent"
            class="text-content-input"
            :class="{ small: textContent.length > 30, large: textContent.length <= 10 }"
            placeholder="输入文字..."
            maxlength="200"
          ></textarea>
        </div>
        <div class="text-bg-picker">
          <span class="bg-label">背景</span>
          <div class="bg-colors">
            <div
              v-for="bg in textBackgrounds"
              :key="bg.name"
              class="bg-color-dot"
              :class="{
                selected: textBgIndex === textBackgrounds.findIndex((b) => b.name === bg.name)
              }"
              :style="{ background: bg.gradient }"
              @click="selectTextBg(textBackgrounds.findIndex((b) => b.name === bg.name))"
            ></div>
          </div>
        </div>
      </div>
    </template>

    <!-- ============================================ -->
    <!-- 拍照/图集预览态                                    -->
    <!-- ============================================ -->
    <template v-if="showPreview && curMode === 'photo'">
      <!-- 多图轮播 -->
      <template v-if="photoList.length > 1">
        <div
          class="carousel-container"
          @touchstart="onSwipeStart"
          @touchmove="onSwipeMove"
          @touchend="onSwipeEnd"
        >
          <div class="carousel-track" :style="{ transform: `translateX(-${carouselIdx * 100}%)` }">
            <div v-for="(img, i) in photoList" :key="i" class="carousel-slide">
              <img :src="getPhotoUrl(img)" class="carousel-img" />
            </div>
          </div>
        </div>
        <!-- 指示点 -->
        <div class="carousel-dots">
          <span
            v-for="(_, i) in photoList"
            :key="i"
            class="dot"
            :class="{ active: i === carouselIdx }"
            @click="carouselIdx = i"
          ></span>
        </div>
        <!-- 图片序号 -->
        <div class="carousel-counter">{{ carouselIdx + 1 }} / {{ photoList.length }}</div>
      </template>
      <!-- 单图 -->
      <img v-else :src="getPhotoUrl(photoList[0])" class="photo-preview" />

      <div class="preview-top-bar">
        <Icon icon="material-symbols:arrow-back" @click="backToCamera" />
        <span class="preview-label-text">{{
          isTextPreview ? '文字' : photoList.length > 1 ? `${photoList.length}张图片` : '照片预览'
        }}</span>
      </div>

      <!-- 多图排序工具栏 -->
      <div v-if="photoList.length > 1" class="carousel-sort-bar">
        <button class="sort-btn" @click="movePhotoLeft(carouselIdx)" :disabled="carouselIdx === 0">
          <Icon icon="mingcute:arrow-left-circle-line" />左移
        </button>
        <button
          class="sort-btn"
          @click="removePhotoAt(carouselIdx)"
          :disabled="photoList.length <= 1"
        >
          <Icon icon="mingcute:delete-line" />删除
        </button>
        <button
          class="sort-btn"
          @click="movePhotoRight(carouselIdx)"
          :disabled="carouselIdx === photoList.length - 1"
        >
          右移<Icon icon="mingcute:arrow-right-circle-line" />
        </button>
      </div>

      <div class="photo-toolbar">
        <div class="pt-item" @click="insertHash">
          <div class="pt-icon-wrap"><Icon icon="mingcute:font-fill" /></div>
          <span>文字</span>
        </div>
        <div class="pt-item">
          <div class="pt-icon-wrap"><Icon icon="tabler:tag" /></div>
          <span>话题</span>
        </div>
        <div class="pt-item">
          <div class="pt-icon-wrap"><Icon icon="ph:sticker" /></div>
          <span>贴纸</span>
        </div>
      </div>
      <div class="preview-bottom">
        <div class="add-more-btn" @click="selectMorePhotos" v-if="photoList.length < 9">
          <Icon icon="mingcute:add-circle-line" /><span>添加图片 ({{ photoList.length }}/9)</span>
        </div>
        <div class="preview-bottom-row" style="justify-content: space-between">
          <div></div>
          <div class="next-btn" @click.stop="goPostEdit">下一步</div>
        </div>
      </div>
    </template>

    <!-- ============================================ -->
    <!-- 预览态：视频回放 + 编辑入口                        -->
    <!-- ============================================ -->
    <template v-if="showPreview && curMode !== 'photo'">
      <video
        ref="previewEl"
        class="camera-preview"
        :src="previewSrc"
        loop
        playsinline
        muted
        @click="handlePreviewClick"
        @loadedmetadata="onPreviewMeta"
        @ended="onPreviewEnded"
      ></video>
      <div v-if="previewMuted" class="unmute-hint" @click="unmutePreview">
        <Icon icon="flowbite:volume-mute-solid" />
        <span>点击取消静音</span>
      </div>

      <div class="preview-top-bar">
        <Icon icon="material-symbols:arrow-back" @click="backToCamera" />
        <span class="music-selector" @click="showMusicPicker = true">
          <Icon icon="vaadin:music" />
          <span class="music-name-text">{{ selectedMusic ? selectedMusic.name : '选择音乐' }}</span>
          <Icon
            v-if="selectedMusic"
            icon="mingcute:close-circle-fill"
            class="remove-music-icon"
            @click.stop="removeMusic"
          />
        </span>
        <Icon icon="mdi:scissors" class="clip-btn" @click="goToEditor" />
      </div>

      <div class="preview-toolbar">
        <div class="pt-item">
          <div class="pt-icon-wrap"><Icon icon="mdi:scissors" /></div>
          <span>剪辑</span>
        </div>
        <div class="pt-item">
          <div class="pt-icon-wrap"><Icon icon="mingcute:font-fill" /></div>
          <span>文字</span>
        </div>
        <div class="pt-item">
          <div class="pt-icon-wrap"><Icon icon="tabler:tag" /></div>
          <span>话题</span>
        </div>
        <div class="pt-item">
          <div class="pt-icon-wrap"><Icon icon="ph:sticker" /></div>
          <span>贴纸</span>
        </div>
      </div>

      <div class="preview-bottom">
        <div class="progress-bar" @click.stop>
          <div class="progress-track">
            <div class="progress-fill" :style="{ width: previewProgress + '%' }"></div>
            <div class="progress-thumb" :style="{ left: previewProgress + '%' }"></div>
          </div>
        </div>

        <div class="preview-bottom-row">
          <div class="daily-btn">
            <img v-if="userAvatar" :src="userAvatar" class="daily-avatar" />
            <span>限时日常</span>
          </div>
          <div class="play-pause-btn glass-btn" @click.stop="togglePreviewPlay">
            <Icon v-if="previewPlaying" icon="mingcute:pause-fill" />
            <Icon v-else icon="mingcute:play-fill" />
          </div>
          <div class="next-btn" @click.stop="goPostEdit">下一步</div>
        </div>
      </div>

      <!-- 音乐选择面板 -->
      <transition name="sheet-fade">
        <div v-if="showMusicPicker" class="music-picker-mask" @click.self="showMusicPicker = false">
          <div class="music-picker-sheet">
            <div class="sheet-header">
              <span class="sheet-title">选择音乐</span>
              <Icon icon="mingcute:close-line" @click="showMusicPicker = false" />
            </div>
            <div class="sheet-list" v-if="musicList.length">
              <div class="sheet-item" :class="{ selected: !selectedMusic }" @click="removeMusic">
                <div class="item-icon no-music-icon">
                  <Icon icon="mingcute:forbid-circle-line" />
                </div>
                <span>不使用音乐</span>
              </div>
              <div
                v-for="m in musicList"
                :key="m.id"
                class="sheet-item"
                :class="{ selected: selectedMusic?.id === m.id }"
                @click="selectMusicItem(m)"
              >
                <img v-if="m.cover_url" :src="m.cover_url" class="item-cover" />
                <div v-else class="item-icon">
                  <Icon icon="vaadin:music" />
                </div>
                <div class="item-info">
                  <span class="item-name">{{ m.name }}</span>
                  <span class="item-artist">{{ m.artist || '未知歌手' }}</span>
                </div>
                <Icon
                  v-if="selectedMusic?.id === m.id"
                  icon="mingcute:check-circle-fill"
                  class="check-icon"
                />
              </div>
            </div>
            <div v-else class="sheet-loading">
              <span>加载中...</span>
            </div>
          </div>
        </div>
      </transition>
    </template>

    <!-- ============================================ -->
    <!-- 发布编辑态：标题/描述/话题                         -->
    <!-- ============================================ -->
    <template v-if="showPostEdit">
      <!-- 封面区域 -->
      <div class="post-edit-cover" @click="captureCoverFrame">
        <img v-if="coverDataUrl" :src="coverDataUrl" class="cover-img" />
        <div v-else class="cover-placeholder"></div>
        <div class="edit-cover-btn">编辑封面</div>
      </div>

      <!-- 顶栏 -->
      <div class="post-edit-top-bar">
        <Icon icon="material-symbols:arrow-back" @click="backToPreview" />
        <span class="preview-label" @click="backToPreview">预览</span>
      </div>

      <!-- 输入区 -->
      <div class="post-edit-inputs">
        <input
          ref="titleInput"
          v-model="postTitle"
          class="input-title"
          type="text"
          placeholder="添加标题"
          maxlength="55"
        />
        <textarea
          ref="descInput"
          v-model="postDesc"
          class="input-desc"
          placeholder="添加作品描述"
          maxlength="500"
          rows="3"
        ></textarea>
      </div>

      <!-- 辅助选项 -->
      <div class="post-edit-options">
        <div class="opt-item" @click="insertHash"># 话题</div>
        <div class="opt-item" @click="insertAt">@ 朋友</div>
        <div class="opt-item" @click="handleLocation">添加地点</div>
        <div class="opt-item" @click="handleTag">添加标签</div>
      </div>

      <!-- 草稿提示 -->
      <div class="draft-tip">作品将自动保存为草稿</div>

      <!-- 底部按钮 -->
      <div class="post-edit-bottom">
        <div class="share-btn" @click="handleShare">
          <Icon icon="ri:share-forward-line" />
          <span>分享</span>
        </div>
        <div class="daily-post-btn" @click="setDaily">
          <img v-if="userAvatar" :src="userAvatar" class="daily-avatar-sm" />
          <span>限时日常</span>
        </div>
        <div class="publish-btn" @click="doUpload" :class="{ loading: uploading }">
          {{ uploading ? '上传中...' : '发作品' }}
        </div>
      </div>

      <canvas ref="coverCanvas" style="display: none"></canvas>
    </template>

    <input
      ref="fileInput"
      type="file"
      accept="video/*,image/*"
      multiple
      style="display: none"
      @change="onFileSelected"
    />
  </div>
</template>
<script setup lang="ts">
import { onMounted, onActivated, onBeforeUnmount, ref, computed, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { uploadVideo, uploadImage } from '@/api/user'
import { publishVideo } from '@/api/videos'
import { getHotMusic } from '@/api/music'
import { _notice } from '@/utils'
import bus, { EVENT_KEY } from '@/utils/bus'
import { useBaseStore } from '@/store/pinia'

defineOptions({ name: 'Publish' })

const MAX_SECONDS = 15
const CIRCUMFERENCE = 2 * Math.PI * 54

const router = useRouter()
const videoEl = ref<HTMLVideoElement | null>(null)
const previewEl = ref<HTMLVideoElement | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const titleInput = ref<HTMLInputElement | null>(null)
const descInput = ref<HTMLTextAreaElement | null>(null)
const coverCanvas = ref<HTMLCanvasElement | null>(null)
const textInputEl = ref<HTMLTextAreaElement | null>(null)
const isRecording = ref(false)
const uploading = ref(false)
const recordedBlob = ref<Blob | null>(null)
const recordSeconds = ref(0)
const modeIndex = ref(2)
const footerIndex = ref(1)
const albumPreview = ref('')
const showPreview = ref(false)
const showPostEdit = ref(false)
const previewSrc = ref('')
const previewPlaying = ref(true)
const previewProgress = ref(0)
const previewMuted = ref(true)
const userAvatar = ref('')
const coverDataUrl = ref('')
const postTitle = ref('')
const postDesc = ref('')
const showMusicPicker = ref(false)
const selectedMusic = ref<{
  id: number
  name: string
  artist?: string
  cover_url?: string
  play_url?: string
  duration?: number
} | null>(null)
const musicList = ref<any[]>([])
const bgmStartOffset = ref(0)
const bgmVolume = ref(0.7)
const videoDuration = ref(15)

// 分段拍
const maxRecordSeconds = ref(15)
const segmentList = ref<{ blob: Blob; duration: number }[]>([])
const totalSegDuration = computed(() => segmentList.value.reduce((s, seg) => s + seg.duration, 0))

// 拍照 & 多图轮播
const photoBlob = ref<Blob | null>(null)
const photoList = ref<Blob[]>([])
const carouselIdx = ref(0)

// 滑动相关
let swipeStartX = 0
let swipeMoved = 0
function onSwipeStart(e: TouchEvent) {
  swipeStartX = e.touches[0].clientX
  swipeMoved = 0
}
function onSwipeMove(e: TouchEvent) {
  swipeMoved = e.touches[0].clientX - swipeStartX
}
function onSwipeEnd() {
  const threshold = 60
  if (swipeMoved > threshold && carouselIdx.value > 0) {
    carouselIdx.value--
  } else if (swipeMoved < -threshold && carouselIdx.value < photoList.value.length - 1) {
    carouselIdx.value++
  }
}

function getPhotoUrl(blob: Blob | undefined): string {
  if (!blob) return ''
  // 缓存 blob URL
  const key = '_blobUrl'
  if (!(blob as any)[key]) {
    ;(blob as any)[key] = URL.createObjectURL(blob)
  }
  return (blob as any)[key]
}

function movePhotoLeft(idx: number) {
  if (idx <= 0) return
  const arr = photoList.value
  ;[arr[idx - 1], arr[idx]] = [arr[idx], arr[idx - 1]]
  carouselIdx.value = idx - 1
}

function movePhotoRight(idx: number) {
  if (idx >= photoList.value.length - 1) return
  const arr = photoList.value
  ;[arr[idx + 1], arr[idx]] = [arr[idx], arr[idx + 1]]
  carouselIdx.value = idx + 1
}

function removePhotoAt(idx: number) {
  if (photoList.value.length <= 1) return
  photoList.value.splice(idx, 1)
  if (carouselIdx.value >= photoList.value.length) {
    carouselIdx.value = photoList.value.length - 1
  }
}

function selectMorePhotos() {
  selectFile()
}

// 文字模式
const textContent = ref('')
const textBgIndex = ref(0)
const textBackgrounds = [
  { name: '渐变红', gradient: 'linear-gradient(135deg, #ff416c, #ff4b2b)' },
  { name: '渐变紫', gradient: 'linear-gradient(135deg, #667eea, #764ba2)' },
  { name: '渐变蓝', gradient: 'linear-gradient(135deg, #2193b0, #6dd5ed)' },
  { name: '渐变绿', gradient: 'linear-gradient(135deg, #11998e, #38ef7d)' },
  { name: '纯黑', gradient: '#1a1a1a' },
  { name: '纯白', gradient: '#f5f5f5' }
]
const textBgGradient = computed(
  () => textBackgrounds[textBgIndex.value]?.gradient || textBackgrounds[0].gradient
)

const modes = [
  { key: 'segment', label: '分段拍' },
  { key: 'photo', label: '照片' },
  { key: 'video', label: '视频' }
]
const footerTabs = [
  { key: 'text', label: '文字' },
  { key: 'camera', label: '相机' },
  { key: 'live', label: '开直播' }
]

const curMode = computed(() => modes[modeIndex.value]?.key || 'video')
const curTab = computed(() => footerTabs[footerIndex.value]?.key || 'camera')

const isTextPreview = computed(() => !!textContent.value?.trim())

const coreClass = computed(() => {
  if (isRecording.value) return 'recording'
  if (curMode.value === 'photo') return 'is-photo'
  if (curMode.value === 'segment') return 'is-segment'
  return 'is-video'
})

let mediaStream: MediaStream | null = null
let mediaRecorder: MediaRecorder | null = null
let chunks: BlobPart[] = []
let facingMode = 'user'
let recordStartTime = 0
let recordTimer: ReturnType<typeof setInterval> | null = null
let progressTimer: ReturnType<typeof setInterval> | null = null
let actualDuration = 0
let previewMusicAudio: HTMLAudioElement | null = null
let previewSyncRafId = 0
const trimStart = ref(0)
const trimEnd = ref(0)
const editorSegments = ref<any[]>([])

const dashOffset = computed(() => {
  const progress = Math.min(recordSeconds.value / MAX_SECONDS, 1)
  return CIRCUMFERENCE * (1 - progress)
})

function formatTime(s: number) {
  const m = Math.floor(s / 60)
  const sec = s % 60
  return `${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}`
}

function close() {
  stopCamera()
  router.back()
}

function stopCamera() {
  if (mediaRecorder && mediaRecorder.state !== 'inactive') mediaRecorder.stop()
  if (recordTimer) {
    clearInterval(recordTimer)
    recordTimer = null
  }
  if (progressTimer) {
    clearInterval(progressTimer)
    progressTimer = null
  }
  if (mediaStream) {
    mediaStream.getTracks().forEach((t) => t.stop())
    mediaStream = null
  }
}

async function startCamera() {
  try {
    mediaStream = await navigator.mediaDevices.getUserMedia({
      video: {
        facingMode,
        width: { ideal: 720 },
        height: { ideal: 1280 }
      },
      audio: {
        echoCancellation: true,
        noiseSuppression: true
      }
    })
    const at = mediaStream.getAudioTracks()
    if (at.length === 0) {
      console.warn('[Record] 没有获取到音频轨道，录制将无声')
    } else {
      console.log('[Record] 音频轨道:', at[0].label, 'enabled:', at[0].enabled)
    }
    if (videoEl.value) {
      videoEl.value.srcObject = mediaStream
      videoEl.value.play()
    }
  } catch (e) {
    console.error('Camera error:', e)
    _notice('无法访问摄像头或麦克风，请检查权限')
  }
}

async function switchCamera() {
  facingMode = facingMode === 'user' ? 'environment' : 'user'
  stopCamera()
  await startCamera()
}

function toggleFlash() {
  _notice('闪光灯功能未实现')
}

function switchMode(key: string) {
  if (isRecording.value) return
  const idx = modes.findIndex((m) => m.key === key)
  if (idx >= 0) modeIndex.value = idx
}

function switchTab(key: string) {
  const idx = footerTabs.findIndex((t) => t.key === key)
  if (idx >= 0) footerIndex.value = idx
}

// ======= 拍照模式 =======
function capturePhoto() {
  const v = videoEl.value
  if (!v) return
  const cvs = document.createElement('canvas')
  cvs.width = v.videoWidth || 720
  cvs.height = v.videoHeight || 1280
  const ctx = cvs.getContext('2d')
  if (!ctx) return
  ctx.drawImage(v, 0, 0, cvs.width, cvs.height)
  cvs.toBlob(
    (blob) => {
      if (!blob) return
      photoBlob.value = blob
      photoList.value = [blob]
      carouselIdx.value = 0
      previewSrc.value = URL.createObjectURL(blob)
      showPreview.value = true
      stopCamera()
    },
    'image/jpeg',
    0.9
  )
}

// ======= 录制逻辑 =======
function toggleRecord() {
  if (isRecording.value) {
    stopRecord()
  } else {
    startRecord()
  }
}

function startRecord() {
  if (!mediaStream) return

  // 分段拍：恢复暂停的录制
  if (curMode.value === 'segment' && mediaRecorder && mediaRecorder.state === 'paused') {
    mediaRecorder.resume()
    recordStartTime = Date.now()
    isRecording.value = true
    recordTimer = setInterval(() => {
      recordSeconds.value = Math.floor((Date.now() - recordStartTime) / 1000)
      const total = totalSegDuration.value + recordSeconds.value
      if (total >= maxRecordSeconds.value) stopRecord()
    }, 200)
    return
  }

  // 新录制
  chunks = []
  recordStartTime = Date.now()
  recordSeconds.value = 0

  const mimeTypes = [
    'video/webm;codecs=vp8,opus',
    'video/webm;codecs=vp9,opus',
    'video/webm;codecs=vp8,vorbis',
    'video/webm',
    'video/mp4'
  ]
  let selectedMime = ''
  for (const mt of mimeTypes) {
    if (MediaRecorder.isTypeSupported(mt)) {
      selectedMime = mt
      break
    }
  }
  console.log(
    '[Record] 使用 mimeType:',
    selectedMime,
    '音轨数:',
    mediaStream.getAudioTracks().length
  )

  try {
    mediaRecorder = new MediaRecorder(mediaStream, {
      mimeType: selectedMime || undefined,
      audioBitsPerSecond: 128000
    })
  } catch {
    mediaRecorder = new MediaRecorder(mediaStream)
  }
  mediaRecorder.ondataavailable = (e) => {
    if (e.data.size > 0) chunks.push(e.data)
  }
  mediaRecorder.onstop = () => {
    actualDuration = (Date.now() - recordStartTime) / 1000
    const mime = selectedMime || mediaRecorder?.mimeType || 'video/webm'
    const blob = new Blob(chunks, { type: mime })
    console.log('[Record] 录制完成, 大小:', blob.size, '时长:', actualDuration)
    recordedBlob.value = blob
    if (curMode.value === 'segment') {
      // 分段拍：保存段落后重置chunks
      segmentList.value.push({ blob, duration: actualDuration })
      chunks = []
      actualDuration = 0
      recordSeconds.value = 0
    } else {
      openPreview(blob)
    }
  }
  mediaRecorder.start()
  isRecording.value = true
  recordTimer = setInterval(() => {
    recordSeconds.value = Math.floor((Date.now() - recordStartTime) / 1000)
    const total = totalSegDuration.value + recordSeconds.value
    if (curMode.value === 'segment' && total >= maxRecordSeconds.value) {
      stopRecord()
    } else if (curMode.value !== 'segment' && recordSeconds.value >= MAX_SECONDS) {
      stopRecord()
    }
  }, 200)
}

function stopRecord() {
  if (!mediaRecorder) return
  if (curMode.value === 'segment' && mediaRecorder.state === 'recording') {
    // 分段拍：暂停而不是停止
    mediaRecorder.pause()
    if (recordTimer) {
      clearInterval(recordTimer)
      recordTimer = null
    }
    isRecording.value = false
    // 保存当前段
    if (chunks.length > 0) {
      const curBlob = new Blob(chunks, { type: mediaRecorder.mimeType || 'video/webm' })
      const curDur = recordSeconds.value
      segmentList.value.push({ blob: curBlob, duration: curDur })
      chunks = []
      actualDuration = 0
      recordSeconds.value = 0
    }
    return
  }
  if (mediaRecorder.state !== 'inactive') mediaRecorder.stop()
  if (recordTimer) {
    clearInterval(recordTimer)
    recordTimer = null
  }
  isRecording.value = false
}

function removeLastSegment() {
  segmentList.value.pop()
}

function finishSegmentRecording() {
  if (segmentList.value.length === 0) return
  // 合并所有片段为一个 Blob
  const allBlobs = segmentList.value.map((s) => s.blob)
  const merged = new Blob(allBlobs, { type: allBlobs[0].type || 'video/webm' })
  actualDuration = totalSegDuration.value
  recordedBlob.value = merged
  segmentList.value = []
  openPreview(merged)
}

// ======= 文字模式 =======
function selectTextBg(idx: number) {
  textBgIndex.value = idx
}

function finishTextEdit() {
  const text = textContent.value.trim()
  if (!text) return
  // 将文字渲染到 canvas → 导出为图片
  const cvs = document.createElement('canvas')
  cvs.width = 1080
  cvs.height = 1920
  const ctx = cvs.getContext('2d')
  if (!ctx) return

  // 绘制背景
  const bg = textBackgrounds[textBgIndex.value]
  const isLightBg = bg.name === '纯白'
  if (bg.gradient.includes('linear-gradient')) {
    // 简单纯色填充，避免解析CSS渐变
    const colors: Record<string, string> = {
      渐变红: '#ff416c',
      渐变紫: '#667eea',
      渐变蓝: '#2193b0',
      渐变绿: '#11998e'
    }
    ctx.fillStyle = colors[bg.name] || '#ff416c'
  } else {
    ctx.fillStyle = bg.gradient
  }
  ctx.fillRect(0, 0, cvs.width, cvs.height)

  // 绘制文字
  ctx.fillStyle = isLightBg ? '#1a1a1a' : '#ffffff'
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'

  const maxWidth = cvs.width - 160
  const fontSize = text.length > 30 ? 52 : text.length > 10 ? 64 : 80
  ctx.font = `bold ${fontSize}px "PingFang SC", "Microsoft YaHei", sans-serif`

  // 换行
  const lines = wrapText(ctx, text, maxWidth)
  const lineHeight = fontSize * 1.4
  const startY = cvs.height / 2 - ((lines.length - 1) * lineHeight) / 2

  lines.forEach((line, i) => {
    ctx.fillText(line, cvs.width / 2, startY + i * lineHeight)
  })

  cvs.toBlob(
    (blob) => {
      if (!blob) return
      photoBlob.value = blob
      photoList.value = [blob]
      carouselIdx.value = 0
      previewSrc.value = URL.createObjectURL(blob)
      showPreview.value = true
      curMode.value = 'photo' // 复用照片预览
    },
    'image/jpeg',
    0.9
  )
}

function wrapText(ctx: CanvasRenderingContext2D, text: string, maxWidth: number): string[] {
  const lines: string[] = []
  let current = ''
  for (const char of text) {
    const test = current + char
    if (ctx.measureText(test).width > maxWidth && current.length > 0) {
      lines.push(current)
      current = char
    } else {
      current = test
    }
  }
  if (current) lines.push(current)
  return lines
}

function openPreview(blob: Blob) {
  stopCamera()
  previewSrc.value = URL.createObjectURL(blob)
  showPreview.value = true
  previewPlaying.value = true
  previewProgress.value = 0
  previewMuted.value = true
  userAvatar.value = useBaseStore().userinfo?.avatar_168x168?.url_list?.[0] || ''

  fetchHotMusicAndAutoSelect().then(() => initPreviewMusic())

  nextTick(() => {
    const v = previewEl.value
    if (!v) return
    v.muted = true
    v.play().catch(() => {})
    progressTimer = setInterval(() => {
      if (v.duration && !v.paused) {
        previewProgress.value = (v.currentTime / v.duration) * 100
      }
    }, 100)
  })
}

function handlePreviewClick() {
  if (previewMuted.value) {
    unmutePreview()
  } else {
    togglePreviewPlay()
  }
}

function unmutePreview() {
  const v = previewEl.value
  if (!v) return
  v.muted = false
  previewMuted.value = false
  v.play().catch(() => {})
  startPreviewMusic()
  previewSyncRafId = requestAnimationFrame(previewMusicSyncLoop)
}

async function fetchHotMusicAndAutoSelect() {
  try {
    const res = await getHotMusic({ limit: 10 })
    if (res.success && res.data?.length) {
      musicList.value = res.data
      if (!selectedMusic.value) {
        selectedMusic.value = res.data[0]
      }
    }
  } catch {
    // 音乐接口不可用时静默降级
  }
}

function initPreviewMusic() {
  stopPreviewMusic()
  if (!selectedMusic.value?.play_url) return
  previewMusicAudio = new Audio(selectedMusic.value.play_url)
  previewMusicAudio.volume = bgmVolume.value
  previewMusicAudio.preload = 'auto'
}

function startPreviewMusic() {
  if (!previewMusicAudio || !previewEl.value || !selectedMusic.value) return
  const vt = previewEl.value.currentTime
  const dur = previewEl.value.duration || videoDuration.value
  const mt = vt - bgmStartOffset.value
  const musicEnd = bgmStartOffset.value + dur
  if (mt >= 0 && vt < musicEnd) {
    previewMusicAudio.currentTime = mt
    previewMusicAudio.play().catch(() => {})
  }
}

function stopPreviewMusic() {
  if (previewMusicAudio) {
    previewMusicAudio.pause()
    previewMusicAudio = null
  }
  cancelAnimationFrame(previewSyncRafId)
}

let lastPreviewVt = 0
let loopJustHandled = false

function previewMusicSyncLoop() {
  if (!previewPlaying.value) {
    previewSyncRafId = requestAnimationFrame(previewMusicSyncLoop)
    return
  }
  const v = previewEl.value
  if (!v) {
    previewSyncRafId = requestAnimationFrame(previewMusicSyncLoop)
    return
  }

  const audio = previewMusicAudio
  if (!audio || !selectedMusic.value || previewMuted.value) {
    previewSyncRafId = requestAnimationFrame(previewMusicSyncLoop)
    return
  }

  const vt = v.currentTime
  const dur = v.duration || videoDuration.value
  const mt = vt - bgmStartOffset.value

  // 检测视频循环：currentTime 大幅回跳（>0.5s 的回退认为是循环）
  const timeJumpedBack = vt < lastPreviewVt - 0.5
  lastPreviewVt = vt

  if (timeJumpedBack && !loopJustHandled) {
    // 视频循环了 → 音乐从头开始
    audio.pause()
    audio.currentTime = 0
    audio.play().catch(() => {})
    loopJustHandled = true
  } else if (!timeJumpedBack) {
    loopJustHandled = false
  }

  // 正常播放期间：微调音乐位置保持同步（仅在非循环帧）
  if (!timeJumpedBack && mt >= 0 && vt < bgmStartOffset.value + dur) {
    if (audio.paused) {
      audio.currentTime = mt
      audio.play().catch(() => {})
    } else if (Math.abs(audio.currentTime - mt) > 0.3) {
      audio.currentTime = mt
    }
  }

  previewSyncRafId = requestAnimationFrame(previewMusicSyncLoop)
}

function selectMusicItem(m: any) {
  selectedMusic.value = m
  showMusicPicker.value = false
  initPreviewMusic()
  if (!previewMuted.value && previewPlaying.value) {
    startPreviewMusic()
  }
}

function removeMusic() {
  selectedMusic.value = null
  showMusicPicker.value = false
  stopPreviewMusic()
}

function goToEditor() {
  const v = previewEl.value
  const duration = v?.duration || actualDuration || 15
  sessionStorage.setItem('video_editor_video_src', previewSrc.value)
  sessionStorage.setItem('video_editor_duration', String(duration))
  sessionStorage.setItem('video_editor_bgm_start_offset', String(bgmStartOffset.value))
  sessionStorage.setItem('video_editor_bgm_volume', String(bgmVolume.value))
  if (selectedMusic.value) {
    sessionStorage.setItem('video_editor_music', JSON.stringify(selectedMusic.value))
  } else {
    sessionStorage.removeItem('video_editor_music')
  }
  stopPreviewMusic()
  if (progressTimer) {
    clearInterval(progressTimer)
    progressTimer = null
  }
  if (v) v.pause()
  router.push('/video-editor')
}

function togglePreviewPlay() {
  const v = previewEl.value
  if (!v) return
  if (v.paused) {
    v.play()
    previewPlaying.value = true
    startPreviewMusic()
    previewSyncRafId = requestAnimationFrame(previewMusicSyncLoop)
  } else {
    v.pause()
    previewPlaying.value = false
    if (previewMusicAudio) previewMusicAudio.pause()
    cancelAnimationFrame(previewSyncRafId)
  }
}

function onPreviewMeta() {
  if (previewEl.value?.duration) {
    videoDuration.value = previewEl.value.duration
  }
}

function onPreviewEnded() {
  previewProgress.value = 100
}

function backToCamera() {
  showPreview.value = false
  previewSrc.value = ''
  recordedBlob.value = null
  photoBlob.value = null
  photoList.value = []
  carouselIdx.value = 0
  previewMuted.value = true
  stopPreviewMusic()
  if (progressTimer) {
    clearInterval(progressTimer)
    progressTimer = null
  }
  if (curTab.value === 'text') {
    textContent.value = ''
  }
  startCamera()
}

function goPostEdit() {
  showPreview.value = false
  stopPreviewMusic()
  if (progressTimer) {
    clearInterval(progressTimer)
    progressTimer = null
  }
  if (previewEl.value) {
    previewEl.value.pause()
  }
  showPostEdit.value = true
  // 只有视频才抓封面，照片直接用预览图（多图用第一张）
  if (curMode.value !== 'photo' && previewSrc.value) {
    captureCoverFrame()
  } else if (photoList.value.length > 0) {
    coverDataUrl.value = getPhotoUrl(photoList.value[0])
  } else if (previewSrc.value) {
    coverDataUrl.value = previewSrc.value
  }
}

function backToPreview() {
  showPostEdit.value = false
  showPreview.value = true
  // 照片模式不需要视频播放逻辑
  if (curMode.value === 'photo') return

  nextTick(() => {
    if (previewEl.value) {
      previewEl.value.muted = previewMuted.value
      previewEl.value.play().catch(() => {})
      previewPlaying.value = true
      if (!previewMuted.value) {
        startPreviewMusic()
        previewSyncRafId = requestAnimationFrame(previewMusicSyncLoop)
      }
    }
    progressTimer = setInterval(() => {
      const v = previewEl.value
      if (v && v.duration && !v.paused) {
        previewProgress.value = (v.currentTime / v.duration) * 100
      }
    }, 100)
  })
}

function captureCoverFrame() {
  if (coverDataUrl.value) return
  // 用隐藏 video 加载并抓帧
  const blob = recordedBlob.value
  if (!blob) return
  const url = URL.createObjectURL(blob)
  const v = document.createElement('video')
  v.src = url
  v.currentTime = 0.5
  v.muted = true
  v.playsInline = true
  v.onloadeddata = () => {
    const cvs = coverCanvas.value
    if (!cvs) return
    const ctx = cvs.getContext('2d')
    if (!ctx) return
    cvs.width = v.videoWidth || 1080
    cvs.height = v.videoHeight || 1920
    ctx.drawImage(v, 0, 0, cvs.width, cvs.height)
    coverDataUrl.value = cvs.toDataURL('image/jpeg', 0.7)
    URL.revokeObjectURL(url)
  }
  v.load()
}

function insertHash() {
  postDesc.value += ' #'
  descInput.value?.focus()
}

function insertAt() {
  postDesc.value += ' @'
  descInput.value?.focus()
}

function handleLocation() {
  _notice('添加地点功能未实现')
}

function handleTag() {
  _notice('添加标签功能未实现')
}

function handleShare() {
  _notice('分享功能未实现')
}

function setDaily() {
  _notice('已设置为日常')
}

function selectFile() {
  fileInput.value?.click()
}

function onFileSelected(e: Event) {
  const input = e.target as HTMLInputElement
  if (!input.files || input.files.length === 0) return

  const files = Array.from(input.files)
  const allImages = files.every((f) => f.type.startsWith('image/'))

  if (allImages || curMode.value === 'photo') {
    // 照片/图集模式：支持多选
    const newBlobs = files.filter((f) => f.type.startsWith('image/'))
    if (newBlobs.length === 0) return
    // 限制最多9张
    const remaining = 9 - photoList.value.length
    const toAdd = newBlobs.slice(0, remaining)
    photoList.value.push(...toAdd)
    carouselIdx.value = photoList.value.length - 1
    photoBlob.value = photoList.value[0]
    previewSrc.value = URL.createObjectURL(photoList.value[0])
    showPreview.value = true
    stopCamera()
  } else if (files.length === 1) {
    actualDuration = 0
    recordedBlob.value = files[0]
    openPreview(files[0])
  }
  input.value = ''
}

async function doUpload() {
  const store = useBaseStore()
  if (!store.isLoggedIn) {
    _notice('请先登录后再发布')
    router.push('/login')
    return
  }

  const isPhoto = curMode.value === 'photo' || (photoBlob.value && !recordedBlob.value)
  const isText = !!photoBlob.value && textContent.value.trim()

  uploading.value = true
  try {
    if (isPhoto || isText) {
      // 图片/文字/图集作品
      const images =
        photoList.value.length > 0 ? photoList.value : photoBlob.value ? [photoBlob.value] : []
      if (images.length === 0) {
        _notice('暂无内容可发布')
        return
      }

      // 上传所有图片
      const urls: string[] = []
      for (const blob of images) {
        const file = new File(
          [blob],
          `photo_${Date.now()}_${Math.random().toString(36).slice(2)}.jpg`,
          { type: 'image/jpeg' }
        )
        const uploadRes = await uploadImage(file)
        if (uploadRes.success) {
          urls.push(uploadRes.data.url)
        }
      }
      if (urls.length === 0) {
        _notice('上传图片失败，请重试')
        return
      }

      const desc =
        [postTitle.value, postDesc.value].filter(Boolean).join('\n') ||
        textContent.value ||
        '分享图片'
      const pubRes = await publishVideo({
        video_url: urls[0],
        desc,
        duration: 0,
        music_title: isText ? '文字' : images.length > 1 ? `${images.length}张图片` : '图片',
        type: isText ? 'text' : 'image',
        image_urls: urls.length > 1 ? JSON.stringify(urls) : undefined
      } as any)
      if (pubRes.success) {
        _notice('发布成功！')
        bus.emit(EVENT_KEY.REFRESH_FEED)
        resetPublishState()
      } else {
        _notice('发布失败，请重试')
      }
    } else {
      // 视频作品
      const blob =
        recordedBlob.value || segmentList.value.length > 0
          ? new Blob(
              segmentList.value.map((s) => s.blob),
              { type: 'video/webm' }
            )
          : null
      if (!blob && !recordedBlob.value) {
        _notice('暂无视频可发布')
        return
      }

      const videoBlob = recordedBlob.value || blob!
      const isWebm = videoBlob.type && videoBlob.type.includes('webm')
      const ext = isWebm ? 'webm' : 'mp4'
      const file = new File([videoBlob], `video_${Date.now()}.${ext}`, {
        type: videoBlob.type || 'video/mp4'
      })
      const uploadRes = await uploadVideo(file)
      if (!uploadRes.success) {
        _notice('上传视频失败，请重试')
        return
      }

      const desc =
        [postTitle.value, postDesc.value].filter(Boolean).join('\n') ||
        '拍摄于 ' + new Date().toLocaleString()
      const pubRes = await publishVideo({
        video_url: uploadRes.data.url,
        desc,
        duration: Math.max(actualDuration, 1),
        music_title: selectedMusic.value
          ? selectedMusic.value.name + ' - ' + (selectedMusic.value.artist || '')
          : '原创',
        music_id: selectedMusic.value?.id,
        bgm_volume: bgmVolume.value,
        bgm_start_offset: bgmStartOffset.value,
        trim_start: trimStart.value,
        trim_end: trimEnd.value || actualDuration,
        segments: editorSegments.value.length > 0 ? JSON.stringify(editorSegments.value) : undefined
      })
      if (pubRes.success) {
        _notice('发布成功！')
        bus.emit(EVENT_KEY.REFRESH_FEED)
        recordedBlob.value = null
        actualDuration = 0
        showPostEdit.value = false
        showPreview.value = false
        router.back()
      } else {
        _notice('发布失败，请重试')
      }
    }
  } finally {
    uploading.value = false
  }
}

function resetPublishState() {
  photoBlob.value = null
  photoList.value = []
  carouselIdx.value = 0
  recordedBlob.value = null
  actualDuration = 0
  segmentList.value = []
  textContent.value = ''
  showPostEdit.value = false
  showPreview.value = false
  router.back()
}

onMounted(() => startCamera())
onActivated(() => {
  if (!showPreview.value) return
  // 从剪辑页返回，读取编辑结果
  const musicId = sessionStorage.getItem('video_editor_music_id')
  const rawMusic = sessionStorage.getItem('video_editor_music')
  const offset = sessionStorage.getItem('video_editor_bgm_start_offset')
  const vol = sessionStorage.getItem('video_editor_bgm_volume')
  const tStart = sessionStorage.getItem('video_editor_trim_start')
  const tEnd = sessionStorage.getItem('video_editor_trim_end')
  const segsJson = sessionStorage.getItem('video_editor_segments')

  if (musicId === '') {
    selectedMusic.value = null
  } else if (rawMusic) {
    try {
      selectedMusic.value = JSON.parse(rawMusic)
    } catch {
      /* keep current */
    }
  }
  if (offset) bgmStartOffset.value = Number(offset)
  if (vol) bgmVolume.value = Number(vol)
  if (tStart) trimStart.value = Number(tStart)
  if (tEnd) trimEnd.value = Number(tEnd)
  if (segsJson) {
    try {
      editorSegments.value = JSON.parse(segsJson)
    } catch {
      editorSegments.value = []
    }
  }

  initPreviewMusic()

  // 恢复预览播放
  if (previewEl.value && previewSrc.value) {
    previewPlaying.value = true
    if (!previewMuted.value) {
      previewEl.value.muted = false
    }
    previewEl.value.play().catch(() => {})
    if (progressTimer) {
      clearInterval(progressTimer)
      progressTimer = null
    }
    progressTimer = setInterval(() => {
      const v = previewEl.value
      if (v && v.duration && !v.paused) {
        previewProgress.value = (v.currentTime / v.duration) * 100
      }
    }, 100)
  }
})
onBeforeUnmount(() => {
  stopCamera()
  stopPreviewMusic()
})
</script>

<style scoped lang="less">
@import '../../assets/less/index';

.Publish {
  position: fixed;
  inset: 0;
  overflow: hidden;
  color: white;
  background: black;

  .camera-preview {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  // ========== 拍摄态 ==========
  .top-bar {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    z-index: 10;
    padding: 20rem 20rem 0;
    display: flex;
    align-items: flex-start;
    justify-content: space-between;

    .close-btn {
      font-size: 28rem;
      margin-top: 2rem;
    }

    .music-btn {
      position: absolute;
      left: 50%;
      top: 20rem;
      transform: translateX(-50%);
      border-radius: 20rem;
      background: rgba(0, 0, 0, 0.4);
      padding: 5rem 15rem;
      display: flex;
      align-items: center;
      font-size: 14rem;
      white-space: nowrap;
      svg {
        font-size: 16rem;
        margin-right: 5rem;
      }
    }

    .right-tools {
      display: flex;
      flex-direction: column;
      gap: 22rem;
      align-items: center;
      .tool-item {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 2rem;
        font-size: 24rem;
        .tool-label {
          font-size: 10rem;
        }
      }
    }
  }

  .record-timer {
    position: absolute;
    top: 60rem;
    left: 50%;
    transform: translateX(-50%);
    z-index: 10;
    font-size: 18rem;
    font-weight: 600;
    display: flex;
    align-items: center;
    gap: 6rem;
    .timer-dot {
      width: 8rem;
      height: 8rem;
      border-radius: 50%;
      background: #ff4444;
      animation: blink 1s infinite;
    }
    @keyframes blink {
      0%,
      100% {
        opacity: 1;
      }
      50% {
        opacity: 0.3;
      }
    }
  }

  .bottom-zone {
    position: absolute;
    bottom: 0;
    left: 0;
    right: 0;
    z-index: 10;
    padding-bottom: 20rem;
  }

  .mode-tabs {
    display: flex;
    justify-content: center;
    font-size: 15rem;
    color: rgba(255, 255, 255, 0.5);
    font-weight: 500;
    margin-bottom: 24rem;
    span {
      width: 80rem;
      text-align: center;
    }
    .active {
      color: white;
    }
  }

  .footer-tabs {
    display: flex;
    justify-content: center;
    font-size: 15rem;
    color: rgba(255, 255, 255, 0.5);
    font-weight: 500;
    span {
      width: 80rem;
      text-align: center;
    }
    .active {
      color: white;
    }
  }

  .action-row {
    display: grid;
    grid-template-columns: 1fr auto 1fr;
    align-items: center;
    margin-bottom: 24rem;
    padding: 0 20rem;

    .side-action {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 6rem;
      font-size: 12rem;

      .side-icon-wrap {
        width: 44rem;
        height: 44rem;
        border-radius: 50%;
        background: rgba(255, 255, 255, 0.15);
        display: flex;
        align-items: center;
        justify-content: center;
        svg {
          font-size: 24rem;
        }
      }
      .album-thumb {
        width: 44rem;
        height: 44rem;
        border-radius: 4rem;
        overflow: hidden;
        background: rgba(255, 255, 255, 0.15);
        display: flex;
        align-items: center;
        justify-content: center;
        img {
          width: 100%;
          height: 100%;
          object-fit: cover;
        }
      }
    }
    .record-btn-wrap {
      justify-self: center;
    }
  }

  .record-btn-wrap {
    position: relative;
    width: 78rem;
    height: 78rem;
    cursor: pointer;

    .record-ring {
      position: absolute;
      inset: 0;
      border-radius: 50%;
      border: 6rem solid white;
    }
    .record-core {
      position: absolute;
      top: 8rem;
      left: 8rem;
      width: 62rem;
      height: 62rem;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);

      &.is-video {
        background: #ff3b30;
      }
      &.is-photo {
        background: white;
      }
      &.is-segment {
        background: #ff3b30;
        .segments-svg {
          position: absolute;
          inset: 0;
          width: 100%;
          height: 100%;
        }
      }
      .photo-dot {
        width: 100%;
        height: 100%;
        border-radius: 50%;
        background: white;
      }
      &.recording {
        width: 34rem;
        height: 34rem;
        top: 22rem;
        left: 22rem;
        border-radius: 8rem;
        background: #ff3b30;
      }
    }
  }

  .recording-ui {
    position: absolute;
    bottom: 80rem;
    left: 50%;
    transform: translateX(-50%);
    z-index: 10;
    width: 90rem;
    height: 90rem;
    cursor: pointer;

    .progress-ring {
      position: absolute;
      top: -15rem;
      left: -15rem;
      width: 120rem;
      height: 120rem;
      transform: rotate(-90deg);
      circle {
        fill: none;
        stroke-width: 5;
      }
      .ring-bg {
        stroke: rgba(255, 255, 255, 0.2);
      }
      .ring-progress {
        stroke: white;
        stroke-dasharray: 339.29;
        stroke-dashoffset: 0;
        transition: stroke-dashoffset 0.3s linear;
        stroke-linecap: round;
      }
    }
    .recording-core {
      position: absolute;
      inset: 0;
      border-radius: 50%;
      background: white;
      display: flex;
      align-items: center;
      justify-content: center;
      .recording-pause {
        width: 22rem;
        height: 22rem;
        border-radius: 5rem;
        background: #ff3b30;
      }
    }
  }

  // ========== 预览态 ==========
  .unmute-hint {
    position: absolute;
    top: 60rem;
    left: 50%;
    transform: translateX(-50%);
    z-index: 10;
    background: rgba(0, 0, 0, 0.65);
    border-radius: 24rem;
    padding: 10rem 20rem;
    display: flex;
    align-items: center;
    gap: 8rem;
    font-size: 14rem;
    cursor: pointer;
    animation: unmutePulse 1.5s ease-in-out infinite;
    svg {
      font-size: 20rem;
    }
  }
  @keyframes unmutePulse {
    0%,
    100% {
      opacity: 0.8;
    }
    50% {
      opacity: 1;
    }
  }

  .preview-top-bar {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    z-index: 10;
    padding: 20rem 20rem 0;
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-size: 26rem;

    .music-selector {
      font-size: 14rem;
      background: rgba(0, 0, 0, 0.35);
      padding: 6rem 14rem;
      border-radius: 20rem;
      display: flex;
      align-items: center;
      gap: 5rem;
      cursor: pointer;
      max-width: 60%;
      overflow: hidden;

      .music-name-text {
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        max-width: 120rem;
      }

      .remove-music-icon {
        font-size: 16rem;
        color: rgba(255, 255, 255, 0.6);
        flex-shrink: 0;
      }
    }

    .clip-btn {
      font-size: 24rem;
      cursor: pointer;
    }
  }

  .preview-toolbar {
    position: absolute;
    right: 12rem;
    top: 50%;
    transform: translateY(-50%);
    z-index: 10;
    display: flex;
    flex-direction: column;
    gap: 36rem;
    align-items: center;

    .pt-item {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 4rem;
      font-size: 11rem;
      .pt-icon-wrap {
        width: 40rem;
        height: 40rem;
        border-radius: 50%;
        background: rgba(255, 255, 255, 0.12);
        display: flex;
        align-items: center;
        justify-content: center;
        svg {
          font-size: 20rem;
        }
      }
    }
  }

  .preview-bottom {
    position: absolute;
    bottom: 0;
    left: 0;
    right: 0;
    z-index: 10;
    padding: 0 16rem 24rem;

    .progress-bar {
      padding: 0 8rem;
      margin-bottom: 16rem;
      .progress-track {
        height: 3rem;
        background: rgba(255, 255, 255, 0.25);
        border-radius: 2rem;
        position: relative;
        .progress-fill {
          height: 100%;
          background: white;
          border-radius: 2rem;
          transition: width 0.1s linear;
        }
        .progress-thumb {
          position: absolute;
          top: 50%;
          transform: translate(-50%, -50%);
          width: 12rem;
          height: 12rem;
          border-radius: 50%;
          background: white;
        }
      }
    }

    .preview-bottom-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }

    .daily-btn {
      display: flex;
      align-items: center;
      gap: 8rem;
      background: rgba(255, 255, 255, 0.15);
      backdrop-filter: blur(10px);
      padding: 8rem 16rem 8rem 8rem;
      border-radius: 24rem;
      font-size: 13rem;
      white-space: nowrap;

      .daily-avatar {
        width: 28rem;
        height: 28rem;
        border-radius: 50%;
        object-fit: cover;
      }
    }

    .play-pause-btn {
      width: 44rem;
      height: 44rem;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      svg {
        font-size: 22rem;
      }
    }

    .glass-btn {
      background: rgba(255, 255, 255, 0.15);
      backdrop-filter: blur(10px);
      border: none;
    }

    .next-btn {
      background: #ff3b30;
      border-radius: 24rem;
      padding: 10rem 28rem;
      font-size: 15rem;
      font-weight: 600;
      cursor: pointer;
    }
  }

  // ========== 发布编辑态 ==========
  .post-edit-cover {
    width: 100%;
    height: 55%;
    position: relative;
    overflow: hidden;
    background: #1a1a1a;

    .cover-img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }

    .cover-placeholder {
      width: 100%;
      height: 100%;
    }

    .edit-cover-btn {
      position: absolute;
      bottom: 20rem;
      left: 50%;
      transform: translateX(-50%);
      background: rgba(0, 0, 0, 0.5);
      border: 1rem solid rgba(255, 255, 255, 0.3);
      padding: 6rem 20rem;
      border-radius: 20rem;
      font-size: 13rem;
      color: #ccc;
    }
  }

  .post-edit-top-bar {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    z-index: 10;
    padding: 20rem 20rem 0;
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-size: 26rem;

    .preview-label {
      font-size: 15rem;
      background: rgba(0, 0, 0, 0.4);
      padding: 6rem 16rem;
      border-radius: 20rem;
    }
  }

  .post-edit-inputs {
    position: absolute;
    top: 58%;
    left: 0;
    right: 0;
    padding: 16rem 20rem;

    .input-title {
      width: 100%;
      background: transparent;
      border: none;
      outline: none;
      color: #888;
      font-size: 17rem;
      padding: 4rem 0;
      &::placeholder {
        color: #888;
      }
    }

    .input-desc {
      width: 100%;
      background: transparent;
      border: none;
      outline: none;
      color: #666;
      font-size: 14rem;
      padding: 4rem 0;
      resize: none;
      &::placeholder {
        color: #666;
      }
    }
  }

  .post-edit-options {
    position: absolute;
    top: calc(58% + 140rem);
    left: 0;
    right: 0;
    padding: 0 20rem;
    display: flex;
    flex-wrap: wrap;
    gap: 6rem;

    .opt-item {
      padding: 6rem 14rem;
      border-radius: 4rem;
      background: rgba(255, 255, 255, 0.08);
      font-size: 13rem;
      color: #ccc;
      cursor: pointer;
    }
  }

  .draft-tip {
    position: absolute;
    top: calc(58% + 190rem);
    left: 20rem;
    font-size: 12rem;
    color: #555;
  }

  .post-edit-bottom {
    position: absolute;
    bottom: 0;
    left: 0;
    right: 0;
    z-index: 10;
    padding: 20rem 20rem 30rem;
    display: flex;
    align-items: center;
    justify-content: space-between;

    .share-btn {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 4rem;
      font-size: 13rem;
      color: #ccc;
      cursor: pointer;
      svg {
        font-size: 28rem;
      }
    }

    .daily-post-btn {
      display: flex;
      align-items: center;
      gap: 10rem;
      background: rgba(255, 255, 255, 0.12);
      padding: 14rem 24rem;
      border-radius: 28rem;
      font-size: 16rem;
      color: #ddd;
      cursor: pointer;

      .daily-avatar-sm {
        width: 32rem;
        height: 32rem;
        border-radius: 50%;
        object-fit: cover;
      }
    }

    .publish-btn {
      background: #ff3b30;
      border-radius: 28rem;
      padding: 14rem 40rem;
      font-size: 17rem;
      font-weight: 600;
      cursor: pointer;
      &.loading {
        opacity: 0.6;
      }
    }
  }

  // ========== 音乐选择面板 ==========
  .music-picker-mask {
    position: fixed;
    inset: 0;
    z-index: 20;
    background: rgba(0, 0, 0, 0.5);
    display: flex;
    align-items: flex-end;
  }

  .music-picker-sheet {
    width: 100%;
    max-height: 60vh;
    background: #1a1a2e;
    border-radius: 16rem 16rem 0 0;
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }

  .sheet-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 16rem 20rem 12rem;
    border-bottom: 1rem solid rgba(255, 255, 255, 0.06);

    .sheet-title {
      font-size: 17rem;
      font-weight: 600;
    }

    svg {
      font-size: 24rem;
      cursor: pointer;
      color: #999;
    }
  }

  .sheet-list {
    flex: 1;
    overflow-y: auto;
    padding: 8rem 0;
  }

  .sheet-item {
    display: flex;
    align-items: center;
    gap: 12rem;
    padding: 14rem 20rem;
    cursor: pointer;

    &.selected {
      background: rgba(255, 204, 0, 0.08);
    }

    .item-cover {
      width: 44rem;
      height: 44rem;
      border-radius: 4rem;
      object-fit: cover;
      flex-shrink: 0;
    }

    .item-icon {
      width: 44rem;
      height: 44rem;
      border-radius: 4rem;
      background: rgba(255, 255, 255, 0.08);
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;

      svg {
        font-size: 24rem;
        color: #aaa;
      }
    }

    .no-music-icon {
      svg {
        color: #666;
        font-size: 28rem;
      }
    }

    .item-info {
      flex: 1;
      min-width: 0;

      .item-name {
        font-size: 15rem;
        display: block;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      .item-artist {
        font-size: 12rem;
        color: #888;
        display: block;
        margin-top: 2rem;
      }
    }

    .check-icon {
      font-size: 22rem;
      color: #ffcc00;
      flex-shrink: 0;
    }
  }

  .sheet-loading {
    padding: 40rem;
    text-align: center;
    color: #666;
    font-size: 14rem;
  }

  // 面板动画
  .sheet-fade-enter-active,
  .sheet-fade-leave-active {
    transition: opacity 0.25s ease;

    .music-picker-sheet {
      transition: transform 0.25s ease;
    }
  }

  .sheet-fade-enter-from,
  .sheet-fade-leave-to {
    opacity: 0;

    .music-picker-sheet {
      transform: translateY(100%);
    }
  }

  // ========== 分段指示器 ==========
  .segment-indicators {
    position: absolute;
    top: 22rem;
    left: 20rem;
    right: 20rem;
    z-index: 10;
    height: 4rem;
    background: rgba(255, 255, 255, 0.15);
    border-radius: 2rem;
    display: flex;
    gap: 3rem;
    overflow: hidden;
    .seg-dot {
      height: 100%;
      background: #ff3b30;
      border-radius: 2rem;
      transition: width 0.2s;
      &.active {
        background: #ff6b5b;
      }
    }
  }

  .seg-count {
    margin-left: 4rem;
    font-size: 13rem;
    color: rgba(255, 255, 255, 0.7);
  }

  .undo-seg-bar {
    position: absolute;
    bottom: 140rem;
    left: 50%;
    transform: translateX(-50%);
    z-index: 10;
    display: flex;
    align-items: center;
    gap: 12rem;
    background: rgba(0, 0, 0, 0.6);
    border-radius: 24rem;
    padding: 10rem 20rem;

    .seg-summary {
      font-size: 13rem;
      color: #ccc;
    }
    .undo-seg-btn {
      background: rgba(255, 255, 255, 0.15);
      border: none;
      color: #fff;
      padding: 6rem 14rem;
      border-radius: 16rem;
      font-size: 13rem;
      display: flex;
      align-items: center;
      gap: 4rem;
      cursor: pointer;
      svg {
        font-size: 14rem;
      }
    }
    .done-seg-btn {
      background: #ff3b30;
      border: none;
      color: #fff;
      padding: 6rem 18rem;
      border-radius: 16rem;
      font-size: 13rem;
      cursor: pointer;
      font-weight: 600;
    }
  }

  // ========== 拍照预览 ==========
  .photo-preview {
    width: 100%;
    height: 100%;
    object-fit: contain;
    background: #000;
  }

  .preview-label-text {
    font-size: 15rem;
    background: rgba(0, 0, 0, 0.35);
    padding: 6rem 14rem;
    border-radius: 20rem;
  }

  .photo-toolbar {
    position: absolute;
    right: 12rem;
    top: 50%;
    transform: translateY(-50%);
    z-index: 10;
    display: flex;
    flex-direction: column;
    gap: 36rem;
    align-items: center;

    .pt-item {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 4rem;
      font-size: 11rem;
      cursor: pointer;
      .pt-icon-wrap {
        width: 40rem;
        height: 40rem;
        border-radius: 50%;
        background: rgba(255, 255, 255, 0.12);
        display: flex;
        align-items: center;
        justify-content: center;
        svg {
          font-size: 20rem;
        }
      }
    }
  }

  // ========== 文字编辑器 ==========
  .text-editor {
    position: fixed;
    inset: 0;
    background: #121212;
    z-index: 10;
    display: flex;
    flex-direction: column;

    .text-top-bar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 12rem 16rem 8rem;
      font-size: 26rem;
      flex-shrink: 0;
      color: #fff;
      .text-editor-title {
        font-size: 17rem;
        font-weight: 600;
      }
      .done-btn {
        font-size: 15rem;
        color: #ffcc00;
        font-weight: 600;
        cursor: pointer;
        &.disabled {
          color: #555;
          cursor: default;
        }
      }
    }

    .text-preview-area {
      flex: 1;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 40rem;
    }

    .text-content-input {
      width: 100%;
      max-width: 600rem;
      background: transparent;
      border: none;
      outline: none;
      color: #fff;
      text-align: center;
      resize: none;
      font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
      &::placeholder {
        color: rgba(255, 255, 255, 0.4);
      }
      &.large {
        font-size: 48rem;
        line-height: 1.3;
      }
      &.small {
        font-size: 24rem;
        line-height: 1.5;
      }
    }

    .text-bg-picker {
      display: flex;
      align-items: center;
      gap: 12rem;
      padding: 16rem 20rem 30rem;
      flex-shrink: 0;
      .bg-label {
        font-size: 13rem;
        color: #888;
        flex-shrink: 0;
      }
      .bg-colors {
        display: flex;
        gap: 10rem;
      }
      .bg-color-dot {
        width: 32rem;
        height: 32rem;
        border-radius: 50%;
        cursor: pointer;
        border: 2rem solid transparent;
        transition: border-color 0.2s;
        &.selected {
          border-color: #ffcc00;
        }
      }
    }
  }

  // 拍照按钮图标
  .photo-icon {
    width: 32rem;
    height: 32rem;
  }

  // ========== 多图轮播 ==========
  .carousel-container {
    width: 100%;
    height: 70%;
    overflow: hidden;
    background: #000;
    touch-action: pan-y;
  }
  .carousel-track {
    display: flex;
    height: 100%;
    transition: transform 0.3s ease;
  }
  .carousel-slide {
    min-width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
  }
  .carousel-img {
    width: 100%;
    height: 100%;
    object-fit: contain;
  }
  .carousel-dots {
    position: absolute;
    bottom: 70rem;
    left: 50%;
    transform: translateX(-50%);
    z-index: 10;
    display: flex;
    flex-direction: row;
    gap: 8rem;
    .dot {
      width: 6rem;
      height: 6rem;
      border-radius: 50%;
      background: rgba(255, 255, 255, 0.4);
      cursor: pointer;
      transition: all 0.2s;
      &.active {
        background: #fff;
        transform: scale(1.4);
      }
    }
  }
  .carousel-counter {
    position: absolute;
    top: 50rem;
    left: 50%;
    transform: translateX(-50%);
    z-index: 10;
    font-size: 13rem;
    background: rgba(0, 0, 0, 0.5);
    padding: 4rem 12rem;
    border-radius: 12rem;
  }
  .carousel-sort-bar {
    position: absolute;
    bottom: 160rem;
    left: 50%;
    transform: translateX(-50%);
    z-index: 10;
    display: flex;
    gap: 8rem;
    .sort-btn {
      background: rgba(0, 0, 0, 0.55);
      border: none;
      color: #fff;
      padding: 6rem 12rem;
      border-radius: 16rem;
      font-size: 12rem;
      display: flex;
      align-items: center;
      gap: 3rem;
      cursor: pointer;
      svg {
        font-size: 14rem;
      }
      &:disabled {
        opacity: 0.3;
        cursor: default;
      }
    }
  }
  .add-more-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 6rem;
    padding: 8rem 0 12rem;
    color: #aaa;
    font-size: 13rem;
    cursor: pointer;
    svg {
      font-size: 18rem;
    }
  }
}
</style>
