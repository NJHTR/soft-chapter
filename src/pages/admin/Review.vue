<template>
  <div class="admin-review">
    <BaseHeader mode="light" backMode="dark" backImg="back">
      <template v-slot:center>
        <span class="header-title">作品审核</span>
      </template>
    </BaseHeader>

    <!-- 统计栏 -->
    <div class="stats-bar">
      <div class="stat-item">
        <span class="stat-num">{{ stats.videoPending }}</span>
        <span class="stat-label">视频待审</span>
      </div>
      <div class="stat-item">
        <span class="stat-num">{{ stats.postPending }}</span>
        <span class="stat-label">图文待审</span>
      </div>
      <div class="stat-item">
        <span class="stat-num">{{ stats.musicPending }}</span>
        <span class="stat-label">音乐待审</span>
      </div>
    </div>

    <!-- Tab 切换 -->
    <div class="tabs">
      <div
        v-for="tab in tabs"
        :key="tab.key"
        class="tab-item"
        :class="{ active: currentTab === tab.key }"
        @click="switchTab(tab.key)"
      >
        {{ tab.label }}
      </div>
    </div>

    <!-- 列表区域 -->
    <div class="list-container">
      <!-- 视频 Tab -->
      <div v-show="currentTab === `video`" class="tab-content">
        <div v-if="videoList.length === 0 && !videoLoading" class="empty">暂无待审核视频</div>
        <div v-for="item in videoList" :key="`v-` + item.aweme_id" class="review-card">
          <div class="card-left">
            <img v-if="item.video?.poster" :src="item.video.poster" class="thumb" />
            <div v-else class="thumb-placeholder">🎬</div>
          </div>
          <div class="card-center">
            <div class="card-title">{{ item.desc || `无描述` }}</div>
            <div class="card-meta">
              <span class="tag">视频</span>
              <span>{{ formatDuration(item.duration) }}</span>
            </div>
            <div class="card-author" v-if="item.author">
              作者: {{ item.author.nickname || item.author.email }}
            </div>
          </div>
          <div class="card-right">
            <button class="btn-approve" @click="doApproveVideo(item.aweme_id)">通过</button>
            <button class="btn-reject" @click="openReject(`video`, item.aweme_id)">驳回</button>
          </div>
        </div>
        <div v-if="videoLoading" class="loading-text">加载中...</div>
        <NoMore v-if="videoNoMore" />
      </div>

      <!-- 图文 Tab -->
      <div v-show="currentTab === `post`" class="tab-content">
        <div v-if="postList.length === 0 && !postLoading" class="empty">暂无待审图文</div>
        <div v-for="item in postList" :key="`p-` + item.aweme_id" class="review-card">
          <div class="card-left">
            <img v-if="item.video?.poster" :src="item.video.poster" class="thumb" />
            <div v-else class="thumb-placeholder">📷</div>
          </div>
          <div class="card-center">
            <div class="card-title">{{ item.desc || `无描述` }}</div>
            <div class="card-meta">
              <span class="tag" :class="item.type === `image` ? `tag-image` : `tag-text`">
                {{ item.type === `image` ? `图文` : `文字` }}
              </span>
            </div>
            <div class="card-author" v-if="item.author">
              作者: {{ item.author.nickname || item.author.email }}
            </div>
          </div>
          <div class="card-right">
            <button class="btn-approve" @click="doApproveVideo(item.aweme_id)">通过</button>
            <button class="btn-reject" @click="openReject(`post`, item.aweme_id)">驳回</button>
          </div>
        </div>
        <div v-if="postLoading" class="loading-text">加载中...</div>
        <NoMore v-if="postNoMore" />
      </div>

      <!-- 音乐 Tab -->
      <div v-show="currentTab === `music`" class="tab-content">
        <div v-if="musicList.length === 0 && !musicLoading" class="empty">暂无待审核音乐</div>
        <div v-for="item in musicList" :key="`m-` + item.id" class="review-card">
          <div class="card-left">
            <img v-if="item.coverUrl" :src="item.coverUrl" class="thumb" />
            <div v-else class="thumb-placeholder">🎵</div>
          </div>
          <div class="card-center">
            <div class="card-title">{{ item.name || `未知歌曲` }}</div>
            <div class="card-meta">
              <span class="tag tag-music">音乐</span>
              <span>{{ item.artist || `未知歌手` }}</span>
            </div>
            <div class="card-meta" v-if="item.album">专辑: {{ item.album }}</div>
          </div>
          <div class="card-right">
            <button class="btn-approve" @click="doApproveMusic(item.id)">通过</button>
            <button class="btn-reject" @click="openReject(`music`, item.id)">驳回</button>
          </div>
        </div>
        <div v-if="musicLoading" class="loading-text">加载中...</div>
        <NoMore v-if="musicNoMore" />
      </div>
    </div>

    <!-- 驳回弹窗 -->
    <BaseMask v-if="rejectDialog.show" @close="rejectDialog.show = false">
      <div class="reject-dialog">
        <div class="dialog-title">驳回原因</div>
        <textarea
          v-model="rejectDialog.reason"
          class="dialog-textarea"
          placeholder="请输入驳回原因..."
          maxlength="200"
        ></textarea>
        <div class="dialog-btns">
          <button class="btn-cancel" @click="rejectDialog.show = false">取消</button>
          <button class="btn-confirm-reject" @click="doReject">确认驳回</button>
        </div>
      </div>
    </BaseMask>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { _notice } from '@/utils'
import BaseHeader from '@/components/BaseHeader.vue'
import BaseMask from '@/components/BaseMask.vue'
import NoMore from '@/components/NoMore.vue'
import {
  getPendingVideos,
  approveVideo,
  rejectVideo,
  getPendingMusic,
  approveMusic,
  rejectMusic,
  getAdminStats
} from '@/api/admin'

const currentTab = ref(`video`)
const tabs = [
  { key: `video`, label: `视频` },
  { key: `post`, label: `图文` },
  { key: `music`, label: `音乐` }
]

const stats = reactive({
  videoPending: 0,
  postPending: 0,
  musicPending: 0
})

// 视频列表
const videoList = ref<any[]>([])
const videoLoading = ref(false)
const videoNoMore = ref(false)
const videoPage = ref(1)
const videoPageSize = 20

// 图文列表
const postList = ref<any[]>([])
const postLoading = ref(false)
const postNoMore = ref(false)
const postPage = ref(1)
const postPageSize = 20

// 音乐列表
const musicList = ref<any[]>([])
const musicLoading = ref(false)
const musicNoMore = ref(false)
const musicPage = ref(1)
const musicPageSize = 20

// 驳回弹窗
const rejectDialog = reactive({
  show: false,
  type: `` as string,
  id: 0,
  reason: `内容不符合平台规范`
})

function switchTab(key: string) {
  currentTab.value = key
  if (key === `video` && videoList.value.length === 0) loadVideos()
  if (key === `post` && postList.value.length === 0) loadPosts()
  if (key === `music` && musicList.value.length === 0) loadMusics()
}

function formatDuration(ms: number): string {
  if (!ms || ms <= 0) return ``
  const sec = Math.floor(ms / 1000)
  const m = Math.floor(sec / 60)
  const s = sec % 60
  return `${m}:${String(s).padStart(2, `0`)}`
}

// ========== 视频 ==========
async function loadVideos() {
  if (videoLoading.value || videoNoMore.value) return
  videoLoading.value = true
  try {
    const res: any = await getPendingVideos({ pageNo: videoPage.value, pageSize: videoPageSize })
    if (res.success) {
      const data = res.data
      const records = data?.records || data?.list || []
      const filtered = records.filter(
        (v: any) => v.type === `recommend-video` || v.type === `long-video` || v.type === `video`
      )
      videoList.value = videoPage.value === 1 ? filtered : [...videoList.value, ...filtered]
      if (filtered.length < videoPageSize) videoNoMore.value = true
      videoPage.value++
    }
  } catch (e) {
    console.error(e)
  } finally {
    videoLoading.value = false
  }
}

// ========== 图文 ==========
async function loadPosts() {
  if (postLoading.value || postNoMore.value) return
  postLoading.value = true
  try {
    const res: any = await getPendingVideos({ pageNo: postPage.value, pageSize: postPageSize })
    if (res.success) {
      const data = res.data
      const records = data?.records || data?.list || []
      const filtered = records.filter((v: any) => v.type === `image` || v.type === `text`)
      postList.value = postPage.value === 1 ? filtered : [...postList.value, ...filtered]
      if (filtered.length < postPageSize) postNoMore.value = true
      postPage.value++
    }
  } catch (e) {
    console.error(e)
  } finally {
    postLoading.value = false
  }
}

async function doApproveVideo(id: number) {
  const res: any = await approveVideo(id)
  if (res.success) {
    _notice(`审核通过`)
    reloadCurrentTab()
  }
}

function openReject(type: string, id: number) {
  rejectDialog.type = type
  rejectDialog.id = id
  rejectDialog.reason = `内容不符合平台规范`
  rejectDialog.show = true
}

async function doReject() {
  const id = rejectDialog.id
  const reason = rejectDialog.reason
  rejectDialog.show = false
  try {
    let res: any
    if (rejectDialog.type === `music`) {
      res = await rejectMusic(id, reason)
    } else {
      res = await rejectVideo(id, reason)
    }
    if (res.success) {
      _notice(`已驳回`)
      reloadCurrentTab()
    }
  } catch (e) {
    console.error(e)
  }
}

function reloadCurrentTab() {
  if (currentTab.value === `video`) {
    videoPage.value = 1
    videoNoMore.value = false
    videoList.value = []
    loadVideos()
  } else if (currentTab.value === `post`) {
    postPage.value = 1
    postNoMore.value = false
    postList.value = []
    loadPosts()
  } else if (currentTab.value === `music`) {
    musicPage.value = 1
    musicNoMore.value = false
    musicList.value = []
    loadMusics()
  }
  loadStats()
}

// ========== 音乐 ==========
async function loadMusics() {
  if (musicLoading.value || musicNoMore.value) return
  musicLoading.value = true
  try {
    const res: any = await getPendingMusic({ pageNo: musicPage.value, pageSize: musicPageSize })
    if (res.success) {
      const data = res.data
      const records = data?.records || data?.list || []
      musicList.value = musicPage.value === 1 ? records : [...musicList.value, ...records]
      if (records.length < musicPageSize) musicNoMore.value = true
      musicPage.value++
    }
  } catch (e) {
    console.error(e)
  } finally {
    musicLoading.value = false
  }
}

async function doApproveMusic(id: number) {
  const res: any = await approveMusic(id)
  if (res.success) {
    _notice(`审核通过`)
    reloadCurrentTab()
  }
}

// ========== 统计 ==========
async function loadStats() {
  try {
    const res: any = await getAdminStats()
    if (res.success) {
      const d = res.data
      stats.videoPending = d.pendingCount || 0
      stats.musicPending = d.musicPendingCount || 0
      stats.postPending = 0
    }
  } catch (e) {
    console.error(e)
  }
}

onMounted(() => {
  loadStats()
  loadVideos()
})

// 监听滚动加载更多
let scrollHandler: any = null
onMounted(() => {
  const container = document.querySelector(`.list-container`)
  if (container) {
    scrollHandler = () => {
      const { scrollTop, scrollHeight, clientHeight } = container
      if (scrollTop + clientHeight >= scrollHeight - 50) {
        if (currentTab.value === `video`) loadVideos()
        else if (currentTab.value === `post`) loadPosts()
        else if (currentTab.value === `music`) loadMusics()
      }
    }
    container.addEventListener(`scroll`, scrollHandler)
  }
})
</script>

<style scoped lang="less">
.admin-review {
  width: 100%;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f5f5;
  padding-top: var(--common-header-height);
  box-sizing: border-box;
  overflow: hidden;
}

.header-title {
  font-size: 16rem;
  font-weight: 600;
  color: #333;
}

.stats-bar {
  display: flex;
  justify-content: space-around;
  padding: 12rem 16rem;
  background: #fff;
  border-bottom: 1rem solid #eee;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.stat-num {
  font-size: 20rem;
  font-weight: 700;
  color: #fe2c55;
}

.stat-label {
  font-size: 11rem;
  color: #999;
  margin-top: 2rem;
}

.tabs {
  display: flex;
  background: #fff;
  border-bottom: 1rem solid #eee;
}

.tab-item {
  flex: 1;
  text-align: center;
  padding: 12rem 0;
  font-size: 14rem;
  color: #666;
  cursor: pointer;
  position: relative;
}

.tab-item.active {
  color: #fe2c55;
  font-weight: 600;
}

.tab-item.active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 24rem;
  height: 3rem;
  background: #fe2c55;
  border-radius: 2rem;
}

.list-container {
  flex: 1;
  overflow-y: auto;
  padding: 8rem 12rem;
}

.tab-content {
  min-height: 200rem;
}

.empty {
  text-align: center;
  padding: 60rem 0;
  font-size: 14rem;
  color: #999;
}

.loading-text {
  text-align: center;
  padding: 20rem 0;
  font-size: 12rem;
  color: #999;
}

.review-card {
  display: flex;
  align-items: center;
  background: #fff;
  border-radius: 8rem;
  padding: 12rem;
  margin-bottom: 8rem;
  box-shadow: 0 1rem 4rem rgba(0, 0, 0, 0.05);
}

.card-left {
  width: 64rem;
  height: 64rem;
  border-radius: 6rem;
  overflow: hidden;
  flex-shrink: 0;
  margin-right: 10rem;
  background: #f0f0f0;
}

.thumb {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.thumb-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rem;
}

.card-center {
  flex: 1;
  min-width: 0;
}

.card-title {
  font-size: 14rem;
  font-weight: 500;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 4rem;
}

.card-meta {
  font-size: 11rem;
  color: #999;
  margin-bottom: 2rem;
}

.tag {
  display: inline-block;
  background: #e8f4ff;
  color: #3b82f6;
  font-size: 10rem;
  padding: 1rem 6rem;
  border-radius: 3rem;
  margin-right: 6rem;
}

.tag-image {
  background: #fef3c7;
  color: #d97706;
}

.tag-text {
  background: #fce7f3;
  color: #db2777;
}

.tag-music {
  background: #d1fae5;
  color: #059669;
}

.card-author {
  font-size: 11rem;
  color: #bbb;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-right {
  display: flex;
  flex-direction: column;
  gap: 6rem;
  flex-shrink: 0;
  margin-left: 8rem;
}

.btn-approve {
  background: #fe2c55;
  color: #fff;
  border: none;
  border-radius: 14rem;
  padding: 6rem 16rem;
  font-size: 12rem;
  cursor: pointer;
}

.btn-reject {
  background: #fff;
  color: #666;
  border: 1rem solid #ddd;
  border-radius: 14rem;
  padding: 6rem 16rem;
  font-size: 12rem;
  cursor: pointer;
}

.reject-dialog {
  background: #fff;
  border-radius: 12rem;
  padding: 20rem;
  width: 300rem;
  max-width: 90vw;
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  z-index: 10;
}

.dialog-title {
  font-size: 16rem;
  font-weight: 600;
  color: #333;
  margin-bottom: 12rem;
}

.dialog-textarea {
  width: 100%;
  height: 100rem;
  border: 1rem solid #ddd;
  border-radius: 8rem;
  padding: 10rem;
  font-size: 13rem;
  resize: none;
  outline: none;
  box-sizing: border-box;
}

.dialog-textarea:focus {
  border-color: #fe2c55;
}

.dialog-btns {
  display: flex;
  justify-content: flex-end;
  gap: 10rem;
  margin-top: 12rem;
}

.btn-cancel {
  background: #f5f5f5;
  color: #666;
  border: none;
  border-radius: 6rem;
  padding: 8rem 20rem;
  font-size: 13rem;
  cursor: pointer;
}

.btn-confirm-reject {
  background: #fe2c55;
  color: #fff;
  border: none;
  border-radius: 6rem;
  padding: 8rem 20rem;
  font-size: 13rem;
  cursor: pointer;
}
</style>
