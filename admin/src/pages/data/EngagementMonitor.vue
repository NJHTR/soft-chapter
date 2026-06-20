<template>
  <div class="engagement-monitor">
    <h2 class="page-title">交互数据监控</h2>

    <div class="card" style="margin-bottom:20px;">
      <h3 class="chart-title">
        互动趋势
        <select v-model="trendDays" @change="loadEngagementTrend" class="chart-select">
          <option :value="7">近7天</option>
          <option :value="15">近15天</option>
          <option :value="30">近30天</option>
        </select>
      </h3>
      <div ref="engagementChart" class="chart-box"></div>
    </div>

    <div class="card" style="margin-bottom:20px;">
      <h3 class="chart-title">搜索趋势</h3>
      <div ref="searchTrendChart" class="chart-box"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { getEngagementTrend, getSearchStats } from '@/api/admin'

const trendDays = ref(15)
const engagementChart = ref<HTMLDivElement | null>(null)
const searchTrendChart = ref<HTMLDivElement | null>(null)

let charts: any[] = []

async function loadEngagementTrend() {
  try {
    const res: any = await getEngagementTrend({ period: 'daily', days: trendDays.value })
    if (!res.success || !engagementChart.value) return
    const data = res.data || []
    if (charts[0]) charts[0].dispose()
    const chart = echarts.init(engagementChart.value)
    chart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['点赞', '收藏', '评论'], bottom: 0, textStyle: { fontSize: 11 } },
      grid: { left: 40, right: 20, top: 10, bottom: 35 },
      xAxis: { type: 'category', data: data.map((d: any) => d.time), axisLabel: { fontSize: 10 } },
      yAxis: { type: 'value', minInterval: 1 },
      series: [
        { name: '点赞', type: 'bar', data: data.map((d: any) => d.likes), itemStyle: { color: '#fe2c55' }, barMaxWidth: 12 },
        { name: '收藏', type: 'bar', data: data.map((d: any) => d.collects), itemStyle: { color: '#f59e0b' }, barMaxWidth: 12 },
        { name: '评论', type: 'bar', data: data.map((d: any) => d.comments || 0), itemStyle: { color: '#3b82f6' }, barMaxWidth: 12 }
      ]
    })
    charts[0] = chart
  } catch (e) { console.error(e) }
}

async function loadSearchTrend() {
  try {
    const res: any = await getSearchStats({ days: 7 })
    if (!res.success || !searchTrendChart.value) return
    const data = res.data?.trend || []
    if (charts[1]) charts[1].dispose()
    const chart = echarts.init(searchTrendChart.value)
    chart.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: 40, right: 20, top: 10, bottom: 30 },
      xAxis: { type: 'category', data: data.map((d: any) => d.time), axisLabel: { fontSize: 10 } },
      yAxis: { type: 'value', minInterval: 1 },
      series: [{
        type: 'line', data: data.map((d: any) => d.count),
        smooth: true, symbol: 'none',
        lineStyle: { color: '#8b5cf6', width: 2 },
        areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(139,92,246,0.2)' }, { offset: 1, color: 'rgba(139,92,246,0.02)' }
        ])}
      }]
    })
    charts[1] = chart
  } catch (e) { console.error(e) }
}

onMounted(async () => {
  await nextTick()
  loadEngagementTrend()
  loadSearchTrend()
})
onUnmounted(() => charts.forEach(c => c?.dispose()))
</script>

<style scoped>
.engagement-monitor { max-width: 1400px; }
.chart-title {
  font-size: 14px; font-weight: 600; color: #333;
  margin-bottom: 12px; display: flex; align-items: center; justify-content: space-between;
}
.chart-select { font-size: 12px; padding: 4px 8px; border: 1px solid #ddd; border-radius: 4px; outline: none; color: #666; }
.chart-box { width: 100%; height: 320px; }
</style>
