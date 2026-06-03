<template>
  <div class="Publish">
    <!-- ============================================ -->
    <!-- 拍摄态：摄像头 + 录制按钮                         -->
    <!-- ============================================ -->
    <template v-if="!showPreview && !showPostEdit">
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

      <div class="record-timer" v-if="isRecording">
        <span class="timer-dot"></span>
        {{ formatTime(recordSeconds) }}
      </div>

      <div class="bottom-zone" v-show="!isRecording">
        <div class="mode-tabs">
          <span v-for="m in modes" :key="m.key"
            :class="{ active: curMode === m.key }"
            @click="modeIndex = modes.findIndex(x => x.key === m.key)">{{ m.label }}</span>
        </div>
        <div class="action-row">
          <div class="side-action">
            <div class="side-icon-wrap"><Icon icon="mdi:magic" /></div>
            <span>特效</span>
          </div>
          <div class="record-btn-wrap" @click="toggleRecord">
            <div class="record-ring"></div>
            <div class="record-core" :class="coreClass">
              <svg v-if="curMode === 'segment'" class="segments-svg" viewBox="0 0 100 100">
                <circle cx="50" cy="50" r="38" fill="none" stroke="#ff3b30"
                  stroke-width="18" stroke-dasharray="45 195"
                  stroke-linecap="round" transform="rotate(-90 50 50)" />
              </svg>
              <div v-else-if="curMode === 'photo'" class="photo-dot"></div>
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
          <span v-for="t in footerTabs" :key="t.key"
            :class="{ active: curTab === t.key }"
            @click="footerIndex = footerTabs.findIndex(x => x.key === t.key)">{{ t.label }}</span>
        </div>
      </div>

      <div class="recording-ui" v-if="isRecording" @click="toggleRecord">
        <svg class="progress-ring" viewBox="0 0 120 120">
          <circle class="ring-bg" cx="60" cy="60" r="54" />
          <circle class="ring-progress" cx="60" cy="60" r="54"
            :style="{ strokeDashoffset: dashOffset }" />
        </svg>
        <div class="recording-core">
          <div class="recording-pause"></div>
        </div>
      </div>
    </template>

    <!-- ============================================ -->
    <!-- 预览态：视频回放 + 编辑入口                        -->
    <!-- ============================================ -->
    <template v-if="showPreview">
      <video ref="previewEl" class="camera-preview"
        :src="previewSrc" loop playsinline
        @click="togglePreviewPlay"
        @ended="onPreviewEnded"></video>

      <div class="preview-top-bar">
        <Icon icon="material-symbols:arrow-back" @click="backToCamera" />
        <span class="music-placeholder">选择音乐</span>
        <Icon icon="mingcute:settings-6-line" />
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

    <input ref="fileInput" type="file" accept="video/*" style="display: none" @change="onFileSelected" />
  </div>
</template>
<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, computed, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { uploadVideo } from '@/api/user'
import { publishVideo } from '@/api/videos'
import { _notice } from '@/utils'
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
const userAvatar = ref('')
const coverDataUrl = ref('')
const postTitle = ref('')
const postDesc = ref('')

const modes = [
  { key: 'segment', label: '分段拍' },
  { key: 'photo', label: '照片' },
  { key: 'video', label: '视频' },
]
const footerTabs = [
  { key: 'text', label: '文字' },
  { key: 'camera', label: '相机' },
  { key: 'live', label: '开直播' },
]

const curMode = computed(() => modes[modeIndex.value]?.key || 'video')
const curTab = computed(() => footerTabs[footerIndex.value]?.key || 'camera')

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
  if (recordTimer) { clearInterval(recordTimer); recordTimer = null }
  if (progressTimer) { clearInterval(progressTimer); progressTimer = null }
  if (mediaStream) {
    mediaStream.getTracks().forEach(t => t.stop())
    mediaStream = null
  }
}

async function startCamera() {
  try {
    mediaStream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode },
      audio: true
    })
    if (videoEl.value) {
      videoEl.value.srcObject = mediaStream
      videoEl.value.play()
    }
  } catch (e) {
    console.error('Camera error:', e)
    _notice('无法访问摄像头')
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

function toggleRecord() {
  if (isRecording.value) { stopRecord() } else { startRecord() }
}

function startRecord() {
  if (!mediaStream) return
  chunks = []
  recordStartTime = Date.now()
  recordSeconds.value = 0
  try {
    mediaRecorder = new MediaRecorder(mediaStream, { mimeType: 'video/webm' })
  } catch {
    mediaRecorder = new MediaRecorder(mediaStream)
  }
  mediaRecorder.ondataavailable = (e) => { if (e.data.size > 0) chunks.push(e.data) }
  mediaRecorder.onstop = () => {
    actualDuration = (Date.now() - recordStartTime) / 1000
    const blob = new Blob(chunks, { type: 'video/webm' })
    recordedBlob.value = blob
    openPreview(blob)
  }
  mediaRecorder.start()
  isRecording.value = true
  recordTimer = setInterval(() => {
    recordSeconds.value = Math.floor((Date.now() - recordStartTime) / 1000)
    if (recordSeconds.value >= MAX_SECONDS) stopRecord()
  }, 200)
}

function stopRecord() {
  if (mediaRecorder && mediaRecorder.state !== 'inactive') mediaRecorder.stop()
  if (recordTimer) { clearInterval(recordTimer); recordTimer = null }
  isRecording.value = false
}

function openPreview(blob: Blob) {
  stopCamera()
  previewSrc.value = URL.createObjectURL(blob)
  showPreview.value = true
  previewPlaying.value = true
  previewProgress.value = 0
  userAvatar.value = useBaseStore().userinfo?.avatar_168x168?.url_list?.[0] || ''

  nextTick(() => {
    const v = previewEl.value
    if (!v) return
    v.play()
    progressTimer = setInterval(() => {
      if (v.duration && !v.paused) {
        previewProgress.value = (v.currentTime / v.duration) * 100
      }
    }, 100)
  })
}

function togglePreviewPlay() {
  const v = previewEl.value
  if (!v) return
  if (v.paused) { v.play(); previewPlaying.value = true }
  else { v.pause(); previewPlaying.value = false }
}

function onPreviewEnded() { previewProgress.value = 100 }

function backToCamera() {
  showPreview.value = false
  previewSrc.value = ''
  recordedBlob.value = null
  if (progressTimer) { clearInterval(progressTimer); progressTimer = null }
  startCamera()
}

function goPostEdit() {
  showPreview.value = false
  if (progressTimer) { clearInterval(progressTimer); progressTimer = null }
  if (previewEl.value) { previewEl.value.pause() }
  showPostEdit.value = true
  captureCoverFrame()
}

function backToPreview() {
  showPostEdit.value = false
  showPreview.value = true
  nextTick(() => {
    if (previewEl.value) { previewEl.value.play(); previewPlaying.value = true }
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
  if (input.files && input.files[0]) {
    actualDuration = 0
    recordedBlob.value = input.files[0]
    openPreview(input.files[0])
  }
}

async function doUpload() {
  if (!recordedBlob.value) return
  const store = useBaseStore()
  if (!store.isLoggedIn) {
    _notice('请先登录后再发布视频')
    router.push('/login')
    return
  }
  uploading.value = true
  try {
    const blob = recordedBlob.value
    const ext = blob.type === 'video/webm' ? 'webm' : 'mp4'
    const file = new File([blob], `video_${Date.now()}.${ext}`, { type: blob.type || 'video/mp4' })
    const uploadRes = await uploadVideo(file)
    if (!uploadRes.success) { _notice('上传视频失败，请重试'); return }

    const desc = [postTitle.value, postDesc.value].filter(Boolean).join('\n') || '拍摄于 ' + new Date().toLocaleString()
    const pubRes = await publishVideo({
      video_url: uploadRes.data.url,
      desc,
      duration: Math.max(actualDuration, 1)
    })
    if (pubRes.success) {
      _notice('发布成功！')
      recordedBlob.value = null
      actualDuration = 0
      showPostEdit.value = false
      showPreview.value = false
      router.back()
    } else {
      _notice('发布失败，请重试')
    }
  } finally {
    uploading.value = false
  }
}

onMounted(() => startCamera())
onBeforeUnmount(() => stopCamera())
</script>

<style scoped lang="less">
@import '../../assets/less/index';

.Publish {
  position: fixed; inset: 0; overflow: hidden; color: white; background: black;

  .camera-preview {
    width: 100%; height: 100%; object-fit: cover;
  }

  // ========== 拍摄态 ==========
  .top-bar {
    position: absolute; top: 0; left: 0; right: 0; z-index: 10;
    padding: 20rem 20rem 0;
    display: flex; align-items: flex-start; justify-content: space-between;

    .close-btn { font-size: 28rem; margin-top: 2rem; }

    .music-btn {
      position: absolute; left: 50%; top: 20rem; transform: translateX(-50%);
      border-radius: 20rem; background: rgba(0,0,0,0.4);
      padding: 5rem 15rem; display: flex; align-items: center;
      font-size: 14rem; white-space: nowrap;
      svg { font-size: 16rem; margin-right: 5rem; }
    }

    .right-tools {
      display: flex; flex-direction: column; gap: 22rem; align-items: center;
      .tool-item {
        display: flex; flex-direction: column; align-items: center; gap: 2rem;
        font-size: 24rem;
        .tool-label { font-size: 10rem; }
      }
    }
  }

  .record-timer {
    position: absolute; top: 60rem; left: 50%; transform: translateX(-50%);
    z-index: 10; font-size: 18rem; font-weight: 600;
    display: flex; align-items: center; gap: 6rem;
    .timer-dot {
      width: 8rem; height: 8rem; border-radius: 50%; background: #ff4444;
      animation: blink 1s infinite;
    }
    @keyframes blink {
      0%, 100% { opacity: 1; } 50% { opacity: 0.3; }
    }
  }

  .bottom-zone {
    position: absolute; bottom: 0; left: 0; right: 0; z-index: 10;
    padding-bottom: 20rem;
  }

  .mode-tabs {
    display: flex; justify-content: center;
    font-size: 15rem; color: rgba(255,255,255,0.5); font-weight: 500;
    margin-bottom: 24rem;
    span { width: 80rem; text-align: center; }
    .active { color: white; }
  }

  .footer-tabs {
    display: flex; justify-content: center;
    font-size: 15rem; color: rgba(255,255,255,0.5); font-weight: 500;
    span { width: 80rem; text-align: center; }
    .active { color: white; }
  }

  .action-row {
    display: grid; grid-template-columns: 1fr auto 1fr;
    align-items: center; margin-bottom: 24rem; padding: 0 20rem;

    .side-action {
      display: flex; flex-direction: column; align-items: center; gap: 6rem;
      font-size: 12rem;

      .side-icon-wrap {
        width: 44rem; height: 44rem; border-radius: 50%;
        background: rgba(255,255,255,0.15);
        display: flex; align-items: center; justify-content: center;
        svg { font-size: 24rem; }
      }
      .album-thumb {
        width: 44rem; height: 44rem; border-radius: 4rem; overflow: hidden;
        background: rgba(255,255,255,0.15);
        display: flex; align-items: center; justify-content: center;
        img { width: 100%; height: 100%; object-fit: cover; }
      }
    }
    .record-btn-wrap { justify-self: center; }
  }

  .record-btn-wrap {
    position: relative; width: 78rem; height: 78rem; cursor: pointer;

    .record-ring {
      position: absolute; inset: 0; border-radius: 50%; border: 6rem solid white;
    }
    .record-core {
      position: absolute; top: 8rem; left: 8rem;
      width: 62rem; height: 62rem; border-radius: 50%;
      display: flex; align-items: center; justify-content: center;
      transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);

      &.is-video { background: #ff3b30; }
      &.is-photo { background: white; }
      &.is-segment {
        background: #ff3b30;
        .segments-svg { position: absolute; inset: 0; width: 100%; height: 100%; }
      }
      .photo-dot { width: 100%; height: 100%; border-radius: 50%; background: white; }
      &.recording {
        width: 34rem; height: 34rem; top: 22rem; left: 22rem;
        border-radius: 8rem; background: #ff3b30;
      }
    }
  }

  .recording-ui {
    position: absolute; bottom: 80rem; left: 50%; transform: translateX(-50%);
    z-index: 10; width: 90rem; height: 90rem; cursor: pointer;

    .progress-ring {
      position: absolute; top: -15rem; left: -15rem;
      width: 120rem; height: 120rem; transform: rotate(-90deg);
      circle { fill: none; stroke-width: 5; }
      .ring-bg { stroke: rgba(255,255,255,0.2); }
      .ring-progress {
        stroke: white; stroke-dasharray: 339.29; stroke-dashoffset: 0;
        transition: stroke-dashoffset 0.3s linear; stroke-linecap: round;
      }
    }
    .recording-core {
      position: absolute; inset: 0; border-radius: 50%; background: white;
      display: flex; align-items: center; justify-content: center;
      .recording-pause {
        width: 22rem; height: 22rem; border-radius: 5rem; background: #ff3b30;
      }
    }
  }

  // ========== 预览态 ==========
  .preview-top-bar {
    position: absolute; top: 0; left: 0; right: 0; z-index: 10;
    padding: 20rem 20rem 0;
    display: flex; align-items: center; justify-content: space-between;
    font-size: 26rem;

    .music-placeholder {
      font-size: 15rem;
      background: rgba(0,0,0,0.35);
      padding: 6rem 16rem; border-radius: 20rem;
    }
  }

  .preview-toolbar {
    position: absolute; right: 12rem; top: 50%; transform: translateY(-50%);
    z-index: 10; display: flex; flex-direction: column; gap: 24rem;
    align-items: center;

    .pt-item {
      display: flex; flex-direction: column; align-items: center; gap: 4rem;
      font-size: 11rem;
      .pt-icon-wrap {
        width: 40rem; height: 40rem; border-radius: 50%;
        background: rgba(255,255,255,0.12);
        display: flex; align-items: center; justify-content: center;
        svg { font-size: 20rem; }
      }
    }
  }

  .preview-bottom {
    position: absolute; bottom: 0; left: 0; right: 0; z-index: 10;
    padding: 0 16rem 24rem;

    .progress-bar {
      padding: 0 8rem; margin-bottom: 16rem;
      .progress-track {
        height: 3rem; background: rgba(255,255,255,0.25); border-radius: 2rem;
        position: relative;
        .progress-fill {
          height: 100%; background: white; border-radius: 2rem;
          transition: width 0.1s linear;
        }
        .progress-thumb {
          position: absolute; top: 50%; transform: translate(-50%, -50%);
          width: 12rem; height: 12rem; border-radius: 50%; background: white;
        }
      }
    }

    .preview-bottom-row {
      display: flex; align-items: center; justify-content: space-between;
    }

    .daily-btn {
      display: flex; align-items: center; gap: 8rem;
      background: rgba(255,255,255,0.15);
      backdrop-filter: blur(10px);
      padding: 8rem 16rem 8rem 8rem; border-radius: 24rem;
      font-size: 13rem; white-space: nowrap;

      .daily-avatar {
        width: 28rem; height: 28rem; border-radius: 50%; object-fit: cover;
      }
    }

    .play-pause-btn {
      width: 44rem; height: 44rem; border-radius: 50%;
      display: flex; align-items: center; justify-content: center;
      svg { font-size: 22rem; }
    }

    .glass-btn {
      background: rgba(255,255,255,0.15);
      backdrop-filter: blur(10px);
      border: none;
    }

    .next-btn {
      background: #ff3b30; border-radius: 24rem;
      padding: 10rem 28rem; font-size: 15rem; font-weight: 600;
      cursor: pointer;
    }
  }

  // ========== 发布编辑态 ==========
  .post-edit-cover {
    width: 100%; height: 55%;
    position: relative; overflow: hidden;
    background: #1a1a1a;

    .cover-img {
      width: 100%; height: 100%; object-fit: cover;
    }

    .cover-placeholder {
      width: 100%; height: 100%;
    }

    .edit-cover-btn {
      position: absolute; bottom: 20rem; left: 50%; transform: translateX(-50%);
      background: rgba(0,0,0,0.5);
      border: 1rem solid rgba(255,255,255,0.3);
      padding: 6rem 20rem; border-radius: 20rem;
      font-size: 13rem; color: #ccc;
    }
  }

  .post-edit-top-bar {
    position: absolute; top: 0; left: 0; right: 0; z-index: 10;
    padding: 20rem 20rem 0;
    display: flex; align-items: center; justify-content: space-between;
    font-size: 26rem;

    .preview-label {
      font-size: 15rem;
      background: rgba(0,0,0,0.4);
      padding: 6rem 16rem; border-radius: 20rem;
    }
  }

  .post-edit-inputs {
    position: absolute; top: 58%; left: 0; right: 0;
    padding: 16rem 20rem;

    .input-title {
      width: 100%; background: transparent; border: none; outline: none;
      color: #888; font-size: 17rem; padding: 4rem 0;
      &::placeholder { color: #888; }
    }

    .input-desc {
      width: 100%; background: transparent; border: none; outline: none;
      color: #666; font-size: 14rem; padding: 4rem 0;
      resize: none;
      &::placeholder { color: #666; }
    }
  }

  .post-edit-options {
    position: absolute; top: calc(58% + 140rem); left: 0; right: 0;
    padding: 0 20rem;
    display: flex; flex-wrap: wrap; gap: 6rem;

    .opt-item {
      padding: 6rem 14rem; border-radius: 4rem;
      background: rgba(255,255,255,0.08);
      font-size: 13rem; color: #ccc;
      cursor: pointer;
    }
  }

  .draft-tip {
    position: absolute; top: calc(58% + 190rem); left: 20rem;
    font-size: 12rem; color: #555;
  }

  .post-edit-bottom {
    position: absolute; bottom: 0; left: 0; right: 0; z-index: 10;
    padding: 20rem 20rem 30rem;
    display: flex; align-items: center; justify-content: space-between;

    .share-btn {
      display: flex; flex-direction: column; align-items: center; gap: 4rem;
      font-size: 13rem; color: #ccc; cursor: pointer;
      svg { font-size: 28rem; }
    }

    .daily-post-btn {
      display: flex; align-items: center; gap: 10rem;
      background: rgba(255,255,255,0.12);
      padding: 14rem 24rem; border-radius: 28rem;
      font-size: 16rem; color: #ddd; cursor: pointer;

      .daily-avatar-sm {
        width: 32rem; height: 32rem; border-radius: 50%; object-fit: cover;
      }
    }

    .publish-btn {
      background: #ff3b30; border-radius: 28rem;
      padding: 14rem 40rem; font-size: 17rem; font-weight: 600;
      cursor: pointer;
      &.loading { opacity: 0.6; }
    }
  }
}
</style>
