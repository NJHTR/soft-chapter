<template>
  <div class="ai-chat-page">
    <header class="top-bar">
      <Icon icon="material-symbols-light:arrow-back-ios-new" @click="router.back()" />
      <img v-if="productCover" :src="productCover" class="product-thumb" />
      <div class="title">
        <div class="main">智能客服</div>
        <div class="sub" v-if="productName">{{ productName }}</div>
      </div>
    </header>

    <div class="chat-body" ref="chatBody">
      <div class="msg-wrapper" v-for="(msg, i) in messages" :key="i">
        <div class="msg-item" :class="msg.role">
          <div class="bubble" v-if="msg.role === 'user'">{{ msg.content }}</div>
          <div class="bubble assistant-bubble" v-else v-html="renderMd(msg.content)"></div>
        </div>
      </div>
      <div class="msg-item assistant" v-if="loading">
        <div class="bubble typing"><span>.</span><span>.</span><span>.</span></div>
      </div>
    </div>

    <div class="input-bar">
      <input
        v-model="input"
        class="input"
        placeholder="输入您的问题..."
        @keyup.enter="send"
        :disabled="loading"
      />
      <div class="send-btn" @click="send" :class="{ disabled: !input.trim() || loading }">发送</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { request } from '@/utils/request'
import { Icon } from '@iconify/vue'

defineOptions({ name: 'AIChat' })

const router = useRouter()
const route = useRoute()

const productId = ref(Number(route.query.product_id) || 0)
const productName = ref((route.query.product_name as string) || '')
const productCover = ref((route.query.product_cover as string) || '')
const productInfo = ref((route.query.product_info as string) || '')
const input = ref('')
const loading = ref(false)
const chatBody = ref<HTMLElement>()
const messages = ref<{ role: string; content: string }[]>([])
const initialLoading = ref(false)

onMounted(async () => {
  // 先从服务器加载历史
  if (productId.value) {
    initialLoading.value = true
    try {
      const res: any = await request({
        url: '/ai/history',
        method: 'get',
        params: { product_id: productId.value }
      })
      const history = res.data || res || []
      if (Array.isArray(history) && history.length > 0) {
        messages.value = history
        return
      }
    } catch {
      /* 忽略加载失败 */
    } finally {
      initialLoading.value = false
    }
  }
  // 没有历史，显示欢迎语
  messages.value.push({
    role: 'assistant',
    content: productName.value
      ? `您好！我是SeekFlow智能客服，关于「${productName.value}」有什么可以帮您的吗？`
      : '您好！我是SeekFlow智能客服，有什么可以帮您的吗？'
  })
})

function renderMd(text: string): string {
  if (!text) return ''
  // 简单 Markdown → HTML：加粗、斜体、换行
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.+?)\*/g, '<em>$1</em>')
    .replace(/\n/g, '<br>')
}

async function send() {
  const text = input.value.trim()
  if (!text || loading.value) return
  input.value = ''

  messages.value.push({ role: 'user', content: text })
  await scrollBottom()
  loading.value = true

  try {
    const history = messages.value.slice(0, -1).map((m) => ({ role: m.role, content: m.content }))
    const res: any = await request({
      url: '/ai/chat',
      method: 'post',
      data: { message: text, product_id: productId.value, history, product_info: productInfo.value }
    })
    const reply = res.data?.reply || res.reply || '抱歉，我暂时无法回答这个问题。'
    messages.value.push({ role: 'assistant', content: reply })
  } catch {
    messages.value.push({ role: 'assistant', content: '网络异常，请稍后重试。' })
  }
  loading.value = false
  await scrollBottom()
}

async function scrollBottom() {
  await nextTick()
  if (chatBody.value) {
    chatBody.value.scrollTop = chatBody.value.scrollHeight
  }
}
</script>

<style scoped lang="less">
.ai-chat-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f5f5f5;

  .top-bar {
    display: flex;
    align-items: center;
    gap: 10rem;
    padding: 0 14rem;
    height: 48rem;
    background: white;
    border-bottom: 1px solid #eee;
    flex-shrink: 0;

    svg {
      font-size: 22rem;
      cursor: pointer;
      flex-shrink: 0;
    }
    .product-thumb {
      width: 32rem;
      height: 32rem;
      border-radius: 6rem;
      object-fit: cover;
      flex-shrink: 0;
    }
    .title {
      flex: 1;
      min-width: 0;
      .main {
        font-size: 16rem;
        font-weight: 600;
      }
      .sub {
        font-size: 11rem;
        color: #999;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
    }
  }

  .chat-body {
    flex: 1;
    overflow-y: auto;
    padding: 14rem;
    padding-bottom: 20rem;

    .msg-wrapper {
      margin-bottom: 12rem;
    }
    .msg-item {
      display: flex;
      &.user {
        justify-content: flex-end;
      }
      &.assistant {
        justify-content: flex-start;
      }
    }

    .bubble {
      max-width: 75%;
      padding: 10rem 14rem;
      border-radius: 14rem;
      font-size: 14rem;
      line-height: 1.5;
      word-break: break-word;
    }

    .user .bubble {
      background: #fe2c55;
      color: white;
      border-bottom-right-radius: 4rem;
    }

    .assistant .bubble {
      background: white;
      color: #333;
      border-bottom-left-radius: 4rem;
    }

    .typing span {
      display: inline-block;
      animation: blink 1.4s infinite both;
      font-size: 20rem;
      line-height: 1;
      margin: 0 1rem;
      color: #999;
      &:nth-child(2) {
        animation-delay: 0.2s;
      }
      &:nth-child(3) {
        animation-delay: 0.4s;
      }
    }
  }

  .input-bar {
    display: flex;
    align-items: center;
    gap: 10rem;
    padding: 8rem 14rem;
    background: white;
    border-top: 1px solid #eee;
    flex-shrink: 0;

    .input {
      flex: 1;
      height: 38rem;
      border: 1px solid #eee;
      border-radius: 20rem;
      padding: 0 14rem;
      font-size: 14rem;
      outline: none;
      background: #f5f5f5;
      &:focus {
        border-color: #fe2c55;
      }
    }

    .send-btn {
      padding: 10rem 18rem;
      border-radius: 20rem;
      background: #fe2c55;
      color: white;
      font-size: 14rem;
      font-weight: 600;
      cursor: pointer;
      flex-shrink: 0;
      &.disabled {
        opacity: 0.5;
        pointer-events: none;
      }
    }
  }
}

@keyframes blink {
  0%,
  80%,
  100% {
    opacity: 0;
  }
  40% {
    opacity: 1;
  }
}
</style>
