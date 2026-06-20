<template>
  <div class="search-page">
    <h2 class="page-title">搜索热门词</h2>

    <div class="stat-grid">
      <div class="stat-card">
        <div class="stat-value" style="color:#3b82f6;">{{ totalSearches }}</div>
        <div class="stat-label">总搜索次数</div>
      </div>
      <div class="stat-card">
        <div class="stat-value" style="color:#8b5cf6;">{{ keywords.length }}</div>
        <div class="stat-label">热门词数量</div>
      </div>
    </div>

    <div class="card" style="margin-bottom:20px;">
      <h3 class="chart-title">热门搜索词 TOP20</h3>
      <div ref="searchChart" class="chart-box"></div>
    </div>

    <div class="card">
      <h3 class="chart-title">搜索词列表</h3>
      <table class="data-table">
        <thead>
          <tr><th>排名</th><th>关键词</th><th>搜索次数</th><th>占比</th></tr>
        </thead>
        <tbody>
          <tr v-for="(k, idx) in keywords" :key="k.keyword">
            <td><span class="top-rank" :class="'rank-' + (idx + 1)">{{ idx + 1 }}</span></td>
            <td>{{ k.keyword }}</td>
            <td>{{ fmtNum(k.count) }}</td>
            <td>{{ getPercent(k.count) }}</td>
          </tr>
          <tr v-if="keywords.length === 0"><td colspan="4" class="empty-row">暂无数据</td></tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import { getSearchStats } from '@/api/admin'

const keywords = ref<any[]>([])
const searchChart = ref<HTMLDivElement | null>(null)
let chart: any = null

const totalSearches = computed(() => keywords.value.reduce((s, k) => s + (k.count || 0), 0))

function fmtNum(n: number) {
  if (!n) return '0'
  if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return String(n)
}

function getPercent(count: number) {
  if (!totalSearches.value) return '0%'
  return (count / totalSearches.value * 100).toFixed(1) + '%'
}

async function loadData() {
  try {
    const res: any = await getSearchStats({ days: 7 })
    if (!res.success) return
    const topKeywords = (res.data?.topKeywords || []).slice(0, 20)
    keywords.value = topKeywords

    if (!searchChart.value) return
    const chartData = [...topKeywords].reverse()
    chart = echarts.init(searchChart.value)
    chart.setOption({
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: 80, right: 50, top: 0, bottom: 20 },
      xAxis: { type: 'value' },
      yAxis: {
        type: 'category', data: chartData.map((k: any) => k.keyword),
        axisLabel: { fontSize: 10 }, inverse: true
      },
      series: [{
        type: 'bar', data: chartData.map((k: any) => k.count),
        itemStyle: { color: '#3b82f6', borderRadius: [0, 4, 4, 0] },
        barMaxWidth: 16, label: { show: true, position: 'right', fontSize: 10 }
      }]
    })
  } catch (e) { console.error(e) }
}

onMounted(() => loadData())
onUnmounted(() => { if (chart) chart.dispose() })
</script>

<style scoped>
.search-page { max-width: 1400px; }
.chart-title { font-size: 14px; font-weight: 600; color: #333; margin-bottom: 12px; }
.chart-box { width: 100%; height: 400px; }
.top-rank {
  width: 22px; height: 22px; border-radius: 4px; display: inline-flex;
  align-items: center; justify-content: center; font-size: 11px; font-weight: 700;
  color: #fff; background: #ccc;
}
.rank-1 { background: #fe2c55; }
.rank-2 { background: #f59e0b; }
.rank-3 { background: #3b82f6; }
</style>
