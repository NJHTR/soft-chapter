<template>
  <div id="LoginDevice">
    <BaseHeader>
      <template v-slot:center>
        <span class="f16">登录设备管理</span>
      </template>
    </BaseHeader>
    <Loading v-if="data.loading" />
    <div class="content" v-else>
      <div class="desc">
        <span>共 {{ data.list.length }} 条登录记录</span>
      </div>
      <Scroll ref="mainScroll">
        <div class="list">
          <div class="item" :key="i" v-for="(item, i) in data.list">
            <div class="device-row">
              <div class="device-icon">
                <Icon :icon="deviceIcon(item)" />
              </div>
              <div class="device-info">
                <div class="os-browser">{{ item.device_os }}{{ item.os_version ? ' ' + item.os_version : '' }} / {{ item.browser_name }}{{ item.browser_version ? ' ' + item.browser_version : '' }}</div>
                <div class="detail">
                  <span v-if="item.screen_width">分辨率 {{ item.screen_width }}x{{ item.screen_height }}</span>
                  <span v-if="item.cpu_cores">CPU {{ item.cpu_cores }}核</span>
                  <span v-if="item.gpu_renderer" class="gpu">{{ item.gpu_renderer }}</span>
                </div>
                <div class="location">
                  <Icon icon="mdi:map-marker-outline" />
                  <span>{{ formatLocation(item) }}</span>
                </div>
                <div class="ip-info">
                  <span>IP: {{ item.ip }}</span>
                  <span v-if="item.isp">（{{ item.isp }}）</span>
                </div>
                <div class="meta">
                  <span class="method-tag">{{ item.login_method === 'code' ? '验证码登录' : '密码登录' }}</span>
                  <span class="time">{{ formatTime(item.create_time) }}</span>
                </div>
              </div>
            </div>
          </div>
          <NoMore v-if="data.list.length > 0" />
          <div class="empty" v-else>
            <p>暂无登录记录</p>
          </div>
        </div>
      </Scroll>
    </div>
  </div>
</template>
<script setup lang="ts">
import { onMounted, reactive } from 'vue'
import Scroll from '@/components/Scroll.vue'
import { getLoginHistory } from '@/api/user'

defineOptions({ name: 'LoginDevice' })

const data = reactive({
  loading: false,
  list: [] as any[],
  pageNo: 1,
  pageSize: 30
})

onMounted(() => {
  loadData()
})

async function loadData() {
  data.loading = true
  try {
    const res = await getLoginHistory({ pageNo: data.pageNo, pageSize: data.pageSize })
    if (res.success) {
      data.list = res.data.list || []
    }
  } finally {
    data.loading = false
  }
}

function deviceIcon(item: any): string {
  const os = (item.device_os || '').toLowerCase()
  if (os.includes('iphone') || os.includes('ipad')) return 'mdi:cellphone'
  if (os.includes('android')) return 'mdi:android'
  if (os.includes('windows')) return 'mdi:microsoft-windows'
  if (os.includes('mac')) return 'mdi:apple'
  if (os.includes('linux')) return 'mdi:linux'
  return 'mdi:devices'
}

function formatLocation(item: any): string {
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
    padding: var(--page-padding);
    padding-top: 0;
    box-sizing: border-box;

    .desc {
      padding: 60rem 0 15rem;
      font-size: 12rem;
      color: var(--second-text-color);
    }

    .list {
      .item {
        background: var(--msg-subpage-card-bg);
        border-radius: 8rem;
        padding: 15rem;
        margin-bottom: 12rem;

        .device-row {
          display: flex;

          .device-icon {
            font-size: 28rem;
            margin-right: 12rem;
            color: var(--second-text-color);
            flex-shrink: 0;
          }

          .device-info {
            flex: 1;
            min-width: 0;

            .os-browser {
              font-size: 15rem;
              margin-bottom: 6rem;
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
            }

            .ip-info {
              font-size: 11rem;
              color: var(--second-text-color);
              margin-bottom: 6rem;
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
