<template>
  <div class="video-monitor">
    <h2 class="page-title">作品数据监控</h2>

    <div class="stat-grid">
      <div class="stat-card">
        <div class="stat-value" style="color:#10b981;">{{ overview.todayNewVideos }}</div>
        <div class="stat-label">今日新增作品</div>
      </div>
      <div class="stat-card">
        <div class="stat-value" style="color:#fe2c55;">{{ fmtNum(overview.todayViews) }}</div>
        <div class="stat-label">今日浏览量</div>
      </div>
      <div class="stat-card">
        <div class="stat-value" style="color:#f59e0b;">{{ fmtNum(overview.todayLikes) }}</div>
        <div class="stat-label">今日点赞</div>
      </div>
      <div class="stat-card">
        <div class="stat-value" style="color:#3b82f6;">{{ topVideos.length }}</div>
        <div class="stat-label">热门作品数</div>
      </div>
    </div>

    <div class="card" style="margin-bottom:20px;">
      <h3 class="chart-title">
        作品发布趋势
        <select v-model="period" @change="loadPostTrend" class="chart-select">
          <option value="hourly">按小时</option>
          <option value="daily">按天</option>
          <option value="monthly">按月</option>
        </select>
      </h3>
      <div ref="postTrendChart" class="chart-box"></div>
    </div>

    <div class="card">
      <h3 class="chart-title">热门作品 TOP20</h3>
      <table class="data-table">
        <thead>
          <tr>
            <th>#</th>
            <th>描述</th>
            <th>播放量</th>
            <th>点赞</th>
            <th>收藏</th>
            <th>评论</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(v, idx) in topVideos" :key="v.id">
            <td><span class="top-rank" :class="'rank-' + (idx + 1)">{{ idx + 1 }}</span></td>
            <td class="desc-cell">{{ v.desc || '无描述' }}</td>
            <td>{{ fmtNum(v.playCount) }}</td>
            <td>{{ fmtNum(v.likeCount) }}</td>
            <td>{{ fmtNum(v.collectCount) }}</td>
            <td>{{ fmtNum(v.commentCount) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { getAnalyticsOverview, getVideoPostTrend, getTopVideos } from '@/api/admin'

const overview = reactive({ todayNewVideos: 0, todayViews: 0, todayLikes: 0 })
const topVideos = ref<any[]>([])
const period = ref('daily')
const postTrendChart = ref<HTMLDivElement | null>(null)

let charts: any[] = []

function fmtNum(n: number) {
  if (!n) return '0'
  if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return String(n)
}

async function loadOverview() {
  try {
    const res: any = await getAnalyticsOverview()
    if (res.success) Object.assign(overview, res.data)
  } catch (e) { console.error(e) }
}

async function loadPostTrend() {
  try {
    const res: any = await getVideoPostTrend({ period: period.value, days: 30 })
    if (!res.success || !postTrendChart.value) return
    const data = res.data || []
    charts.forEach(c => c.dispose())
    charts = []
    const chart = echarts.init(postTrendChart.value)
    chart.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: 50, right: 20, top: 10, bottom: 30 },
      xAxis: { type: 'category', data: data.map((d: any) => d.time), axisLabel: { fontSize: 10, rotate: 30 } },
      yAxis: { type: 'value', minInterval: 1 },
      series: [{
        type: 'bar', data: data.map((d: any) => d.count),
        itemStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#fe2c55' }, { offset: 1, color: '#fecdd3' }
        ]) },
        barMaxWidth: 20
      }]
    })
    charts.push(chart)
  } catch (e) { console.error(e) }
}

async function loadTopVideos() {
  try {
    const res: any = await getTopVideos({ metric: 'views', limit: 20 })
    if (res.success) topVideos.value = res.data || []
  } catch (e) { console.error(e) }
}

onMounted(async () => {
  await Promise.all([loadOverview(), loadTopVideos()])
  await nextTick()
  loadPostTrend()
})
onUnmounted(() => charts.forEach(c => c.dispose()))
</script>

<style scoped>
.video-monitor { max-width: 1400px; }
.chart-title {
  font-size: 14px; font-weight: 600; color: #333;
  margin-bottom: 12px; display: flex; align-items: center; justify-content: space-between;
}
.chart-select { font-size: 12px; padding: 4px 8px; border: 1px solid #ddd; border-radius: 4px; outline: none; color: #666; }
.chart-box { width: 100%; height: 300px; }
.desc-cell { max-width: 300px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.top-rank {
  width: 22px; height: 22px; border-radius: 4px; display: inline-flex;
  align-items: center; justify-content: center; font-size: 11px; font-weight: 700;
  color: #fff; background: #ccc;
}
.rank-1 { background: #fe2c55; }
.rank-2 { background: #f59e0b; }
.rank-3 { background: #3b82f6; }
</style>
