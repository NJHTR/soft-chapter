<template>
  <div>
    <h2 class="page-title">作品审核</h2>

    <div class="stat-grid">
      <div class="stat-card">
        <div class="stat-value" style="color:#fe2c55;">{{ stats.videoPending }}</div>
        <div class="stat-label">视频待审</div>
      </div>
      <div class="stat-card">
        <div class="stat-value" style="color:#f59e0b;">{{ stats.postPending }}</div>
        <div class="stat-label">图文待审</div>
      </div>
      <div class="stat-card">
        <div class="stat-value" style="color:#3b82f6;">{{ stats.musicPending }}</div>
        <div class="stat-label">音乐待审</div>
      </div>
    </div>

    <div style="display:flex;gap:0;margin-bottom:16px;background:#fff;border-radius:8px;overflow:hidden;">
      <div v-for="tab in tabs" :key="tab.key"
        style="flex:1;text-align:center;padding:12px 0;font-size:14px;cursor:pointer;transition:all .2s;border-bottom:2px solid transparent;"
        :style="currentTab === tab.key ? 'color:#fe2c55;font-weight:600;border-bottom-color:#fe2c55;' : 'color:#666;'"
        @click="switchTab(tab.key)">{{ tab.label }}</div>
    </div>

    <div v-show="currentTab === 'video'">
      <div v-if="videoList.length === 0 && !videoLoading" class="empty-state"><p>暂无待审核视频</p></div>
      <div v-for="item in videoList" :key="'v-' + item.aweme_id" class="card" style="display:flex;align-items:center;gap:12px;margin-bottom:8px;padding:12px;">
        <div style="width:64px;height:64px;border-radius:6px;overflow:hidden;flex-shrink:0;background:#f0f0f0;">
          <img v-if="item.video?.poster" :src="checkImg(item.video.poster)" style="width:100%;height:100%;object-fit:cover;" />
          <Film v-else :size="28" style="color:#ccc;" />
        </div>
        <div style="flex:1;min-width:0;">
          <div style="font-size:14px;font-weight:500;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">{{ item.desc || '无描述' }}</div>
          <div style="font-size:11px;color:#999;margin-top:4px;">
            <span class="tag" style="background:#e8f4ff;color:#3b82f6;margin-right:6px;">视频</span>
            <span v-if="item.duration">{{ formatDuration(item.duration) }}</span>
          </div>
          <div style="font-size:11px;color:#bbb;margin-top:2px;" v-if="item.author">作者: {{ item.author.nickname || item.author.email }}</div>
        </div>
        <div style="display:flex;flex-direction:column;gap:6px;flex-shrink:0;">
          <button class="btn-primary" style="padding:6px 16px;font-size:12px;border-radius:14px;" :disabled="actionLoading === item.aweme_id" @click="doApprove(item.aweme_id)">{{ actionLoading === item.aweme_id ? '...' : '通过' }}</button>
          <button class="btn-cancel" style="border:1px solid #ddd;padding:6px 16px;font-size:12px;border-radius:14px;" :disabled="actionLoading === item.aweme_id" @click="openReject('video', item.aweme_id)">驳回</button>
        </div>
      </div>
      <div v-if="videoLoading" style="text-align:center;padding:20px;color:#999;">加载中...</div>
    </div>

    <div v-show="currentTab === 'post'">
      <div v-if="postList.length === 0 && !postLoading" class="empty-state"><p>暂无待审核图文</p></div>
      <div v-for="item in postList" :key="'p-' + item.aweme_id" class="card" style="display:flex;align-items:center;gap:12px;margin-bottom:8px;padding:12px;">
        <div style="width:64px;height:64px;border-radius:6px;overflow:hidden;flex-shrink:0;background:#f0f0f0;">
          <img v-if="item.video?.poster" :src="checkImg(item.video.poster)" style="width:100%;height:100%;object-fit:cover;" />
          <Image v-else :size="28" style="color:#ccc;" />
        </div>
        <div style="flex:1;min-width:0;">
          <div style="font-size:14px;font-weight:500;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">{{ item.desc || '无描述' }}</div>
          <div style="font-size:11px;color:#999;margin-top:4px;">
            <span class="tag" :style="item.type === 'image' ? 'background:#fef3c7;color:#d97706;' : 'background:#fce7f3;color:#db2777;'" style="margin-right:6px;">{{ item.type === 'image' ? '图文' : '文字' }}</span>
          </div>
          <div style="font-size:11px;color:#bbb;margin-top:2px;" v-if="item.author">作者: {{ item.author.nickname || item.author.email }}</div>
        </div>
        <div style="display:flex;flex-direction:column;gap:6px;flex-shrink:0;">
          <button class="btn-primary" style="padding:6px 16px;font-size:12px;border-radius:14px;" :disabled="actionLoading === item.aweme_id" @click="doApprove(item.aweme_id)">{{ actionLoading === item.aweme_id ? '...' : '通过' }}</button>
          <button class="btn-cancel" style="border:1px solid #ddd;padding:6px 16px;font-size:12px;border-radius:14px;" :disabled="actionLoading === item.aweme_id" @click="openReject('post', item.aweme_id)">驳回</button>
        </div>
      </div>
      <div v-if="postLoading" style="text-align:center;padding:20px;color:#999;">加载中...</div>
    </div>

    <div v-show="currentTab === 'music'">
      <div v-if="musicList.length === 0 && !musicLoading" class="empty-state"><p>暂无待审核音乐</p></div>
      <div v-for="item in musicList" :key="'m-' + item.id" class="card" style="display:flex;align-items:center;gap:12px;margin-bottom:8px;padding:12px;">
        <div style="width:64px;height:64px;border-radius:6px;overflow:hidden;flex-shrink:0;background:#f0f0f0;">
          <img v-if="item.coverUrl" :src="checkImg(item.coverUrl)" style="width:100%;height:100%;object-fit:cover;" />
          <Music v-else :size="28" style="color:#ccc;" />
        </div>
        <div style="flex:1;min-width:0;">
          <div style="font-size:14px;font-weight:500;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">{{ item.name || '未知歌曲' }}</div>
          <div style="font-size:11px;color:#999;margin-top:4px;">
            <span class="tag" style="background:#d1fae5;color:#059669;margin-right:6px;">音乐</span>
            <span>{{ item.artist || '未知歌手' }}</span>
          </div>
        </div>
        <div style="display:flex;flex-direction:column;gap:6px;flex-shrink:0;">
          <button class="btn-primary" style="padding:6px 16px;font-size:12px;border-radius:14px;" :disabled="actionLoading === item.id" @click="doApproveMusic(item.id)">{{ actionLoading === item.id ? '...' : '通过' }}</button>
          <button class="btn-cancel" style="border:1px solid #ddd;padding:6px 16px;font-size:12px;border-radius:14px;" :disabled="actionLoading === item.id" @click="openReject('music', item.id)">驳回</button>
        </div>
      </div>
      <div v-if="musicLoading" style="text-align:center;padding:20px;color:#999;">加载中...</div>
    </div>

    <!-- Reject dialog -->
    <div v-if="rejectDialog.show" class="modal-mask" @click.self="rejectDialog.show = false">
      <div class="modal" style="width:360px;">
        <h3 class="modal-title">驳回原因</h3>
        <div class="form-group">
          <textarea v-model="rejectDialog.reason" placeholder="请输入驳回原因..." maxlength="200" style="height:100px;resize:none;"></textarea>
        </div>
        <div class="modal-btns">
          <button class="btn-cancel" @click="rejectDialog.show = false">取消</button>
          <button class="btn-primary" @click="doReject">确认驳回</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { Film, Image, Music } from 'lucide-vue-next'
import {
  getPendingVideos, approveVideo, rejectVideo,
  getPendingMusic, approveMusic, rejectMusic,
  getAdminStats
} from '@/api/admin'
import { toast } from '@/utils/toast'
import { normalizeImgUrl } from '@/utils/image'

const currentTab = ref('video')
const tabs = [{ key: 'video', label: '视频' }, { key: 'post', label: '图文' }, { key: 'music', label: '音乐' }]

const stats = reactive({ videoPending: 0, postPending: 0, musicPending: 0 })

const videoList = ref<any[]>([])
const videoLoading = ref(false)
const videoPage = ref(1)
const pageSize = 20

const postList = ref<any[]>([])
const postLoading = ref(false)
const postPage = ref(1)

const musicList = ref<any[]>([])
const musicLoading = ref(false)
const musicPage = ref(1)

const rejectDialog = reactive({ show: false, type: '', id: 0, reason: '内容不符合平台规范' })

function checkImg(url: string) {
  return normalizeImgUrl(url)
}

function formatDuration(ms: number) {
  if (!ms || ms <= 0) return ''
  const sec = Math.floor(ms / 1000)
  return `${Math.floor(sec / 60)}:${String(sec % 60).padStart(2, '0')}`
}

function switchTab(key: string) {
  currentTab.value = key
  if (key === 'video' && videoList.value.length === 0) loadVideos()
  if (key === 'post' && postList.value.length === 0) loadPosts()
  if (key === 'music' && musicList.value.length === 0) loadMusic()
}

async function loadVideos() {
  videoLoading.value = true
  try {
    const res: any = await getPendingVideos({ pageNo: videoPage.value, pageSize, type: 'video' })
    if (res.success) {
      const records = res.data?.list || res.data?.records || []
      videoList.value = videoPage.value === 1 ? records : [...videoList.value, ...records]
      videoPage.value++
    }
  } catch (e) { console.error(e) } finally { videoLoading.value = false }
}

async function loadPosts() {
  postLoading.value = true
  try {
    const res: any = await getPendingVideos({ pageNo: postPage.value, pageSize, type: 'post' })
    if (res.success) {
      const records = res.data?.list || res.data?.records || []
      postList.value = postPage.value === 1 ? records : [...postList.value, ...records]
      postPage.value++
    }
  } catch (e) { console.error(e) } finally { postLoading.value = false }
}

async function loadMusic() {
  musicLoading.value = true
  try {
    const res: any = await getPendingMusic({ pageNo: musicPage.value, pageSize })
    if (res.success) {
      const records = res.data?.list || res.data?.records || []
      musicList.value = musicPage.value === 1 ? records : [...musicList.value, ...records]
      musicPage.value++
    }
  } catch (e) { console.error(e) } finally { musicLoading.value = false }
}

const actionLoading = ref<number | null>(null)

async function doApprove(id: number) {
  actionLoading.value = id
  try {
    const res: any = await approveVideo(id)
    if (res.success) { toast.success('审核通过'); reloadCurrentTab() }
    else toast.error('操作失败')
  } catch (e) { toast.error('网络错误') } finally { actionLoading.value = null }
}

function openReject(type: string, id: number) {
  rejectDialog.type = type
  rejectDialog.id = id
  rejectDialog.reason = '内容不符合平台规范'
  rejectDialog.show = true
}

async function doReject() {
  const { id, reason, type } = rejectDialog
  rejectDialog.show = false
  actionLoading.value = id
  try {
    const res = type === 'music' ? await rejectMusic(id, reason) : await rejectVideo(id, reason)
    if (res.success) { toast.warning('已驳回'); reloadCurrentTab() }
    else toast.error('操作失败')
  } catch (e) { toast.error('网络错误') } finally { actionLoading.value = null }
}

async function doApproveMusic(id: number) {
  actionLoading.value = id
  try {
    const res: any = await approveMusic(id)
    if (res.success) { toast.success('审核通过'); reloadCurrentTab() }
    else toast.error('操作失败')
  } catch (e) { toast.error('网络错误') } finally { actionLoading.value = null }
}

function reloadCurrentTab() {
  if (currentTab.value === 'video') { videoPage.value = 1; videoList.value = []; loadVideos() }
  else if (currentTab.value === 'post') { postPage.value = 1; postList.value = []; loadPosts() }
  else if (currentTab.value === 'music') { musicPage.value = 1; musicList.value = []; loadMusic() }
  loadStats()
}

async function loadStats() {
  try {
    const res: any = await getAdminStats()
    if (res.success) {
      stats.videoPending = res.data.videoPending || 0
      stats.postPending = res.data.postPending || 0
      stats.musicPending = res.data.musicPendingCount || 0
    }
  } catch (e) { console.error(e) }
}

onMounted(() => { loadStats(); loadVideos() })
</script>
