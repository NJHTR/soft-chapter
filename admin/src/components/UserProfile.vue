<template>
  <teleport to="body">
    <div v-if="show" class="modal-mask" @click.self="$emit('close')">
      <div class="user-profile-modal">
        <div class="profile-header">
          <img v-if="profile.avatar" :src="normalizeImgUrl(profile.avatar)" class="profile-avatar" @error="onAvatarError" />
          <div v-else class="profile-avatar-placeholder">{{ (profile.nickname || '?').charAt(0) }}</div>
          <div>
            <h3>{{ profile.nickname || '未设置昵称' }}</h3>
            <span class="profile-uid">UID: {{ profile.uid }}</span>
            <span class="profile-role" :class="'role-' + (profile.role || 'user').toLowerCase()">{{ profile.role }}</span>
          </div>
          <button class="close-btn" @click="$emit('close')">✕</button>
        </div>

        <div class="profile-body">
          <div class="profile-section">
            <h4>基本信息</h4>
            <div class="info-grid">
              <div class="info-item"><span>邮箱</span><span>{{ profile.email || '-' }}</span></div>
              <div class="info-item"><span>性别</span><span>{{ ['未知','男','女'][profile.gender] || '未知' }}</span></div>
              <div class="info-item"><span>地区</span><span>{{ [profile.province, profile.city].filter(Boolean).join(' ') || '未知' }}</span></div>
              <div class="info-item"><span>注册时间</span><span>{{ fmtDate(profile.createTime) }}</span></div>
              <div class="info-item"><span>粉丝</span><span>{{ fmtNum(profile.followerCount) }}</span></div>
              <div class="info-item"><span>关注</span><span>{{ fmtNum(profile.followingCount) }}</span></div>
              <div class="info-item"><span>获赞</span><span>{{ fmtNum(profile.totalFavorited) }}</span></div>
              <div class="info-item"><span>作品数</span><span>{{ profile.videoCount || 0 }}</span></div>
            </div>
          </div>

          <div v-if="profile.portrait" class="profile-section">
            <h4>用户画像</h4>
            <div class="info-grid">
              <div class="info-item"><span>用户类型</span><span class="tag" :style="userTypeStyle">{{ userTypeLabel }}</span></div>
              <div class="info-item"><span>用户分层</span><span class="tag" :style="segmentStyle">{{ segmentLabel }}</span></div>
              <div class="info-item"><span>平均观看时长</span><span>{{ toFixed(profile.portrait.avgWatchDuration, 1) }}s</span></div>
              <div class="info-item"><span>完成率</span><span>{{ toPercent(profile.portrait.avgCompletionRate) }}</span></div>
              <div class="info-item"><span>快速划走率</span><span>{{ toPercent(profile.portrait.bounceRate) }}</span></div>
              <div class="info-item"><span>点赞率</span><span>{{ toPercent(profile.portrait.likeRate) }}</span></div>
              <div class="info-item"><span>收藏率</span><span>{{ toPercent(profile.portrait.collectRate) }}</span></div>
              <div class="info-item"><span>分享率</span><span>{{ toPercent(profile.portrait.shareRate) }}</span></div>
              <div class="info-item"><span>评论率</span><span>{{ toPercent(profile.portrait.commentRate) }}</span></div>
              <div class="info-item"><span>搜索频率</span><span>{{ toFixed(profile.portrait.searchFrequency, 2) }}/会话</span></div>
              <div class="info-item"><span>7天活跃天数</span><span>{{ profile.portrait.activeDaysLastWeek }}天</span></div>
              <div class="info-item"><span>平均会话时长</span><span>{{ toFixed(profile.portrait.avgSessionDuration, 0) }}s</span></div>
              <div class="info-item"><span>总观看次数</span><span>{{ fmtNum(profile.portrait.totalWatchCount) }}</span></div>
              <div class="info-item"><span>总点赞数</span><span>{{ fmtNum(profile.portrait.totalLikeCount) }}</span></div>
              <div class="info-item"><span>总收藏数</span><span>{{ fmtNum(profile.portrait.totalCollectCount) }}</span></div>
              <div class="info-item"><span>总评论数</span><span>{{ fmtNum(profile.portrait.totalCommentCount) }}</span></div>
              <div class="info-item"><span>总搜索次数</span><span>{{ fmtNum(profile.portrait.totalSearchCount) }}</span></div>
              <div class="info-item"><span>总观看时长</span><span>{{ fmtDuration(profile.portrait.totalWatchTimeSec) }}</span></div>
            </div>

            <div v-if="categoryWeights.length > 0" style="margin-top:12px;">
              <h5 style="font-size:12px;color:#888;margin-bottom:8px;">内容偏好</h5>
              <div style="display:flex;flex-wrap:wrap;gap:6px;">
                <span v-for="c in categoryWeights" :key="c.name" class="tag" style="background:#fef3c7;color:#d97706;">
                  {{ c.name }} {{ toPercent(c.weight) }}
                </span>
              </div>
            </div>

            <div v-if="recentSearches.length > 0" style="margin-top:12px;">
              <h5 style="font-size:12px;color:#888;margin-bottom:8px;">最近搜索</h5>
              <div style="display:flex;flex-wrap:wrap;gap:6px;">
                <span v-for="s in recentSearches" :key="s" class="tag" style="background:#e8f4ff;color:#3b82f6;">{{ s }}</span>
              </div>
            </div>

            <div v-if="activeHours.length > 0" style="margin-top:12px;">
              <h5 style="font-size:12px;color:#888;margin-bottom:8px;">活跃时段分布 (24小时)</h5>
              <div class="hour-bars">
                <div v-for="(v, h) in activeHours" :key="h" class="hour-bar" :title="h + '时: ' + v">
                  <div class="hour-fill" :style="{ height: getHourHeight(v) }"></div>
                  <span class="hour-label">{{ h }}</span>
                </div>
              </div>
            </div>
          </div>
          <div v-else class="profile-section">
            <p style="color:#999;text-align:center;">暂无画像数据</p>
          </div>
        </div>
      </div>
    </div>
  </teleport>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { getUserProfile } from '@/api/admin'
import { normalizeImgUrl } from '@/utils/image'

const props = defineProps<{ show: boolean; uid: number }>()
defineEmits(['close'])

function onAvatarError(e: Event) {
  const img = e.target as HTMLImageElement
  img.style.display = 'none'
}

const profile = ref<any>({})
const loading = ref(false)

const userTypeLabel = computed(() => {
  const map: Record<string, string> = {
    passive_consumer: '被动消费者', social_butterfly: '社交达人', power_liker: '点赞狂魔',
    collector: '收藏家', active_searcher: '搜索达人', creator_fan: '创作者追随者',
    explorer: '探索者', balanced: '均衡型'
  }
  return map[profile.value.portrait?.userType] || profile.value.portrait?.userType || '-'
})

const userTypeStyle = computed(() => {
  const colors: Record<string, string> = {
    passive_consumer: 'background:#f3f4f6;color:#666;',
    social_butterfly: 'background:#fce7f3;color:#db2777;',
    power_liker: 'background:#fef3c7;color:#d97706;',
    collector: 'background:#d1fae5;color:#059669;',
    active_searcher: 'background:#e8f4ff;color:#3b82f6;',
    creator_fan: 'background:#ede9fe;color:#7c3aed;',
    explorer: 'background:#fce7f3;color:#db2777;',
    balanced: 'background:#f3f4f6;color:#666;'
  }
  return colors[profile.value.portrait?.userType] || ''
})

const segmentLabel = computed(() => {
  const map: Record<string, string> = { new_user: '新用户', light: '轻度', medium: '中度', heavy: '重度' }
  return map[profile.value.portrait?.userSegment] || '-'
})

const segmentStyle = computed(() => {
  const colors: Record<string, string> = {
    new_user: 'background:#d1fae5;', light: 'background:#e8f4ff;',
    medium: 'background:#fef3c7;', heavy: 'background:#fee2e2;'
  }
  return colors[profile.value.portrait?.userSegment] || ''
})

const categoryWeights = computed(() => {
  try {
    const cw = profile.value.portrait?.categoryWeights
    if (typeof cw === 'string') {
      const parsed = JSON.parse(cw)
      return Object.entries(parsed).map(([k, v]) => ({ name: k, weight: v as number })).sort((a, b) => b.weight - a.weight)
    }
  } catch { /* JSON parse may fail */ }
  return []
})

const recentSearches = computed(() => {
  try {
    const rs = profile.value.portrait?.recentSearchQueries
    if (typeof rs === 'string') return JSON.parse(rs)
  } catch { /* JSON parse may fail */ }
  return []
})

const activeHours = computed(() => {
  try {
    const ah = profile.value.portrait?.activeHours
    if (typeof ah === 'string') return JSON.parse(ah)
  } catch { /* JSON parse may fail */ }
  return []
})

function getHourHeight(v: number) {
  const max = Math.max(...activeHours.value, 1)
  return (v / max * 100).toFixed(0) + '%'
}

function fmtNum(n: any) { return n ? (n >= 10000 ? (n / 10000).toFixed(1) + 'w' : n >= 1000 ? (n / 1000).toFixed(1) + 'k' : String(n)) : '0' }
function fmtDate(d: any) { return d ? new Date(d).toLocaleDateString('zh-CN') : '-' }
function toFixed(n: any, d: number) { return n != null ? Number(n).toFixed(d) : '-' }
function toPercent(n: any) { return n != null ? (Number(n) * 100).toFixed(1) + '%' : '-' }
function fmtDuration(sec: any) {
  if (!sec) return '0s'
  const s = Number(sec)
  if (s < 60) return s + 's'
  if (s < 3600) return Math.floor(s / 60) + 'm ' + (s % 60) + 's'
  return Math.floor(s / 3600) + 'h ' + Math.floor((s % 3600) / 60) + 'm'
}

watch(() => props.show, async (v) => {
  if (v && props.uid) {
    loading.value = true
    try {
      const res: any = await getUserProfile(props.uid)
      if (res.success) profile.value = res.data || {}
    } catch (e) { console.error(e) } finally { loading.value = false }
  }
})
</script>

<style scoped>
.user-profile-modal {
  background: #fff;
  border-radius: 12px;
  width: 700px;
  max-width: 90vw;
  max-height: 85vh;
  overflow-y: auto;
}
.profile-header {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 24px;
  border-bottom: 1px solid #eee;
}
.profile-avatar { width: 56px; height: 56px; border-radius: 50%; object-fit: cover; }
.profile-avatar-placeholder {
  width: 56px; height: 56px; border-radius: 50%; background: linear-gradient(135deg, #fe2c55, #ff6b81);
  color: #fff; display: flex; align-items: center; justify-content: center; font-size: 24px; font-weight: 600;
}
.profile-header h3 { font-size: 16px; margin: 0 0 4px 0; }
.profile-uid { font-size: 12px; color: #999; }
.profile-role { font-size: 11px; padding: 2px 8px; border-radius: 10px; margin-left: 8px; }
.role-admin { background: #fce7f3; color: #db2777; }
.role-user { background: #e8f4ff; color: #3b82f6; }
.role-merchant { background: #fef3c7; color: #d97706; }
.close-btn {
  margin-left: auto; background: none; border: none; font-size: 18px;
  color: #999; cursor: pointer; padding: 4px 8px;
}
.close-btn:hover { color: #333; }
.profile-body { padding: 20px 24px; }
.profile-section { margin-bottom: 20px; }
.profile-section h4 { font-size: 14px; font-weight: 600; color: #333; margin-bottom: 12px; padding-bottom: 8px; border-bottom: 1px solid #f5f5f5; }
.info-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px 20px; }
.info-item { display: flex; justify-content: space-between; font-size: 13px; color: #666; }
.info-item span:first-child { color: #999; }
.hour-bars { display: flex; gap: 2px; height: 60px; align-items: flex-end; }
.hour-bar { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: flex-end; height: 100%; }
.hour-fill { width: 100%; max-width: 20px; background: #3b82f6; border-radius: 2px 2px 0 0; min-height: 2px; }
.hour-label { font-size: 9px; color: #ccc; margin-top: 2px; }
</style>
