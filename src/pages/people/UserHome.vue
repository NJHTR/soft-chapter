<template>
  <UserPanel
    v-if="!data.loading"
    ref="userPanelRef"
    v-model:currentItem="data.currentItem"
    :active="!data.loading"
    @back="$router.back()"
    @showFollowSetting="_no"
    @showFollowSetting2="handleCancelFollow"
  />
  <Loading v-else :is-full-screen="false" />
</template>
<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { panel } from '@/api/user'
import { _no } from '@/utils'
import UserPanel from '@/components/UserPanel.vue'
import Loading from '@/components/Loading.vue'

defineOptions({ name: 'UserHome' })

const route = useRoute()
const userPanelRef = ref<InstanceType<typeof UserPanel>>()
const data = reactive({
  loading: true,
  currentItem: {
    author: { uid: null },
    aweme_list: []
  }
})

function handleCancelFollow() {
  userPanelRef.value?.cancelFollow()
}

async function loadUser(uid: string) {
  if (!uid) return
  data.loading = true
  const infoRes = await panel({ uid })
  console.log('UserHome panel response:', infoRes)
  if (infoRes.success) {
    const u = infoRes.data
    console.log('UserHome panel data:', { follower_count: u.follower_count, following_count: u.following_count, uid: u.uid })
    const uidVal = u.uid != null ? u.uid : uid
    data.currentItem = {
      author: {
        uid: uidVal,
        nickname: u.nickname || '',
        unique_id: u.unique_id || '',
        short_id: u.short_id || '',
        signature: u.signature || '',
        gender: u.gender,
        province: u.province || '',
        city: u.city || '',
        avatar_168x168: u.avatar_168x168 || { url_list: [] },
        avatar_300x300: u.avatar_300x300 || { url_list: [] },
        cover_url: u.cover_url || [{ url_list: [] }],
        total_favorited: u.total_favorited || 0,
        following_count: u.following_count || 0,
        user_age: u.user_age ?? -1,
        mplatform_followers_count: u.follower_count || 0,
        follow_status: u.is_followed ? 1 : 0
      },
      aweme_list: []
    }
  }
  data.loading = false
}

onMounted(() => {
  loadUser(route.params.uid as string)
})

watch(
  () => route.params.uid,
  (newUid) => {
    if (newUid) loadUser(newUid as string)
  }
)
</script>
