<template>
  <!-- 登录/注册页 — 无布局 -->
  <div v-if="route.meta.noAuth" class="auth-shell">
    <router-view />
  </div>

  <!-- 管理后台 — 顶部导航 + 侧边栏 + 主体 -->
  <div v-else class="admin-shell" :class="{ 'fs-mode': bigScreenFs }">
    <!-- 顶部导航栏 -->
    <header v-if="!bigScreenFs" class="top-nav">
      <div class="top-nav-left">
        <router-link to="/big-screen" class="top-logo">
          <img class="logo-mark" src="/gznxl-gu0vc-001.ico" alt="logo" />
          <span class="logo-text">SeekFlow</span>
          <small>运营管理</small>
        </router-link>
      </div>

      <nav class="top-nav-center">
        <button
          v-for="item in topNavItems"
          :key="item.key"
          class="top-nav-item"
          :class="{ active: activeTopNav === item.key }"
          @click="switchTopNav(item)"
        >
          {{ item.label }}
        </button>
      </nav>

      <div class="top-nav-right">
        <button class="header-btn" title="通知"><Bell :size="18" /></button>
        <div class="user-menu" ref="userMenuRef">
          <button class="header-btn user-btn" @click="showUserMenu = !showUserMenu">
            <span class="user-avatar">{{ auth.nickname?.charAt(0) || '管' }}</span>
            <span class="user-name">{{ auth.nickname || '管理员' }}</span>
            <span class="user-arrow">▾</span>
          </button>
          <div v-if="showUserMenu" class="user-dropdown">
            <div class="dropdown-item disabled">
              <span>{{ auth.nickname || '管理员' }}</span>
              <small>管理员</small>
            </div>
            <div class="dropdown-divider"></div>
            <a class="dropdown-item" href="/home" target="_blank">返回用户端</a>
            <div class="dropdown-item danger" @click="handleLogout">退出登录</div>
          </div>
        </div>
      </div>
    </header>

    <!-- 下方：侧边栏 + 主体 -->
    <div class="admin-body">
      <aside v-if="!bigScreenFs && !isBigScreen" class="admin-sidebar">
        <div class="sidebar-title">{{ activeTopNavLabel }}</div>
        <nav class="sidebar-nav">
          <router-link
            v-for="item in currentSidebarItems"
            :key="item.route"
            :to="item.route"
            class="sidebar-item"
          >
            <component :is="sidebarIcons[item.icon]" :size="16" class="sidebar-icon" />
            <span class="sidebar-label">{{ item.label }}</span>
          </router-link>
        </nav>
      </aside>

      <div class="admin-main">
        <!-- 面包屑 -->
        <div v-if="!bigScreenFs && !isBigScreen" class="breadcrumb-bar">
          <template v-for="(crumb, idx) in breadcrumbs" :key="idx">
            <span v-if="idx > 0" class="crumb-sep">/</span>
            <span :class="{ 'crumb-active': idx === breadcrumbs.length - 1 }">{{ crumb }}</span>
          </template>
        </div>

        <main
          class="admin-content"
          :class="{ 'fs-content': bigScreenFs, 'no-padding': isBigScreen }"
        >
          <router-view v-slot="{ Component }">
            <transition name="fade" mode="out-in">
              <component :is="Component" />
            </transition>
          </router-view>
        </main>
      </div>
    </div>

    <GlobalToast />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, provide, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import {
  Bell,
  BarChart3,
  Tv,
  Video,
  Users,
  MessageCircle,
  Globe,
  Flame,
  User,
  Brain,
  CheckCheck,
  ClipboardList,
  Tag,
  Flag,
  Search,
  Settings
} from 'lucide-vue-next'

const sidebarIcons: Record<string, any> = {
  '📊': BarChart3,
  '📺': Tv,
  '📹': Video,
  '👥': Users,
  '💬': MessageCircle,
  '🌍': Globe,
  '🔥': Flame,
  '👤': User,
  '🧠': Brain,
  '✅': CheckCheck,
  '📋': ClipboardList,
  '🏷️': Tag,
  '🚩': Flag,
  '🔍': Search,
  '⚙️': Settings
}

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const showUserMenu = ref(false)
const userMenuRef = ref<HTMLElement | null>(null)
const bigScreenFs = ref(false)
provide('bigScreenFs', bigScreenFs)

// ===== 顶部导航配置 =====
interface TopNavItem {
  key: string
  label: string
  firstRoute: string
}

const topNavItems: TopNavItem[] = [
  { key: 'bigscreen', label: '数字大屏', firstRoute: '/big-screen' },
  { key: 'data', label: '数据中心', firstRoute: '/data/dashboard' },
  { key: 'users', label: '用户中心', firstRoute: '/users' },
  { key: 'works', label: '作品中心', firstRoute: '/works/review' },
  { key: 'config', label: '系统配置', firstRoute: '/config/search' }
]

interface SidebarItem {
  label: string
  route: string
  icon: string
}

const sidebarMap: Record<string, SidebarItem[]> = {
  bigscreen: [],
  data: [
    { label: '仪表盘', route: '/data/dashboard', icon: '📊' },
    { label: '数据大屏', route: '/big-screen', icon: '📺' },
    { label: '作品数据监控', route: '/data/video-monitor', icon: '📹' },
    { label: '用户数据监控', route: '/data/user-monitor', icon: '👥' },
    { label: '交互数据监控', route: '/data/engagement', icon: '💬' },
    { label: '地域分布', route: '/data/geo', icon: '🌍' },
    { label: '搜索热门词', route: '/data/search-hot', icon: '🔥' }
  ],
  users: [
    { label: '用户管理', route: '/users', icon: '👤' },
    { label: '用户画像', route: '/users/portraits', icon: '🧠' }
  ],
  works: [
    { label: '作品审核', route: '/works/review', icon: '✅' },
    { label: '作品管理', route: '/works/manage', icon: '📋' },
    { label: '标签管理', route: '/works/tags', icon: '🏷️' },
    { label: '举报处理', route: '/works/reports', icon: '🚩' }
  ],
  config: [
    { label: '搜索配置', route: '/config/search', icon: '🔍' },
    { label: '系统设置', route: '/config/system', icon: '⚙️' }
  ]
}

// ===== 由当前路由推导 active top nav =====
const isBigScreen = computed(() => route.path === '/big-screen')

const activeTopNav = computed(() => {
  const p = route.path
  if (isBigScreen.value) return 'bigscreen'
  if (p.startsWith('/data/')) return 'data'
  if (p.startsWith('/users')) return 'users'
  if (p.startsWith('/works/')) return 'works'
  if (p.startsWith('/config/')) return 'config'
  return 'bigscreen'
})

const activeTopNavLabel = computed(() => {
  return topNavItems.find((i) => i.key === activeTopNav.value)?.label || ''
})

const currentSidebarItems = computed(() => {
  return sidebarMap[activeTopNav.value] || []
})

// 面包屑
const breadcrumbs = computed(() => {
  const crumbs: string[] = [activeTopNavLabel.value]
  const item = currentSidebarItems.value.find((i) => i.route === route.path)
  if (item && item.label !== activeTopNavLabel.value) crumbs.push(item.label)
  return crumbs
})

function switchTopNav(item: TopNavItem) {
  router.push(item.firstRoute)
}

function handleLogout() {
  showUserMenu.value = false
  auth.logout()
}

function onClickOutside(e: MouseEvent) {
  if (userMenuRef.value && !userMenuRef.value.contains(e.target as Node)) {
    showUserMenu.value = false
  }
}

onMounted(() => document.addEventListener('click', onClickOutside))
onUnmounted(() => document.removeEventListener('click', onClickOutside))
</script>

<style scoped lang="less">
.auth-shell {
  min-height: 100vh;
}

.admin-shell {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
}

// ===================== 顶部导航 =====================
.top-nav {
  height: 52px;
  background: #001529;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  flex-shrink: 0;
  z-index: 200;
}

.top-nav-left {
  display: flex;
  align-items: center;
}
.top-logo {
  display: flex;
  align-items: center;
  gap: 8px;
  text-decoration: none;
  .logo-mark {
    width: 28px;
    height: 28px;
    border-radius: 6px;
    object-fit: cover;
  }
  .logo-text {
    font-size: 15px;
    font-weight: 700;
    color: #fff;
  }
  small {
    font-size: 10px;
    color: rgba(255, 255, 255, 0.4);
  }
}

.top-nav-center {
  display: flex;
  align-items: center;
  gap: 2px;
}

.top-nav-item {
  background: none;
  border: none;
  color: rgba(255, 255, 255, 0.65);
  font-size: 13px;
  padding: 8px 18px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;

  &:hover {
    color: #fff;
    background: rgba(255, 255, 255, 0.08);
  }
  &.active {
    color: #fff;
    background: rgba(254, 44, 85, 0.3);
    font-weight: 500;
  }
}

.top-nav-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-btn {
  background: none;
  border: none;
  font-size: 18px;
  cursor: pointer;
  padding: 6px 8px;
  border-radius: 6px;
  color: rgba(255, 255, 255, 0.65);
  transition: all 0.2s;
  &:hover {
    background: rgba(255, 255, 255, 0.08);
    color: #fff;
  }
}

.user-menu {
  position: relative;
}
.user-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 10px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.75) !important;
}
.user-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: linear-gradient(135deg, #fe2c55, #ff6b81);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  flex-shrink: 0;
}
.user-name {
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
}
.user-arrow {
  font-size: 10px;
}

.user-dropdown {
  position: absolute;
  top: 100%;
  right: 0;
  margin-top: 6px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  min-width: 180px;
  z-index: 500;
  overflow: hidden;
}
.dropdown-item {
  display: block;
  padding: 10px 16px;
  font-size: 13px;
  color: #333;
  text-decoration: none;
  cursor: pointer;
  transition: background 0.15s;
  &:hover:not(.disabled) {
    background: #f5f5f5;
  }
  &.disabled {
    cursor: default;
    small {
      display: block;
      color: #999;
      font-size: 11px;
    }
  }
  &.danger {
    color: #ef4444;
    &:hover {
      background: #fef2f2;
    }
  }
}
.dropdown-divider {
  height: 1px;
  background: #eee;
  margin: 4px 0;
}

// ===================== 侧边栏 + 主体 =====================
.admin-body {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.admin-sidebar {
  width: 200px;
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(8px);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  overflow: hidden;
}

.sidebar-title {
  font-size: 11px;
  font-weight: 600;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 1px;
  padding: 16px 20px 10px;
}

.sidebar-nav {
  flex: 1;
  overflow-y: auto;
  padding: 4px 8px;
}

.sidebar-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  color: #64748b;
  text-decoration: none;
  font-size: 13px;
  border-radius: 8px;
  transition: all 0.15s;

  &:hover {
    color: #0f172a;
    background: rgba(0, 0, 0, 0.04);
  }
  &.router-link-active {
    color: #fe2c55;
    background: rgba(254, 44, 85, 0.06);
    font-weight: 500;
  }
}
.sidebar-icon {
  flex-shrink: 0;
}
.sidebar-label {
  white-space: nowrap;
}

// ===================== 主体 =====================
.admin-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: url('@/assets/img/p_bg.png') center / cover no-repeat;
}

.breadcrumb-bar {
  height: 34px;
  display: flex;
  align-items: center;
  padding: 0 24px;
  font-size: 12px;
  color: #94a3b8;
  flex-shrink: 0;
}
.crumb-sep {
  margin: 0 6px;
  color: #ddd;
}
.crumb-active {
  color: #333;
  font-weight: 500;
}

.admin-content {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(6px);
}
.admin-content.no-padding {
  padding: 0;
}
.admin-content.fs-content {
  padding: 0;
  overflow: hidden;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.12s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
.admin-shell.fs-mode .admin-main {
  position: fixed;
  inset: 0;
  z-index: 9999;
}
</style>
