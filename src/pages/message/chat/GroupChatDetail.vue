<template>
  <div class="GroupChatDetail">
    <BaseHeader backMode="light" backImg="back">
      <template v-slot:center>
        <span class="f16">聊天详情</span>
      </template>
    </BaseHeader>

    <div class="content">
      <!-- 群成员 -->
      <div class="section">
        <div class="section-title">
          <span>群成员 ({{ data.members.length }})</span>
          <span
            v-if="data.showRemoveMember"
            class="remove-hint"
            @click="data.showRemoveMember = false"
            >完成</span
          >
        </div>
        <div class="member-grid">
          <div
            class="member-item"
            v-for="m in data.members"
            :key="m.user_id"
            :class="{
              removable: data.showRemoveMember && m.user_id !== store.userinfo.uid && m.role !== 1
            }"
            @click="
              data.showRemoveMember && m.user_id !== store.userinfo.uid && m.role !== 1
                ? handleRemoveMember(m)
                : null
            "
          >
            <div class="avatar-wrapper">
              <img
                class="avatar"
                :src="_checkImgUrl(m.user?.avatar_168x168?.url_list?.[0]) || defaultAvatarPng"
                alt=""
              />
              <div
                class="remove-overlay"
                v-if="data.showRemoveMember && m.user_id !== store.userinfo.uid && m.role !== 1"
              >
                <span>—</span>
              </div>
            </div>
            <span class="name">{{ m.user?.nickname || '群成员' }}</span>
            <span class="role-tag" v-if="m.role === 1">群主</span>
            <span class="role-tag admin" v-else-if="m.role === 2">管理员</span>
          </div>
          <!-- 添加按钮 -->
          <div class="member-item" @click="data.showAddMember = true">
            <div class="action-circle add-circle">
              <span>+</span>
            </div>
            <span class="name">添加</span>
          </div>
          <!-- 移除按钮（仅群主可见） -->
          <div
            class="member-item"
            v-if="isOwner"
            @click="data.showRemoveMember = !data.showRemoveMember"
          >
            <div class="action-circle remove-circle" :class="{ active: data.showRemoveMember }">
              <span>—</span>
            </div>
            <span class="name">移除</span>
          </div>
        </div>
      </div>

      <!-- 群设置 -->
      <div class="section menu-list">
        <div class="menu-item">
          <span>群聊名称</span>
          <span class="right">{{ data.groupName }}</span>
        </div>
        <div class="menu-item" @click="toggleMute">
          <span>消息免打扰</span>
          <Check mode="red" v-model="data.isMuted" />
        </div>
        <div class="menu-item" @click="handleLeave">
          <span class="danger-text">退出群聊</span>
        </div>
      </div>
    </div>

    <!-- 添加成员弹窗 -->
    <from-bottom-dialog v-model="data.showAddMember" page-id="GroupChatDetail">
      <div class="add-member-dialog">
        <div class="dialog-header">
          <span class="f16">添加成员</span>
          <span v-if="data.addSelected.length" class="confirm-btn" @click="handleAddMembers">
            确定({{ data.addSelected.length }})
          </span>
        </div>
        <Search class="search-bar" placeholder="搜索好友" v-model="data.addSearchKey" />
        <div class="friend-list">
          <div
            class="friend-item"
            :key="f.uid || f.id"
            v-for="f in data.addCandidates"
            @click="toggleAddSelect(f)"
          >
            <Check mode="red" v-model="f._selected" />
            <img
              :src="_checkImgUrl(f.avatar_168x168?.url_list?.[0] || f.avatar) || defaultAvatarPng"
              alt=""
            />
            <span>{{ f.nickname || f.name }}</span>
          </div>
          <div v-if="!data.addCandidates.length" class="no-result">没有可添加的好友</div>
        </div>
      </div>
    </from-bottom-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useBaseStore } from '@/store/pinia'
import { _checkImgUrl, _showSimpleConfirmDialog, _notice } from '@/utils'
import defaultAvatarPng from '@/assets/img/icon/people-gray.png'
import Check from '@/components/Check.vue'
import FromBottomDialog from '@/components/dialog/FromBottomDialog.vue'
import Search from '@/components/Search.vue'
import {
  getGroupInfo,
  getGroupMembers,
  leaveGroup,
  addGroupMembers,
  removeGroupMember
} from '@/api/message'

defineOptions({ name: 'GroupChatDetail' })

const route = useRoute()
const router = useRouter()
const store = useBaseStore()

const groupId = computed(() => {
  const id = Number(route.query.group_id)
  return isNaN(id) ? -1 : id
})

const data = reactive({
  groupName: (route.query.name as string) || '群聊',
  groupAvatar: (route.query.avatar as string) || '',
  members: [] as any[],
  isMuted: false,
  isPinned: false,
  showAddMember: false,
  showRemoveMember: false,
  addSearchKey: '',
  addCandidates: [] as any[],
  addSelected: [] as any[]
})

const isOwner = computed(() => {
  return data.members.some((m) => m.user_id === store.userinfo.uid && m.role === 1)
})

onMounted(() => {
  if (groupId.value < 0) {
    router.back()
    return
  }
  loadInfo()
  loadMembers()
})

async function loadInfo() {
  try {
    const res = await getGroupInfo(groupId.value)
    if (res.success && res.data) {
      data.groupName = res.data.name || data.groupName
    }
  } catch {
    /* ignore */
  }
}

async function loadMembers() {
  try {
    const res = await getGroupMembers(groupId.value)
    if (res.success && res.data) {
      data.members = res.data || []
    }
  } catch {
    /* ignore */
  }
}

function toggleMute() {
  data.isMuted = !data.isMuted
}

// 添加成员 — 弹窗打开时加载好友列表并过滤已在群里的
watch(
  () => data.showAddMember,
  (show) => {
    if (show) {
      const existingIds = new Set(data.members.map((m) => m.user_id))
      data.addCandidates = (store.friends?.all || [])
        .filter((f) => !existingIds.has(f.uid || f.id))
        .map((f) => ({ ...f, _selected: false }))
      data.addSearchKey = ''
      data.addSelected = []
    }
  }
)

watch(
  () => data.addSearchKey,
  (kw) => {
    if (!kw) {
      const existingIds = new Set(data.members.map((m) => m.user_id))
      data.addCandidates = (store.friends?.all || [])
        .filter((f) => !existingIds.has(f.uid || f.id))
        .map((f) => ({
          ...f,
          _selected: !!data.addSelected.find((s) => (s.uid || s.id) === (f.uid || f.id))
        }))
      return
    }
    const lower = kw.toLowerCase()
    const existingIds = new Set(data.members.map((m) => m.user_id))
    data.addCandidates = (store.friends?.all || [])
      .filter((f) => !existingIds.has(f.uid || f.id))
      .filter((f) => (f.nickname || f.name || '').toLowerCase().includes(lower))
      .map((f) => ({
        ...f,
        _selected: !!data.addSelected.find((s) => (s.uid || s.id) === (f.uid || f.id))
      }))
  }
)

function toggleAddSelect(friend: any) {
  friend._selected = !friend._selected
  const fid = friend.uid || friend.id
  const idx = data.addSelected.findIndex((s) => (s.uid || s.id) === fid)
  if (idx >= 0) {
    data.addSelected.splice(idx, 1)
  } else {
    data.addSelected.push(friend)
  }
}

async function handleAddMembers() {
  const uids = data.addSelected.map((f) => String(f.uid || f.id)).filter(Boolean)
  if (!uids.length) return
  try {
    const res = await addGroupMembers(groupId.value, uids)
    if (res.success) {
      data.showAddMember = false
      loadMembers()
    } else {
      _notice('添加失败: ' + (res.msg || ''))
    }
  } catch {
    _notice('添加失败')
  }
}

function handleRemoveMember(member: any) {
  const name = member.user?.nickname || '群成员'
  _showSimpleConfirmDialog(
    `确定要将 ${name} 移出群聊吗？`,
    async () => {
      try {
        const res = await removeGroupMember(groupId.value, member.user_id)
        if (res.success) {
          data.members = data.members.filter((m) => m.user_id !== member.user_id)
        } else {
          _notice('移除失败: ' + (res.msg || ''))
        }
      } catch {
        _notice('移除失败')
      }
    },
    () => {}
  )
}

function handleLeave() {
  _showSimpleConfirmDialog(
    '确定要退出群聊吗？',
    async () => {
      try {
        await leaveGroup(groupId.value)
        router.replace('/message')
      } catch {
        _notice('退出失败')
      }
    },
    () => {}
  )
}
</script>

<style scoped lang="less">
@import '@/assets/less/index';

.GroupChatDetail {
  position: fixed;
  top: 0;
  bottom: 0;
  left: 0;
  right: 0;
  background: var(--color-message);
  overflow-y: auto;
  color: white;
  font-size: 14rem;

  .content {
    padding-top: 60rem;
  }

  .section {
    background: rgb(37, 37, 38);
    margin-bottom: 10rem;
    padding: 15rem;

    .section-title {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding-bottom: 12rem;
      font-size: 13rem;
      color: var(--second-text-color);

      .remove-hint {
        color: var(--primary-btn-color);
        cursor: pointer;
      }
    }
  }

  .member-grid {
    display: flex;
    flex-wrap: wrap;
    gap: 15rem;

    .member-item {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 5rem;
      position: relative;

      &.removable {
        cursor: pointer;

        .avatar-wrapper .avatar {
          opacity: 0.6;
        }
      }

      .avatar-wrapper {
        position: relative;
        width: 48rem;
        height: 48rem;

        .avatar {
          width: 48rem;
          height: 48rem;
          border-radius: 50%;
          object-fit: cover;
        }

        .remove-overlay {
          position: absolute;
          top: -4rem;
          right: -4rem;
          width: 20rem;
          height: 20rem;
          border-radius: 50%;
          background: #fe2c55;
          display: flex;
          align-items: center;
          justify-content: center;

          span {
            color: white;
            font-size: 16rem;
            font-weight: bold;
            line-height: 1;
            transform: translateY(-1.5rem);
          }
        }
      }

      .action-circle {
        width: 48rem;
        height: 48rem;
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        cursor: pointer;

        span {
          display: flex;
          align-items: center;
          justify-content: center;
          width: 100%;
          height: 100%;
          font-size: 24rem;
          font-weight: 300;
          line-height: 1;
          text-align: center;
        }

        &.add-circle {
          background: rgba(255, 255, 255, 0.12);
          span {
            color: var(--second-text-color);
            font-size: 31rem;
          }
        }

        &.remove-circle {
          background: rgba(255, 255, 255, 0.12);
          span {
            color: var(--second-text-color);
            transform: translate(1rem, -2rem);
          }
          &.active {
            background: rgba(254, 44, 85, 0.2);
            span {
              color: #fe2c55;
            }
          }
        }

        &:active {
          opacity: 0.6;
        }
      }

      .name {
        font-size: 12rem;
        max-width: 60rem;
        text-overflow: ellipsis;
        overflow: hidden;
        white-space: nowrap;
        text-align: center;
        color: white;
      }

      .role-tag {
        position: absolute;
        top: 0;
        right: 0;
        background: #fe4070;
        color: white;
        font-size: 9rem;
        padding: 1rem 4rem;
        border-radius: 4rem;
        &.admin {
          background: #ff9800;
        }
      }
    }
  }

  .menu-list {
    .menu-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 12rem 0;
      border-bottom: 1px solid rgba(255, 255, 255, 0.08);
      cursor: pointer;
      gap: 12rem;

      &:last-child {
        border-bottom: none;
      }

      > span:first-child {
        flex-shrink: 0;
      }

      .right {
        color: var(--second-text-color);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        text-align: right;
      }

      .danger-text {
        color: #fe4070;
      }
    }
  }

  .add-member-dialog {
    min-height: 60vh;
    padding: 15rem;
    padding-bottom: 60rem;
    color: white;

    .dialog-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 12rem;

      .confirm-btn {
        color: var(--primary-btn-color);
        cursor: pointer;
      }
    }

    .search-bar {
      margin-bottom: 10rem;
    }

    .friend-list {
      .friend-item {
        display: flex;
        align-items: center;
        gap: 12rem;
        padding: 10rem 0;
        cursor: pointer;

        img {
          width: 40rem;
          height: 40rem;
          border-radius: 50%;
          object-fit: cover;
        }

        span {
          font-size: 14rem;
          color: white;
        }
      }
    }

    .no-result {
      text-align: center;
      color: var(--second-text-color);
      padding: 30rem;
    }
  }
}
</style>
