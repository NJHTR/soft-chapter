<template>
  <div class="video-manage">
    <h2 class="page-title">作品管理</h2>

    <div class="search-bar">
      <input v-model="searchKw" placeholder="搜索作品描述..." style="width:240px;" @keyup.enter="loadVideos" />
      <select v-model="typeFilter" @change="loadVideos">
        <option value="">全部类型</option>
        <option value="recommend-video">推荐视频</option>
        <option value="long-video">长视频</option>
        <option value="image">图文</option>
        <option value="text">文字</option>
      </select>
      <button class="btn-primary" @click="loadVideos">搜索</button>
    </div>

    <table class="data-table">
      <thead>
        <tr>
          <th>ID</th>
          <th>封面</th>
          <th>描述</th>
          <th>类型</th>
          <th>播放量</th>
          <th>点赞</th>
          <th>收藏</th>
          <th>评论</th>
          <th>发布时间</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="v in videos" :key="v.id">
          <td>{{ v.id }}</td>
          <td><img v-if="v.coverUrl" :src="normalizeImgUrl(v.coverUrl)" style="width:48px;height:64px;object-fit:cover;border-radius:4px;" @error="onCoverError" /></td>
          <td class="desc-cell">{{ v.desc || '无描述' }}</td>
          <td><span class="badge" :class="typeBadgeClass(v.type)">{{ typeLabel(v.type) }}</span></td>
          <td>{{ fmtNum(v.playCount) }}</td>
          <td>{{ fmtNum(v.likeCount) }}</td>
          <td>{{ fmtNum(v.collectCount) }}</td>
          <td>{{ fmtNum(v.commentCount) }}</td>
          <td>{{ fmtDate(v.createTime) }}</td>
        </tr>
        <tr v-if="videos.length === 0"><td colspan="9" class="empty-row">暂无数据</td></tr>
      </tbody>
    </table>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getTopVideos } from '@/api/admin'
import { normalizeImgUrl } from '@/utils/image'

const videos = ref<any[]>([])
const searchKw = ref('')
const typeFilter = ref('')

function typeLabel(t: string) {
  const m: Record<string, string> = { 'recommend-video': '推荐视频', 'long-video': '长视频', 'image': '图文', 'text': '文字' }
  return m[t] || t || '视频'
}
function typeBadgeClass(t: string) {
  const m: Record<string, string> = { 'recommend-video': 'role-user', 'long-video': 'role-merchant', 'image': 'role-admin', 'text': '' }
  return m[t] || ''
}
function fmtNum(n: number) {
  if (!n) return '0'
  if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return String(n)
}
function fmtDate(d: any) { return d ? new Date(d).toLocaleDateString('zh-CN') : '-' }

function onCoverError(e: Event) {
  const img = e.target as HTMLImageElement
  img.style.display = 'none'
}

async function loadVideos() {
  try {
    const res: any = await getTopVideos({ metric: 'views', limit: 50 })
    if (res.success) {
      let list = res.data || []
      if (searchKw.value) {
        const kw = searchKw.value.toLowerCase()
        list = list.filter((v: any) => (v.desc || '').toLowerCase().includes(kw))
      }
      if (typeFilter.value) {
        list = list.filter((v: any) => v.type === typeFilter.value)
      }
      videos.value = list
    }
  } catch (e) { console.error(e) }
}

onMounted(() => loadVideos())
</script>

<style scoped>
.video-manage { max-width: 1400px; }
.desc-cell { max-width: 240px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
