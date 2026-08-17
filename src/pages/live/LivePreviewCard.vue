<template>
  <div class="room-card" ref="cardRef" @click="enterRoom">
    <div class="cover">
      <img v-if="coverImage" class="cover-img" :src="coverImage" alt="" />
      <div class="cover-placeholder" v-else></div>
      <div class="live-tag">LIVE</div>
      <div class="viewer-badge">{{ formatCount(room.viewerCount) }}人观看</div>
    </div>
    <div class="info">
      <span class="room-title">{{ room.title || '直播间' }}</span>
      <span class="host-name">@{{ room.host?.nickname || '主播' }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { _checkImgUrl } from '@/utils'
import defaultAvatarPng from '@/assets/img/icon/people-gray.png'

const props = defineProps<{ room: any }>()
const router = useRouter()

const hostAvatar = computed(() => {
  return (
    _checkImgUrl(props.room.host?.avatar_168x168?.url_list?.[0]) ||
    _checkImgUrl(props.room.host?.avatar) ||
    defaultAvatarPng
  )
})

const coverImage = computed(() => _checkImgUrl(props.room.coverUrl) || hostAvatar.value)

function enterRoom() {
  router.push('/live/' + props.room.id)
}

function formatCount(n: number): string {
  if (!n) return '0'
  if (n >= 10000) return (n / 10000).toFixed(1) + '万'
  return String(n)
}
</script>

<style scoped lang="less">
.room-card {
  cursor: pointer;
  border-radius: 10rem;
  overflow: hidden;
}

.cover {
  position: relative;
  aspect-ratio: 3/4;
  background: #222;
  overflow: hidden;
  border-radius: 10rem;

  .cover-img,
  .cover-placeholder {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    z-index: 0;
    object-fit: cover;
  }

  .cover-img {
    filter: blur(8px) brightness(0.72);
    transform: scale(1.04);
  }

  .cover-placeholder {
    background: #1a1a1a;
  }

  .live-tag {
    position: absolute;
    top: 8rem;
    left: 8rem;
    padding: 2rem 6rem;
    background: #fe2c55;
    color: #fff;
    font-size: 10rem;
    font-weight: 700;
    border-radius: 4rem;
    z-index: 2;
  }

  .viewer-badge {
    position: absolute;
    bottom: 8rem;
    right: 8rem;
    padding: 2rem 8rem;
    background: rgba(0, 0, 0, 0.5);
    color: #fff;
    font-size: 10rem;
    border-radius: 10rem;
    z-index: 2;
  }
}

.info {
  padding: 6rem 2rem;

  .room-title {
    color: #fff;
    font-size: 13rem;
    font-weight: 500;
    display: block;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .host-name {
    color: #888;
    font-size: 11rem;
    display: block;
    margin-top: 2rem;
  }
}
</style>
