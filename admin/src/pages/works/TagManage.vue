<template>
  <div class="tag-manage">
    <h2 class="page-title">标签管理</h2>

    <div class="toolbar">
      <div class="search-bar">
        <input
          v-model="searchKw"
          placeholder="搜索描述或作品ID..."
          style="width: 220px"
          @keyup.enter="search"
        />
        <select v-model="statusFilter">
          <option value="">全部提取状态</option>
          <option value="null">未提取</option>
          <option value="0">排队中</option>
          <option value="1">已提取</option>
          <option value="2">提取失败</option>
          <option value="3">处理中</option>
        </select>
        <button class="btn-primary" @click="search">搜索</button>
      </div>
      <div class="toolbar-actions">
        <button
          class="btn-primary"
          @click="reExtractAll"
          :disabled="reExtractingAll"
          style="background: #f59e0b"
        >
          {{ reExtractingAll ? '批量重提中...' : '批量重提失败作品' }}
        </button>
      </div>
    </div>

    <table class="data-table">
      <thead>
        <tr>
          <th style="width: 60px">ID</th>
          <th style="width: 120px">描述</th>
          <th style="width: 60px">类型</th>
          <th style="width: 70px">特征状态</th>
          <th style="width: 80px">质量/情绪</th>
          <th style="min-width: 200px">标签 (top 5)</th>
          <th style="width: 60px">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="v in items" :key="v.videoId">
          <td>{{ v.videoId }}</td>
          <td class="desc-cell" :title="v.desc">{{ v.desc || '-' }}</td>
          <td>
            <span class="badge" :class="typeBadgeClass(v.type)">{{ typeLabel(v.type) }}</span>
          </td>
          <td>
            <span class="status-dot" :style="{ background: statusColor(v.extractStatus) }"></span>
            {{ statusLabel(v.extractStatus) }}
          </td>
          <td>
            <div v-if="v.qualityScore != null" style="font-size: 11px">
              质量 {{ (v.qualityScore * 100).toFixed(0) }}
            </div>
            <div v-if="v.mood" style="font-size: 11px; color: #999">{{ v.mood }}</div>
            <span v-if="v.qualityScore == null && !v.mood" style="color: #ccc">-</span>
          </td>
          <td class="tags-cell">
            <span v-if="v.tags.length === 0" style="color: #ccc; font-size: 12px">暂无标签</span>
            <span
              v-for="t in v.tags"
              :key="t.tag"
              class="tag-chip"
              :style="{ background: sourceBg(t.source), color: sourceFg(t.source) }"
            >
              {{ t.tag }}
              <small>{{ (t.weight * 100).toFixed(0) }}%</small>
            </span>
            <span v-if="v.tagCount > 5" style="font-size: 11px; color: #999">
              +{{ v.tagCount - 5 }}
            </span>
          </td>
          <td>
            <button class="btn-sm" @click="reExtractOne(v.videoId)">重提</button>
          </td>
        </tr>
        <tr v-if="items.length === 0">
          <td colspan="7" class="empty-row">暂无数据</td>
        </tr>
      </tbody>
    </table>

    <!-- 分页 -->
    <div class="pagination" v-if="total > pageSize">
      <button :disabled="page <= 1" @click="goPage(page - 1)">上一页</button>
      <span class="page-info">{{ page }} / {{ totalPages }}</span>
      <button :disabled="page >= totalPages" @click="goPage(page + 1)">下一页</button>
      <span class="total-info">共 {{ total }} 条</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { listVideoTags, reExtractVideo, reExtractAllFailed } from '@/api/admin'
import { toast } from '@/utils/toast'

const items = ref<any[]>([])
const searchKw = ref('')
const statusFilter = ref('')
const page = ref(1)
const total = ref(0)
const pageSize = 20
const reExtractingAll = ref(false)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

function typeLabel(t: string) {
  const m: Record<string, string> = {
    'recommend-video': '短视频',
    'long-video': '长视频',
    image: '图文',
    text: '文字'
  }
  return m[t] || t || '视频'
}

function typeBadgeClass(t: string) {
  const m: Record<string, string> = {
    'recommend-video': 'type-video',
    'long-video': 'type-long',
    image: 'type-image',
    text: 'type-text'
  }
  return m[t] || ''
}

function statusLabel(s: number | null | undefined) {
  if (s == null) return '未提取'
  const m: Record<number, string> = { 0: '排队', 1: '已提取', 2: '失败', 3: '处理中' }
  return m[s] || '?'
}

function statusColor(s: number | null | undefined) {
  if (s == null) return '#bbb'
  const m: Record<number, string> = { 0: '#f59e0b', 1: '#10b981', 2: '#ef4444', 3: '#3b82f6' }
  return m[s] || '#999'
}

function sourceBg(src: string) {
  if (!src) return '#e8f4ff'
  if (src.includes('ai')) return '#e8f4ff'
  if (src.includes('comment')) return '#d1fae5'
  if (src.includes('co_search')) return '#ede9fe'
  if (src.includes('search')) return '#dbeafe'
  if (src.includes('cowatch')) return '#fef3c7'
  if (src.includes('trend')) return '#fce7f3'
  if (src.includes('manual')) return '#fef3c7'
  if (src.includes('inherit')) return '#f5f0ff'
  return '#f3f4f6'
}

function sourceFg(src: string) {
  if (!src) return '#3b82f6'
  if (src.includes('ai')) return '#3b82f6'
  if (src.includes('comment')) return '#059669'
  if (src.includes('co_search')) return '#7c3aed'
  if (src.includes('search')) return '#2563eb'
  if (src.includes('cowatch')) return '#d97706'
  if (src.includes('trend')) return '#db2777'
  if (src.includes('manual')) return '#d97706'
  if (src.includes('inherit')) return '#8b5cf6'
  return '#666'
}

async function loadList() {
  try {
    const res: any = await listVideoTags({
      page: page.value,
      pageSize,
      keyword: searchKw.value || undefined,
      extractStatus: statusFilter.value || undefined
    })
    if (res.success) {
      items.value = res.data.items || []
      total.value = res.data.total || 0
    }
  } catch (e) {
    console.error(e)
  }
}

function search() {
  page.value = 1
  loadList()
}

function goPage(p: number) {
  page.value = p
  loadList()
}

async function reExtractOne(videoId: number) {
  try {
    const res: any = await reExtractVideo(String(videoId))
    if (res.success) {
      toast.success(`作品 ${videoId} 已加入提取队列`)
      setTimeout(() => loadList(), 2000)
    } else {
      toast.error(res.message || '操作失败')
    }
  } catch (e) {
    toast.error('网络错误')
  }
}

async function reExtractAll() {
  reExtractingAll.value = true
  try {
    const res: any = await reExtractAllFailed()
    if (res.success) {
      toast.success(`已重提 ${res.data.count} 个作品`)
      setTimeout(() => loadList(), 3000)
    } else {
      toast.error(res.message || '操作失败')
    }
  } catch (e) {
    toast.error('网络错误')
  } finally {
    reExtractingAll.value = false
  }
}

onMounted(() => loadList())
</script>

<style scoped>
.tag-manage {
  max-width: 1400px;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 10px;
}

.search-bar {
  display: flex;
  align-items: center;
  gap: 8px;
}

.search-bar input {
  padding: 7px 12px;
  border: 1px solid #ddd;
  border-radius: 6px;
  font-size: 13px;
  outline: none;
}
.search-bar select {
  padding: 7px 10px;
  border: 1px solid #ddd;
  border-radius: 6px;
  font-size: 13px;
  outline: none;
  background: #fff;
}

.btn-primary {
  background: #fe2c55;
  color: #fff;
  border: none;
  padding: 7px 16px;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;
}
.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-sm {
  background: none;
  border: 1px solid #fe2c55;
  color: #fe2c55;
  padding: 3px 10px;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
}
.btn-sm:hover {
  background: #fff5f7;
}

.toolbar-actions {
  display: flex;
  gap: 8px;
}

/* === Table === */
.data-table {
  width: 100%;
  border-collapse: collapse;
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}

.data-table th {
  background: #fafafa;
  padding: 10px 12px;
  font-size: 12px;
  font-weight: 600;
  color: #888;
  text-align: left;
  border-bottom: 1px solid #eee;
  white-space: nowrap;
}

.data-table td {
  padding: 10px 12px;
  font-size: 13px;
  border-bottom: 1px solid #f5f5f5;
  vertical-align: middle;
}

.data-table tbody tr:hover {
  background: #fafafa;
}

.desc-cell {
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tags-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
}

.tag-chip {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  white-space: nowrap;
}
.tag-chip small {
  font-size: 10px;
  opacity: 0.7;
}

.status-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  margin-right: 4px;
  vertical-align: middle;
}

.empty-row {
  text-align: center;
  color: #aaa;
  padding: 40px;
}

/* === Badges === */
.badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
}
.type-video {
  background: #e8f4ff;
  color: #3b82f6;
}
.type-long {
  background: #ede9fe;
  color: #7c3aed;
}
.type-image {
  background: #d1fae5;
  color: #059669;
}
.type-text {
  background: #f3f4f6;
  color: #666;
}

/* === Pagination === */
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 20px;
  font-size: 13px;
}

.pagination button {
  padding: 6px 14px;
  border: 1px solid #ddd;
  background: #fff;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
}
.pagination button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.pagination button:hover:not(:disabled) {
  border-color: #fe2c55;
  color: #fe2c55;
}

.page-info {
  color: #666;
}
.total-info {
  color: #999;
  margin-left: 8px;
}
</style>
