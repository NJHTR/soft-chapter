<template>
  <div id="AllMessage">
    <BaseHeader :is-fixed="false">
      <template v-slot:center>
        <div class="center" @click="data.isShowType = !data.isShowType">
          <span class="f16">{{ showTypeText }}</span>
          <img
            :class="{ show: data.isShowType }"
            src="@/assets/img/icon/arrow-up-white.png"
            alt=""
          />
        </div>
      </template>
    </BaseHeader>
    <transition name="fade">
      <div class="type-dialog" v-if="data.isShowType">
        <div class="dialog-content">
          <div class="row" @click="toggleShowType(1)">
            <div class="left">
              <img src="@/assets/img/icon/message/done-gray.png" alt="" />
              <span>全部消息</span>
            </div>
          </div>
          <div class="row" @click="toggleShowType(2)">
            <div class="left">
              <img src="@/assets/img/icon/message/like-gray.png" alt="" />
              <span>赞</span>
            </div>
          </div>
          <div class="row" @click="toggleShowType(3)">
            <div class="left">
              <img src="@/assets/img/icon/message/comment-gray.png" alt="" />
              <span>评论</span>
            </div>
          </div>
          <div class="row" @click="toggleShowType(4)">
            <div class="left">
              <img src="@/assets/img/icon/collect-gray.png" alt="" />
              <span>收藏</span>
            </div>
          </div>
          <div class="row" @click="toggleShowType(5)">
            <div class="left">
              <img src="@/assets/img/icon/message/call-gray.png" alt="" />
              <span>@我的</span>
            </div>
          </div>
        </div>
        <div class="mask" @click="data.isShowType = false"></div>
      </div>
    </transition>
    <Scroll ref="mainScroll">
      <div class="messages">
        <div
          class="message"
          v-for="(item, i) in data.notifications"
          :key="i"
          @click="handleNotificationClick(item)"
        >
          <div class="left">
            <img v-lazy="_checkImgUrl(item.from_user?.avatar_168x168?.url_list?.[0])" alt="" class="avatar" />
          </div>
          <div class="right">
            <div class="desc">
              <div class="name">{{ item.from_user?.nickname || '用户' }}</div>
              <div class="bottom">
                <div class="desc-content">{{ item.content }}</div>
                <div class="time">{{ formatTime(item.create_time) }}</div>
              </div>
            </div>
          </div>
        </div>
        <div class="no-data" v-if="!data.loading && !data.notifications.length">
          暂无消息
        </div>
      </div>
    </Scroll>
  </div>
</template>
<script setup lang="ts">
import Scroll from '@/components/Scroll.vue'
import { useBaseStore } from '@/store/pinia'
import { _checkImgUrl } from '@/utils'

import { computed, onActivated, onMounted, onUnmounted, reactive } from 'vue'
import { useNav } from '@/utils/hooks/useNav.js'
import { getNotifications, markNotificationRead } from '@/api/message'
import bus from '@/utils/bus'

defineOptions({
  name: 'AllMessage'
})

const store = useBaseStore()
const nav = useNav()
const data = reactive({
  isShowType: false,
  selectShowType: 1,
  notifications: [] as any[],
  loading: false
})

// 前端筛选索引 → 后端 NotificationVO type 常量映射
// 后端: 1关注 2点赞 3评论 4收藏 5@提及
const NOTIFICATION_TYPE: Record<number, number | undefined> = {
  1: undefined, // 全部
  2: 2,         // 赞
  3: 3,         // 评论
  4: 4,         // 收藏
  5: 5          // @我的
}

const showTypeText = computed(() => {
  switch (data.selectShowType) {
    case 1:
      return '全部消息'
    case 2:
      return '赞'
    case 3:
      return '评论'
    case 4:
      return '收藏'
    case 5:
      return '@我的'
    default:
      return ''
  }
})

onMounted(() => {
  loadNotifications()
  markNotificationRead({}).catch(() => {})
  bus.on('NEW_NOTIFICATION', loadNotifications)
})

// keep-alive 激活时刷新并标记已读
onActivated(() => {
  loadNotifications()
  markNotificationRead({}).catch(() => {})
})

onUnmounted(() => {
  bus.off('NEW_NOTIFICATION', loadNotifications)
})

async function loadNotifications() {
  if (!store.userinfo?.uid) return
  data.loading = true
  try {
    const typeFilter = NOTIFICATION_TYPE[data.selectShowType]
    const res = await getNotifications({ type: typeFilter })
    if (res.success && res.data) {
      data.notifications = res.data
    }
  } catch { /* ignore */ }
  data.loading = false
}

async function toggleShowType(index: number) {
  data.selectShowType = index
  data.isShowType = false
  await loadNotifications()
  // 标记此类通知为已读
  const type = NOTIFICATION_TYPE[index]
  try { await markNotificationRead({ type }) } catch { /* ignore */ }
}

function formatTime(timeStr: string) {
  if (!timeStr) return ''
  try {
    const date = new Date(timeStr)
    return `${date.getMonth() + 1}-${date.getDate()}`
  } catch { return '' }
}

function handleNotificationClick(item: any) {
  // 关注 → 跳转用户主页
  if (item.type === 1 && item.from_user_id) {
    nav('/people/user-home/' + item.from_user_id)
    return
  }
  // 点赞/收藏 → 跳转视频
  if ((item.type === 2 || item.type === 4) && item.video_id) {
    nav('/slide', { id: item.video_id })
    return
  }
  // 评论/@提及 → 跳转视频并打开评论区（滚动到对应评论）
  if ((item.type === 3 || item.type === 5) && item.video_id) {
    nav('/slide', { id: item.video_id, openComments: '1', commentId: item.comment_id })
    return
  }
}
</script>

<style scoped lang="less">
#AllMessage {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  top: 0;
  background: var(--color-message);
  overflow: auto;
  color: white;
  font-size: 14rem;
  display: flex;
  flex-direction: column;

  .center {
    display: flex;
    align-items: center;

    img {
      width: 15rem;
      transform: rotate(180deg);
      transition: all 0.3s;
      margin-left: 3rem;
    }

    .show {
      transform: rotate(0deg);
    }
  }

  .type-dialog {
    z-index: 9;
    position: fixed;
    height: calc(var(--vh, 1vh) * 100);
    width: 100vw;
    margin-top: var(--common-header-height);

    .dialog-content {
      border-radius: 0 0 4rem 4rem;
      background: var(--color-message);
      overflow: hidden;

      img {
        width: 18rem;
      }
    }

    .mask {
      z-index: 8;
      position: fixed;
      height: calc(var(--vh, 1vh) * 100);
      width: 100vw;
      background: var(--mask-dark);
    }
  }

  .scroll {
    flex: 1;
    padding: 0 10rem;
  }

  .messages {
    .message {
      margin-bottom: 20rem;
      display: flex;
      gap: 10rem;

      &:first-child {
        margin-top: 20rem;
      }

      .left {
        display: flex;
        align-items: center;
        position: relative;

        .avatar {
          width: 58rem;
          height: 58rem;
          border-radius: 50%;
        }
      }

      .right {
        flex: 1;
        display: flex;
        justify-content: space-between;

        .desc {
          display: flex;
          flex-direction: column;
          justify-content: center;
          gap: 5rem;
          color: white;
          font-size: 16rem;

          .bottom {
            display: flex;
            align-items: center;
            font-size: 13rem;
            color: lightgrey;

            .time {
              font-size: 12rem;
              margin-left: 10rem;
              color: var(--second-text-color);
            }
          }
        }

        .poster {
          margin-left: 10rem;
          width: 58rem;
          height: 70rem;
          object-fit: cover;
          border-radius: 3rem;
        }
      }
    }

    .look-all {
      font-size: 12rem;
      color: var(--second-text-color);
      display: flex;
      justify-content: center;
      align-items: center;

      .close {
        margin-left: 10rem;
        transform: rotate(270deg) !important;
        width: 12rem;
        height: 12rem;
      }
    }

    .no-data {
      text-align: center;
      color: var(--second-text-color);
      padding: 100rem 0;
      font-size: 14rem;
    }
  }
}
</style>
