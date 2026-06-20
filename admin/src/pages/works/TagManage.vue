<template>
  <div class="tag-manage">
    <h2 class="page-title">标签管理</h2>

    <div class="search-bar">
      <input v-model="videoIdInput" placeholder="输入作品ID..." style="width:200px;" @keyup.enter="loadTags" />
      <button class="btn-primary" @click="loadTags" :disabled="loading">查看标签</button>
    </div>

    <div v-if="videoData" class="card" style="margin-bottom:20px;">
      <h3 style="font-size:14px;margin-bottom:12px;">作品信息</h3>
      <div class="video-info">
        <span><strong>ID:</strong> {{ videoData.videoId }}</span>
        <span v-if="videoData.type"><strong>类型:</strong> {{ typeLabel(videoData.type) }}</span>
        <span v-if="videoData.desc"><strong>描述:</strong> {{ videoData.desc }}</span>
      </div>
    </div>

    <div v-if="videoData" class="card" style="margin-bottom:20px;">
      <h3 style="font-size:14px;margin-bottom:12px;">AI 场景标签</h3>
      <div style="display:flex;flex-wrap:wrap;gap:8px;margin-bottom:8px;">
        <span v-for="t in sceneTags" :key="t" class="tag" style="background:#d1fae5;color:#059669;">{{ t }}</span>
        <span v-if="sceneTags.length === 0" style="color:#999;font-size:13px;">暂无</span>
      </div>

      <h3 style="font-size:14px;margin-bottom:12px;">AI 物体标签</h3>
      <div style="display:flex;flex-wrap:wrap;gap:8px;margin-bottom:8px;">
        <span v-for="t in objectTags" :key="t" class="tag" style="background:#dbeafe;color:#2563eb;">{{ t }}</span>
        <span v-if="objectTags.length === 0" style="color:#999;font-size:13px;">暂无</span>
      </div>

      <h3 style="font-size:14px;margin-bottom:12px;">AI 自动关键词</h3>
      <div style="display:flex;flex-wrap:wrap;gap:8px;margin-bottom:8px;">
        <span v-for="t in autoKeywords" :key="t" class="tag" style="background:#f3f4f6;color:#666;">{{ t }}</span>
        <span v-if="autoKeywords.length === 0" style="color:#999;font-size:13px;">暂无</span>
      </div>

      <h3 style="font-size:14px;margin-bottom:12px;">AI 权重标签</h3>
      <div style="display:flex;flex-wrap:wrap;gap:8px;margin-bottom:20px;">
        <span v-for="t in autoTags" :key="t.id" class="tag" style="background:#e8f4ff;color:#3b82f6;">
          {{ t.tag }}
          <small v-if="t.weight != null" style="margin-left:4px;opacity:0.7;">{{ (t.weight * 100).toFixed(0) }}%</small>
        </span>
        <span v-if="autoTags.length === 0" style="color:#999;font-size:13px;">暂无</span>
      </div>

      <h3 style="font-size:14px;margin-bottom:12px;">人工标签</h3>
      <div style="display:flex;flex-wrap:wrap;gap:8px;margin-bottom:16px;">
        <span v-for="(t, idx) in manualTags" :key="idx" class="tag" style="background:#fef3c7;color:#d97706;">
          {{ t }}
          <button class="tag-remove" @click="removeManualTag(idx)">×</button>
        </span>
        <span v-if="manualTags.length === 0" style="color:#999;font-size:13px;">暂无人工标签</span>
      </div>

      <div style="display:flex;gap:8px;">
        <input
          v-model="newTag"
          placeholder="输入新标签..."
          style="flex:1;padding:8px 12px;border:1px solid #ddd;border-radius:6px;font-size:13px;outline:none;"
          @keyup.enter="addManualTag"
        />
        <button class="btn-primary" @click="addManualTag" :disabled="!newTag.trim() || saving">添加</button>
        <button class="btn-primary" @click="saveTags" :disabled="saving" style="background:#10b981;">
          {{ saving ? '保存中...' : '保存修改' }}
        </button>
      </div>
    </div>

    <div v-else-if="!loading && videoIdInput" class="empty-state">
      <p>输入作品ID后点击"查看标签"</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { getVideoTags, updateVideoTags } from '@/api/admin'
import { toast } from '@/utils/toast'

const videoIdInput = ref('')
const videoData = ref<any>(null)
const sceneTags = ref<string[]>([])
const objectTags = ref<string[]>([])
const autoKeywords = ref<string[]>([])
const autoTags = ref<any[]>([])
const manualTags = ref<string[]>([])
const newTag = ref('')
const loading = ref(false)
const saving = ref(false)

function typeLabel(t: string) {
  const m: Record<string, string> = { 'recommend-video': '推荐视频', 'long-video': '长视频', 'image': '图文', 'text': '文字' }
  return m[t] || t
}

async function loadTags() {
  const id = parseInt(videoIdInput.value)
  if (!id) return
  loading.value = true
  try {
    const res: any = await getVideoTags(id)
    if (res.success) {
      const data = res.data || {}
      videoData.value = data
      sceneTags.value = data.sceneTags || []
      objectTags.value = data.objectTags || []
      autoKeywords.value = data.autoKeywords || []
      autoTags.value = data.autoTags || []
      manualTags.value = (data.manualTags || []).map((t: any) => t.tag || t)
    } else {
      toast.error('未找到该作品')
    }
  } catch (e) { toast.error('加载失败') }
  finally { loading.value = false }
}

function addManualTag() {
  const tag = newTag.value.trim()
  if (!tag) return
  if (manualTags.value.includes(tag)) { toast.warning('标签已存在'); return }
  manualTags.value.push(tag)
  newTag.value = ''
}

function removeManualTag(idx: number) {
  manualTags.value.splice(idx, 1)
}

async function saveTags() {
  const id = parseInt(videoIdInput.value)
  if (!id) return
  saving.value = true
  try {
    const tags = manualTags.value.map(t => ({ tag: t, weight: 1.0 }))
    const res: any = await updateVideoTags(id, tags)
    if (res.success) toast.success('标签已保存')
    else toast.error('保存失败')
  } catch (e) { toast.error('网络错误') }
  finally { saving.value = false }
}
</script>

<style scoped>
.tag-manage { max-width: 1000px; }
.video-info { display: flex; flex-wrap: wrap; gap: 16px; font-size: 13px; color: #666; }
.tag-remove {
  background: none; border: none; color: #d97706; cursor: pointer;
  font-size: 14px; margin-left: 2px; padding: 0 2px;
  &:hover { color: #b45309; }
}
</style>
