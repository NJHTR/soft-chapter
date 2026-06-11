<template>
  <div class="live-list-page">
    <BaseHeader mode="dark" backImg="back" @back="$router.back()">
      <template v-slot:center><span class="header-title">直播</span></template>
    </BaseHeader>

    <div class="body">
      <!-- 关注的直播（横向滚动） -->
      <div class="section" v-if="followingLives.length">
        <div class="section-title">关注的主播</div>
        <div class="following-scroll">
          <div
            class="following-card"
            v-for="item in followingLives"
            :key="item.id"
            @click="enterRoom(item.id)"
          >
            <div class="avatar-wrap">
              <img
                class="avatar"
                :src="
                  _checkImgUrl(item.host?.avatar_168x168?.url_list?.[0]) ||
                  _checkImgUrl(item.host?.avatar) ||
                  defaultAvatarPng
                "
              />
              <div class="live-dot"></div>
            </div>
            <span class="name">{{ item.host?.nickname || '主播' }}</span>
            <span class="count">{{ item.viewerCount }}人</span>
          </div>
        </div>
      </div>

      <!-- 全部直播间 -->
      <div class="section">
        <div class="section-title">推荐直播</div>
        <div class="room-grid">
          <LivePreviewCard v-for="item in roomList" :key="item.id" :room="item" />
        </div>

        <div v-if="loading" class="loading">加载中...</div>
        <div v-if="!loading && roomList.length === 0" class="empty">暂无直播</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getLiveRooms } from '@/api/live'
import { _checkImgUrl } from '@/utils'
import BaseHeader from '@/components/BaseHeader.vue'
import LivePreviewCard from '@/pages/live/LivePreviewCard.vue'
import defaultAvatarPng from '@/assets/img/icon/people-gray.png'

const router = useRouter()
const roomList = ref<any[]>([])
const followingLives = ref<any[]>([])
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    const res: any = await getLiveRooms({ pageNo: 1, pageSize: 20 })
    if (res.success && res.data?.list) {
      roomList.value = res.data.list
    }
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
})

function enterRoom(id: number) {
  router.push('/live/' + id)
}
</script>

<style scoped lang="less">
.live-list-page {
  width: 100%;
  height: 100vh;
  background: #111;
  display: flex;
  flex-direction: column;
}

.header-title {
  font-size: 16rem;
  font-weight: 600;
  color: #fff;
}

.body {
  flex: 1;
  overflow-y: auto;
  padding: 0 12rem;
  padding-bottom: 20rem;
}

// ============ 关注的主播 ============
.section {
  margin-top: 16rem;
}

.section-title {
  color: #fff;
  font-size: 15rem;
  font-weight: 600;
  margin-bottom: 10rem;
}

.following-scroll {
  display: flex;
  gap: 12rem;
  overflow-x: auto;
  padding-bottom: 4rem;
  &::-webkit-scrollbar {
    display: none;
  }
}

.following-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4rem;
  flex-shrink: 0;
  cursor: pointer;

  .avatar-wrap {
    position: relative;
    width: 52rem;
    height: 52rem;

    .avatar {
      width: 100%;
      height: 100%;
      border-radius: 50%;
      object-fit: cover;
      border: 2rem solid #fe2c55;
    }

    .live-dot {
      position: absolute;
      bottom: 0;
      right: 0;
      width: 14rem;
      height: 14rem;
      background: #fe2c55;
      border-radius: 50%;
      border: 2rem solid #111;
    }
  }

  .name {
    color: #fff;
    font-size: 11rem;
    max-width: 52rem;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .count {
    color: #999;
    font-size: 10rem;
  }
}

// ============ 直播间网格 ============
.room-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10rem;
}

.loading,
.empty {
  text-align: center;
  color: #666;
  font-size: 13rem;
  padding: 40rem 0;
}
</style>
