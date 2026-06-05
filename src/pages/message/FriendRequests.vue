<template>
  <div id="FriendRequests">
    <BaseHeader>
      <template v-slot:center>
        <span class="f16">朋友申请</span>
      </template>
    </BaseHeader>
    <div class="content">
      <Loading v-if="data.loading" />
      <Scroll v-else>
        <div class="list">
          <div
            class="item"
            :key="i"
            v-for="(item, i) in data.requests"
          >
            <img
              class="avatar"
              :src="_checkImgUrl(item.avatar_168x168?.url_list?.[0])"
              @click="nav('/people/user-home/' + item.uid)"
            />
            <div class="info" @click="nav('/people/user-home/' + item.uid)">
              <div class="name">{{ item.nickname }}</div>
              <div class="detail">请求添加你为朋友</div>
            </div>
            <div class="actions">
              <div
                class="btn accept"
                :class="{ loading: item._accepting }"
                @click="handleAccept(item, i)"
              >
                {{ item._accepting ? '...' : '接受' }}
              </div>
              <div
                class="btn reject"
                :class="{ loading: item._rejecting }"
                @click="handleReject(item, i)"
              >
                {{ item._rejecting ? '...' : '拒绝' }}
              </div>
            </div>
          </div>
          <div class="empty" v-if="!data.loading && !data.requests.length">
            <img src="@/assets/img/icon/no-result.png" alt="" />
            <span>暂无朋友申请</span>
          </div>
        </div>
      </Scroll>
    </div>
  </div>
</template>
<script setup lang="ts">
import { onMounted, reactive } from 'vue'
import { useNav } from '@/utils/hooks/useNav'
import { _checkImgUrl } from '@/utils'
import { getFriendRequests, acceptFriendRequest, rejectFriendRequest } from '@/api/user'

defineOptions({ name: 'FriendRequests' })

const nav = useNav()
const data = reactive({
  loading: false,
  requests: [] as any[]
})

async function loadRequests() {
  data.loading = true
  try {
    const res = await getFriendRequests()
    if (res.success) {
      data.requests = (res.data || []).map((u: any) => ({ ...u, _accepting: false, _rejecting: false }))
    }
  } finally {
    data.loading = false
  }
}

async function handleAccept(item: any, idx: number) {
  if (item._accepting || item._rejecting) return
  item._accepting = true
  try {
    const res = await acceptFriendRequest(item.uid)
    if (res.success) {
      data.requests.splice(idx, 1)
    }
  } finally {
    item._accepting = false
  }
}

async function handleReject(item: any, idx: number) {
  if (item._accepting || item._rejecting) return
  item._rejecting = true
  try {
    const res = await rejectFriendRequest(item.uid)
    if (res.success) {
      data.requests.splice(idx, 1)
    }
  } finally {
    item._rejecting = false
  }
}

onMounted(loadRequests)
</script>
<style scoped lang="less">
#FriendRequests {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  top: 0;
  color: white;
  font-size: 14rem;

  .content {
    height: calc(var(--vh, 1vh) * 100 - var(--common-header-height));
    padding-top: var(--common-header-height);

    .list {
      padding: 0 var(--page-padding);
    }

    .item {
      display: flex;
      align-items: center;
      padding: 14rem 0;
      border-bottom: 1px solid var(--line-color2);

      .avatar {
        width: 48rem;
        height: 48rem;
        border-radius: 50%;
        object-fit: cover;
        flex-shrink: 0;
        cursor: pointer;
      }

      .info {
        flex: 1;
        margin-left: 12rem;
        min-width: 0;
        cursor: pointer;

        .name {
          font-size: 15rem;
          font-weight: 500;
        }

        .detail {
          font-size: 12rem;
          color: var(--second-text-color);
          margin-top: 4rem;
        }
      }

      .actions {
        display: flex;
        gap: 10rem;
        flex-shrink: 0;

        .btn {
          padding: 8rem 18rem;
          border-radius: 20rem;
          font-size: 13rem;
          cursor: pointer;
          font-weight: 500;

          &.accept {
            background: var(--primary-btn-color);
          }

          &.reject {
            background: var(--second-btn-color);
          }

          &.loading {
            opacity: 0.5;
          }
        }
      }
    }

    .empty {
      display: flex;
      flex-direction: column;
      align-items: center;
      padding-top: 120rem;
      color: var(--second-text-color);

      img {
        width: 80rem;
        height: 80rem;
        margin-bottom: 20rem;
        opacity: 0.4;
      }
    }
  }
}
</style>
