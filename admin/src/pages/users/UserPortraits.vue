<template>
  <div class="portraits-page">
    <h2 class="page-title">用户画像</h2>
    <p class="page-desc">点击"详情"查看用户完整行为画像，包括用户类型、分层、互动率、内容偏好、活跃时段等。</p>

    <div class="search-bar">
      <input v-model="searchKw" placeholder="搜索用户昵称/邮箱..." style="width:240px;" @keyup.enter="loadUsers" />
      <button class="btn-primary" @click="loadUsers">搜索</button>
    </div>

    <table class="data-table">
      <thead>
        <tr>
          <th>UID</th>
          <th>头像</th>
          <th>昵称</th>
          <th>邮箱</th>
          <th>角色</th>
          <th>粉丝</th>
          <th>作品数</th>
          <th>状态</th>
          <th>注册时间</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="u in users" :key="u.uid">
          <td>{{ u.uid }}</td>
          <td>
            <div v-if="u.avatar" :style="{ backgroundImage: 'url(' + normalizeImgUrl(u.avatar) + ')' }" class="avatar-img" />
            <div v-else class="avatar-placeholder">{{ (u.nickname || '?').charAt(0) }}</div>
          </td>
          <td>{{ u.nickname || '-' }}</td>
          <td>{{ u.email || '-' }}</td>
          <td><span class="badge" :class="'role-' + (u.role || 'user').toLowerCase()">{{ u.role || 'USER' }}</span></td>
          <td>{{ fmtNum(u.followerCount) }}</td>
          <td>{{ u.videoCount || 0 }}</td>
          <td><span class="badge" :class="u.status === 'BANNED' ? 'banned' : 'active'">{{ u.status === 'BANNED' ? '已封禁' : '正常' }}</span></td>
          <td>{{ fmtDate(u.createTime) }}</td>
          <td>
            <button class="btn-sm" style="background:#ede9fe;color:#7c3aed;" @click="openProfile(u.uid)">画像详情</button>
          </td>
        </tr>
        <tr v-if="users.length === 0"><td colspan="10" class="empty-row">暂无数据</td></tr>
      </tbody>
    </table>

    <UserProfile :show="profileVisible" :uid="profileUid" @close="profileVisible = false" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getUsers } from '@/api/admin'
import { normalizeImgUrl } from '@/utils/image'
import UserProfile from '@/components/UserProfile.vue'

const users = ref<any[]>([])
const searchKw = ref('')
const profileVisible = ref(false)
const profileUid = ref(0)

function fmtNum(n: number) {
  if (!n) return '0'
  if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return String(n)
}
function fmtDate(d: any) { return d ? new Date(d).toLocaleDateString('zh-CN') : '-' }

function openProfile(uid: number) {
  profileUid.value = uid
  profileVisible.value = true
}

async function loadUsers() {
  try {
    const res: any = await getUsers({ keyword: searchKw.value || undefined, pageSize: 50 })
    if (res.success) users.value = res.data?.list || res.data?.records || []
  } catch (e) { console.error(e) }
}

onMounted(() => loadUsers())
</script>

<style scoped>
.portraits-page { max-width: 1400px; }
.page-desc { font-size: 13px; color: #999; margin-top: -16px; margin-bottom: 16px; }
.avatar-img {
  width: 32px; height: 32px; border-radius: 50%;
  background-size: cover; background-position: center;
}
.avatar-placeholder {
  width: 32px; height: 32px; border-radius: 50%;
  background: linear-gradient(135deg, #fe2c55, #ff6b81);
  color: #fff; display: flex; align-items: center; justify-content: center;
  font-size: 13px; font-weight: 600;
}
</style>
