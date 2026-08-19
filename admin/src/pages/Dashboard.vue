<template>
  <div class="dashboard">
    <!-- 页面头部 -->
    <header class="page-header">
      <h1 class="page-title">平台总览</h1>
      <div class="header-right">
        <select v-model="selectedPeriod" @change="onPeriodChange" class="period-select">
          <option v-for="p in periods" :key="p.key" :value="p.key">{{ p.label }}</option>
        </select>
        <template v-if="selectedPeriod === 'custom'">
          <input type="date" v-model="customStart" class="cr-input" @change="onCustomRangeChange" />
          <span class="cr-sep">至</span>
          <input type="date" v-model="customEnd" class="cr-input" @change="onCustomRangeChange" />
        </template>
        <span class="page-meta">· {{ timeRangeLabel }}</span>
      </div>
    </header>

    <!-- 核心指标条 -->
    <section class="hero-strip">
      <div class="hero-metrics">
        <!-- 在线用户 — 按角色拆分 -->
        <div class="hero-metric" v-for="r in onlineRoleMetrics" :key="r.role">
          <component :is="Users" :size="18" class="hm-icon" :style="{ color: r.color }" />
          <div class="hm-body">
            <div class="hm-main">
              <span class="hm-value" :style="{ color: r.color }">{{ r.count }}</span>
              <span class="hm-label">{{ r.label }}</span>
            </div>
          </div>
        </div>
        <!-- 其余 3 项 -->
        <div class="hero-metric" v-for="s in heroStatsRest" :key="s.label">
          <component :is="s.icon" :size="18" class="hm-icon" :style="{ color: s.color }" />
          <div class="hm-body">
            <div class="hm-main">
              <span class="hm-value">{{ s.value }}</span>
              <span class="hm-label">{{ s.label }}</span>
            </div>
            <span class="hm-detail">{{ s.detail }}</span>
            <span v-if="s.extras" class="hm-extras">
              <span v-for="(e, i) in s.extras" :key="i" class="hm-extra-tag">{{ e }}</span>
            </span>
          </div>
        </div>
      </div>
      <div class="hm-avatar-row hero-avatar-strip" v-if="onlineUserAvatars.length">
        <template v-for="(u, i) in onlineUserAvatars" :key="u.userId">
          <span class="hm-av-wrap">
            <img
              v-if="u.avatarUrl"
              :src="normalizeImgUrl(u.avatarUrl)" class="hm-av"
              :title="u.nickname"
              @error="onAvatarError"
            />
            <span v-else class="hm-av-fallback" :title="u.nickname">
              <svg viewBox="0 0 24 24" fill="none"><circle cx="12" cy="8" r="4" fill="currentColor"/><path d="M4 22c0-4.4 3.6-8 8-8s8 3.6 8 8" fill="currentColor"/></svg>
            </span>
            <i class="hm-av-dot"></i>
          </span>
        </template>
        <span v-if="summary.onlineUsers > 5" class="hm-av-more">+{{ summary.onlineUsers - 5 }}</span>
      </div>
      <div ref="sparklineChart" class="sparkline-area"></div>
    </section>

    <hr class="soft-rule" />

    <!-- 今日数据 — 流式排列 -->
    <section class="block">
      <h3 class="block-heading">{{ periodDesc }}数据速览</h3>
      <div class="flow-stats">
        <div class="flow-stat" v-for="t in todayItems" :key="t.key">
          <span class="fs-value">{{ t.value }}</span>
          <span class="fs-label">{{ t.label }}</span>
          <span v-if="t.cmp" class="fs-cmp" :class="t.cmp.up ? 'up' : 'down'">
            {{ t.cmp.text }}
          </span>
        </div>
      </div>
    </section>

    <hr class="soft-rule" />

    <!-- 双列区 -->
    <div class="grid-2">
      <section class="block">
        <div class="block-heading-row">
          <h3 class="block-heading">审核流水线</h3>
          <div class="pipeline-summary-row">
            <span class="ps-badge pending">⏳ {{ fmtNum(rp('pending')) }}</span>
            <span class="ps-badge approved">✓ {{ fmtNum(rp('approved')) }}</span>
            <span class="ps-badge rejected">✗ {{ fmtNum(rp('rejected')) }}</span>
            <span class="ps-pass-rate">通过率 {{ rp('approved') + rp('rejected') > 0 ? Math.round(rp('approved') / (rp('approved') + rp('rejected')) * 100) : 0 }}%</span>
          </div>
        </div>
        <div class="gantt-container">
          <div class="gantt-header">
            <span class="gh-label">内容</span>
            <div class="gh-timeline">
              <span class="gh-title">审核甘特图 · 最近 8 小时</span>
              <div class="gh-ticks">
                <span v-for="t in ganttTicks" :key="t.label" class="gh-tick" :style="{ left: t.pos + '%' }">{{ t.label }}</span>
              </div>
            </div>
            <span class="gh-detail">耗时 · 审核人</span>
          </div>
          <div v-if="ganttItems.length === 0" class="gantt-empty">暂无审核记录</div>
          <div v-for="gi in ganttItems" :key="gi.id" class="gantt-row">
            <span class="gr-desc" :title="gi.desc">{{ gi.desc?.slice(0, 14) || '—' }}{{ gi.desc?.length > 14 ? '…' : '' }}</span>
            <div class="gr-track">
              <div class="gr-grid">
                <div v-for="t in ganttTicks" :key="'g'+t.label" class="gr-grid-line" :style="{ left: t.pos + '%' }"></div>
              </div>
              <div class="gr-now" :style="{ left: ganttNowPos + '%' }" title="当前时间"></div>
              <div class="gr-bar"
                :style="{ left: gi.barLeft + '%', width: Math.max(gi.barWidth, 1.5) + '%' }"
                :class="'gr-bar-' + gi.status.toLowerCase()"
                @mouseenter="gi._hover = true" @mouseleave="gi._hover = false"
              >
                <span class="gr-bar-label" v-if="gi.barWidth > 12">{{ gi.waitText }}</span>
              </div>
              <span v-if="gi.status !== 'PENDING'" class="gr-marker" :style="{ left: Math.min(gi.barLeft + gi.barWidth, 97) + '%' }" :class="'gr-marker-' + gi.status.toLowerCase()">
                <span class="gr-marker-dot"></span>
              </span>
              <div v-if="gi._hover" class="gr-tooltip">
                <div class="gr-tt-title">{{ gi.desc }}</div>
                <div class="gr-tt-row">提交 {{ gi.createTimeLabel }}</div>
                <div class="gr-tt-row" v-if="gi.endTimeLabel">完成 {{ gi.endTimeLabel }}</div>
                <div class="gr-tt-row">耗时 {{ gi.waitText }}</div>
              </div>
            </div>
            <span class="gr-meta">
              <span class="gr-status" :class="'gr-st-' + gi.status.toLowerCase()">{{ gi.statusLabel }}</span>
              <span class="gr-wait">{{ gi.waitText }}</span>
              <span class="gr-reviewer">{{ gi.reviewer }}</span>
            </span>
          </div>
        </div>
      </section>

      <section class="block block-wide">
        <div class="block-heading-row">
          <h3 class="block-heading">互动趋势 · {{ periodDesc }}</h3>
          <div class="legend-inline">
            <span class="dot" style="background:#f43f5e"></span> 点赞
            <span class="dot" style="background:#3b82f6"></span> 收藏
          </div>
        </div>
        <div ref="engagementChart" class="chart-area"></div>
      </section>
    </div>

    <hr class="soft-rule" />

    <!-- 三列区 -->
    <div class="grid-3">
      <section class="block">
        <h3 class="block-heading">内容类型分布</h3>
        <div ref="contentDonut" class="chart-area chart-donut"></div>
      </section>

      <section class="block">
        <h3 class="block-heading">流量来源 Top 5</h3>
        <div class="traffic-flow">
          <div v-for="t in topSources" :key="t.name" class="traf-row">
            <span class="traf-name">{{ t.name }}</span>
            <div class="traf-track">
              <div class="traf-fill" :style="{ width: t.pct + '%' }"></div>
            </div>
            <span class="traf-pct">{{ t.pct }}%</span>
          </div>
        </div>
      </section>

      <section class="block">
        <h3 class="block-heading">用户分层</h3>
        <div class="bubble-flow">
          <div
            v-for="s in segmentItems" :key="s.name"
            class="seg-bubble"
            :style="{ width: s.size + 'px', height: s.size + 'px', background: s.color }"
          >
            <span class="seg-val">{{ s.value }}</span>
            <span class="seg-name">{{ s.name }}</span>
          </div>
        </div>
      </section>
    </div>

    <hr class="soft-rule" />

    <!-- 底行三列 -->
    <div class="grid-3">
      <section class="block">
        <h3 class="block-heading">用户角色</h3>
        <div class="role-flow">
          <div v-for="(v, k) in summary.roleDistribution" :key="k" class="role-item" :class="'role-' + String(k).toLowerCase()">
            <div class="ri-badge">
              <component :is="roleIcons[k]" :size="16" />
            </div>
            <div class="ri-info">
              <span class="ri-num">{{ fmtNum(v) }}</span>
              <span class="ri-label">{{ roleLabels[k] || k }}</span>
              <span class="ri-ratio">{{ (v / (summary.totalUsers || 1) * 100).toFixed(1) }}%</span>
            </div>
          </div>
        </div>
        <div class="role-bar">
          <div class="role-bar-fill admin" :style="{ width: ((summary.roleDistribution?.ADMIN || 0) / (summary.totalUsers || 1) * 100).toFixed(2) + '%' }"></div>
          <div class="role-bar-fill merchant" :style="{ width: ((summary.roleDistribution?.MERCHANT || 0) / (summary.totalUsers || 1) * 100).toFixed(2) + '%' }"></div>
          <div class="role-bar-fill user" :style="{ width: ((summary.roleDistribution?.USER || 0) / (summary.totalUsers || 1) * 100).toFixed(2) + '%' }"></div>
        </div>
      </section>

      <section class="block">
        <h3 class="block-heading">电商概况</h3>
        <div class="shop-flow">
          <div class="shop-item">
            <span class="si-num">{{ fmtNum(summary.totalGoods) }}</span>
            <span class="si-lbl">商品 · {{ summary.totalGoods > 0 ? Math.round(summary.todayOrders / summary.totalGoods * 10) / 10 : 0 }}% 动销</span>
          </div>
          <div class="shop-item">
            <span class="si-num highlight">{{ fmtNum(summary.todayOrders) }}</span>
            <span class="si-lbl highlight">今日订单</span>
          </div>
          <div class="shop-item">
            <span class="si-num revenue">¥{{ ((summary.todayRevenue || 0) / 100).toFixed(0) }}</span>
            <span class="si-lbl">今日交易额</span>
          </div>
        </div>
        <div class="shop-detail">
          <div class="sd-row"><span>总订单</span><b>{{ fmtNum(summary.totalOrders) }}</b></div>
          <div class="sd-row"><span>履约率</span><b class="good">94%</b></div>
          <div class="sd-row"><span>客单价</span><b>¥{{ ((summary.todayRevenue || 0) / (summary.todayOrders || 1) / 100).toFixed(0) }}</b></div>
        </div>
      </section>

      <section class="block">
        <h3 class="block-heading">直播大厅</h3>
        <div class="live-summary">
          <span class="ls-pulse"></span>
          <span class="ls-count">{{ summary.activeLiveRooms || liveRoomList.length }} 场直播</span>
          <span v-if="liveHotCount" class="ls-hot">🔥 {{ liveHotCount }} 热门</span>
          <span class="ls-viewers">👁 {{ fmtNum(liveTotalViewers) }} 观众</span>
        </div>
        <div class="live-cards">
          <div v-for="lr in liveRoomList.slice(0, 5)" :key="lr.id" class="live-card">
            <div class="lc-cover">
              <span class="lc-cover-fallback" :style="{ background: ['#ef4444','#f59e0b','#3b82f6','#10b981','#8b5cf6'][lr.id % 5] }">
                {{ lr.hostName?.[0] || '?' }}
              </span>
              <span class="lc-dot"></span>
            </div>
            <div class="lc-body">
              <span class="lc-title">
                {{ lr.title }}
                <span v-if="lr.viewerCount >= 1000" class="lc-badge-hot">🔥</span>
              </span>
              <span class="lc-host">{{ lr.hostName }}</span>
            </div>
            <div class="lc-stats">
              <span class="lc-viewers">{{ fmtNum(lr.viewerCount) }}</span>
              <span class="lc-likes">❤️ {{ fmtNum(lr.likeCount) }}</span>
            </div>
          </div>
        </div>
        <div v-if="summary.activeLiveRooms > 5" class="live-more">
          还有 {{ summary.activeLiveRooms - 5 }} 场直播...
        </div>
      </section>
    </div>

    <hr class="soft-rule" />

    <!-- 实时动态 + 热门内容 -->
    <div class="grid-2" style="grid-template-columns: 2fr 1fr;">
      <section class="block">
        <h3 class="block-heading">实时动态</h3>
        <div class="activity-feed">
          <div v-for="(a, i) in recentActivities" :key="i" class="af-row">
            <div class="af-icon-wrap" :class="'af-' + a.type">
              <component :is="activityIcons[a.type] || User" :size="13" />
            </div>
            <span class="af-desc">{{ a.desc }}</span>
            <span class="af-meta">{{ a.user }}</span>
            <span class="af-time">{{ timeAgo(a.createTime || a.create_time) }}</span>
          </div>
        </div>
      </section>

      <section class="block">
        <h3 class="block-heading">热门内容 Top 5</h3>
        <div class="top-content">
          <div v-for="(c, i) in topContentList" :key="i" class="tc-row">
            <span class="tc-rank" :class="{ 'top3': i < 3 }">{{ i + 1 }}</span>
            <div class="tc-body">
              <span class="tc-title">{{ c.title }}</span>
              <span class="tc-sub">{{ c.author }}</span>
            </div>
            <div class="tc-stats">
              <span class="tc-views"><Eye :size="12" /> {{ fmtNum(c.views) }}</span>
              <span class="tc-likes"><Heart :size="12" /> {{ fmtNum(c.likes) }}</span>
            </div>
          </div>
        </div>
      </section>
    </div>

    <hr class="soft-rule" />

    <!-- 设备 + 标签 + 漏斗 -->
    <div class="grid-3">
      <section class="block">
        <h3 class="block-heading">设备分布</h3>
        <div class="device-flow">
          <div v-for="d in deviceDistribution" :key="d.name" class="dev-row">
            <span class="dev-name">{{ d.name }}</span>
            <div class="dev-track">
              <div class="dev-fill" :style="{ width: d.pct + '%', background: d.color }"></div>
            </div>
            <span class="dev-pct">{{ d.pct }}%</span>
          </div>
        </div>
        <div class="device-total">日活设备 {{ fmtNum(deviceTotal) }} 台</div>
      </section>

      <section class="block">
        <h3 class="block-heading">热门标签</h3>
        <div class="tag-cloud">
          <span
            v-for="t in popularTags" :key="t.name"
            class="tag-badge"
            :style="{ fontSize: (12 + t.scale * 10) + 'px', opacity: 0.55 + t.scale * 0.45 }"
          >
            {{ t.name }}
          </span>
        </div>
      </section>

      <section class="block">
        <h3 class="block-heading">转化漏斗</h3>
        <div class="funnel-flow">
          <div v-for="(f, i) in funnelData" :key="f.label" class="fun-step">
            <div class="fun-bar-wrap">
              <div class="fun-bar" :style="{ width: f.pct + '%', background: funnelColors[i] }"></div>
            </div>
            <div class="fun-meta">
              <span class="fun-label">{{ f.label }}</span>
              <span class="fun-val">{{ fmtNum(f.value) }}</span>
              <span class="fun-rate">{{ f.pct }}%</span>
            </div>
          </div>
        </div>
      </section>
    </div>

    <hr class="soft-rule" />

    <!-- 用户增长 + 时段活跃 + 订单状态 -->
    <div class="grid-3">
      <section class="block">
        <h3 class="block-heading">{{ periodDesc }}用户增长</h3>
        <div class="growth-strip">
          <div class="gs-bar-group" v-if="userGrowthData.length">
            <div
              v-for="(g, i) in userGrowthData.slice(-14)" :key="i"
              class="gs-bar"
              :style="{ height: g.scale * 100 + '%' }"
              :title="g.time + ': ' + g.count"
            ></div>
          </div>
          <div class="gs-summary" v-if="userGrowthData.length">
            <span class="gs-val">{{ fmtNum(userGrowthData[userGrowthData.length - 1]?.cumulative || summary.totalUsers) }}</span>
            <span class="gs-sub">累计注册</span>
          </div>
        </div>
      </section>

      <section class="block">
        <h3 class="block-heading">{{ periodDesc }}活跃分布</h3>
        <div class="hourly-strip">
          <div v-for="(v, h) in hourlyActivity" :key="h" class="hour-bar-wrap">
            <div
              class="hour-bar"
              :style="{ height: maxHourly > 0 ? (v / maxHourly * 100) + '%' : '0%' }"
            ></div>
            <span class="hour-label" :class="{ peak: v === maxHourly && v > 0 }">{{ h }}</span>
          </div>
        </div>
      </section>

      <section class="block">
        <h3 class="block-heading">订单状态分布</h3>
        <div class="order-status-flow">
          <div v-for="os in orderStatusItems" :key="os.label" class="os-row">
            <span class="os-dot" :style="{ background: os.color }"></span>
            <span class="os-label">{{ os.label }}</span>
            <span class="os-val">{{ os.value }}</span>
          </div>
        </div>
        <div class="ecom-extra" v-if="summary.todayRevenue !== undefined">
          <span class="ee-label">{{ periodDesc }}交易额</span>
          <span class="ee-val">¥{{ (summary.todayRevenue / 100).toFixed(0) }}</span>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import {
  Users, User, Video, Package,
  Eye, Heart, Star, MessageCircle, Search, ShoppingCart,
  Shield, ShoppingBag, Smartphone, Tag, Share2, UserPlus, Radio, Upload
} from 'lucide-vue-next'
import {
  getDashboardSummary, getEngagementTrend, getTrafficSources,
  getTopVideos, getDeviceStats, getSearchStats, getEcommerceOverview,
  getUserSegments, getRecentActivity,
  getUserGrowth, getUserActivityFlow, getAuditDrilldown, getOnlineUsers
} from '@/api/admin'
import { normalizeImgUrl } from '@/utils/image'

// ── Period selector ──
type Period = 'realtime' | 'today' | 'yesterday' | 'week' | 'month' | 'year' | 'custom'
const selectedPeriod = ref<Period>('today')
const customStart = ref('')
const customEnd = ref('')
const periods = [
  { key: 'realtime' as Period, label: '实时' },
  { key: 'today' as Period, label: '今日' },
  { key: 'yesterday' as Period, label: '昨日' },
  { key: 'week' as Period, label: '本周' },
  { key: 'month' as Period, label: '本月' },
  { key: 'year' as Period, label: '本年' },
  { key: 'custom' as Period, label: '自定义' },
]
const fmtDate = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
const timeRange = computed(() => {
  const now = new Date()
  let start: Date
  let end = now
  let interval: string

  switch (selectedPeriod.value) {
    case 'realtime':
      start = new Date(now.getTime() - 60 * 60 * 1000)
      interval = '5m'; break
    case 'today':
      start = new Date(now.getFullYear(), now.getMonth(), now.getDate())
      interval = '1h'; break
    case 'yesterday':
      start = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1)
      end = new Date(now.getFullYear(), now.getMonth(), now.getDate())
      interval = '1h'; break
    case 'week':
      const dow = now.getDay() || 7
      start = new Date(now.getFullYear(), now.getMonth(), now.getDate() - dow + 1)
      start.setHours(0, 0, 0, 0)
      interval = '1d'; break
    case 'month':
      start = new Date(now.getFullYear(), now.getMonth(), 1)
      interval = '1d'; break
    case 'year':
      start = new Date(now.getFullYear(), 0, 1)
      interval = '1d'; break
    case 'custom':
      if (customStart.value && customEnd.value) {
        start = new Date(customStart.value)
        end = new Date(customEnd.value)
        end.setHours(23, 59, 59, 999)
        const rangeDays = Math.ceil((end.getTime() - start.getTime()) / 86400000)
        interval = rangeDays <= 1 ? '1h' : rangeDays <= 31 ? '1d' : '1w'
      } else {
        start = new Date(now.getFullYear(), now.getMonth(), now.getDate())
        interval = '1h'
      }
      break
    default:
      start = new Date(now.getFullYear(), now.getMonth(), now.getDate())
      interval = '1h'
  }
  return { start: start.toISOString(), end: end.toISOString(), days: Math.max(1, Math.ceil((end.getTime() - start.getTime()) / 86400000)), interval }
})
const periodLabels: Record<Period, string> = { realtime: '实时', today: '今日', yesterday: '昨日', week: '本周', month: '本月', year: '本年', custom: '自定义' }
const periodDesc = computed(() => {
  if (selectedPeriod.value === 'custom' && customStart.value && customEnd.value) {
    const fmt = (s: string) => s.slice(5)
    return fmt(customStart.value) + ' — ' + fmt(customEnd.value)
  }
  return periodLabels[selectedPeriod.value]
})
const timeRangeLabel = computed(() => {
  const fmt = (d: Date) => `${d.getMonth() + 1}/${d.getDate()}`
  const s = new Date(timeRange.value.start)
  const e = new Date(timeRange.value.end)
  if (selectedPeriod.value === 'realtime') return '实时 ' + s.getHours() + ':' + String(s.getMinutes()).padStart(2, '0')
  if (selectedPeriod.value === 'today') return '今天 ' + fmt(s)
  if (selectedPeriod.value === 'yesterday') return '昨天 ' + fmt(s)
  return fmt(s) + ' — ' + fmt(e)
})
const avatarColors = ['#ef4444', '#f59e0b', '#3b82f6', '#10b981', '#8b5cf6', '#ec4899', '#06b6d4', '#f97316']
const onlineUserAvatars = ref<any[]>([])
const onlineRoleDist = ref<Record<string, number>>({})
const onlineSessionCount = ref(0)
const roleLabelMap: Record<string, string> = { ADMIN: '管理员', MERCHANT: '商家', USER: '用户' }
function onAvatarError(e: Event) {
  const img = e.target as HTMLImageElement
  const fallback = document.createElement('span')
  fallback.className = 'hm-av-fallback'
  fallback.style.background = avatarColors[0]
  fallback.textContent = (img.title?.[0] || '?')
  fallback.title = img.title
  img.replaceWith(fallback)
}
function onCustomRangeChange() {
  if (customStart.value && customEnd.value) {
    refreshDashboardData()
  }
}
function onPeriodChange() {
  if (selectedPeriod.value === 'custom') {
    const now = new Date()
    if (!customStart.value) customStart.value = fmtDate(new Date(now.getFullYear(), now.getMonth(), 1))
    if (!customEnd.value) customEnd.value = fmtDate(now)
  }
  refreshDashboardData()
}

// ── snake_case → camelCase ──
function toCamel(o: any): any {
  if (o === null || o === undefined) return o
  if (Array.isArray(o)) return o.map(toCamel)
  if (typeof o === 'object') {
    const r: any = {}
    for (const [k, v] of Object.entries(o)) {
      const key = k.replace(/_([a-z])/g, (_, c) => c.toUpperCase())
      r[key] = toCamel(v)
    }
    return r
  }
  return o
}

const todayStr = new Date().toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
const summary = reactive<Record<string, any>>({
  onlineUsers: 0, totalUsers: 0, totalVideos: 0, totalOrders: 0,
  totalGoods: 0, activeLiveRooms: 0,
  todayNewVideos: 0, todayActiveUsers: 0, todayViews: 0, todayLikes: 0,
  todayCollects: 0, todayComments: 0, todaySearches: 0, todayOrders: 0,
  yesterdayViews: 0, yesterdayLikes: 0,
  reviewPipeline: {}, roleDistribution: {}, contentTypeBreakdown: {},
  userSegmentDistribution: {}, orderStatus: {}, todayRevenue: 0
})

// ── charts refs ──
const sparklineChart = ref<HTMLDivElement | null>(null)
const contentDonut = ref<HTMLDivElement | null>(null)
const engagementChart = ref<HTMLDivElement | null>(null)
let charts: any[] = []

const topContentList = ref<any[]>([])
const recentActivities = ref<any[]>([])
const searchKeywords = ref<any[]>([])
const userGrowthData = ref<any[]>([])
const hourlyActivity = ref<number[]>(new Array(24).fill(0))
const reviewTimeline = ref<any[]>([])
const liveRoomList = ref<any[]>([
  { id: 1, title: '深夜电台', hostName: '音乐人小林', coverUrl: '', viewerCount: 328, likeCount: 45, commentCount: 12, status: 'LIVE' },
  { id: 2, title: '音乐现场', hostName: 'DJ小王', coverUrl: '', viewerCount: 1200, likeCount: 230, commentCount: 56, status: 'LIVE' },
  { id: 3, title: '聊天陪伴', hostName: '温暖的小窝', coverUrl: '', viewerCount: 892, likeCount: 128, commentCount: 34, status: 'LIVE' },
  { id: 4, title: '游戏竞技', hostName: '电竞达人', coverUrl: '', viewerCount: 567, likeCount: 89, commentCount: 21, status: 'LIVE' },
  { id: 5, title: '户外探险', hostName: '行者无疆', coverUrl: '', viewerCount: 256, likeCount: 32, commentCount: 8, status: 'LIVE' },
])
const liveTotalViewers = computed(() => liveRoomList.value.reduce((s, lr) => s + lr.viewerCount, 0))
const liveHotCount = computed(() => liveRoomList.value.filter(lr => lr.viewerCount >= 1000).length)
const peakOnline = computed(() => Math.round(summary.onlineUsers * 1.12))
const peakOnlineTime = computed(() => {
  const now = new Date()
  return (now.getHours() - 1) + ':' + String(now.getMinutes()).padStart(2, '0')
})
const onlineRoleSummary = computed(() => {
  const parts: string[] = []
  for (const role of ['USER', 'MERCHANT', 'ADMIN']) {
    const count = onlineRoleDist.value[role]
    if (count) parts.push(roleLabelMap[role] + ' ' + count)
  }
  return parts.length ? parts.join(' · ') : ''
})
const roleMetricConfig: Record<string, { label: string; color: string; order: number }> = {
  ADMIN:    { label: '在线管理员', color: '#ef4444', order: 1 },
  MERCHANT: { label: '在线商家',   color: '#f59e0b', order: 2 },
  USER:     { label: '在线用户',   color: '#3b82f6', order: 3 },
}
const onlineRoleMetrics = computed(() => {
  return ['ADMIN', 'MERCHANT', 'USER']
    .map(role => ({
      role,
      count: onlineRoleDist.value[role] || 0,
      label: roleMetricConfig[role]?.label || role,
      color: roleMetricConfig[role]?.color || '#94a3b8',
      order: roleMetricConfig[role]?.order || 99,
    }))
    .sort((a, b) => a.order - b.order)
})

// Gantt time range: last 8 hours
const ganttNow = computed(() => Date.now())
const ganttRangeStart = computed(() => ganttNow.value - 8 * 3600 * 1000)
const ganttRangeMs = computed(() => 8 * 3600 * 1000)

const ganttTicks = computed(() => {
  const ticks = []
  const start = ganttRangeStart.value
  const ms = ganttRangeMs.value
  for (let h = 0; h <= 8; h++) {
    const t = start + h * 3600 * 1000
    ticks.push({
      pos: (h / 8) * 100,
      label: new Date(t).getHours() + ':00',
    })
  }
  return ticks
})

const ganttNowPos = computed(() => {
  return ((ganttNow.value - ganttRangeStart.value) / ganttRangeMs.value) * 100
})

const formatGanttTime = (ts: number) => {
  const d = new Date(ts)
  return d.getHours().toString().padStart(2, '0') + ':' + d.getMinutes().toString().padStart(2, '0')
}

const ganttItems = computed(() => {
  const now = ganttNow.value
  const rangeStart = ganttRangeStart.value
  const rangeMs = ganttRangeMs.value
  const statusLabels: Record<string, string> = { PENDING: '排队中', APPROVED: '已通过', REJECTED: '已驳回' }
  return reviewTimeline.value.map((item: any) => {
    const createTime = new Date(item.createTime || item.create_time).getTime()
    const reviewTimeRaw = item.reviewTime || item.review_time
    const endTime = item.status === 'PENDING' ? now : (reviewTimeRaw ? new Date(reviewTimeRaw).getTime() : now)
    return {
      ...item,
      _hover: false,
      desc: item.desc || '',
      barLeft: Math.max(0, (createTime - rangeStart) / rangeMs * 100),
      barWidth: Math.max(2, (endTime - createTime) / rangeMs * 100),
      statusLabel: statusLabels[item.status] || item.status,
      waitText: item.status === 'PENDING'
        ? (item.pendingHours >= 1 ? Math.round(item.pendingHours) + 'h' : Math.round((item.pendingHours || 0) * 60) + 'm')
        : (item.reviewDuration ? item.reviewDuration + 'min' : ''),
      reviewer: item.reviewedBy || item.reviewed_by || (item.status === 'PENDING' ? '排队中' : '—'),
      createTimeLabel: formatGanttTime(createTime),
      endTimeLabel: item.status !== 'PENDING' && reviewTimeRaw ? formatGanttTime(new Date(reviewTimeRaw).getTime()) : null,
    }
  })
})

// Activity type → icon mapping
const activityIcons: Record<string, any> = {
  register: UserPlus, upload: Upload, order: ShoppingCart,
  comment: MessageCircle, live: Radio
}
function timeAgo(t: string): string {
  const diff = Date.now() - new Date(t).getTime()
  const mins = Math.floor(diff / 60000)
  if (mins < 1) return '刚刚'
  if (mins < 60) return mins + '分钟前'
  const hours = Math.floor(mins / 60)
  if (hours < 24) return hours + '小时前'
  return Math.floor(hours / 24) + '天前'
}

const deviceDistribution = ref<any[]>([])
const deviceTotal = computed(() => summary.todayActiveUsers || 0)

const popularTags = computed(() => {
  if (searchKeywords.value.length === 0) return []
  const total = searchKeywords.value.reduce((s: number, k: any) => s + (k.count || 0), 0) || 1
  const max = Math.max(...searchKeywords.value.map((t: any) => t.count || 0))
  return searchKeywords.value.slice(0, 10).map((t: any) => ({
    name: (t.keyword || t.name || '').startsWith('#') ? t.keyword : '#' + (t.keyword || t.name),
    count: t.count || 0,
    scale: (t.count || 0) / max
  }))
})

const maxHourly = computed(() => Math.max(...hourlyActivity.value, 1))

const orderStatusColors: Record<string, string> = {
  'PENDING': '#f59e0b', 'PAID': '#3b82f6', 'SHIPPED': '#8b5cf6',
  'RECEIVED': '#10b981', 'CANCELLED': '#ef4444'
}
const orderStatusLabels: Record<string, string> = {
  'PENDING': '待付款', 'PAID': '已付款', 'SHIPPED': '已发货',
  'RECEIVED': '已签收', 'CANCELLED': '已取消'
}
const orderStatusItems = computed(() => {
  const status = summary.orderStatus || {}
  return Object.entries(orderStatusColors).map(([key, color]) => ({
    label: orderStatusLabels[key] || key,
    value: status[key] || 0,
    color
  }))
})

const funnelColors = ['#3b82f6', '#8b5cf6', '#f59e0b', '#10b981']
const funnelData = computed(() => {
  const views    = summary.todayViews || 65000
  const likes    = summary.todayLikes || 12000
  const comments = summary.todayComments || 2100
  const shares   = Math.round(comments * 0.35)
  const steps = [
    { label: '浏览', value: views },
    { label: '点赞', value: likes },
    { label: '评论', value: comments },
    { label: '分享', value: shares },
  ]
  const max = Math.max(...steps.map(s => s.value), 1)
  return steps.map(s => ({ ...s, pct: Math.round(s.value / max * 100) }))
})

const roleIcons: Record<string, any> = { ADMIN: Shield, USER: User, MERCHANT: ShoppingBag }
const roleLabels: Record<string, string> = { ADMIN: '管理员', USER: '用户', MERCHANT: '商家' }
const sourceLabels: Record<string, string> = {
  'HOME_RECOMMEND': '首页推荐', 'SEARCH': '搜索', 'USER_PROFILE': '个人主页',
  'FOLLOWING': '关注页', 'HASHTAG': '话题', 'EXTERNAL': '外部来源', 'SHARE': '分享'
}

function fmtNum(n: any): string {
  if (!n) return '0'
  if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return String(n)
}
function rp(k: string) { return summary.reviewPipeline?.[k] || 0 }
function calcCmp(today: number, yesterday: number) {
  if (!yesterday || !today) return null
  const pct = Math.round((today - yesterday) / yesterday * 100)
  return { up: pct >= 0, text: (pct >= 0 ? '↑ ' : '↓ ') + Math.abs(pct) + '%' }
}

// ── computed ──
const heroStatsRest = computed(() => [
  {
    icon: User, label: '总计用户', value: fmtNum(summary.totalUsers),
    color: '#3b82f6',
    detail: periodDesc.value + ' +' + fmtNum(summary.todayActiveUsers || 0) + ' 活跃',
    extras: ['活跃率 ' + ((summary.todayActiveUsers || 0) / (summary.totalUsers || 1) * 100).toFixed(1) + '%']
  },
  {
    icon: Video, label: '内容作品', value: fmtNum(summary.totalVideos),
    color: '#8b5cf6',
    detail: '周期 +' + fmtNum(summary.todayNewVideos || 0) + ' · 短视频60%',
    extras: ['高质量 35%', '平均分 0.72']
  },
  {
    icon: ShoppingCart, label: periodDesc.value + '交易额',
    value: '¥' + ((summary.todayRevenue || 0) / 100).toFixed(1) + 'w',
    color: '#f59e0b',
    detail: fmtNum(summary.totalOrders) + ' 总订单 · ' + periodDesc.value + ' ' + fmtNum(summary.todayOrders || 0) + ' · 履约94%'
  }
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

const trafficSources = ref<any[]>([])
const topSources = computed(() => {
  const total = trafficSources.value.reduce((s: number, i: any) => s + i.value, 0) || 1
  return trafficSources.value.slice(0, 5).map((s: any) => ({
    name: sourceLabels[s.name] || s.name,
    pct: Math.round(s.value / total * 100)
  }))
})

const segmentItems = computed(() => {
  const seg = summary.userSegmentDistribution || {}
  const labels: Record<string, { name: string; color: string }> = {
    'heavy': { name: '重度用户', color: '#ef4444' },
    'medium': { name: '中度用户', color: '#f59e0b' },
    'light': { name: '轻度用户', color: '#3b82f6' },
    'new_user': { name: '新注册', color: '#10b981' },
    'unknown': { name: '未知', color: '#9ca3af' },
  }
  const max = Math.max(...Object.values(seg).map(Number), 1)
  return Object.entries(seg).map(([k, v]) => {
    const info = labels[k] || { name: k, color: '#9ca3af' }
    const size = 52 + (Number(v) / max) * 36
    return { ...info, value: fmtNum(v), size }
  })
})

// ── ECharts ──
function initSparkline() {
  if (!sparklineChart.value) return
  const c = echarts.init(sparklineChart.value)
  c.setOption({
    grid: { left: 0, right: 0, top: 8, bottom: 0 },
    xAxis: { show: false, data: [] },
    yAxis: { show: false, min: 0 },
    tooltip: { trigger: 'axis', backgroundColor: '#fff', padding: 10, borderRadius: 8, textStyle: { color: '#333' }, extraCssText: 'box-shadow: 0 4px 12px rgba(0,0,0,0.08); border: none;' },
    series: [{
      name: '互动量', type: 'line', data: [], smooth: 0.4, symbol: 'none',
      lineStyle: { color: '#818cf8', width: 2.5 },
      areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(129,140,248,0.25)' }, { offset: 1, color: 'rgba(129,140,248,0)' }]) }
    }]
  })
  charts.push(c)
  return c
}

function initEngagementChart() {
  if (!engagementChart.value) return
  const c = echarts.init(engagementChart.value)
  c.setOption({
    grid: { left: 0, right: 0, top: 12, bottom: 0 },
    xAxis: { show: false, data: [] },
    yAxis: { show: false },
    tooltip: { trigger: 'axis', backgroundColor: '#fff', padding: 10, borderRadius: 8, extraCssText: 'box-shadow: 0 4px 12px rgba(0,0,0,0.08); border: none;' },
    series: [
      {
        name: '点赞', type: 'line', data: [], smooth: 0.4, symbol: 'none',
        lineStyle: { color: '#f43f5e', width: 2.5 },
        areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(244,63,94,0.12)' }, { offset: 1, color: 'rgba(244,63,94,0)' }]) }
      },
      {
        name: '收藏', type: 'line', data: [], smooth: 0.4, symbol: 'none',
        lineStyle: { color: '#3b82f6', width: 2.5 },
        areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(59,130,246,0.12)' }, { offset: 1, color: 'rgba(59,130,246,0)' }]) }
      }
    ]
  })
  charts.push(c)
  return c
}

function initContentDonut() {
  if (!contentDonut.value) return
  const bd = summary.contentTypeBreakdown || {}
  const labels: Record<string, string> = { 'recommend-video': '短视频', 'image': '图文', 'text': '纯文本', 'long-video': '长视频' }
  const data = Object.entries(bd).map(([k, v]) => ({ name: labels[k] || k, value: v }))
  const c = echarts.init(contentDonut.value)
  c.setOption({
    tooltip: { trigger: 'item', backgroundColor: '#fff', padding: 12, borderRadius: 8, textStyle: { color: '#333' }, extraCssText: 'box-shadow: 0 4px 12px rgba(0,0,0,0.08);' },
    color: ['#3b82f6', '#8b5cf6', '#f43f5e', '#10b981', '#f59e0b'],
    series: [{
      type: 'pie', radius: ['55%', '82%'], center: ['50%', '50%'], data,
      label: { show: false },
      emphasis: { label: { show: false } },
      itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 3 }
    }]
  })
  charts.push(c)
}

// ── WebSocket real-time stream (design.md §3) ──
let ws: WebSocket | null = null
function connectDashboardWS() {
  const token = localStorage.getItem('token')
  if (!token) return
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  ws = new WebSocket(`${protocol}//${location.host}/ws/dashboard/stream?token=${token}`)
  ws.onmessage = (e) => {
    try {
      const data = JSON.parse(e.data)
      if (data.onlineUsers !== undefined) summary.onlineUsers = data.onlineUsers
      if (data.activeLiveRooms !== undefined) summary.activeLiveRooms = data.activeLiveRooms
    } catch (_) {}
  }
  ws.onclose = () => { setTimeout(connectDashboardWS, 10000) }
}

// ── data refresh (called on mount AND on period change) ──
async function refreshDashboardData() {
  const tr = timeRange.value
  const periodParam = tr.interval === '1h' ? 'hourly' : 'daily'
  const hoursForFlow = selectedPeriod.value === 'today' || selectedPeriod.value === 'yesterday' ? 24 : tr.days * 24

  // Fetch all data in parallel
  const timeRangeParams = { start: tr.start, end: tr.end }
  const [
    summaryRes, trendRes, trafficRes, topVideosRes,
    deviceRes, searchRes, activityRes, ecomRes, segmentsRes,
    onlineRes
  ] = await Promise.allSettled([
    getDashboardSummary(timeRangeParams),
    getEngagementTrend({ ...timeRangeParams, period: periodParam, days: tr.days }),
    getTrafficSources(timeRangeParams),
    getTopVideos({ metric: 'views', limit: 5 }),
    getDeviceStats(),
    getSearchStats({ days: tr.days }),
    getRecentActivity({ limit: 10 }),
    getEcommerceOverview(timeRangeParams),
    getUserSegments(),
    getOnlineUsers(),
  ])

  // Populate summary
  if (summaryRes.status === 'fulfilled' && summaryRes.value.success) {
    Object.assign(summary, toCamel(summaryRes.value.data))
  }

  // Online user avatars
  if (onlineRes.status === 'fulfilled' && onlineRes.value.success) {
    const od = onlineRes.value.data || {}
    onlineUserAvatars.value = od.recentUsers || od.recent_users || []
    onlineRoleDist.value = od.roleDistribution || od.role_distribution || {}
    onlineSessionCount.value = od.sessionCount || 0
    if (od.count !== undefined) summary.onlineUsers = od.count
  }

  // Traffic sources
  if (trafficRes.status === 'fulfilled' && trafficRes.value.success) {
    trafficSources.value = trafficRes.value.data?.sources || []
  }

  // Top videos — map from API format
  if (topVideosRes.status === 'fulfilled' && topVideosRes.value.success) {
    const videos = topVideosRes.value.data || []
    topContentList.value = videos.map((v: any) => ({
      title: v.desc || v.title || '',
      author: v.author || ('用户' + (v.authorUserId || v.author_user_id || '')),
      views: v.playCount || v.play_count || v.views || 0,
      likes: v.likeCount || v.like_count || v.likes || 0,
    }))
  }

  // Device distribution
  if (deviceRes.status === 'fulfilled' && deviceRes.value.success) {
    const d = deviceRes.value.data || {}
    const osDist = d.osDistribution || d.os_distribution || {}
    const total = d.totalSessions || d.total_sessions || Object.values(osDist).reduce((s: number, v: any) => s + (Number(v) || 0), 0) || 1
    const colors: Record<string, string> = { 'Windows': '#3b82f6', 'iOS': '#10b981', 'Android': '#f59e0b', 'Mac': '#8b5cf6', 'Linux': '#ef4444' }
    deviceDistribution.value = Object.entries(osDist).map(([name, count]: [string, any]) => ({
      name,
      pct: Math.round((Number(count) || 0) / total * 100),
      color: colors[name] || '#94a3b8'
    }))
  }

  // Search keywords
  if (searchRes.status === 'fulfilled' && searchRes.value.success) {
    searchKeywords.value = searchRes.value.data?.topKeywords || searchRes.value.data?.top_keywords || []
  }

  // Audit drill-down for review pipeline Gantt
  try {
    const drillRes: any = await getAuditDrilldown({ status: 'PENDING', pageSize: 5 })
    if (drillRes.success) {
      const pendingItems = (drillRes.data?.items || []).slice(0, 5)
      // Also fetch a few recently completed items for the timeline
      const approvedRes: any = await getAuditDrilldown({ status: 'APPROVED', pageSize: 2 })
      const rejectedRes: any = await getAuditDrilldown({ status: 'REJECTED', pageSize: 2 })
      const allItems = [
        ...(approvedRes.success ? (approvedRes.data?.items || []).slice(0, 2) : []),
        ...(rejectedRes.success ? (rejectedRes.data?.items || []).slice(0, 2) : []),
        ...pendingItems,
      ].sort((a: any, b: any) => {
        const ta = new Date(a.createTime || a.create_time).getTime()
        const tb = new Date(b.createTime || b.create_time).getTime()
        return tb - ta
      }).slice(0, 8)
      reviewTimeline.value = allItems
    }
  } catch (e) { console.error(e) }

  // Recent activity
  if (activityRes.status === 'fulfilled' && activityRes.value.success) {
    recentActivities.value = (activityRes.value.data || []).map((a: any) => ({
      type: a.type,
      desc: a.desc,
      user: a.user,
      createTime: a.createTime || a.create_time,
    }))
  }

  // Ecommerce enrich
  if (ecomRes.status === 'fulfilled' && ecomRes.value.success) {
    const ed = toCamel(ecomRes.value.data)
    if (ed.todayRevenue !== undefined) summary.todayRevenue = ed.todayRevenue
    if (ed.orderStatus) summary.orderStatus = ed.orderStatus
  }

  // User segments enrich
  if (segmentsRes.status === 'fulfilled' && segmentsRes.value.success) {
    const sd = toCamel(segmentsRes.value.data)
    if (sd.avgBounceBySegment) summary.avgBounceBySegment = sd.avgBounceBySegment
    if (sd.avgWatchBySegment) summary.avgWatchBySegment = sd.avgWatchBySegment
  }

  // User growth trend
  const [growthRes, activityFlowRes] = await Promise.allSettled([
    getUserGrowth({ days: Math.max(tr.days, 7) }),
    getUserActivityFlow({ hours: Math.min(hoursForFlow, 720) }),
  ])
  if (growthRes.status === 'fulfilled' && growthRes.value.success) {
    const gd = growthRes.value.data || {}
    const registrations = gd.dailyRegistrations || gd.daily_registrations || {}
    const cumulative = gd.cumulativeGrowth || gd.cumulative_growth || []
    const maxCount = Math.max(...Object.values(registrations).map(Number), 1)
    // Build bar chart data from last 14 days
    const entries = Object.entries(registrations).slice(-14) as [string, any][]
    userGrowthData.value = entries.map(([time, count]) => ({
      time,
      count: Number(count) || 0,
      scale: (Number(count) || 0) / maxCount,
      cumulative: cumulative.length > 0 ? cumulative.find((c: any) => c.time === time)?.count : undefined,
    }))
  }

  // Hourly activity
  if (activityFlowRes.status === 'fulfilled' && activityFlowRes.value.success) {
    const flowData = activityFlowRes.value.data || []
    const hours = new Array(24).fill(0)
    flowData.forEach((d: any) => {
      const h = parseInt((d.time || '').split(' ')[1]?.split(':')[0])
      if (!isNaN(h) && h >= 0 && h < 24) hours[h] += (Number(d.count) || 0)
    })
    hourlyActivity.value = hours
  }

  // Init charts (reset array so re-init on period change doesn't accumulate stale refs)
  charts = []
  await nextTick()
  initSparkline()
  initContentDonut()
  initEngagementChart()

  // Populate chart data
  await nextTick()
  if (trendRes.status === 'fulfilled' && trendRes.value.success) {
    const trendData = trendRes.value.data || []
    const spark = charts[0]
    const eng = charts[2]
    if (spark) {
      spark.setOption({
        xAxis: { data: trendData.map((d: any) => d.time) },
        series: [{ data: trendData.map((d: any) => (d.likes || 0) + (d.collects || 0)) }]
      })
    }
    if (eng) {
      eng.setOption({
        xAxis: { data: trendData.map((d: any) => d.time) },
        series: [
          { data: trendData.map((d: any) => d.likes || 0) },
          { data: trendData.map((d: any) => d.collects || 0) }
        ]
      })
    }
  }

}

// ── lifecycle ──
const onResize = () => charts.forEach(c => c.resize())
onMounted(async () => {
  await refreshDashboardData()
  connectDashboardWS()
  window.addEventListener('resize', onResize)
})
onUnmounted(() => {
  charts.forEach(c => c.dispose())
  window.removeEventListener('resize', onResize)
  if (ws) { ws.close(); ws = null }
})
</script>

<style scoped>
/* ── 全局基调：去卡片、无阴影、无圆角容器 ── */
.dashboard {
  max-width: 1400px;
  margin: 0 auto;
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
  color: #1e293b;
  padding-bottom: 24px;
}

/* ── 页面头部 ── */
.page-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 18px;
}
.page-title {
  font-size: 22px;
  font-weight: 700;
  color: #0f172a;
  letter-spacing: -0.5px;
  margin: 0;
}
.page-meta {
  font-size: 13px;
  color: #94a3b8;
  white-space: nowrap;
}

/* ── 头部右侧：下拉 + 自定义日期 ── */
.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* ── 周期下拉框 ── */
.period-select {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 6px 28px 6px 10px;
  font-size: 13px;
  font-weight: 600;
  font-family: inherit;
  color: #334155;
  background: #fff url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='10' height='6'%3E%3Cpath d='M0 0l5 6 5-6z' fill='%2394a3b8'/%3E%3C/svg%3E") no-repeat right 8px center;
  appearance: none;
  cursor: pointer;
  outline: none;
  transition: border-color 0.15s;
}
.period-select:hover { border-color: #cbd5e1; }
.period-select:focus { border-color: #3b82f6; }

/* ── 自定义日期范围 ── */
.cr-input {
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 4px 8px;
  font-size: 12px;
  font-family: inherit;
  color: #334155;
  background: #fff;
  outline: none;
  transition: border-color 0.15s;
}
.cr-input:focus { border-color: #3b82f6; }
.cr-sep {
  font-size: 12px;
  color: #94a3b8;
}

/* ── 区块基类：无背景、无边框、无阴影 ── */
.block {
  /* intentionally empty — no card styling */
}
.block-heading {
  font-size: 11px;
  font-weight: 600;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.6px;
  margin: 0 0 10px 0;
}
.block-heading-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.block-heading-row .block-heading { margin-bottom: 0; }

/* ── 软分割线 ── */
.soft-rule {
  border: none;
  height: 1px;
  background: #f1f5f9;
  margin: 18px 0;
}

/* ── 双列 / 三列网格 ── */
.grid-2 {
  display: grid;
  grid-template-columns: 1fr 2fr;
  gap: 24px;
}
.grid-2 > .block:first-child {
  padding-right: 20px;
  border-right: 1px solid #e2e8f0;
}
.grid-3 {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}
.grid-3 > .block:not(:last-child) {
  padding-right: 16px;
  border-right: 1px solid #e2e8f0;
}

/* ── Hero 区域 ── */
.hero-strip {
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  margin: 0 -24px;
  padding: 18px 24px 12px;
}
.hero-metrics {
  display: flex;
  gap: 32px;
  flex-wrap: wrap;
}
.hero-metric {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  min-width: 0;
}
.hm-icon { color: #64748b; flex-shrink: 0; margin-top: 3px; }
.hm-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.hm-main {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.hm-value {
  font-size: 24px;
  font-weight: 800;
  color: #0f172a;
  letter-spacing: -0.5px;
  line-height: 1;
}
.hm-value.online { color: #059669; }
.hm-avatar-row {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 2px;
}
.hero-avatar-strip {
  margin-top: 4px;
  padding-left: 2px;
}
.hm-av-wrap {
  position: relative;
  display: inline-flex;
  flex-shrink: 0;
}
.hm-av-dot {
  position: absolute;
  bottom: -1px;
  right: -1px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #22c55e;
  border: 2px solid #fff;
  box-shadow: 0 0 0 1px rgba(34,197,94,.3);
}
.hm-av {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  object-fit: cover;
  border: 2px solid #fff;
  box-shadow: 0 0 0 1px #e2e8f0;
}
.hm-av-fallback {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #e2e8f0;
  color: #94a3b8;
  border: 2px solid #fff;
  box-shadow: 0 0 0 1px #e2e8f0;
  overflow: hidden;
}
.hm-av-fallback svg {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
}
.hm-av-more {
  font-size: 10px;
  color: #94a3b8;
  font-weight: 600;
  margin-left: 2px;
}
.hm-role-badges {
  display: flex;
  gap: 6px;
  margin-top: 2px;
}
.hm-role-badge {
  font-size: 10px;
  font-weight: 600;
  padding: 1px 7px;
  border-radius: 4px;
  white-space: nowrap;
}
.hm-role-badge.user    { background: #eff6ff; color: #3b82f6; }
.hm-role-badge.merchant { background: #fef3c7; color: #d97706; }
.hm-role-badge.admin   { background: #fef2f2; color: #ef4444; }
.hm-label {
  font-size: 12px;
  color: #64748b;
  font-weight: 500;
}
.hm-detail {
  font-size: 11px;
  color: #94a3b8;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.hm-extras {
  display: flex;
  gap: 6px;
  margin-top: 2px;
}
.hm-extra-tag {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 3px;
  background: #f1f5f9;
  color: #64748b;
  font-weight: 500;
}
.sparkline-area {
  width: 100%;
  height: 48px;
  margin-top: 10px;
}

/* ── 流式统计 (今日数据) ── */
.flow-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 2px 24px;
}
.flow-stat {
  display: flex;
  align-items: baseline;
  gap: 5px;
  padding: 5px 0;
}
.fs-value {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}
.fs-label {
  font-size: 12px;
  color: #64748b;
}
.fs-cmp {
  font-size: 11px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 4px;
}
.fs-cmp.up { background: #ecfdf5; color: #059669; }
.fs-cmp.down { background: #fef2f2; color: #ef4444; }

/* ── 审核流水线 Gantt ── */
.pipeline-summary-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.ps-badge {
  font-size: 12px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 4px;
  white-space: nowrap;
}
.ps-badge.pending { background: #fef3c7; color: #d97706; }
.ps-badge.approved { background: #ecfdf5; color: #059669; }
.ps-badge.rejected { background: #fef2f2; color: #ef4444; }
.ps-pass-rate {
  font-size: 11px;
  color: #94a3b8;
  font-weight: 600;
}
.gantt-container {
  margin-top: 12px;
  overflow-x: auto;
  overflow-y: visible;
  padding-bottom: 4px;
}
.gantt-header {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding-bottom: 4px;
  min-width: 460px;
}
.gh-label { width: 80px; flex-shrink: 0; font-size: 10px; color: #94a3b8; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px; }
.gh-timeline {
  flex: 1;
  position: relative;
  padding-bottom: 4px;
}
.gh-title {
  font-size: 10px;
  color: #94a3b8;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  display: block;
  text-align: center;
  margin-bottom: 6px;
}
.gh-ticks {
  position: relative;
  height: 16px;
}
.gh-tick {
  position: absolute;
  transform: translateX(-50%);
  font-size: 9px;
  color: #94a3b8;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}
.gh-detail { width: 110px; flex-shrink: 0; text-align: right; font-size: 10px; color: #94a3b8; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px; }
.gantt-empty {
  padding: 20px 0;
  text-align: center;
  color: #cbd5e1;
  font-size: 12px;
}
.gantt-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 0;
  min-width: 460px;
  position: relative;
}
.gantt-row + .gantt-row { border-top: 1px solid #f8fafc; }
.gr-desc {
  width: 80px;
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 600;
  color: #334155;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.gr-track {
  flex: 1;
  height: 24px;
  background: #f8fafc;
  border-radius: 5px;
  position: relative;
  overflow: visible;
}
.gr-grid {
  position: absolute;
  inset: 0;
  pointer-events: none;
}
.gr-grid-line {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 1px;
  background: #e2e8f0;
}
.gr-now {
  position: absolute;
  top: -3px;
  bottom: -3px;
  width: 2px;
  background: #ef4444;
  border-radius: 1px;
  z-index: 3;
  pointer-events: none;
}
.gr-now::after {
  content: '';
  position: absolute;
  top: -2px;
  left: -3px;
  width: 8px;
  height: 8px;
  background: #ef4444;
  border-radius: 50%;
}
.gr-bar {
  position: absolute;
  top: 3px;
  height: calc(100% - 6px);
  border-radius: 4px;
  min-width: 4px;
  z-index: 2;
  cursor: pointer;
  transition: filter 0.15s;
}
.gr-bar:hover { filter: brightness(0.9); }
.gr-bar-approved { background: linear-gradient(135deg, #10b981, #34d399); box-shadow: 0 1px 3px rgba(16,185,129,.25); }
.gr-bar-rejected { background: linear-gradient(135deg, #ef4444, #f87171); box-shadow: 0 1px 3px rgba(239,68,68,.25); }
.gr-bar-pending {
  background: linear-gradient(90deg, #f59e0b 25%, #fbbf24 50%, #f59e0b 75%);
  background-size: 200% 100%;
  animation: gantt-stripe 2s linear infinite;
  box-shadow: 0 1px 3px rgba(245,158,11,.2);
}
@keyframes gantt-stripe { to { background-position: -200% 0; } }
.gr-bar-label {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: 600;
  color: #fff;
  text-shadow: 0 1px 2px rgba(0,0,0,.2);
  white-space: nowrap;
}
.gr-marker {
  position: absolute;
  top: 50%;
  transform: translate(-50%, -50%);
  z-index: 4;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid #fff;
  box-shadow: 0 1px 3px rgba(0,0,0,.15);
}
.gr-marker-approved { background: #10b981; }
.gr-marker-rejected { background: #ef4444; }
.gr-marker-dot { display: none; }
.gr-marker-approved::after { content: '✓'; color: #fff; font-size: 10px; font-weight: 700; line-height: 1; }
.gr-marker-rejected::after { content: '✗'; color: #fff; font-size: 10px; font-weight: 700; line-height: 1; }
.gr-tooltip {
  position: absolute;
  top: 100%;
  left: 50%;
  transform: translateX(-50%);
  margin-top: 8px;
  background: #1e293b;
  color: #f1f5f9;
  border-radius: 6px;
  padding: 8px 12px;
  font-size: 11px;
  white-space: nowrap;
  z-index: 10;
  box-shadow: 0 4px 12px rgba(0,0,0,.15);
  pointer-events: none;
}
.gr-tooltip::before {
  content: '';
  position: absolute;
  top: -5px;
  left: 50%;
  transform: translateX(-50%);
  border: 5px solid transparent;
  border-bottom-color: #1e293b;
  border-top: none;
}
.gr-tt-title { font-weight: 700; margin-bottom: 3px; }
.gr-tt-row { color: #94a3b8; font-size: 10px; line-height: 1.5; }
.gr-meta {
  width: 110px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 1px;
  font-size: 10px;
}
.gr-status { font-weight: 700; font-size: 11px; }
.gr-st-approved { color: #10b981; }
.gr-st-rejected { color: #ef4444; }
.gr-st-pending { color: #f59e0b; }
.gr-wait { color: #94a3b8; }
.gr-reviewer { color: #cbd5e1; font-size: 9px; }

/* ── 图表通用 ── */
.chart-area {
  width: 100%;
  min-height: 110px;
  flex: 1;
}
.chart-donut {
  min-height: 140px;
}

/* ── 图例行内 ── */
.legend-inline {
  font-size: 12px;
  color: #94a3b8;
  display: flex;
  align-items: center;
  gap: 8px;
}
.legend-inline .dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  display: inline-block;
}

/* ── 流量来源 ── */
.traffic-flow {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.traf-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.traf-name {
  font-size: 13px;
  color: #475569;
  width: 68px;
  flex-shrink: 0;
  font-weight: 500;
  text-align: right;
}
.traf-track {
  flex: 1;
  height: 6px;
  background: #f1f5f9;
  border-radius: 3px;
  overflow: hidden;
}
.traf-fill {
  height: 100%;
  border-radius: 3px;
  background: linear-gradient(90deg, #60a5fa, #3b82f6);
  transition: width 1s cubic-bezier(0.4, 0, 0.2, 1);
}
.traf-pct {
  font-size: 13px;
  color: #64748b;
  font-weight: 600;
  width: 36px;
  text-align: right;
}

/* ── 用户分层气泡 ── */
.bubble-flow {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}
.seg-bubble {
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #fff;
  transition: transform 0.25s;
}
.seg-bubble:hover { transform: scale(1.06); }
.seg-val { font-size: 14px; font-weight: 700; line-height: 1; }
.seg-name { font-size: 10px; opacity: 0.9; margin-top: 2px; }

/* ── 用户角色 ── */
.role-flow {
  display: flex;
  gap: 14px;
}
.role-item {
  display: flex;
  align-items: center;
  gap: 8px;
}
.ri-badge {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}
.role-admin .ri-badge { background: linear-gradient(135deg, #f43f5e, #fb7185); }
.role-user .ri-badge { background: linear-gradient(135deg, #3b82f6, #60a5fa); }
.role-merchant .ri-badge { background: linear-gradient(135deg, #f59e0b, #fbbf24); }
.ri-info { display: flex; flex-direction: column; }
.ri-num { font-size: 15px; font-weight: 700; color: #0f172a; line-height: 1.2; }
.ri-label { font-size: 11px; color: #64748b; }
.ri-ratio {
  font-size: 10px;
  color: #94a3b8;
  font-weight: 500;
  margin-top: 1px;
}
.role-bar {
  margin-top: 10px;
  display: flex;
  height: 4px;
  border-radius: 2px;
  overflow: hidden;
  background: #f1f5f9;
}
.role-bar-fill {
  height: 100%;
  transition: width 0.6s ease;
}
.role-bar-fill.admin { background: #f43f5e; }
.role-bar-fill.merchant { background: #f59e0b; }
.role-bar-fill.user { background: #3b82f6; }

/* ── 电商概况 ── */
.shop-flow {
  display: flex;
  gap: 16px;
}
.shop-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.si-num {
  font-size: 20px;
  font-weight: 800;
  color: #334155;
  line-height: 1;
}
.si-num.highlight { color: #059669; }
.si-num.revenue { color: #f59e0b; }
.si-lbl { font-size: 11px; color: #64748b; }
.si-lbl.highlight { color: #10b981; }
.shop-detail {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px solid #f1f5f9;
  display: flex;
  gap: 16px;
}
.sd-row {
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.sd-row span { font-size: 10px; color: #94a3b8; }
.sd-row b { font-size: 13px; color: #334155; }
.sd-row b.good { color: #059669; }

/* ── 直播大厅 ── */
.live-summary {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  font-size: 12px;
  font-weight: 600;
  color: #475569;
}
.ls-pulse {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ef4444;
  flex-shrink: 0;
  animation: ring-pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
}
.ls-count { color: #ef4444; }
.ls-hot { color: #f59e0b; }
.ls-viewers { color: #94a3b8; font-weight: 500; }
.live-cards {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.live-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 0;
  border-bottom: 1px solid #f8fafc;
  cursor: pointer;
  transition: background 0.15s;
  border-radius: 6px;
  padding: 4px 6px;
  margin: 0 -6px;
}
.live-card:last-child { border-bottom: none; }
.live-card:hover { background: #fafbfd; }
.lc-cover {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  overflow: hidden;
  position: relative;
  flex-shrink: 0;
}
.lc-cover-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 14px;
  font-weight: 700;
}
.lc-dot {
  position: absolute;
  bottom: 0;
  right: 0;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #ef4444;
  border: 2px solid #fff;
  animation: ring-pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
}
.lc-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.lc-title {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.lc-badge-hot { font-size: 10px; }
.lc-host {
  font-size: 11px;
  color: #94a3b8;
}
.lc-stats {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  flex-shrink: 0;
}
.lc-viewers {
  font-size: 15px;
  font-weight: 800;
  color: #1e293b;
  line-height: 1;
}
.lc-likes {
  font-size: 10px;
  color: #94a3b8;
}
.live-more {
  margin-top: 8px;
  text-align: center;
  font-size: 11px;
  color: #cbd5e1;
}

@keyframes ring-pulse {
  0% { box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.3); }
  70% { box-shadow: 0 0 0 14px rgba(239, 68, 68, 0); }
  100% { box-shadow: 0 0 0 0 rgba(239, 68, 68, 0); }
}

/* ── 实时动态 ── */
.activity-feed {
  display: flex;
  flex-direction: column;
  gap: 0;
}
.af-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  border-bottom: 1px solid #f8fafc;
}
.af-row:last-child { border-bottom: none; }
.af-icon-wrap {
  width: 26px;
  height: 26px;
  border-radius: 7px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.af-register  { background: #eff6ff; color: #3b82f6; }
.af-upload    { background: #fef3c7; color: #d97706; }
.af-order     { background: #ecfdf5; color: #059669; }
.af-comment   { background: #f3e8ff; color: #8b5cf6; }
.af-live      { background: #fef2f2; color: #ef4444; }
.af-desc {
  flex: 1;
  font-size: 13px;
  color: #334155;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.af-meta {
  font-size: 12px;
  color: #94a3b8;
  flex-shrink: 0;
}
.af-time {
  font-size: 11px;
  color: #cbd5e1;
  flex-shrink: 0;
  width: 52px;
  text-align: right;
}

/* ── 热门内容 Top 5 ── */
.top-content {
  display: flex;
  flex-direction: column;
  gap: 0;
}
.tc-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 0;
  border-bottom: 1px solid #f8fafc;
}
.tc-row:last-child { border-bottom: none; }
.tc-rank {
  width: 22px;
  height: 22px;
  border-radius: 6px;
  background: #f1f5f9;
  color: #94a3b8;
  font-size: 12px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.tc-rank.top3 { background: #fef3c7; color: #d97706; }
.tc-body {
  flex: 1;
  min-width: 0;
}
.tc-title {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
  display: block;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.tc-sub {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 2px;
  display: block;
}
.tc-stats {
  display: flex;
  gap: 14px;
  flex-shrink: 0;
}
.tc-views, .tc-likes {
  font-size: 12px;
  color: #64748b;
  display: flex;
  align-items: center;
  gap: 3px;
  white-space: nowrap;
}
.tc-likes { color: #f43f5e; }

/* ── 设备分布 ── */
.device-flow {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.dev-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.dev-name {
  font-size: 13px;
  font-weight: 500;
  color: #475569;
  width: 56px;
  flex-shrink: 0;
  text-align: right;
}
.dev-track {
  flex: 1;
  height: 7px;
  background: #f1f5f9;
  border-radius: 4px;
  overflow: hidden;
}
.dev-fill {
  height: 100%;
  border-radius: 4px;
  transition: width 0.8s cubic-bezier(0.4, 0, 0.2, 1);
}
.dev-pct {
  font-size: 13px;
  font-weight: 600;
  color: #64748b;
  width: 32px;
  text-align: right;
}
.device-total {
  margin-top: 6px;
  font-size: 10px;
  color: #94a3b8;
}

/* ── 热门标签云 ── */
.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 10px;
  align-items: center;
  padding-top: 4px;
}
.tag-badge {
  font-weight: 700;
  color: #475569;
  cursor: default;
  transition: color 0.15s;
}
.tag-badge:hover { color: #3b82f6; }

/* ── 转化漏斗 ── */
.funnel-flow {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.fun-step {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.fun-bar-wrap {
  width: 100%;
  height: 6px;
  background: #f1f5f9;
  border-radius: 3px;
  overflow: hidden;
}
.fun-bar {
  height: 100%;
  border-radius: 3px;
  transition: width 1s cubic-bezier(0.4, 0, 0.2, 1);
}
.fun-meta {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.fun-label {
  font-size: 12px;
  color: #64748b;
  font-weight: 500;
  width: 32px;
}
.fun-val {
  font-size: 13px;
  font-weight: 700;
  color: #1e293b;
  flex: 1;
}
.fun-rate {
  font-size: 11px;
  font-weight: 600;
  color: #94a3b8;
}

/* ── 用户增长 ── */
.growth-strip {
  display: flex;
  align-items: flex-end;
  gap: 16px;
}
.gs-bar-group {
  display: flex;
  gap: 2px;
  align-items: flex-end;
  height: 60px;
  flex: 1;
}
.gs-bar {
  flex: 1;
  min-width: 3px;
  background: linear-gradient(180deg, #3b82f6, #93c5fd);
  border-radius: 2px 2px 0 0;
  transition: height 0.5s ease;
  cursor: pointer;
  opacity: 0.75;
}
.gs-bar:hover { opacity: 1; }
.gs-summary {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 56px;
  text-align: right;
}
.gs-val {
  font-size: 20px;
  font-weight: 800;
  color: #1e293b;
  line-height: 1;
}
.gs-sub {
  font-size: 10px;
  color: #94a3b8;
}

/* ── 24h 活跃分布 ── */
.hourly-strip {
  display: flex;
  gap: 2px;
  align-items: flex-end;
  height: 60px;
}
.hour-bar-wrap {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  height: 100%;
  gap: 4px;
}
.hour-bar {
  width: 100%;
  max-width: 16px;
  min-height: 2px;
  background: linear-gradient(180deg, #818cf8, #c7d2fe);
  border-radius: 2px 2px 0 0;
  transition: height 0.4s ease;
}
.hour-label {
  font-size: 9px;
  color: #cbd5e1;
  font-weight: 500;
}
.hour-label.peak { color: #6366f1; font-weight: 700; }

/* ── 订单状态 ── */
.order-status-flow {
  display: flex;
  flex-direction: column;
  gap: 7px;
}
.os-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.os-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.os-label {
  font-size: 13px;
  color: #475569;
  flex: 1;
  font-weight: 500;
}
.os-val {
  font-size: 15px;
  font-weight: 700;
  color: #1e293b;
}
.ecom-extra {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px solid #f1f5f9;
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}
.ee-label { font-size: 11px; color: #94a3b8; }
.ee-val { font-size: 16px; font-weight: 800; color: #059669; }

/* ── 响应式 ── */
@media (max-width: 1100px) {
  .grid-2 { grid-template-columns: 1fr; gap: 32px; }
  .grid-3 { grid-template-columns: repeat(2, 1fr); }
  .hero-metrics { gap: 28px; }
}
@media (max-width: 700px) {
  .grid-3 { grid-template-columns: 1fr; }
  .hero-metrics { flex-direction: column; gap: 16px; }
  .flow-stats { gap: 4px 16px; }
  .pipeline-flow { gap: 24px; }
  .role-flow { flex-wrap: wrap; }
}
</style>
