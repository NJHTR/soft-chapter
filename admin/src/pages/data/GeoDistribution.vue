<template>
  <div class="geo-page">
    <h2 class="page-title">地域分布</h2>

    <div class="card">
      <h3 class="chart-title">用户地域分布 TOP15</h3>
      <div ref="geoChart" class="chart-box"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import { getGeoData } from '@/api/admin'

const geoChart = ref<HTMLDivElement | null>(null)
let chart: any = null

async function loadGeoData() {
  try {
    const res: any = await getGeoData()
    if (!res.success || !geoChart.value) return
    const data = (res.data || []).slice(0, 15).reverse()
    chart = echarts.init(geoChart.value)
    chart.setOption({
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: 80, right: 60, top: 10, bottom: 20 },
      xAxis: { type: 'value' },
      yAxis: {
        type: 'category', data: data.map((d: any) => d.name),
        axisLabel: { fontSize: 11 }, inverse: true
      },
      series: [{
        type: 'bar', data: data.map((d: any) => d.value),
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: '#667eea' }, { offset: 1, color: '#764ba2' }
          ]),
          borderRadius: [0, 4, 4, 0]
        },
        barMaxWidth: 20,
        label: { show: true, position: 'right', fontSize: 11 }
      }]
    })
  } catch (e) { console.error(e) }
}

onMounted(() => loadGeoData())
onUnmounted(() => { if (chart) chart.dispose() })
</script>

<style scoped>
.geo-page { max-width: 1200px; }
.chart-title { font-size: 14px; font-weight: 600; color: #333; margin-bottom: 12px; }
.chart-box { width: 100%; height: 450px; }
</style>
