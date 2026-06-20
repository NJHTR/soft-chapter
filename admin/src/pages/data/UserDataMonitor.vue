<template>
  <div class="user-monitor">
    <h2 class="page-title">用户数据监控</h2>

    <div class="stat-grid">
      <div class="stat-card">
        <div class="stat-value" style="color:#10b981;">{{ overview.onlineUsers }}</div>
        <div class="stat-label">当前在线</div>
      </div>
      <div class="stat-card">
        <div class="stat-value" style="color:#3b82f6;">{{ overview.todayActiveUsers }}</div>
        <div class="stat-label">今日活跃用户</div>
      </div>
      <div class="stat-card">
        <div class="stat-value" style="color:#f59e0b;">{{ fmtNum(overview.todaySearches) }}</div>
        <div class="stat-label">今日搜索次数</div>
      </div>
      <div class="stat-card">
        <div class="stat-value" style="color:#8b5cf6;">{{ fmtNum(overview.totalUsers) }}</div>
        <div class="stat-label">总注册用户</div>
      </div>
    </div>

    <div class="card" style="margin-bottom:20px;">
      <h3 class="chart-title">
        用户活跃时段
        <select v-model="flowHours" @change="loadActivityFlow" class="chart-select">
          <option :value="6">近6小时</option>
          <option :value="12">近12小时</option>
          <option :value="24">近24小时</option>
          <option :value="48">近48小时</option>
        </select>
      </h3>
      <div ref="activityFlowChart" class="chart-box"></div>
    </div>

    <div class="card">
      <h3 class="chart-title">实时在线用户</h3>
      <div v-if="onlineData" class="online-detail">
        <div class="online-stat"><span>总会话数</span><strong>{{ onlineCount }}</strong></div>
        <div class="online-stat"><span>设备分布</span><strong>{{ deviceSummary }}</strong></div>
      </div>
      <div v-if="onlineData?.recentSessions?.length" style="margin-top:16px;">
        <h5 style="font-size:12px;color:#888;margin-bottom:8px;">最近活跃会话</h5>
        <table class="data-table">
          <thead>
            <tr><th>用户ID</th><th>设备</th><th>城市</th><th>登录时间</th><th>最近活跃</th></tr>
          </thead>
          <tbody>
            <tr v-for="s in onlineData.recentSessions" :key="s.userId + '-' + s.loginTime">
              <td>{{ s.userId || '-' }}</td>
              <td>{{ s.deviceName || '-' }}</td>
              <td>{{ s.city || '-' }}</td>
              <td>{{ s.loginTime || '-' }}</td>
              <td>{{ s.lastActiveTime || '-' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { getAnalyticsOverview, getUserActivityFlow, getOnlineUsers } from '@/api/admin'

const overview = reactive({ onlineUsers: 0, todayActiveUsers: 0, todaySearches: 0, totalUsers: 0 })
const onlineData = ref<any>(null)
const flowHours = ref(24)
const activityFlowChart = ref<HTMLDivElement | null>(null)

let chart: any = null

const onlineCount = computed(() => onlineData.value?.count ?? 0)
const deviceSummary = computed(() => {
  const bd = onlineData.value?.deviceBreakdown
  if (!bd) return '-'
  return Object.entries(bd).map(([k, v]) => `${k}:${v}`).join(', ')
})

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

async function loadActivityFlow() {
  try {
    const res: any = await getUserActivityFlow({ hours: flowHours.value })
    if (!res.success || !activityFlowChart.value) return
    const data = res.data || []
    if (chart) chart.dispose()
    chart = echarts.init(activityFlowChart.value)
    chart.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: 40, right: 20, top: 10, bottom: 60 },
      xAxis: { type: 'category', data: data.map((d: any) => d.time), axisLabel: { fontSize: 10, rotate: 30 } },
      yAxis: { type: 'value', minInterval: 1 },
      series: [{
        type: 'line', data: data.map((d: any) => d.count),
        smooth: true, symbol: 'none', step: 'end',
        lineStyle: { color: '#10b981', width: 2 },
        areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(16,185,129,0.2)' }, { offset: 1, color: 'rgba(16,185,129,0.02)' }
        ])}
      }]
    })
  } catch (e) { console.error(e) }
}

async function loadOnlineUsers() {
  try {
    const res: any = await getOnlineUsers()
    if (res.success) onlineData.value = res.data
  } catch (e) { console.error(e) }
}

onMounted(async () => {
  await Promise.all([loadOverview(), loadOnlineUsers()])
  await nextTick()
  loadActivityFlow()
})
onUnmounted(() => { if (chart) chart.dispose() })
</script>

<style scoped>
.user-monitor { max-width: 1400px; }
.chart-title {
  font-size: 14px; font-weight: 600; color: #333;
  margin-bottom: 12px; display: flex; align-items: center; justify-content: space-between;
}
.chart-select { font-size: 12px; padding: 4px 8px; border: 1px solid #ddd; border-radius: 4px; outline: none; color: #666; }
.chart-box { width: 100%; height: 300px; }
.online-detail { display: flex; gap: 40px; margin-bottom: 12px; }
.online-stat { display: flex; flex-direction: column; gap: 4px; }
.online-stat span { font-size: 12px; color: #999; }
.online-stat strong { font-size: 24px; color: #333; }
</style>
