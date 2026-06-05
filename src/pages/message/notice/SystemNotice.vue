<template>
  <div id="SystemNotice">
    <BaseHeader :isFixed="false">
      <template v-slot:center>
        <span class="f16">系统通知</span>
      </template>
      <template v-slot:right>
        <span class="f14" @click="nav('/message/notice-setting', { type: 'SYSTEM' })"
          >通知设置</span
        >
      </template>
    </BaseHeader>
    <Loading v-if="data.loading" />
    <div class="content" v-else>
      <Scroll ref="mainScroll">
        <div class="list">
          <NoMore />
          <!--TODO 超过3行显示全文-->
          <div class="item" :key="i" v-for="(item, i) in data.list" @click="goDetail(item)" :class="{ 'is-read': item.read }">
            <div class="title">
              <span class="type-badge">{{ typeLabel(item.type) }}</span>
              {{ item.title }}
              <div class="ml1r not-read" v-if="!item.read"></div>
            </div>
            <div class="time">{{ item.time }}</div>
            <div class="content-text" v-html="formatContent(item.content)"></div>
          </div>
        </div>
      </Scroll>

      <!--TODO 子页面未做-->
      <div class="hover-dialog left" v-if="data.isShowLeftHover">
        <div class="arrow"></div>
        <div class="l-row no-border" @click="_no">登录设备管理</div>
        <div class="l-row" @click="_no">账号锁定</div>
        <div class="l-row" @click="_no">账号解锁</div>
      </div>

      <div class="hover-dialog right" v-if="data.isShowRightHover">
        <div class="arrow"></div>
        <div class="l-row no-border" @click="_no">常见问题</div>
        <div class="l-row" @click="_no">安全课堂</div>
      </div>

      <BaseMask mode="white" v-if="data.isShowMask" @click="data.isShowMask = false" />

      <div class="options">
        <div class="option" @click="data.isShowLeftHover = !data.isShowLeftHover">
          <img src="../../../assets/img/icon/message/menu-thin.png" alt="" />
          <span>自助工具</span>
        </div>
        <div class="option" @click="_no">
          <span>规则中心</span>
        </div>
        <div class="option" @click="data.isShowRightHover = !data.isShowRightHover">
          <img src="../../../assets/img/icon/message/menu-thin.png" alt="" />
          <span>更多帮助</span>
        </div>
      </div>
    </div>
  </div>
</template>
<script setup lang="ts">
import { onMounted, reactive, watch } from 'vue'
import Scroll from '@/components/Scroll.vue'
import { useNav } from '@/utils/hooks/useNav.js'
import { _no } from '@/utils'
import { useScroll } from '@/utils/hooks/useScroll'
import { getSystemNotices, markNoticeRead, markAllNoticesRead } from '@/api/user'

defineOptions({
  name: 'SystemNotice'
})

const mainScroll = useScroll()
const nav = useNav()
const data = reactive({
  loading: false,
  isShowMask: false,
  isShowLeftHover: false,
  isShowRightHover: false,
  list: [] as any[],
  pageNo: 1,
  pageSize: 20
})

onMounted(() => {
  loadNotices()
})

watch(
  () => data.isShowLeftHover,
  (newVal) => {
    if (newVal) data.isShowMask = true
  }
)

watch(
  () => data.isShowRightHover,
  (newVal) => {
    if (newVal) data.isShowMask = true
  }
)

watch(
  () => data.isShowMask,
  (newVal) => {
    if (!newVal) {
      data.isShowLeftHover = false
      data.isShowRightHover = false
    }
  }
)

async function loadNotices() {
  data.loading = true
  try {
    const res = await getSystemNotices({ pageNo: data.pageNo, pageSize: data.pageSize })
    if (res.success) {
      data.list = (res.data.list || []).map((n: any) => ({
        ...n,
        read: n.is_read === 1,
        time: n.create_time?.replace('T', ' ').substring(0, 19) || ''
      }))
    }
  } finally {
    data.loading = false
  }
}

function goDetail(item: any) {
  if (!item.read) {
    markNoticeRead(item.id).catch(() => {})
    item.read = true
  }
}

function typeLabel(type: string) {
  const map: Record<string, string> = {
    'login': '登录',
    'friend_accepted': '好友',
    'publish_recommend-video': '作品',
    'publish_image': '作品',
    'publish_text': '作品',
    'system': '系统'
  }
  return map[type] || '系统'
}

function formatContent(content: string) {
  if (!content) return ''
  return content.replace(/\n/g, '<br/>')
}
</script>

<style scoped lang="less">
#SystemNotice {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  top: 0;
  color: white;
  font-size: 14rem;

  .scroll {
    height: calc(var(--vh, 1vh) * 100 - var(--common-header-height) - var(--common-header-height));
  }

  .content {
    height: calc(var(--vh, 1vh) * 100 - var(--common-header-height));
    padding: var(--page-padding);
    padding-top: 0;
    box-sizing: border-box;

    .list {
      .item {
        padding: var(--page-padding);
        background: var(--msg-subpage-card-bg);
        border-radius: 5rem;
        margin-bottom: 20rem;

        .title {
          display: flex;
          align-items: center;
          font-size: 16rem;
          margin-bottom: 10rem;

          .type-badge {
            display: inline-block;
            padding: 1rem 8rem;
            border-radius: 4rem;
            font-size: 11rem;
            margin-right: 8rem;
            background: var(--primary-btn-color);
            color: #fff;
            flex-shrink: 0;
          }
        }

        .item.is-read {
          opacity: 0.55;
        }

        .time {
          font-size: 12rem;
          color: var(--second-text-color);
          margin-bottom: 20rem;
        }

        .content-text {
          margin-bottom: 30rem;
        }

        .look-detail {
          border-top: 1px solid var(--line-color2);
          padding-top: var(--page-padding);
          color: var(--second-text-color);
          display: flex;
          justify-content: space-between;
          align-items: center;
        }
      }
    }

    .options {
      border-top: 1px solid var(--line-color);
      height: 60rem;
      display: flex;
      align-items: center;

      .option {
        width: 33%;
        display: flex;
        align-items: center;
        justify-content: center;
        border-right: 1px solid var(--line-color);

        &:nth-last-child(1) {
          border: none;
        }

        img {
          width: 10rem;
          height: 15rem;
          margin-right: 4rem;
        }
      }
    }

    .hover-dialog {
      z-index: 9;
      position: fixed;
      bottom: 80rem;
      border-radius: 6rem;
      background: rgba(0, 0, 0, 0.9);
      font-size: 12rem;

      &.left {
        left: 0;
      }

      &.right {
        right: 0;
      }

      .arrow {
        width: 0;
        height: 0;
        border: 7rem solid transparent;
        border-top: 7rem solid rgba(0, 0, 0, 0.9);
        position: absolute;
        right: 50rem;
        bottom: -14rem;
      }

      .l-row {
        width: 120rem;
        height: 40rem;
        display: flex;
        align-items: center;
        justify-content: center;
        //padding: 10rem 22rem;
        border-top: 1px solid #2c2c2c;
        text-align: center;

        &.no-border {
          border: none;
        }
      }
    }
  }
}
</style>
