<template>
  <div class="bento">
    <h2 class="page-title">平台总览</h2>

    <div class="bento-grid">
      <!-- 1. 大卡片：核心指标 + 迷你趋势 -->
      <div class="bento-card card-hero">
        <div class="hero-stats">
          <div class="hero-stat" v-for="s in heroStats" :key="s.label">
            <component :is="s.icon" :size="22" class="hero-icon" />
            <span class="hero-value">{{ s.value }}</span>
            <span class="hero-label">{{ s.label }}</span>
          </div>
        </div>
        <div ref="sparklineChart" class="sparkline"></div>
      </div>

      <!-- 2. 今日数据速览 -->
      <div class="bento-card card-today">
        <h3 class="card-h3"><BarChart3 :size="14" class="h3-icon" /> 今日数据</h3>
        <div class="today-list">
          <div class="today-row" v-for="t in todayItems" :key="t.key">
            <component :is="t.icon" :size="15" class="today-icon" />
            <span class="today-key">{{ t.label }}</span>
            <span class="today-val">{{ t.value }}</span>
            <span v-if="t.cmp" class="today-cmp" :class="t.cmp.up ? 'up' : 'down'">{{ t.cmp.text }}</span>
          </div>
        </div>
      </div>

      <!-- 3. 审核流水线 -->
      <div class="bento-card card-pipeline">
        <h3 class="card-h3"><CheckCheck :size="14" class="h3-icon" /> 审核流水线</h3>
        <div class="pipe-cols">
          <div class="pipe-col">
            <div class="pipe-ring pending">
              <span class="pipe-num">{{ fmtNum(rp('pending')) }}</span>
            </div>
            <span class="pipe-label">待审核</span>
          </div>
          <div class="pipe-col">
            <div class="pipe-ring approved">
              <span class="pipe-num">{{ fmtNum(rp('approved')) }}</span>
            </div>
            <span class="pipe-label">已通过</span>
          </div>
          <div class="pipe-col">
            <div class="pipe-ring rejected">
              <span class="pipe-num">{{ fmtNum(rp('rejected')) }}</span>
            </div>
            <span class="pipe-label">已驳回</span>
          </div>
        </div>
      </div>

      <!-- 4. 内容类型分布 -->
      <div class="bento-card card-content">
        <h3 class="card-h3"><PieChart :size="14" class="h3-icon" /> 内容类型</h3>
        <div ref="contentDonut" class="donut-chart"></div>
      </div>

      <!-- 5. 互动趋势大卡片 -->
      <div class="bento-card card-engagement card-wide">
        <h3 class="card-h3"><Activity :size="14" class="h3-icon" /> 互动趋势 · 近7天 <span style="font-weight:400;font-size:10px;color:#999;">点赞 · 收藏</span></h3>
        <div ref="engagementMini" class="mini-line"></div>
      </div>

      <!-- 6. 流量来源 -->
      <div class="bento-card card-traffic">
        <h3 class="card-h3"><Globe :size="14" class="h3-icon" /> 流量来源</h3>
        <div class="traffic-bars">
          <div v-for="t in topSources" :key="t.name" class="traffic-row">
            <span class="traf-name">{{ t.name }}</span>
            <div class="traf-track"><div class="traf-fill" :style="{ width: t.pct + '%' }"></div></div>
            <span class="traf-pct">{{ t.pct }}%</span>
          </div>
        </div>
      </div>

      <!-- 7. 用户分层 -->
      <div class="bento-card card-segments">
        <h3 class="card-h3"><Layers :size="14" class="h3-icon" /> 用户分层</h3>
        <div class="segment-bubbles">
          <div v-for="s in segmentItems" :key="s.name" class="seg-bubble" :style="{ width: s.size + 'px', height: s.size + 'px', background: s.color }">
            <span class="seg-name">{{ s.name }}</span>
            <span class="seg-val">{{ s.value }}</span>
          </div>
        </div>
      </div>

      <!-- 8. 角色分布 -->
      <div class="bento-card card-roles">
        <h3 class="card-h3"><Shield :size="14" class="h3-icon" /> 用户角色</h3>
        <div class="role-blocks">
          <div v-for="(v, k) in summary.roleDistribution" :key="k" class="role-block" :class="'role-' + k.toLowerCase()">
            <component :is="roleIcons[k]" :size="20" class="rb-icon" />
            <span class="rb-num">{{ v }}</span>
            <span class="rb-label">{{ k }}</span>
          </div>
        </div>
      </div>

      <!-- 9. 电商概况 -->
      <div class="bento-card card-shop">
        <h3 class="card-h3"><ShoppingBag :size="14" class="h3-icon" /> 电商概况</h3>
        <div class="shop-stats">
          <div class="shop-stat"><span class="ss-num">{{ fmtNum(summary.totalGoods) }}</span><span class="ss-lbl">商品</span></div>
          <div class="shop-stat"><span class="ss-num">{{ fmtNum(summary.totalOrders) }}</span><span class="ss-lbl">订单</span></div>
          <div class="shop-stat"><span class="ss-num">{{ fmtNum(summary.todayOrders) }}</span><span class="ss-lbl">今日订单</span></div>
        </div>
      </div>

      <!-- 10. 直播状态 -->
      <div class="bento-card card-live">
        <h3 class="card-h3"><Radio :size="14" class="h3-icon" /> 直播状态</h3>
        <div class="live-indicator">
          <span class="live-dot" :class="{ on: summary.activeLiveRooms > 0 }"></span>
          <span class="live-num">{{ summary.activeLiveRooms || 0 }}</span>
          <span class="live-label">场直播中</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { getDashboardSummary, getEngagementTrend, getTrafficSources } from '@/api/admin'
import {
  Users, User, Video, Package, Radio,
  Eye, Heart, Star, MessageCircle, Search, ShoppingCart,
  BarChart3, CheckCheck, PieChart, Activity, Globe, Layers, Shield, ShoppingBag
} from 'lucide-vue-next'

const summary = reactive<Record<string, any>>({
  onlineUsers: 0, totalUsers: 0, totalVideos: 0, totalOrders: 0,
  totalGoods: 0, activeLiveRooms: 0,
  todayNewVideos: 0, todayActiveUsers: 0, todayViews: 0, todayLikes: 0,
  todayCollects: 0, todayComments: 0, todaySearches: 0, todayOrders: 0,
  yesterdayViews: 0, yesterdayLikes: 0,
  reviewPipeline: {}, roleDistribution: {}, contentTypeBreakdown: {},
  userSegmentDistribution: {}
})

const sparklineChart = ref<HTMLDivElement | null>(null)
const contentDonut = ref<HTMLDivElement | null>(null)
const engagementMini = ref<HTMLDivElement | null>(null)
let charts: any[] = []

// Icon mappings
const roleIcons: Record<string, any> = { ADMIN: Shield, USER: User, MERCHANT: ShoppingBag }

function fmtNum(n: any): string {
  if (!n) return '0'
  if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return String(n)
}
function rp(k: string) { return summary.reviewPipeline?.[k] || 0 }

const heroStats = computed(() => [
  { icon: Users,  label: '在线',   value: fmtNum(summary.onlineUsers) },
  { icon: User,   label: '总用户', value: fmtNum(summary.totalUsers) },
  { icon: Video,  label: '总作品', value: fmtNum(summary.totalVideos) },
  { icon: Package, label: '总订单', value: fmtNum(summary.totalOrders) },
  { icon: Radio,  label: '直播中', value: summary.activeLiveRooms || 0 },
])

const todayItems = computed(() => {
  const cmpViews = calcCmp(summary.todayViews, summary.yesterdayViews)
  const cmpLikes = calcCmp(summary.todayLikes, summary.yesterdayLikes)
  return [
    { key: 'newVideos',   icon: Video,          label: '新增作品', value: fmtNum(summary.todayNewVideos) },
    { key: 'activeUsers', icon: User,           label: '活跃用户', value: fmtNum(summary.todayActiveUsers) },
    { key: 'views',       icon: Eye,            label: '浏览量',   value: fmtNum(summary.todayViews), cmp: cmpViews },
    { key: 'likes',       icon: Heart,          label: '点赞',     value: fmtNum(summary.todayLikes), cmp: cmpLikes },
    { key: 'collects',    icon: Star,           label: '收藏',     value: fmtNum(summary.todayCollects) },
    { key: 'comments',    icon: MessageCircle,  label: '评论',     value: fmtNum(summary.todayComments) },
    { key: 'searches',    icon: Search,         label: '搜索',     value: fmtNum(summary.todaySearches) },
    { key: 'orders',      icon: ShoppingCart,   label: '订单',     value: fmtNum(summary.todayOrders) },
  ]
})

function calcCmp(today: number, yesterday: number) {
  if (!yesterday || !today) return null
  const pct = Math.round((today - yesterday) / yesterday * 100)
  return { up: pct >= 0, text: (pct >= 0 ? '↑' : '↓') + Math.abs(pct) + '%' }
}

const trafficSources = ref<any[]>([])
const topSources = computed(() => {
  const total = trafficSources.value.reduce((s: number, i: any) => s + i.value, 0) || 1
  return trafficSources.value.slice(0, 5).map((s: any) => ({
    name: sourceLabels[s.name] || s.name,
    pct: Math.round(s.value / total * 100)
  }))
})
const sourceLabels: Record<string, string> = {
  'HOME_RECOMMEND': '首页推荐', 'SEARCH': '搜索', 'USER_PROFILE': '个人主页',
  'FOLLOWING': '关注页', 'HASHTAG': '话题', 'EXTERNAL': '外部', 'SHARE': '分享'
}

const segmentItems = computed(() => {
  const seg = summary.userSegmentDistribution || {}
  const labels: Record<string, { name: string; color: string }> = {
    'heavy': { name: '重度', color: '#ef4444' },
    'medium': { name: '中度', color: '#f59e0b' },
    'light': { name: '轻度', color: '#3b82f6' },
    'new_user': { name: '新用户', color: '#10b981' },
    'unknown': { name: '未知', color: '#9ca3af' },
  }
  const max = Math.max(...Object.values(seg).map(Number), 1)
  return Object.entries(seg).map(([k, v]) => {
    const info = labels[k] || { name: k, color: '#9ca3af' }
    const size = 48 + (Number(v) / max) * 48
    return { ...info, value: v as number, size }
  })
})

// === Charts ===
function loadSparkline() {
  if (!sparklineChart.value) return
  const c = echarts.init(sparklineChart.value)
  c.setOption({
    grid: { left: 0, right: 0, top: 5, bottom: 0 },
    xAxis: { show: false, data: [] },
    yAxis: { show: false, min: 0 },
    series: [{ type: 'line', data: [], smooth: true, symbol: 'none', lineStyle: { color: 'rgba(255,255,255,0.4)', width: 2 }, areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(255,255,255,0.2)' }, { offset: 1, color: 'rgba(255,255,255,0)' }]) } }]
  })
  charts.push(c)
}

async function updateSparkline() {
  try {
    const res: any = await getEngagementTrend({ period: 'daily', days: 7 })
    if (res.success && charts[0]) {
      const data = res.data || []
      charts[0].setOption({
        xAxis: { data: data.map((d: any) => d.time) },
        series: [{ data: data.map((d: any) => (d.likes || 0) + (d.collects || 0)) }]
      })
    }
  } catch (e) { console.error(e) }
}

function loadContentDonut() {
  if (!contentDonut.value) return
  const bd = summary.contentTypeBreakdown || {}
  const labels: Record<string, string> = { 'recommend-video': '短视频', 'image': '图文', 'text': '文字', 'long-video': '长视频' }
  const data = Object.entries(bd).map(([k, v]) => ({ name: labels[k] || k, value: v }))
  const c = echarts.init(contentDonut.value)
  c.setOption({
    tooltip: { trigger: 'item' },
    series: [{ type: 'pie', radius: ['55%', '80%'], center: ['50%', '50%'], data, label: { show: false }, emphasis: { label: { show: true, fontSize: 12 } }, itemStyle: { borderRadius: 3, borderColor: '#fff', borderWidth: 2 } }]
  })
  charts.push(c)
}

function loadEngagementMini() {
  if (!engagementMini.value) return
  const c = echarts.init(engagementMini.value)
  c.setOption({
    grid: { left: 5, right: 15, top: 5, bottom: 5 },
    xAxis: { show: false, data: [] }, yAxis: { show: false },
    series: [
      { type: 'line', data: [], smooth: true, symbol: 'none', lineStyle: { color: '#fe2c55', width: 2 }, areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(254,44,85,0.2)' }, { offset: 1, color: 'rgba(254,44,85,0)' }]) } },
      { type: 'line', data: [], smooth: true, symbol: 'none', lineStyle: { color: '#3b82f6', width: 2 }, areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(59,130,246,0.2)' }, { offset: 1, color: 'rgba(59,130,246,0)' }]) } }
    ]
  })
  charts.push(c)
}

async function updateEngagementMini() {
  try {
    const res: any = await getEngagementTrend({ period: 'daily', days: 7 })
    if (res.success && charts.length >= 3) {
      const data = res.data || []
      charts[2].setOption({ xAxis: { data: data.map((d: any) => d.time) }, series: [{ data: data.map((d: any) => d.likes || 0) }, { data: data.map((d: any) => d.collects || 0) }] })
    }
  } catch (e) { console.error(e) }
}

async function loadTraffic() {
  try {
    const res: any = await getTrafficSources()
    if (res.success) trafficSources.value = res.data?.sources || []
  } catch (e) { console.error(e) }
}

onMounted(async () => {
  try {
    const res: any = await getDashboardSummary()
    if (res.success) Object.assign(summary, res.data)
  } catch (e) { console.error(e) }
  await nextTick()
  loadSparkline(); loadContentDonut(); loadEngagementMini(); loadTraffic()
  await nextTick()
  updateSparkline(); updateEngagementMini()
})

onUnmounted(() => charts.forEach(c => c.dispose()))
</script>

<style scoped>
.bento { max-width: 1500px; }

.bento-grid {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr;
  grid-auto-rows: auto;
  gap: 14px;
}

.bento-card {
  position: relative;
  background: #fff;
  border-radius: 16px;
  padding: 18px 20px;
  box-shadow:
    0 0 0 1px rgba(0,0,0,0.03),
    0 2px 4px rgba(0,0,0,0.04),
    0 8px 24px rgba(0,0,0,0.06);
  border-top: 1px solid rgba(255,255,255,0.8);
  transition: box-shadow 0.25s ease, transform 0.25s ease;
  overflow: hidden;
  &:hover {
    box-shadow:
      0 0 0 1px rgba(0,0,0,0.04),
      0 4px 8px rgba(0,0,0,0.06),
      0 12px 32px rgba(0,0,0,0.1);
    transform: translateY(-1px);
  }
}
.bento-card::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  pointer-events: none;
  z-index: 1;
  filter: url(#noise-filter-subtle);
}

.card-wide { grid-column: span 2; }

.card-h3 {
  font-size: 13px; font-weight: 600; color: #888;
  margin-bottom: 12px; letter-spacing: 0.5px;
  display: flex; align-items: center; gap: 6px;
}
.h3-icon { flex-shrink: 0; color: #aaa; }

/* Hero Card */
.card-hero {
  grid-row: span 1;
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
  color: #fff;
  padding: 24px 28px;
}
.hero-stats { display: flex; gap: 20px; justify-content: space-between; margin-bottom: 8px; }
.hero-stat { display: flex; flex-direction: column; align-items: center; gap: 4px; }
.hero-icon { opacity: 0.7; }
.hero-value { font-size: 26px; font-weight: 700; letter-spacing: -1px; }
.hero-label { font-size: 11px; opacity: 0.5; }
.sparkline { width: 100%; height: 50px; margin-top: 6px; }

/* Today Card */
.card-today { background: #fafbff; }
.today-list { display: flex; flex-direction: column; gap: 2px; }
.today-row {
  display: flex; align-items: center; gap: 8px;
  padding: 6px 8px; border-radius: 8px; transition: background 0.15s;
  &:hover { background: #f0f3ff; }
}
.today-icon { color: #999; flex-shrink: 0; }
.today-key { flex: 1; font-size: 13px; color: #666; }
.today-val { font-size: 14px; font-weight: 600; color: #333; }
.today-cmp { font-size: 11px; font-weight: 600; }
.today-cmp.up { color: #10b981; }
.today-cmp.down { color: #ef4444; }

/* Pipeline Card */
.card-pipeline { background: #fffdf7; }
.pipe-cols { display: flex; justify-content: space-around; align-items: center; padding-top: 8px; }
.pipe-col { display: flex; flex-direction: column; align-items: center; gap: 8px; }
.pipe-ring {
  width: 72px; height: 72px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center; border: 4px solid;
}
.pipe-ring.pending { border-color: #f59e0b; background: #fef9e7; }
.pipe-ring.approved { border-color: #10b981; background: #ecfdf5; }
.pipe-ring.rejected { border-color: #ef4444; background: #fef2f2; }
.pipe-num { font-size: 20px; font-weight: 700; }
.pipe-ring.pending .pipe-num { color: #d97706; }
.pipe-ring.approved .pipe-num { color: #059669; }
.pipe-ring.rejected .pipe-num { color: #dc2626; }
.pipe-label { font-size: 12px; color: #888; }

/* Content Donut */
.card-content { background: #fff; }
.donut-chart { width: 100%; height: 150px; }

/* Engagement Wide */
.card-engagement { background: linear-gradient(135deg, #faf5ff 0%, #fdf2f8 100%); }
.mini-line { width: 100%; height: 140px; }

/* Traffic Card */
.card-traffic { background: #f8fcff; }
.traffic-bars { display: flex; flex-direction: column; gap: 10px; }
.traffic-row { display: flex; align-items: center; gap: 8px; }
.traf-name { font-size: 12px; color: #666; width: 60px; text-align: right; flex-shrink: 0; }
.traf-track { flex: 1; height: 6px; background: #e8f0fe; border-radius: 3px; overflow: hidden; }
.traf-fill { height: 100%; border-radius: 3px; background: linear-gradient(90deg, #3b82f6, #60a5fa); transition: width 0.8s ease; }
.traf-pct { font-size: 11px; color: #999; width: 32px; text-align: right; }

/* Segments Card */
.card-segments { background: #fff; }
.segment-bubbles { display: flex; flex-wrap: wrap; gap: 10px; justify-content: center; align-items: center; padding-top: 8px; }
.seg-bubble {
  border-radius: 50%; display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  color: #fff; font-weight: 600; transition: transform 0.3s;
  &:hover { transform: scale(1.1); }
}
.seg-name { font-size: 11px; opacity: 0.9; }
.seg-val { font-size: 16px; }

/* Roles Card */
.card-roles { background: #f5f5ff; }
.role-blocks { display: flex; gap: 10px; padding-top: 4px; }
.role-block {
  flex: 1; border-radius: 12px; padding: 14px 10px;
  text-align: center; color: #fff; display: flex; flex-direction: column; align-items: center; gap: 4px;
}
.role-block.role-admin { background: linear-gradient(135deg, #db2777, #f472b6); }
.role-block.role-user { background: linear-gradient(135deg, #2563eb, #60a5fa); }
.role-block.role-merchant { background: linear-gradient(135deg, #d97706, #fbbf24); }
.rb-icon { opacity: 0.7; }
.rb-num { font-size: 22px; font-weight: 700; }
.rb-label { font-size: 11px; opacity: 0.85; }

/* Shop Card */
.card-shop { background: #f0fdf4; }
.shop-stats { display: flex; justify-content: space-around; padding-top: 8px; }
.shop-stat { display: flex; flex-direction: column; align-items: center; gap: 4px; }
.ss-num { font-size: 22px; font-weight: 700; color: #059669; }
.ss-lbl { font-size: 11px; color: #888; }

/* Live Card */
.card-live { background: #fff5f5; }
.live-indicator { display: flex; align-items: center; gap: 10px; padding-top: 14px; justify-content: center; }
.live-dot {
  width: 14px; height: 14px; border-radius: 50%; background: #ddd;
  &.on { background: #ef4444; animation: pulse 1.5s ease infinite; }
}
@keyframes pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.5); }
  50% { box-shadow: 0 0 0 8px rgba(239, 68, 68, 0); }
}
.live-num { font-size: 28px; font-weight: 700; color: #ef4444; }
.live-label { font-size: 13px; color: #888; }

/* Responsive */
@media (max-width: 1200px) {
  .bento-grid { grid-template-columns: 1fr 1fr; }
  .card-hero { grid-column: span 2; }
  .card-wide { grid-column: span 1; }
}
@media (max-width: 768px) {
  .bento-grid { grid-template-columns: 1fr; }
  .card-hero, .card-wide { grid-column: span 1; }
  .hero-stats { flex-wrap: wrap; gap: 12px; }
}
</style>
