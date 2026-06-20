<template>
  <div id="LoginDevice">
    <BaseHeader>
      <template v-slot:center>
        <span class="f16">登录设备管理</span>
      </template>
    </BaseHeader>
    <div class="content">
      <!-- Tabs -->
      <div class="tabs">
        <div class="tab" :class="{ active: activeTab === 0 }" @click="activeTab = 0">
          在线设备 ({{ sessions.length }})
        </div>
        <div class="tab" :class="{ active: activeTab === 1 }" @click="activeTab = 1">
          登录记录
        </div>
      </div>

      <Scroll ref="mainScroll">
        <!-- 活跃会话 Tab -->
        <template v-if="activeTab === 0">
          <Loading v-if="sessionsLoading" />
          <template v-else>
            <div class="batch-action" v-if="sessions.length > 1">
              <div class="revoke-all-btn" @click="revokeAllOther">
                <Icon icon="mdi:logout-variant" />
                <span>退出所有其他设备</span>
              </div>
            </div>
            <div class="list">
              <div class="item" :key="i" v-for="(item, i) in sessions">
                <div class="device-row">
                  <div class="device-icon">
                    <Icon :icon="deviceIcon(item)" />
                  </div>
                  <div class="device-info">
                    <div class="top-row">
                      <span class="os-browser">{{ item.deviceName || (item.deviceOs + ' / ' + item.browserName) }}</span>
                      <span class="current-badge" v-if="item.id === currentSessionId">当前设备</span>
                    </div>
                    <div class="detail" v-if="item.screenWidth">
                      <span>分辨率 {{ item.screenWidth }}x{{ item.screenHeight }}</span>
                      <span v-if="item.cpuCores">CPU {{ item.cpuCores }}核</span>
                      <span v-if="item.gpuRenderer" class="gpu">{{ item.gpuRenderer }}</span>
                    </div>
                    <div class="location">
                      <Icon icon="mdi:map-marker-outline" />
                      <span>{{ item.city || '未知地点' }}</span>
                      <span class="ip">IP: {{ item.ip }}</span>
                    </div>
                    <div class="meta">
                      <span class="time">登录于 {{ formatTime(item.loginTime) }}</span>
                      <span class="active-dot" v-if="item.id === currentSessionId">● 在线</span>
                    </div>
                  </div>
                  <div class="device-actions" v-if="item.id !== currentSessionId">
                    <div class="revoke-btn" @click="revokeOne(item)" :class="{ loading: revokingId === item.id }">
                      {{ revokingId === item.id ? '退出中...' : '退出' }}
                    </div>
                  </div>
                </div>
              </div>
              <NoMore v-if="sessions.length > 0" />
              <div class="empty" v-else>
                <p>暂无在线设备</p>
              </div>
            </div>
          </template>
        </template>

        <!-- 登录记录 Tab -->
        <template v-if="activeTab === 1">
          <Loading v-if="historyLoading" />
          <div class="list" v-else>
            <div class="item" :key="i" v-for="(item, i) in historyList">
              <div class="device-row">
                <div class="device-icon">
                  <Icon :icon="deviceIcon(item)" />
                </div>
                <div class="device-info">
                  <div class="os-browser">{{ item.device_os }}{{ item.os_version ? ' ' + item.os_version : '' }} / {{ item.browser_name }}{{ item.browser_version ? ' ' + item.browser_version : '' }}</div>
                  <div class="detail" v-if="item.screen_width">
                    <span>分辨率 {{ item.screen_width }}x{{ item.screen_height }}</span>
                    <span v-if="item.cpu_cores">CPU {{ item.cpu_cores }}核</span>
                  </div>
                  <div class="location">
                    <Icon icon="mdi:map-marker-outline" />
                    <span>{{ formatLoc(item) }}</span>
                    <span class="ip">IP: {{ item.ip }}</span>
                  </div>
                  <div class="meta">
                    <span class="method-tag">{{ item.login_method === 'code' ? '验证码登录' : '密码登录' }}</span>
                    <span class="time">{{ formatTime(item.create_time) }}</span>
                  </div>
                </div>
              </div>
            </div>
            <NoMore v-if="historyList.length > 0" />
            <div class="empty" v-else>
              <p>暂无登录记录</p>
            </div>
          </div>
        </template>
      </Scroll>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Icon } from '@iconify/vue'
import Scroll from '@/components/Scroll.vue'
import { getLoginHistory } from '@/api/user'
import { getSessions, revokeSession, revokeAllOtherSessions } from '@/api/session'
import { _showSimpleConfirmDialog } from '@/utils'

defineOptions({ name: 'LoginDevice' })

const activeTab = ref(0)
const sessionsLoading = ref(false)
const historyLoading = ref(false)
const sessions = ref<any[]>([])
const currentSessionId = ref<number | null>(null)
const historyList = ref<any[]>([])
const revokingId = ref<number | null>(null)

onMounted(() => {
  loadSessions()
  loadHistory()
})

async function loadSessions() {
  sessionsLoading.value = true
  try {
    const res = await getSessions()
    if (res.success) {
      sessions.value = res.data.sessions || []
      currentSessionId.value = res.data.currentSessionId || null
    }
  } catch { sessions.value = [] }
  sessionsLoading.value = false
}

async function loadHistory() {
  historyLoading.value = true
  try {
    const res = await getLoginHistory({ pageNo: 1, pageSize: 50 })
    if (res.success) {
      historyList.value = res.data.list || []
    }
  } catch { historyList.value = [] }
  historyLoading.value = false
}

async function revokeOne(item: any) {
  _showSimpleConfirmDialog(
    `确定要退出「${item.deviceName || item.deviceOs}」的登录吗？`,
    async () => {
      revokingId.value = item.id
      try {
        const res = await revokeSession(item.id)
        if (res.success) {
          sessions.value = sessions.value.filter(s => s.id !== item.id)
        }
      } catch {} finally {
        revokingId.value = null
      }
    },
    null,
    '确定退出',
    '取消'
  )
}

async function revokeAllOther() {
  _showSimpleConfirmDialog(
    '确定要退出所有其他设备的登录吗？当前设备不受影响。',
    async () => {
      try {
        const res = await revokeAllOtherSessions()
        if (res.success) {
          // 重新加载列表
          await loadSessions()
        }
      } catch {}
    },
    null,
    '确定退出',
    '取消'
  )
}

function deviceIcon(item: any): string {
  const os = (item.deviceOs || item.device_os || '').toLowerCase()
  if (os.includes('iphone') || os.includes('ipad')) return 'mdi:cellphone'
  if (os.includes('android')) return 'mdi:android'
  if (os.includes('windows')) return 'mdi:microsoft-windows'
  if (os.includes('mac')) return 'mdi:apple'
  if (os.includes('linux')) return 'mdi:linux'
  return 'mdi:devices'
}

function formatLoc(item: any): string {
  const parts = [item.country, item.region, item.city].filter(Boolean)
  return parts.length > 0 ? parts.join(' ') : '未知地点'
}

function formatTime(time: string): string {
  if (!time) return ''
  return time.replace('T', ' ').substring(0, 19)
}
</script>

<style scoped lang="less">
#LoginDevice {
  position: fixed; left: 0; right: 0; bottom: 0; top: 0;
  color: white; font-size: 14rem;

  .content {
    height: calc(var(--vh, 1vh) * 100 - var(--common-header-height));
    margin-top: var(--common-header-height);
    box-sizing: border-box;
    display: flex;
    flex-direction: column;

    .tabs {
      display: flex;
      padding: 12rem var(--page-padding);
      gap: 24rem;
      font-size: 15rem;
      border-bottom: 1px solid var(--line-color);
      flex-shrink: 0;

      .tab {
        color: var(--second-text-color);
        padding-bottom: 8rem;
        cursor: pointer;

        &.active {
          color: white;
          font-weight: bold;
          border-bottom: 2px solid var(--primary-btn-color);
        }
      }
    }

    .list {
      padding: var(--page-padding);

      .batch-action {
        margin-bottom: 16rem;
        display: flex;
        justify-content: flex-end;

        .revoke-all-btn {
          display: flex;
          align-items: center;
          gap: 6rem;
          padding: 8rem 16rem;
          border-radius: 20rem;
          background: rgba(255, 77, 79, 0.15);
          color: #ff4d4f;
          font-size: 13rem;
          cursor: pointer;

          &:active { opacity: 0.7; }
        }
      }

      .item {
        background: var(--msg-subpage-card-bg);
        border-radius: 8rem;
        padding: 15rem;
        margin-bottom: 12rem;

        .device-row {
          display: flex;
          align-items: flex-start;

          .device-icon {
            font-size: 28rem;
            margin-right: 12rem;
            color: var(--second-text-color);
            flex-shrink: 0;
            margin-top: 2rem;
          }

          .device-info {
            flex: 1;
            min-width: 0;

            .top-row {
              display: flex;
              align-items: center;
              gap: 8rem;
              margin-bottom: 6rem;
            }

            .os-browser {
              font-size: 15rem;
            }

            .current-badge {
              font-size: 10rem;
              padding: 1rem 6rem;
              border-radius: 3rem;
              background: rgba(82, 196, 26, 0.2);
              color: #52c41a;
              flex-shrink: 0;
            }

            .detail {
              font-size: 11rem;
              color: var(--second-text-color);
              margin-bottom: 6rem;
              display: flex;
              flex-wrap: wrap;
              gap: 8rem;

              .gpu {
                max-width: 200rem;
                overflow: hidden;
                text-overflow: ellipsis;
                white-space: nowrap;
              }
            }

            .location {
              font-size: 12rem;
              color: var(--second-text-color);
              margin-bottom: 4rem;
              display: flex;
              align-items: center;
              gap: 4rem;

              .ip { margin-left: 8rem; font-size: 11rem; }
            }

            .meta {
              display: flex;
              justify-content: space-between;
              align-items: center;
              font-size: 12rem;

              .method-tag {
                background: var(--primary-btn-color);
                padding: 2rem 8rem;
                border-radius: 3rem;
                font-size: 10rem;
                color: #fff;
              }

              .time {
                color: var(--second-text-color);
              }

              .active-dot {
                color: #52c41a;
                font-size: 11rem;
              }
            }
          }

          .device-actions {
            flex-shrink: 0;
            margin-left: 12rem;
            display: flex;
            align-items: center;

            .revoke-btn {
              padding: 6rem 14rem;
              border-radius: 16rem;
              border: 1px solid rgba(255, 255, 255, 0.2);
              font-size: 12rem;
              color: var(--second-text-color);
              cursor: pointer;
              white-space: nowrap;

              &:active { background: rgba(255, 77, 79, 0.15); color: #ff4d4f; border-color: #ff4d4f; }

              &.loading { opacity: 0.5; pointer-events: none; }
            }
          }
        }
      }
    }

    .empty {
      text-align: center;
      color: var(--second-text-color);
      padding-top: 100rem;
    }
  }
}
</style>
