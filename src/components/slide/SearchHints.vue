<template>
  <transition name="hint-fade">
    <div class="search-hints" :class="{ inline: inline }" v-if="visible && hints.length > 0 && !dismissed" @click.stop>
      <div class="hint-label">猜你想搜</div>
      <div class="hint-chips">
        <div
          class="hint-chip"
          v-for="(hint, idx) in hints"
          :key="idx"
          @click="goSearch(hint.text)"
        >
          <img class="hint-icon" src="../../assets/img/icon/search-gray.png" alt="" />
          <span>{{ hint.text }}</span>
        </div>
      </div>
    </div>
  </transition>
</template>

<script setup lang="ts">
import { ref, watch, onUnmounted } from 'vue'
import { videoSearchHints } from '@/api/videos'

defineOptions({
  name: 'SearchHints'
})

const props = defineProps<{
  videoId: number | string
  visible: boolean
  inline?: boolean
  onSearch?: (keyword: string) => void
}>()
const hints = ref<any[]>([])
const dismissed = ref(false)
let dismissTimer: any = null
let fetchTimer: any = null

async function fetchHints() {
  if (!props.videoId) return
  try {
    const res = await videoSearchHints(props.videoId)
    if (res.success && Array.isArray(res.data?.hints)) {
      hints.value = res.data.hints.slice(0, 3)
      if (hints.value.length > 0) {
        dismissed.value = false
        // 5秒后自动消失
        if (dismissTimer) clearTimeout(dismissTimer)
        dismissTimer = setTimeout(() => {
          dismissed.value = true
        }, 5000)
      }
    }
  } catch {
    hints.value = []
  }
}

function goSearch(keyword: string) {
  dismissed.value = true
  props.onSearch?.(keyword)
}

watch(
  () => [props.videoId, props.visible],
  ([vid, vis]) => {
    if (vid && vis) {
      // 视频开始播放后延迟1秒再请求，避免阻塞视频加载
      if (fetchTimer) clearTimeout(fetchTimer)
      fetchTimer = setTimeout(fetchHints, 1000)
    } else {
      hints.value = []
    }
  },
  { immediate: true }
)

onUnmounted(() => {
  if (dismissTimer) clearTimeout(dismissTimer)
  if (fetchTimer) clearTimeout(fetchTimer)
})
</script>

<style scoped lang="less">
.search-hints {
  position: absolute;
  top: 60rem;
  left: 16rem;
  right: 16rem;
  z-index: 5;
  padding: 10rem 12rem;
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(10px);
  border-radius: 8rem;
  border: 1px solid rgba(255, 255, 255, 0.1);

  .hint-label {
    font-size: 11rem;
    color: rgba(255, 255, 255, 0.5);
    margin-bottom: 8rem;
  }

  .hint-chips {
    display: flex;
    flex-wrap: wrap;
    gap: 8rem;

    .hint-chip {
      display: flex;
      align-items: center;
      gap: 4rem;
      padding: 5rem 10rem;
      background: rgba(255, 255, 255, 0.12);
      border-radius: 16rem;
      font-size: 12rem;
      color: white;
      cursor: pointer;
      white-space: nowrap;
      transition: background 0.2s;

      &:active {
        background: rgba(255, 255, 255, 0.25);
      }

      .hint-icon {
        width: 12rem;
        height: 12rem;
        opacity: 0.6;
      }
    }
  }
}

/* 底部 inline 模式 — 跟在描述文字后面 */
.search-hints.inline {
  position: relative;
  top: auto;
  left: auto;
  right: auto;
  margin-top: 10rem;
}

.hint-fade-enter-active {
  transition: opacity 0.3s ease;
}
.hint-fade-leave-active {
  transition: opacity 0.3s ease;
}
.hint-fade-enter-from,
.hint-fade-leave-to {
  opacity: 0;
}
</style>
