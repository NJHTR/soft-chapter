<template>
  <div>
    <h2 class="page-title">用户管理</h2>

    <div class="search-bar">
      <input v-model="searchKw" placeholder="搜索昵称/邮箱..." @keyup.enter="loadUsers" style="width:240px;" />
      <select v-model="roleFilter" @change="loadUsers">
        <option value="">全部角色</option>
        <option value="USER">普通用户</option>
        <option value="ADMIN">管理员</option>
        <option value="MERCHANT">商家</option>
      </select>
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
          <th>状态</th>
          <th>注册时间</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="u in users" :key="u.uid">
          <td>{{ u.uid }}</td>
          <td><img v-if="u.avatar" :src="normalizeImgUrl(u.avatar)" style="width:32px;height:32px;border-radius:50%;object-fit:cover;" @error="onAvatarError" /></td>
          <td>{{ u.nickname || '-' }}</td>
          <td>{{ u.email || '-' }}</td>
          <td><span class="badge" :class="'role-' + (u.role || 'user').toLowerCase()">{{ u.role || 'USER' }}</span></td>
          <td>{{ u.followerCount || 0 }}</td>
          <td><span class="badge" :class="u.status === 'BANNED' ? 'banned' : 'active'">{{ u.status === 'BANNED' ? '已封禁' : '正常' }}</span></td>
          <td>{{ formatDate(u.createTime) }}</td>
          <td>
            <button class="btn-sm" style="background:#ede9fe;color:#7c3aed;" @click="openProfile(u.uid)">画像</button>
            <button v-if="u.role !== 'ADMIN'" class="btn-sm btn-edit" :disabled="actionLoading === u.uid" @click="toggleRole(u)">{{ actionLoading === u.uid ? '...' : '改角色' }}</button>
            <button v-if="u.role !== 'ADMIN'" class="btn-sm" :class="u.status === 'BANNED' ? 'btn-approve' : 'btn-del'" :disabled="actionLoading === u.uid" @click="toggleBan(u)">
              {{ actionLoading === u.uid ? '...' : (u.status === 'BANNED' ? '解封' : '封禁') }}
            </button>
          </td>
        </tr>
        <tr v-if="users.length === 0"><td colspan="9" class="empty-row">暂无数据</td></tr>
      </tbody>
    </table>

    <UserProfile :show="profileVisible" :uid="profileUid" @close="profileVisible = false" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getUsers, changeUserRole, banUser, unbanUser } from '@/api/admin'
import { toast } from '@/utils/toast'
import { normalizeImgUrl } from '@/utils/image'
import UserProfile from '@/components/UserProfile.vue'

const users = ref<any[]>([])
const searchKw = ref('')
const roleFilter = ref('')
const actionLoading = ref<number | null>(null)
const profileVisible = ref(false)
const profileUid = ref(0)

function openProfile(uid: number) {
  profileUid.value = uid
  profileVisible.value = true
}

async function loadUsers() {
  try {
    const res: any = await getUsers({ keyword: searchKw.value || undefined, role: roleFilter.value || undefined, pageSize: 50 })
    if (res.success) users.value = res.data?.list || res.data?.records || []
  } catch (e) { console.error(e) }
}

async function toggleRole(user: any) {
  const newRole = user.role === 'MERCHANT' ? 'USER' : 'MERCHANT'
  actionLoading.value = user.uid
  try {
    const res: any = await changeUserRole(user.uid, newRole)
    if (res.success) { toast.success(`角色已改为 ${newRole === 'MERCHANT' ? '商家' : '普通用户'}`); loadUsers() }
    else toast.error('操作失败')
  } catch (e) { toast.error('网络错误') } finally { actionLoading.value = null }
}

async function toggleBan(user: any) {
  actionLoading.value = user.uid
  try {
    const action = user.status === 'BANNED' ? unbanUser : banUser
    const res: any = await action(user.uid)
    if (res.success) { toast.success(user.status === 'BANNED' ? '已解封' : '已封禁'); loadUsers() }
    else toast.error('操作失败')
  } catch (e) { toast.error('网络错误') } finally { actionLoading.value = null }
}

function formatDate(d: any) { return d ? new Date(d).toLocaleDateString('zh-CN') : '-' }

function onAvatarError(e: Event) {
  const img = e.target as HTMLImageElement
  img.style.display = 'none'
}
onMounted(() => loadUsers())
</script>
