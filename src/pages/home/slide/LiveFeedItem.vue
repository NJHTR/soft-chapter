<template>
  <div class="live-feed-item">
    <!-- Cards use a static cover; playback starts only after entering the room. -->
    <img class="cover-bg" :src="coverImage" alt="" />

    <!-- 信息覆盖层 -->
    <div class="overlay">
      <!-- 左上：主播信息 -->
      <div class="top-left">
        <img
          class="avatar"
          :src="
            _checkImgUrl(room.host?.avatar_168x168?.url_list?.[0]) ||
            _checkImgUrl(room.host?.avatar) ||
            defaultAvatarPng
          "
        />
        <div class="host-detail">
          <span class="host-name">{{ room.host?.nickname || '主播' }}</span>
          <span class="host-title">{{ room.title || '直播间' }}</span>
        </div>
      </div>

      <!-- 右上：观看人数 + LIVE标签 -->
      <div class="top-right">
        <div class="live-tag">LIVE</div>
        <div class="viewer-count">{{ formatCount(room.viewerCount) }}人观看</div>
      </div>

      <!-- 底部提示 -->
      <div class="bottom-hint">
        <span>点击进入直播间</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { _checkImgUrl } from '@/utils'
import defaultAvatarPng from '@/assets/img/icon/people-gray.png'

const props = defineProps<{
  room: any
  isActive: boolean
}>()

const coverImage = computed(
  () =>
    _checkImgUrl(props.room?.coverUrl) ||
    _checkImgUrl(props.room?.host?.avatar_168x168?.url_list?.[0]) ||
    _checkImgUrl(props.room?.host?.avatar) ||
    defaultAvatarPng
)

function formatCount(n: number): string {
  if (!n) return '0'
  if (n >= 10000) return (n / 10000).toFixed(1) + '万'
  return String(n)
}
</script>

<style scoped lang="less">
.live-feed-item {
  width: 100%;
  height: 100%;
  position: relative;
  background: #000;
  overflow: hidden;
}

.cover-bg {
  width: 100%;
  height: 100%;
  object-fit: cover;
  filter: blur(8px) brightness(0.55);
  transform: scale(1.04);
}

.overlay {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

// 左上
.top-left {
  position: absolute;
  top: max(50rem, env(safe-area-inset-top));
  left: 12rem;
  display: flex;
  align-items: center;
  gap: 8rem;
  padding: 5rem 12rem 5rem 5rem;
  background: rgba(0, 0, 0, 0.35);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-radius: 24rem;

  .avatar {
    width: 34rem;
    height: 34rem;
    border-radius: 50%;
    object-fit: cover;
    border: 1.5rem solid rgba(255, 255, 255, 0.3);
  }

  .host-detail {
    display: flex;
    flex-direction: column;
    gap: 1rem;
  }

  .host-name {
    color: #fff;
    font-size: 13rem;
    font-weight: 600;
    max-width: 100rem;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .host-title {
    color: rgba(255, 255, 255, 0.7);
    font-size: 10rem;
    max-width: 100rem;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

// 右上
.top-right {
  position: absolute;
  top: max(50rem, env(safe-area-inset-top));
  right: 12rem;
  display: flex;
  align-items: center;
  gap: 8rem;

  .live-tag {
    padding: 3rem 8rem;
    background: #fe2c55;
    color: #fff;
    font-size: 10rem;
    font-weight: 700;
    border-radius: 4rem;
  }

  .viewer-count {
    color: #fff;
    font-size: 11rem;
    padding: 4rem 10rem;
    background: rgba(0, 0, 0, 0.35);
    backdrop-filter: blur(12px);
    -webkit-backdrop-filter: blur(12px);
    border-radius: 24rem;
  }
}

// 底部提示
.bottom-hint {
  position: absolute;
  bottom: 60rem;
  left: 50%;
  transform: translateX(-50%);
  padding: 8rem 20rem;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border-radius: 20rem;
  color: rgba(255, 255, 255, 0.8);
  font-size: 12rem;
}
</style>
