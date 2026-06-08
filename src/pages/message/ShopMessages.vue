<template>
  <div class="shop-messages base-page">
    <header class="top-bar">
      <Icon icon="material-symbols-light:arrow-back-ios-new" @click="router.back()" />
      <span class="title">购物消息</span>
      <span class="right-btn" v-if="unreadCount" @click="markAll">全部已读</span>
    </header>

    <div class="msg-list" v-if="messages.length">
      <div
        class="msg-item"
        :class="{ unread: m.isRead === 0 }"
        v-for="m in messages"
        :key="m.id"
        @click="readMsg(m)"
      >
        <div class="icon-wrap">
          <Icon v-if="m.type === 'cart'" icon="ep:shopping-cart" class="type-icon cart" />
          <Icon
            v-else-if="m.type === 'order'"
            icon="lets-icons:order-light"
            class="type-icon order"
          />
          <Icon v-else icon="icon-park-outline:message-emoji" class="type-icon service" />
        </div>
        <div class="info">
          <div class="title">{{ m.title }}</div>
          <div class="content">{{ m.content }}</div>
          <div class="time">{{ formatTime(m.createTime) }}</div>
        </div>
        <div class="dot" v-if="m.isRead === 0"></div>
      </div>
    </div>

    <div class="empty" v-else>
      <Icon icon="icon-park-outline:message-emoji" />
      <span>暂无购物消息</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import { getShopMessages, markShopMessageRead, markAllShopMessagesRead } from '@/api/user'

defineOptions({ name: 'ShopMessages' })

const router = useRouter()
const messages = ref<any[]>([])
const unreadCount = ref(0)

onMounted(async () => {
  try {
    const res: any = await getShopMessages()
    const list = res.data || res || []
    messages.value = list
    unreadCount.value = list.filter((m: any) => m.isRead === 0).length
  } catch {
    /* ignore */
  }
})

async function readMsg(m: any) {
  if (m.isRead === 0) {
    try {
      await markShopMessageRead(m.id)
      m.isRead = 1
      unreadCount.value--
    } catch {
      /* ignore */
    }
  }
}

async function markAll() {
  try {
    await markAllShopMessagesRead()
    messages.value.forEach((m: any) => (m.isRead = 1))
    unreadCount.value = 0
  } catch {
    /* ignore */
  }
}

function formatTime(t: string) {
  if (!t) return ''
  return t.replace('T', ' ').substring(0, 16)
}
</script>

<style scoped lang="less">
.shop-messages {
  min-height: 100vh;
  background: #f5f5f5;
  color: #333;

  .top-bar {
    position: sticky;
    top: 0;
    z-index: 10;
    background: white;
    display: flex;
    align-items: center;
    padding: 0 16rem;
    height: 48rem;
    border-bottom: 1px solid #eee;

    svg {
      font-size: 22rem;
      cursor: pointer;
    }
    .title {
      flex: 1;
      text-align: center;
      font-size: 17rem;
      font-weight: 600;
    }
    .right-btn {
      color: #999;
      font-size: 13rem;
      cursor: pointer;
    }
  }

  .msg-list {
    padding: 0 16rem;
  }

  .msg-item {
    display: flex;
    align-items: flex-start;
    gap: 12rem;
    padding: 14rem 0;
    border-bottom: 1px solid #f0f0f0;
    cursor: pointer;

    .icon-wrap {
      width: 40rem;
      height: 40rem;
      border-radius: 50%;
      background: #f0f0f0;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
      .type-icon {
        font-size: 20rem;
      }
      .cart {
        color: #fe2c55;
      }
      .order {
        color: #ff9500;
      }
      .service {
        color: #3478f6;
      }
    }

    .info {
      flex: 1;
      min-width: 0;
      .title {
        font-size: 15rem;
        font-weight: 600;
        margin-bottom: 4rem;
      }
      .content {
        font-size: 13rem;
        color: #666;
        margin-bottom: 4rem;
      }
      .time {
        font-size: 11rem;
        color: #999;
      }
    }

    .dot {
      width: 8rem;
      height: 8rem;
      border-radius: 50%;
      background: #fe2c55;
      flex-shrink: 0;
      margin-top: 6rem;
    }

    &.unread {
      background: #fff9fa;
      margin: 0 -16rem;
      padding-left: 16rem;
      padding-right: 16rem;
    }
  }

  .empty {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding-top: 40vh;
    color: #999;
    gap: 12rem;
    svg {
      font-size: 48rem;
    }
  }
}
</style>
