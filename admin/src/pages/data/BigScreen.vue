<template>
  <div class="digital-twin" :class="{ 'dt-fullscreen': isFs }">
    <!-- ========== 顶部栏 ========== -->
    <!-- <header class="dt-header">
      <div class="dt-header-left">
        <span class="dt-logo-diamond"></span>
        <h1 class="dt-title-text">SeekFlow 数字孪生核心态势</h1>
      </div>
      <div class="dt-header-deco">
        <div class="deco-line"></div>
      </div>
      <div class="dt-header-right">
        <span class="dt-clock-time">{{ now }}</span>
        <button class="dt-fs-btn" @click="toggleFs" :title="isFs ? '退出全屏' : '全屏'">
          <Minimize2 v-if="isFs" :size="16" />
          <Maximize2 v-else :size="16" />
        </button>
      </div>
    </header> -->

    <!-- ========== 核心三栏 ========== -->
    <div class="dt-body">
      <!-- 左翼 -->
      <div class="dt-wing dt-wing-left">
        <div class="dt-panel dt-panel-glow">
          <div class="dt-panel-corner top-left"></div>
          <div class="dt-panel-corner bottom-right"></div>
          <h3 class="dt-panel-title"><Activity :size="14" /> 实时核心指标</h3>
          <div class="dt-core-new">
            <div class="dt-hero-metric">
              <span class="hero-lbl">CURRENT ONLINE / 实时在线</span>
              <span class="hero-val">{{ fmtNum(summary.onlineUsers) }}</span>
              <div class="hero-bar"><div class="hero-bar-inner"></div></div>
            </div>
            <div class="dt-sub-metrics">
              <div class="sub-item">
                <span class="sub-val text-green">{{ fmtNum(summary.todayActiveUsers) }}</span>
                <span class="sub-lbl">今日活跃</span>
              </div>
              <div class="sub-divider"></div>
              <div class="sub-item">
                <span class="sub-val text-purple">{{ fmtNum(summary.todayViews) }}</span>
                <span class="sub-lbl">今日浏览</span>
              </div>
              <div class="sub-divider"></div>
              <div class="sub-item">
                <span class="sub-val text-pink">{{ fmtNum(summary.todayLikes) }}</span>
                <span class="sub-lbl">今日点赞</span>
              </div>
            </div>
          </div>
        </div>

        <div class="dt-panel dt-panel-review dt-panel-glow">
          <div class="dt-panel-corner top-left"></div>
          <div class="dt-panel-corner bottom-right"></div>
          <h3 class="dt-panel-title"><CheckCheck :size="14" /> 审核流水线</h3>
          <div class="dt-pipeline">
            <div class="pipe-node node-pending">
              <div class="node-glow"></div>
              <span class="n-val">{{ fmtNum(rp.pending) }}</span>
              <span class="n-lbl">待审核</span>
            </div>
            <div class="pipe-flow">
              <div class="flow-line line-up"></div>
              <div class="flow-line line-down"></div>
            </div>
            <div class="pipe-ends">
              <div class="pipe-node node-approved">
                <span class="n-val">{{ fmtNum(rp.approved) }}</span>
                <span class="n-lbl">已通过</span>
              </div>
              <div class="pipe-node node-rejected">
                <span class="n-val">{{ fmtNum(rp.rejected) }}</span>
                <span class="n-lbl">已驳回</span>
              </div>
            </div>
          </div>
        </div>

        <div class="dt-panel dt-panel-live dt-panel-glow">
          <div class="dt-panel-corner top-left"></div>
          <div class="dt-panel-corner bottom-right"></div>
          <h3 class="dt-panel-title"><Radio :size="14" /> 商业态势监控</h3>
          <div class="dt-biz-monitor">
            <div class="biz-live">
              <div class="radar-box" :class="{ 'is-active': summary.activeLiveRooms > 0 }">
                <div class="radar-ring r1"></div>
                <div class="radar-ring r2"></div>
                <div class="radar-core">
                  <span class="r-num">{{ summary.activeLiveRooms || 0 }}</span>
                  <span class="r-lbl">LIVE</span>
                </div>
              </div>
            </div>
            <div class="biz-shop">
              <div class="shop-row">
                <div class="s-meta"><span class="s-lbl">总商品</span><span class="s-val">{{ fmtNum(summary.totalGoods) }}</span></div>
                <div class="s-bar"><div class="s-fill bg-teal" style="width: 85%"></div></div>
              </div>
              <div class="shop-row">
                <div class="s-meta"><span class="s-lbl">总订单</span><span class="s-val">{{ fmtNum(summary.totalOrders) }}</span></div>
                <div class="s-bar"><div class="s-fill bg-blue" style="width: 60%"></div></div>
              </div>
              <div class="shop-row">
                <div class="s-meta"><span class="s-lbl">今日订单</span><span class="s-val text-green">{{ fmtNum(summary.todayOrders) }}</span></div>
                <div class="s-bar"><div class="s-fill bg-green" style="width: 90%; box-shadow: 0 0 10px #10b981;"></div></div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 中央地图 -->
      <div class="dt-center">
        <div class="dt-map-container">
          <div ref="mapChart" class="dt-map"></div>
          <div v-if="mapLoading" class="dt-map-loading">
            <span class="dt-loading-pulse"></span>
            <span>加载地图数据...</span>
          </div>
          <div class="map-overlay-title">用户地域分布中心</div>
        </div>
      </div>

      <!-- 右翼 -->
      <div class="dt-wing dt-wing-right">
        <div class="dt-panel dt-panel-glow">
          <div class="dt-panel-corner top-left"></div>
          <div class="dt-panel-corner bottom-right"></div>
          <h3 class="dt-panel-title"><Navigation :size="14" /> 流量来源</h3>
          <div ref="trafficChart" class="dt-chart-inner"></div>
        </div>

        <div class="dt-panel dt-panel-content-type dt-panel-glow">
          <div class="dt-panel-corner top-left"></div>
          <div class="dt-panel-corner bottom-right"></div>
          <h3 class="dt-panel-title"><PieChart :size="14" /> 内容类型分布</h3>
          <div ref="contentDonutChart" class="dt-chart-inner"></div>
        </div>

        <div class="dt-panel dt-panel-device dt-panel-glow">
          <div class="dt-panel-corner top-left"></div>
          <div class="dt-panel-corner bottom-right"></div>
          <h3 class="dt-panel-title"><Monitor :size="14" /> 设备分布</h3>
          <div ref="deviceChart" class="dt-chart-inner"></div>
        </div>
      </div>
    </div>

    <!-- ========== 热门作品封面条：第一行← 第二行→ ========== -->
    <div class="dt-hot-bar" v-if="hotVideos.length">
      <div class="dt-hot-label"><Flame :size="16" /> <span>热门作品</span></div>
      <div class="dt-hot-tracks">
        <div class="dt-hot-track">
          <div class="dt-hot-inner dt-hot-scroll-l">
            <div class="dt-hot-card glass-morphism" v-for="v in hotScroll" :key="'l'+v._k">
              <img class="dt-hot-cover" :src="normalizeImgUrl(v.coverUrl)" :alt="v.desc" @error="onCoverError" />
              <div class="dt-hot-meta">
                <span class="dt-hot-title" :title="v.desc">{{ v.desc || '无描述' }}</span>
                <span class="dt-hot-stats">
                  <Eye :size="10" /> {{ fmtNum(v.playCount) }}
                  <Heart :size="10" style="margin-left:8px" /> {{ fmtNum(v.likeCount) }}
                </span>
              </div>
            </div>
          </div>
        </div>
        <div class="dt-hot-track">
          <div class="dt-hot-inner dt-hot-scroll-r">
            <div class="dt-hot-card glass-morphism" v-for="v in hotScroll" :key="'r'+v._k">
              <img class="dt-hot-cover" :src="normalizeImgUrl(v.coverUrl)" :alt="v.desc" @error="onCoverError" />
              <div class="dt-hot-meta">
                <span class="dt-hot-title" :title="v.desc">{{ v.desc || '无描述' }}</span>
                <span class="dt-hot-stats">
                  <Eye :size="10" /> {{ fmtNum(v.playCount) }}
                  <Heart :size="10" style="margin-left:8px" /> {{ fmtNum(v.likeCount) }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ========== 底部图表 ========== -->
    <div class="dt-bottom">
      <div class="dt-panel dt-bottom-panel dt-panel-glow">
        <div class="dt-panel-corner top-left"></div>
        <div class="dt-panel-corner bottom-right"></div>
        <h3 class="dt-panel-title"><TrendingUp :size="14" /> 用户增长 · 30天</h3>
        <div ref="userGrowthChart" class="dt-chart-inner"></div>
      </div>
      <div class="dt-panel dt-bottom-panel dt-panel-glow">
        <div class="dt-panel-corner top-left"></div>
        <div class="dt-panel-corner bottom-right"></div>
        <h3 class="dt-panel-title"><Activity :size="14" /> 互动趋势 · 15天</h3>
        <div ref="engagementChart" class="dt-chart-inner"></div>
      </div>
      <div class="dt-panel dt-bottom-panel dt-panel-glow">
        <div class="dt-panel-corner top-left"></div>
        <div class="dt-panel-corner bottom-right"></div>
        <h3 class="dt-panel-title"><Layers :size="14" /> 用户分层</h3>
        <div ref="segmentChart" class="dt-chart-inner"></div>
      </div>
      <div class="dt-panel dt-bottom-panel dt-panel-glow">
        <div class="dt-panel-corner top-left"></div>
        <div class="dt-panel-corner bottom-right"></div>
        <h3 class="dt-panel-title"><ShoppingBag :size="14" /> 电商订单状态</h3>
        <div ref="ecommerceChart" class="dt-chart-inner"></div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, inject, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import {
  Maximize2, Minimize2, CheckCheck, Navigation, TrendingUp,
  Activity, PieChart, Monitor, Layers, ShoppingBag, Radio, Flame, Eye, Heart
} from 'lucide-vue-next'
import {
  getDashboardSummary, getContentBreakdown, getUserGrowth,
  getEngagementDetail, getTrafficSources, getDeviceStats,
  getEcommerceOverview, getUserSegments, getGeoData, getTopVideos
} from '@/api/admin'
import { normalizeImgUrl, NO_COVER_SVG } from '@/utils/image'

const bigScreenFs = inject<any>('bigScreenFs')
const isFs = ref(false)
function toggleFs() {
  isFs.value = !isFs.value
  if (bigScreenFs) bigScreenFs.value = isFs.value
  setTimeout(resizeAll, 300)
}

const now = ref('')
const clockTimer = setInterval(() => {
  now.value = new Date().toLocaleString('zh-CN', { hour12: false })
}, 1000)

const mapChart = ref<HTMLDivElement | null>(null)
const trafficChart = ref<HTMLDivElement | null>(null)
const contentDonutChart = ref<HTMLDivElement | null>(null)
const deviceChart = ref<HTMLDivElement | null>(null)
const userGrowthChart = ref<HTMLDivElement | null>(null)
const engagementChart = ref<HTMLDivElement | null>(null)
const segmentChart = ref<HTMLDivElement | null>(null)
const ecommerceChart = ref<HTMLDivElement | null>(null)

const mapLoading = ref(true)
let charts: any[] = []

const summary = reactive<Record<string, any>>({})
const rp = reactive({ pending: 0, approved: 0, rejected: 0 })

function fmtNum(n: any): string {
  if (!n) return '0'
  if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return String(n)
}

interface HotVideo { id: number; desc: string; coverUrl: string; playCount: number; likeCount: number }
const hotVideos = ref<HotVideo[]>([])

// 双倍数据用于 CSS 动画无缝循环滚动
const hotScroll = computed(() => {
  const list = hotVideos.value
  if (!list.length) return []
  const doubled: (HotVideo & { _k: string })[] = []
  for (let r = 0; r < 2; r++) {
    for (const v of list) doubled.push({ ...v, _k: r + '-' + v.id })
  }
  return doubled
})

function onCoverError(e: Event) {
  const img = e.target as HTMLImageElement
  img.src = NO_COVER_SVG
}


async function loadHotVideos() {
  try {
    const res: any = await getTopVideos({ metric: 'views', limit: 10 })
    if (res.success && res.data) {
      hotVideos.value = res.data.filter((v: any) => v.coverUrl)
    }
  } catch (e) { console.error(e) }
}

// ==== ECharts 暗色工具 ====
const axisTextColor = '#6b7c93'
const axisLineColor = '#1e3a5f'
const gridLineColor = '#152040'

function darkGrid(extra?: any) {
  return { left: 45, right: 15, top: 10, bottom: 30, ...(extra || {}) }
}
function darkXAxis(extra?: any) {
  return { axisLabel: { color: axisTextColor, fontSize: 9 }, axisLine: { lineStyle: { color: axisLineColor } }, ...(extra || {}) }
}
function darkYAxis(extra?: any) {
  return { axisLabel: { color: axisTextColor, fontSize: 9 }, splitLine: { lineStyle: { color: gridLineColor } }, ...(extra || {}) }
}

// ==== 省份归一化 ====
const provinceMap: Record<string, string> = {
  '北京':'北京市','北京市':'北京市','天津':'天津市','天津市':'天津市',
  '上海':'上海市','上海市':'上海市','重庆':'重庆市','重庆市':'重庆市',
  '河北':'河北省','河北省':'河北省','山西':'山西省','山西省':'山西省',
  '内蒙古':'内蒙古自治区','内蒙古自治区':'内蒙古自治区',
  '辽宁':'辽宁省','辽宁省':'辽宁省','吉林':'吉林省','吉林省':'吉林省',
  '黑龙江':'黑龙江省','黑龙江省':'黑龙江省',
  '江苏':'江苏省','江苏省':'江苏省','浙江':'浙江省','浙江省':'浙江省',
  '安徽':'安徽省','安徽省':'安徽省','福建':'福建省','福建省':'福建省',
  '江西':'江西省','江西省':'江西省','山东':'山东省','山东省':'山东省',
  '河南':'河南省','河南省':'河南省','湖北':'湖北省','湖北省':'湖北省',
  '湖南':'湖南省','湖南省':'湖南省','广东':'广东省','广东省':'广东省',
  '广西':'广西壮族自治区','广西壮族自治区':'广西壮族自治区',
  '海南':'海南省','海南省':'海南省','四川':'四川省','四川省':'四川省',
  '贵州':'贵州省','贵州省':'贵州省','云南':'云南省','云南省':'云南省',
  '西藏':'西藏自治区','西藏自治区':'西藏自治区',
  '陕西':'陕西省','陕西省':'陕西省','甘肃':'甘肃省','甘肃省':'甘肃省',
  '青海':'青海省','青海省':'青海省','宁夏':'宁夏回族自治区','宁夏回族自治区':'宁夏回族自治区',
  '新疆':'新疆维吾尔自治区','新疆维吾尔自治区':'新疆维吾尔自治区',
  '台湾':'台湾省','台湾省':'台湾省',
  '香港':'香港特别行政区','香港特别行政区':'香港特别行政区',
  '澳门':'澳门特别行政区','澳门特别行政区':'澳门特别行政区',
}
function normalizeProvince(name: string): string {
  return provinceMap[name] || provinceMap[name.replace(/省|市|自治区|壮族自治区|回族自治区|维吾尔自治区|特别行政区/g, '')] || name
}

// ============== 中国地图 ==============
let chinaGeoJson: any = null

async function initMap() {
  if (!mapChart.value) return
  try {
    if (!chinaGeoJson) {
      const resp = await fetch('https://geo.datav.aliyun.com/areas_v3/bound/100000_full.json')
      chinaGeoJson = await resp.json()
    }
    echarts.registerMap('china', chinaGeoJson)

    const geoRes: any = await getGeoData()
    const geoData: { name: string; value: number }[] = []
    if (geoRes.success && geoRes.data) {
      for (const item of geoRes.data) {
        geoData.push({ name: normalizeProvince(item.name), value: item.value })
      }
    }

    const c = echarts.init(mapChart.value)
    c.setOption({
      tooltip: {
        trigger: 'item',
        appendToBody: true,
        backgroundColor: 'rgba(6,16,40,0.92)',
        borderColor: 'rgba(0,180,255,0.3)',
        textStyle: { color: '#c8d6e5', fontSize: 12 },
        formatter: (p: any) => `<strong>${p.name}</strong><br/>用户数：${p.value || 0}`
      },
      visualMap: {
        min: 0, max: Math.max(...geoData.map(d => d.value), 100),
        text: ['高', '低'], realtime: false, calculable: true,
        inRange: { color: ['#0a1a3a', '#0d2f5e', '#15508a', '#1e78c2', '#30a0e8', '#55ccff'] },
        textStyle: { color: '#6b7c93', fontSize: 10 },
        left: 8, bottom: 8,
      },
      series: [{
        type: 'map', map: 'china', roam: false,
        zoom: 1.15, center: [104.5, 36], aspectScale: 0.85,
        itemStyle: { areaColor: '#0d1f3c', borderColor: 'rgba(0,160,255,0.25)', borderWidth: 0.8 },
        emphasis: { label: { show: true, color: '#fff', fontSize: 11 }, itemStyle: { areaColor: '#1a5aa8', borderColor: '#40c8ff', borderWidth: 1.5 } },
        data: geoData,
      }],
    })
    charts.push(c)
  } catch (e) { console.error('Map init failed:', e) }
  finally { mapLoading.value = false }
}

async function loadTraffic() {
  if (!trafficChart.value) return
  try {
    const res: any = await getTrafficSources()
    if (!res.success) return
    const sources = (res.data?.sources || []).slice(0, 7).reverse()
    const sourceLabels: Record<string, string> = {
      'HOME_RECOMMEND':'首页推荐','SEARCH':'搜索','USER_PROFILE':'个人主页',
      'FOLLOWING':'关注页','HASHTAG':'话题页','EXTERNAL':'外部','SHARE':'分享'
    }
    const c = echarts.init(trafficChart.value)
    c.setOption({
      tooltip: { trigger: 'axis', appendToBody: true, backgroundColor: 'rgba(6,16,40,0.9)', borderColor: 'rgba(0,180,255,0.25)', textStyle: { color: '#c8d6e5', fontSize: 11 } },
      grid: darkGrid({ left: 65, right: 25, top: 5, bottom: 20 }),
      xAxis: darkXAxis({ type: 'value' }),
      yAxis: { ...darkYAxis({ type: 'category', data: sources.map((s: any) => sourceLabels[s.name] || s.name) }), axisLine: { lineStyle: { color: axisLineColor } } },
      series: [{
        type: 'bar', data: sources.map((s: any) => s.value), barMaxWidth: 10,
        itemStyle: { borderRadius: [0, 3, 3, 0], color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [{ offset: 0, color: '#667eea' }, { offset: 1, color: '#00d4ff' }]) },
        label: { show: true, position: 'right', color: axisTextColor, fontSize: 9 }
      }]
    })
    charts.push(c)
  } catch (e) { console.error(e) }
}

async function loadContentDonut() {
  if (!contentDonutChart.value) return
  try {
    const res: any = await getContentBreakdown()
    if (!res.success) return
    const bd = res.data?.byType || {}
    const labels: Record<string, string> = { 'recommend-video':'短视频','long-video':'长视频','image':'图文','text':'文字' }
    const data = Object.entries(bd).map(([k, v]) => ({ name: labels[k] || k, value: v }))
    const c = echarts.init(contentDonutChart.value)
    c.setOption({
      tooltip: { trigger: 'item', appendToBody: true, backgroundColor: 'rgba(6,16,40,0.9)', borderColor: 'rgba(0,180,255,0.25)', textStyle: { color: '#c8d6e5', fontSize: 11 } },
      legend: { orient: 'horizontal', top: 0, left: 'center', textStyle: { color: axisTextColor, fontSize: 9 } },
      series: [{
        type: 'pie', radius: ['45%', '65%'], center: ['50%', '50%'], data,
        label: { show: false },
        emphasis: { label: { show: true, color: '#c8d6e5', fontSize: 11 } },
        itemStyle: { borderRadius: 2, borderColor: '#060d1f', borderWidth: 2 }
      }]
    })
    charts.push(c)
  } catch (e) { console.error(e) }
}

async function loadDeviceStats() {
  if (!deviceChart.value) return
  try {
    const res: any = await getDeviceStats()
    if (!res.success) return
    const os = res.data?.osDistribution || {}
    const entries = Object.entries(os).sort((a: any, b: any) => b[1] - a[1])
    const colors = ['#00d4ff','#667eea','#fe2c55','#10b981','#f59e0b'].slice(0, entries.length)
    const c = echarts.init(deviceChart.value)
    c.setOption({
      tooltip: { trigger: 'axis', appendToBody: true, backgroundColor: 'rgba(6,16,40,0.9)', borderColor: 'rgba(0,180,255,0.25)', textStyle: { color: '#c8d6e5', fontSize: 11 } },
      grid: darkGrid({ left: 55, right: 20, top: 5, bottom: 20 }),
      xAxis: darkXAxis({ type: 'value' }),
      yAxis: { ...darkYAxis({ type: 'category', data: entries.map(([k]) => k) }), axisLine: { lineStyle: { color: axisLineColor } } },
      series: [{
        type: 'bar', data: entries.map(([, v], i) => ({ value: v, itemStyle: { color: colors[i] || axisTextColor, borderRadius: [0, 3, 3, 0] } })),
        barMaxWidth: 12, label: { show: true, position: 'right', color: axisTextColor, fontSize: 9 }
      }]
    })
    charts.push(c)
  } catch (e) { console.error(e) }
}

async function loadUserGrowth() {
  if (!userGrowthChart.value) return
  try {
    const res: any = await getUserGrowth({ days: 30 })
    if (!res.success) return
    const daily = res.data?.dailyRegistrations || {}
    const cum = res.data?.cumulativeGrowth || []
    const c = echarts.init(userGrowthChart.value)
    c.setOption({
      tooltip: { trigger: 'axis', appendToBody: true, backgroundColor: 'rgba(6,16,40,0.9)', borderColor: 'rgba(0,180,255,0.25)', textStyle: { color: '#c8d6e5', fontSize: 11 } },
      legend: { data: ['新增','累计'], textStyle: { color: axisTextColor, fontSize: 10 }, top: 0, left: 'center' },
      grid: darkGrid({ left: 50, right: 55, top: 35, bottom: 45 }),
      xAxis: darkXAxis({ type: 'category', data: Object.keys(daily), axisLabel: { rotate: 30 } }),
      yAxis: [
        { ...darkYAxis({ name: '新增', nameTextStyle: { color: axisTextColor, fontSize: 9 } }) },
        { ...darkYAxis({ name: '累计', nameTextStyle: { color: axisTextColor, fontSize: 9 } }) },
      ],
      series: [
        { name: '新增', type: 'bar', data: Object.values(daily), barMaxWidth: 8,
          itemStyle: { borderRadius: [3, 3, 0, 0], color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: '#fe2c55' }, { offset: 1, color: '#ff6b81' }]) } },
        { name: '累计', type: 'line', yAxisIndex: 1, data: cum.map((d: any) => d.count), smooth: true, symbol: 'none',
          lineStyle: { color: '#00d4ff', width: 2 },
          areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(0,212,255,0.2)' }, { offset: 1, color: 'rgba(0,212,255,0)' }]) } }
      ]
    })
    charts.push(c)
  } catch (e) { console.error(e) }
}

async function loadEngagement() {
  if (!engagementChart.value) return
  try {
    const res: any = await getEngagementDetail({ days: 15 })
    if (!res.success) return
    const trend = res.data?.trend || []
    const c = echarts.init(engagementChart.value)
    c.setOption({
      tooltip: { trigger: 'axis', appendToBody: true, backgroundColor: 'rgba(6,16,40,0.9)', borderColor: 'rgba(0,180,255,0.25)', textStyle: { color: '#c8d6e5', fontSize: 11 } },
      legend: { data: ['观看','点赞','收藏','评论'], textStyle: { color: axisTextColor, fontSize: 10 }, top: 0, left: 'center' },
      grid: darkGrid({ left: 55, right: 20, top: 35, bottom: 45 }),
      xAxis: darkXAxis({ type: 'category', data: trend.map((d: any) => d.time), axisLabel: { rotate: 25 } }),
      yAxis: darkYAxis(),
      series: [
        { name: '观看', type: 'line', data: trend.map((d: any) => d.views), smooth: true, symbol: 'none', lineStyle: { color: '#00d4ff', width: 1.5 }, areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(0,212,255,0.2)' }, { offset: 1, color: 'rgba(0,212,255,0)' }]) } },
        { name: '点赞', type: 'line', data: trend.map((d: any) => d.likes), smooth: true, symbol: 'none', lineStyle: { color: '#fe2c55', width: 1.5 }, areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(254,44,85,0.18)' }, { offset: 1, color: 'rgba(254,44,85,0)' }]) } },
        { name: '收藏', type: 'line', data: trend.map((d: any) => d.collects), smooth: true, symbol: 'none', lineStyle: { color: '#f59e0b', width: 1.5 } },
        { name: '评论', type: 'line', data: trend.map((d: any) => d.comments), smooth: true, symbol: 'none', lineStyle: { color: '#8b5cf6', width: 1.5 } }
      ]
    })
    charts.push(c)
  } catch (e) { console.error(e) }
}

async function loadSegments() {
  if (!segmentChart.value) return
  try {
    const res: any = await getUserSegments()
    if (!res.success) return
    const seg = res.data?.bySegment || {}
    const labels: Record<string, string> = { 'heavy':'重度','medium':'中度','light':'轻度','new_user':'新用户','unknown':'未分类' }
    const colors: Record<string, string> = { '重度':'#fe2c55','中度':'#f59e0b','轻度':'#00d4ff','新用户':'#10b981','未分类':'#6b7c93' }
    const entries = Object.entries(seg).map(([k, v]) => ({ name: labels[k] || k, value: v as number }))
    const total = entries.reduce((s, e) => s + e.value, 0) || 1
    const c = echarts.init(segmentChart.value)
    c.setOption({
      tooltip: { trigger: 'axis', appendToBody: true, backgroundColor: 'rgba(6,16,40,0.9)', borderColor: 'rgba(0,180,255,0.25)', textStyle: { color: '#c8d6e5', fontSize: 11 } },
      grid: darkGrid({ left: 45, right: 15, top: 15, bottom: 25 }),
      xAxis: darkXAxis({ type: 'value', max: 100, axisLabel: { formatter: '{value}%' } }),
      yAxis: { ...darkYAxis({ type: 'category', data: ['占比'] }), axisLine: { lineStyle: { color: axisLineColor } } },
      series: entries.map((e, idx) => ({
        name: e.name, type: 'bar', stack: 'total',
        data: [Math.round(e.value / total * 100)],
        itemStyle: { color: colors[e.name] || axisTextColor, borderRadius: idx === entries.length - 1 ? [4, 4, 4, 4] : [0, 0, 0, 0] },
        barMaxWidth: 18,
        label: { show: true, fontSize: 9, color: '#c8d6e5', formatter: (p: any) => p.value > 6 ? p.seriesName + ' ' + p.value + '%' : '' }
      }))
    })
    charts.push(c)
  } catch (e) { console.error(e) }
}

async function loadEcommerce() {
  if (!ecommerceChart.value) return
  try {
    const res: any = await getEcommerceOverview()
    if (!res.success) return
    const os = res.data?.orderStatus || {}
    const statusLabels: Record<string, string> = { 'PENDING':'待支付','PAID':'已支付','SHIPPED':'已发货','RECEIVED':'已收货','CANCELLED':'已取消' }
    const statusColors: Record<string, string> = { '待支付':'#f59e0b','已支付':'#667eea','已发货':'#00d4ff','已收货':'#10b981','已取消':'#ef4444' }
    const entries = Object.entries(os).map(([k, v]) => ({ name: statusLabels[k] || k, value: v }))
    const c = echarts.init(ecommerceChart.value)
    c.setOption({
      tooltip: { trigger: 'axis', appendToBody: true, backgroundColor: 'rgba(6,16,40,0.9)', borderColor: 'rgba(0,180,255,0.25)', textStyle: { color: '#c8d6e5', fontSize: 11 } },
      grid: darkGrid({ left: 50, right: 15, top: 10, bottom: 35 }),
      xAxis: darkXAxis({ type: 'category', data: entries.map(e => e.name), axisLabel: { rotate: 20, fontSize: 9 } }),
      yAxis: darkYAxis(),
      series: [{
        type: 'bar', data: entries.map(e => ({ value: e.value, itemStyle: { color: statusColors[e.name] || axisTextColor, borderRadius: [5, 5, 0, 0] } })),
        barMaxWidth: 18, label: { show: true, position: 'top', color: axisTextColor, fontSize: 10 }
      }]
    })
    charts.push(c)
  } catch (e) { console.error(e) }
}

function resizeAll() { charts.forEach(c => { try { c.resize() } catch { /* chart may be disposed */ } }) }

onMounted(async () => {
  try {
    const res: any = await getDashboardSummary()
    if (res.success) {
      Object.assign(summary, res.data)
      Object.assign(rp, res.data.reviewPipeline || {})
    }
  } catch (e) { console.error(e) }

  initMap()
  loadHotVideos()

  await Promise.all([
    loadTraffic(), loadContentDonut(), loadDeviceStats(),
    loadUserGrowth(), loadEngagement(), loadSegments(), loadEcommerce()
  ])

  setTimeout(() => resizeAll(), 200)
  window.addEventListener('resize', () => { resizeAll() })
})

onUnmounted(() => {
  clearInterval(clockTimer)
  window.removeEventListener('resize', resizeAll)
  charts.forEach(c => c.dispose())
  charts = []
})
</script>

<style scoped>
/* ================================================================
   DIGITAL TWIN — 现代科技/玻璃拟态大屏
   ================================================================ */

:global(body) {
  margin: 0; padding: 0; overflow: hidden;
}

.digital-twin {
  min-height: 100vh;
  background: #040816;
  padding: 10px 15px 15px;
  color: #c8d6e5;
  background-image:
    radial-gradient(circle at 50% 20%, rgba(30, 80, 255, 0.15) 0%, rgba(4, 8, 22, 0) 50%),
    linear-gradient(rgba(0, 180, 255, 0.02) 1px, transparent 1px),
    linear-gradient(90deg, rgba(0, 180, 255, 0.02) 1px, transparent 1px);
  background-size: 100% 100%, 64px 64px, 64px 64px;
  background-attachment: fixed, fixed, fixed;
  position: relative;
  box-sizing: border-box;
  font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif;
}

/* 全屏模式：精确一屏 */
.dt-fullscreen {
  height: 100vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  padding: clamp(10px, 1vh, 18px) clamp(10px, 1vw, 25px);
}
.dt-fullscreen .dt-body {
  flex: 1; min-height: 0; overflow: hidden;
}
.dt-fullscreen .dt-center,
.dt-fullscreen .dt-map-container {
  overflow: hidden;
}

/* --- 全局装饰元素 --- */
.dt-panel-glow {
  position: relative;
  box-shadow: 0 4px 16px 0 rgba(0, 0, 0, 0.3);
}
.dt-panel-corner {
  position: absolute;
  width: 15px; height: 15px;
  border-color: rgba(0, 200, 255, 0.5);
  border-style: solid;
  pointer-events: none; z-index: 1;
}
.dt-panel-corner.top-left { top: -1px; left: -1px; border-width: 2px 0 0 2px; }
.dt-panel-corner.bottom-right { bottom: -1px; right: -1px; border-width: 0 2px 2px 0; }

/* ================================================================
   --- 顶部栏 ---
   ================================================================ */
.dt-header {
  display: flex; align-items: center; justify-content: space-between;
  height: 50px;
  background: rgba(8, 20, 50, 0.3);
  border: 1px solid rgba(0, 180, 255, 0.1);
  border-radius: 4px;
  padding: 0 15px;
  margin-bottom: clamp(6px, 0.6vh, 12px);
  position: relative;
  overflow: hidden;
}
.dt-header::after {
  content: ''; position: absolute; bottom: 0; left: 0; right: 0; height: 1px;
  background: linear-gradient(90deg, transparent, rgba(0, 212, 255, 0.5), transparent);
}
.dt-header-left { display: flex; align-items: center; gap: 10px; z-index: 2; }
.dt-logo-diamond {
  display: inline-block; width: 12px; height: 12px; background: #00d4ff;
  transform: rotate(45deg);
  box-shadow: 0 0 15px rgba(0, 212, 255, 1);
  animation: logo-pulse 2.5s ease-in-out infinite; flex-shrink: 0;
}
@keyframes logo-pulse {
  0%, 100% { box-shadow: 0 0 15px rgba(0, 212, 255, 1); }
  50% { box-shadow: 0 0 30px rgba(0, 212, 255, 1), 0 0 10px rgba(255, 255, 255, 0.6); }
}
.dt-title-text {
  display: inline; font-size: clamp(14px, 1.2vw, 20px); font-weight: 800;
  background: linear-gradient(90deg, #00d4ff, #667eea);
  -webkit-background-clip: text; -webkit-text-fill-color: transparent;
  letter-spacing: 2px; white-space: nowrap; margin: 0;
}
.dt-header-deco { flex: 1; display: flex; justify-content: center; z-index: 1; padding: 0 30px; }
.dt-header-deco .deco-line {
  width: 100%; height: 2px; background: rgba(0, 180, 255, 0.06);
  position: relative;
}
.dt-header-deco .deco-line::after {
  content: ''; position: absolute; width: 20px; height: 2px; background: #00d4ff;
  box-shadow: 0 0 8px #00d4ff; left: 50%; transform: translateX(-50%);
}
.dt-header-right { display: flex; align-items: center; gap: 15px; z-index: 2; }
.dt-clock-time {
  font-size: 14px; font-weight: 500; color: #6b7c93;
  letter-spacing: 1px; font-family: 'Courier New', monospace;
  border-right: 1px solid rgba(255,255,255,0.06); padding-right: 15px;
}
.dt-fs-btn {
  background: transparent; border: none;
  color: #6b7c93; cursor: pointer;
  padding: 5px; display: flex; align-items: center;
  transition: all 0.3s; flex-shrink: 0;
}
.dt-fs-btn:hover { color: #00d4ff; text-shadow: 0 0 8px #00d4ff; }

/* ================================================================
   --- 核心三栏 ---
   ================================================================ */
.dt-body {
  display: grid;
  grid-template-columns: minmax(280px, 1fr) minmax(400px, 2.5fr) minmax(280px, 1fr);
  gap: clamp(6px, 0.6vw, 12px);
  margin-bottom: clamp(6px, 0.5vh, 12px);
}

/* --- 面板基础：玻璃拟态 --- */
.dt-wing { display: flex; flex-direction: column; gap: clamp(6px, 0.6vh, 12px); }

.dt-panel {
  background: rgba(10, 22, 50, 0.5);
  backdrop-filter: blur(5px); -webkit-backdrop-filter: blur(5px);
  border: 1px solid rgba(0, 180, 255, 0.12);
  border-radius: 4px;
  padding: clamp(10px, 1vh, 18px);
  flex: 1; display: flex; flex-direction: column; min-height: 0;
  transition: all 0.3s ease;
}
.dt-panel:hover {
  border-color: rgba(0, 200, 255, 0.3);
  box-shadow: 0 0 25px rgba(0, 150, 255, 0.1), 0 4px 16px rgba(0, 0, 0, 0.4);
}
.dt-panel-title {
  font-size: clamp(10px, 0.7vw, 12px); font-weight: 700; color: #a1b2c3;
  margin: 0 0 10px 0; letter-spacing: 1.5px;
  display: flex; align-items: center; gap: 6px;
  flex-shrink: 0; text-transform: uppercase;
}
.dt-panel-title svg { color: #00bfff; }

/* ================================================================
   --- 左翼内容 ---
   ================================================================ */
.dt-core-stats {
  display: grid; grid-template-columns: 1fr 1fr;
  gap: clamp(4px, 0.4vh, 8px); flex: 1; align-content: center;
}
.dt-core-item {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  background: rgba(0, 30, 80, 0.15);
  border: 1px solid rgba(0, 180, 255, 0.06);
  border-radius: 3px; padding: clamp(4px, 0.5vh, 10px);
  position: relative; overflow: hidden;
}
.dt-core-item::after {
  content: ''; position: absolute; left: 0; top: 0; width: 3px; height: 100%;
}
.dt-core-val {
  font-size: clamp(16px, 1.3vw, 24px); font-weight: 800; color: #fff;
  font-family: 'Courier New', monospace; line-height: 1.1; margin-bottom: 2px;
}
.dt-core-lbl { font-size: clamp(8px, 0.6vw, 10px); color: #6b7c93; margin-top: 2px; }

/* 霓虹发光色 */
.dt-core-item.blue-glow::after, .blue-glow .dt-core-val { background-color: #00d4ff; color: #00d4ff; text-shadow: 0 0 10px rgba(0,212,255,0.7); }
.dt-core-item.blue-glow { background: linear-gradient(135deg, rgba(0,212,255,0.1), transparent 60%); }

.dt-core-item.green-glow::after, .green-glow .dt-core-val { background-color: #10b981; color: #10b981; text-shadow: 0 0 10px rgba(16,185,129,0.7); }
.dt-core-item.green-glow { background: linear-gradient(135deg, rgba(16,185,129,0.1), transparent 60%); }

.dt-core-item.purple-glow::after, .purple-glow .dt-core-val { background-color: #8b5cf6; color: #8b5cf6; text-shadow: 0 0 10px rgba(139,92,246,0.7); }
.dt-core-item.purple-glow { background: linear-gradient(135deg, rgba(139,92,246,0.1), transparent 60%); }

.dt-core-item.pink-glow::after, .pink-glow .dt-core-val { background-color: #fe2c55; color: #fe2c55; text-shadow: 0 0 10px rgba(254,44,85,0.7); }
.dt-core-item.pink-glow { background: linear-gradient(135deg, rgba(254,44,85,0.1), transparent 60%); }

/* 审核 */
.dt-review {
  display: flex; justify-content: space-around; align-items: center;
  flex: 1; padding: 5px 0;
}
.dt-review-item { display: flex; flex-direction: column; align-items: center; gap: 6px; }
.dt-review-ring {
  width: clamp(40px, 3.5vw, 55px); height: clamp(40px, 3.5vw, 55px);
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  border: 3px solid;
  font-size: clamp(12px, 1.1vw, 16px); font-weight: 800;
  font-family: 'Courier New', monospace;
}
.dt-review-item.pending .dt-review-ring { border-color: #f59e0b; color: #f59e0b; box-shadow: inset 0 0 10px rgba(245,158,11,0.2), 0 0 10px rgba(245,158,11,0.2); }
.dt-review-item.approved .dt-review-ring { border-color: #10b981; color: #10b981; box-shadow: inset 0 0 10px rgba(16,185,129,0.2), 0 0 10px rgba(16,185,129,0.2); }
.dt-review-item.rejected .dt-review-ring { border-color: #ef4444; color: #ef4444; box-shadow: inset 0 0 10px rgba(239,68,68,0.2), 0 0 10px rgba(239,68,68,0.2); }
.dt-review-label { font-size: 10px; color: #6b7c93; }

/* 直播电商 */
.dt-live-shop {
  display: flex; flex-direction: column; gap: 8px; flex: 1; justify-content: center;
}
.dt-live-status { display: flex; align-items: center; justify-content: center; gap: 8px; background: rgba(0,0,0,0.1); padding: 5px; border-radius: 4px; }
.dt-live-dot {
  width: 10px; height: 10px; border-radius: 50%; background: #3a3f5c;
}
.dt-live-dot.on {
  background: #ef4444; box-shadow: 0 0 15px rgba(239,68,68,0.8);
  animation: live-pulse 1.5s ease infinite;
}
@keyframes live-pulse {
  0%,100% { box-shadow: 0 0 10px rgba(239,68,68,0.6); opacity: 0.8; }
  50% { box-shadow: 0 0 20px rgba(239,68,68,1); opacity: 1; }
}
.dt-live-num { font-size: clamp(16px, 1.5vw, 24px); font-weight: 800; color: #ef4444; font-family: 'Courier New', monospace; text-shadow: 0 0 10px rgba(239,68,68,0.5); }
.dt-live-label { font-size: 11px; color: #c8d6e5; }
.dt-shop-stats { display: flex; gap: 8px; }
.dt-shop-item { display: flex; flex-direction: column; align-items: center; flex: 1; padding: 5px; background: rgba(0,0,0,0.15); border-radius: 4px; border-bottom: 2px solid transparent;}
.dt-shop-item.teal-glow { border-bottom-color: #10b981; }
.dt-shop-item.green-glow { border-bottom-color: #00d4ff; }
.dt-shop-num { font-size: clamp(14px, 1.2vw, 18px); font-weight: 700; color: #fff; font-family: 'Courier New', monospace; }
.dt-shop-lbl { font-size: 9px; color: #6b7c93; margin-top: 1px; }

/* ================================================================
   --- 中央地图 ---
   ================================================================ */
.dt-center { display: flex; align-items: stretch; position: relative;}
.dt-center::after {
    content: ''; position: absolute; inset: -1px;
    border: 1px solid rgba(0, 180, 255, 0.15);
    border-radius: 6px; pointer-events: none;
    box-shadow: inset 0 0 40px rgba(0, 80, 255, 0.1);
}
.dt-map-container {
  flex: 1; position: relative;
  background: rgba(10, 22, 50, 0.3);
  backdrop-filter: blur(3px); -webkit-backdrop-filter: blur(3px);
  border-radius: 6px; overflow: hidden;
  box-shadow: 0 0 40px rgba(0, 180, 255, 0.06), inset 0 0 60px rgba(0, 0, 0, 0.5);
}
.dt-map { width: 100%; height: 100%; }
.dt-map-loading {
  position: absolute; inset: 0;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 15px; color: #00d4ff; font-size: 12px;
  background: rgba(4, 8, 22, 0.8); z-index: 10;
}
.dt-loading-pulse {
  width: 12px; height: 12px; background: #00d4ff; border-radius: 50%;
  animation: loading-pulse 1.2s ease-in-out infinite;
  box-shadow: 0 0 15px #00d4ff;
}
@keyframes loading-pulse {
  0%, 100% { opacity: 0.3; transform: scale(0.8); box-shadow: 0 0 8px #00d4ff;}
  50% { opacity: 1; transform: scale(1.5); box-shadow: 0 0 20px #00d4ff;}
}
.map-overlay-title {
  position: absolute; left: 20px; top: 15px;
  font-size: 14px; font-weight: 700; color: rgba(200, 214, 229, 0.7);
  letter-spacing: 1px;
  border-left: 3px solid #00d4ff; padding-left: 10px;
}

/* --- 图表内嵌 --- */
.dt-chart-inner { width: 100%; flex: 1; min-height: 0; }

/* ================================================================
   --- 热门作品封面条 ---
   ================================================================ */
.dt-hot-bar {
  display: flex; align-items: flex-start; gap: 10px;
  background: rgba(10, 22, 50, 0.5);
  backdrop-filter: blur(5px); -webkit-backdrop-filter: blur(5px);
  border: 1px solid rgba(0, 180, 255, 0.12);
  border-radius: 4px;
  padding: 6px 15px;
  margin-bottom: clamp(6px, 0.5vh, 12px);
  flex-shrink: 0;
}
.dt-hot-label {
  display: flex; align-items: center; gap: 8px;
  font-size: 12px; font-weight: 700; color: #fe2c55;
  white-space: nowrap; letter-spacing: 1px;
  padding-right: 15px; border-right: 1px solid rgba(255,255,255,0.06);
  align-self: stretch; padding-top: 10px;
}
.dt-hot-label svg { text-shadow: 0 0 10px rgba(254,44,85,0.8); }
.dt-hot-tracks { flex: 1; display: flex; flex-direction: column; gap: 4px; overflow: hidden; }
.dt-hot-track { width: 100%; overflow: hidden; }
.dt-hot-inner { display: flex; gap: 10px; width: max-content; }

/* 玻璃拟态卡片 */
.glass-morphism {
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 4px;
  backdrop-filter: blur(2px);
  overflow: hidden;
  transition: all 0.3s ease;
}

.dt-hot-card {
  flex-shrink: 0; width: clamp(140px, 9vw, 170px);
  cursor: pointer; position: relative;
}
.dt-hot-card:hover {
  border-color: rgba(0, 212, 255, 0.4);
  transform: translateY(-3px) scale(1.02);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.5), 0 0 10px rgba(0, 212, 255, 0.1);
  z-index: 10;
}
.dt-hot-cover {
  width: 100%; height: clamp(60px, 6vh, 90px); object-fit: cover;
  background: #0d1f3c; display: block;
}
.dt-hot-meta { padding: 6px 10px 8px; }
.dt-hot-title {
  display: block; font-size: 11px; color: #e1eaf2;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.dt-hot-stats {
  display: flex; align-items: center;
  font-size: 10px; color: #6b7c93; margin-top: 4px;
}
.dt-hot-stats svg { color: #58697f; }

/* 全屏下热门栏更紧凑 */
.dt-fullscreen .dt-hot-bar { margin-bottom: 0; }
.dt-fullscreen .dt-hot-cover { height: clamp(50px, 5vh, 70px); }

/* 第一行 ← 左滚 */
.dt-hot-scroll-l { animation: hot-scroll-l 50s linear infinite; }
@keyframes hot-scroll-l {
  0% { transform: translateX(0); }
  100% { transform: translateX(-50%); }
}

/* 第二行 → 右滚 */
.dt-hot-scroll-r { animation: hot-scroll-r 50s linear infinite; }
@keyframes hot-scroll-r {
  0% { transform: translateX(-50%); }
  100% { transform: translateX(0); }
}
.dt-hot-inner:hover { animation-play-state: paused; }

/* ================================================================
   --- 底部图表 ---
   ================================================================ */
.dt-bottom {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: clamp(6px, 0.6vw, 12px);
}
.dt-bottom-panel {
  padding: clamp(10px, 1vh, 18px) clamp(10px, 1vw, 15px) clamp(18px, 2vh, 30px);
}
/* ECharts 底部图表默认高度 */
.dt-bottom .dt-chart-inner { min-height: clamp(160px, 24vh, 260px); }

/* ================================================================
   响应式断点优化
   ================================================================ */

/* > 1920px: 增加高度 */
@media (min-width: 1921px) {
  .dt-header { height: 60px; }
  .dt-panel { padding: 20px; }
  .dt-bottom .dt-chart-inner { min-height: 300px; }
}

/* 1200-1599px: 缩小中央地图 */
@media (max-width: 1599px) {
  .dt-body {
    grid-template-columns: minmax(260px, 1fr) minmax(350px, 2.2fr) minmax(260px, 1fr);
  }
}

/* 1024-1199px: 改为 3 栏，底部 2x2 */
@media (max-width: 1199px) {
  .dt-body {
    grid-template-columns: minmax(240px, 1fr) minmax(300px, 2fr) minmax(240px, 1fr);
  }
  .dt-bottom {
    grid-template-columns: 1fr 1fr;
    grid-template-rows: 1fr 1fr;
  }
}

/* 768-1023px: 两栏布局，隐藏部分底部面板 */
@media (max-width: 1023px) {
  .dt-body {
    grid-template-columns: minmax(260px, 1fr) 2fr;
  }
  .dt-wing-right {
    display: none; /* 隐藏右翼，减少拥挤 */
  }
  .dt-bottom { grid-template-columns: 1fr 1fr; }
  .dt-bottom > .dt-panel:nth-child(n+3) { display: none; }
}

/* < 768px: 单列布局 */
@media (max-width: 767px) {
  .dt-fullscreen { overflow-y: auto; height: auto; }
  .dt-header { height: auto; flex-direction: column; padding: 10px; gap: 5px; }
  .dt-header-right { gap: 10px; }
  .dt-clock-time { border: none; padding: 0; }
  .dt-header-deco, .dt-fs-btn { display: none; }
  .dt-body {
    grid-template-columns: 1fr;
  }
  .dt-wing { flex-direction: row; flex-wrap: wrap; }
  .dt-wing .dt-panel { flex: 1 1 calc(50% - 4px); min-width: 240px; }
  .dt-center { height: 300px; }
  .dt-wing-right { display: flex; flex-direction: row; flex-wrap: wrap; }
  .dt-hot-bar { display: none; } /* 手机端隐藏滚动条 */
  .dt-bottom { grid-template-columns: 1fr; }
  .dt-bottom .dt-chart-inner { min-height: 150px; }
}

/* =========================================================
   1. 实时核心指标 (HUD 风格)
   ========================================================= */
.dt-core-new { display: flex; flex-direction: column; flex: 1; justify-content: center; gap: 15px; 
padding-bottom: clamp(8px, 2vh, 16px);}
.dt-hero-metric { display: flex; flex-direction: column; align-items: center; position: relative; }
.hero-lbl { font-size: 10px; color: #00d4ff; letter-spacing: 2px; opacity: 0.8; margin-bottom: 4px; }
.hero-val { 
  font-size: clamp(28px, 2.5vw, 36px); font-weight: 900; color: #fff; 
  font-family: 'Courier New', monospace; text-shadow: 0 0 15px rgba(0,212,255,0.8); line-height: 1;
}
.hero-bar { width: 80%; height: 2px; background: rgba(0,212,255,0.2); margin-top: 8px; position: relative; overflow: hidden;}
.hero-bar-inner { 
  position: absolute; left: 0; top: 0; height: 100%; width: 50%; background: #00d4ff; 
  box-shadow: 0 0 10px #00d4ff; animation: scan-line 3s linear infinite;
}
@keyframes scan-line { 0% { transform: translateX(-100%); } 100% { transform: translateX(200%); } }

.dt-sub-metrics { display: flex; justify-content: space-between; align-items: center; background: rgba(0,0,0,0.2); padding: 8px 15px; border-radius: 4px; }
.sub-item { display: flex; flex-direction: column; align-items: center; flex: 1; }
.sub-val { font-size: clamp(14px, 1.2vw, 18px); font-weight: 700; font-family: 'Courier New', monospace; }
.sub-lbl { font-size: 10px; color: #6b7c93; margin-top: 2px; }
.sub-divider { width: 1px; height: 20px; background: rgba(255,255,255,0.1); }

.text-green { color: #10b981; text-shadow: 0 0 8px rgba(16,185,129,0.5); }
.text-purple { color: #8b5cf6; text-shadow: 0 0 8px rgba(139,92,246,0.5); }
.text-pink { color: #fe2c55; text-shadow: 0 0 8px rgba(254,44,85,0.5); }

/* =========================================================
   2. 审核流水线 (拓扑流向风格)
   ========================================================= */
.dt-pipeline { display: flex; align-items: center; justify-content: space-between; flex: 1; padding: 0 10px; }
.pipe-node {
  background: rgba(10,22,50,0.8); border: 1px solid; border-radius: 6px;
  padding: 10px; display: flex; flex-direction: column; align-items: center; width: 70px; z-index: 2;
  position: relative;
}
.node-pending { border-color: rgba(245,158,11,0.5); box-shadow: inset 0 0 15px rgba(245,158,11,0.2); }
.node-approved { border-color: rgba(16,185,129,0.5); box-shadow: inset 0 0 15px rgba(16,185,129,0.2); }
.node-rejected { border-color: rgba(239,68,68,0.5); box-shadow: inset 0 0 15px rgba(239,68,68,0.2); }
.n-val { font-size: 16px; font-weight: 800; font-family: 'Courier New', monospace; color: #fff; }
.n-lbl { font-size: 10px; color: #a1b2c3; margin-top: 4px; }
.node-pending .n-val { color: #f59e0b; text-shadow: 0 0 8px rgba(245,158,11,0.6); }

.pipe-flow { flex: 1; display: flex; flex-direction: column; justify-content: center; height: 60px; position: relative; margin: 0 10px; }
.flow-line { height: 1px; border-top: 1px dashed rgba(0,212,255,0.4); width: 100%; position: absolute; left: 0; }
.flow-line::after {
  content: ''; position: absolute; width: 6px; height: 6px; background: #00d4ff; border-radius: 50%;
  top: -4px; box-shadow: 0 0 8px #00d4ff; animation: flow-dot 2s infinite linear;
}
.line-up { top: 15px; transform-origin: left center; transform: rotate(-15deg); }
.line-down { bottom: 15px; transform-origin: left center; transform: rotate(15deg); }
.line-down::after { animation-delay: 1s; background: #fe2c55; box-shadow: 0 0 8px #fe2c55; }
@keyframes flow-dot { 0% { left: 0; opacity: 1; } 90% { opacity: 1; } 100% { left: 100%; opacity: 0; } }
.pipe-ends { display: flex; flex-direction: column; gap: 10px; }

/* =========================================================
   3. 直播 & 电商 (雷达与科技条)
   ========================================================= */
.dt-biz-monitor { display: flex; align-items: center; flex: 1; gap: 15px; padding: 5px 0; }
.biz-live { flex: 0.8; display: flex; justify-content: center; align-items: center; border-right: 1px solid rgba(255,255,255,0.06); padding-right: 15px; }
.radar-box { position: relative; width: 70px; height: 70px; display: flex; justify-content: center; align-items: center; }
.radar-core { z-index: 2; display: flex; flex-direction: column; align-items: center; background: #040816; border-radius: 50%; width: 50px; height: 50px; justify-content: center; border: 2px solid #3a3f5c; transition: all 0.3s; }
.r-num { font-size: 16px; font-weight: 800; font-family: 'Courier New', monospace; color: #6b7c93; line-height: 1.2; }
.r-lbl { font-size: 9px; color: #6b7c93; font-weight: bold; }
.radar-ring { position: absolute; inset: 0; border-radius: 50%; border: 1px solid transparent; }

/* 雷达激活态 */
.radar-box.is-active .radar-core { border-color: #ef4444; box-shadow: inset 0 0 10px rgba(239,68,68,0.5); }
.radar-box.is-active .r-num { color: #ef4444; text-shadow: 0 0 8px rgba(239,68,68,0.8); }
.radar-box.is-active .radar-ring { border-color: rgba(239,68,68,0.6); animation: radar-ping 2s cubic-bezier(0, 0, 0.2, 1) infinite; }
.radar-box.is-active .r2 { animation-delay: 1s; }
@keyframes radar-ping { 75%, 100% { transform: scale(1.6); opacity: 0; } }

.biz-shop { flex: 1.2; display: flex; flex-direction: column; gap: 8px; justify-content: center; }
.shop-row { display: flex; flex-direction: column; gap: 3px; }
.s-meta { display: flex; justify-content: space-between; align-items: flex-end; }
.s-lbl { font-size: 10px; color: #a1b2c3; }
.s-val { font-size: 13px; font-weight: 700; font-family: 'Courier New', monospace; color: #fff; }
.s-bar { height: 4px; background: rgba(255,255,255,0.05); border-radius: 2px; overflow: hidden; }
.s-fill { height: 100%; border-radius: 2px; }
.bg-teal { background: #00d4ff; }
.bg-blue { background: #667eea; }
.bg-green { background: #10b981; }
</style>