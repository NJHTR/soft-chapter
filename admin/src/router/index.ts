import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  // === Auth ===
  {
    path: '/login', name: 'Login',
    component: () => import('@/pages/auth/Login.vue'),
    meta: { title: '登录', noAuth: true }
  },
  {
    path: '/register', name: 'Register',
    component: () => import('@/pages/auth/Register.vue'),
    meta: { title: '注册', noAuth: true }
  },
  { path: '/', redirect: '/big-screen' },

  // === 数字大屏 ===
  {
    path: '/big-screen', name: 'BigScreen',
    component: () => import('@/pages/data/BigScreen.vue'),
    meta: { title: '数字大屏' }
  },

  // === 数据中心 ===
  {
    path: '/data/dashboard', name: 'Dashboard',
    component: () => import('@/pages/Dashboard.vue'),
    meta: { title: '仪表盘' }
  },
  {
    path: '/data/video-monitor', name: 'VideoDataMonitor',
    component: () => import('@/pages/data/VideoDataMonitor.vue'),
    meta: { title: '作品数据监控' }
  },
  {
    path: '/data/user-monitor', name: 'UserDataMonitor',
    component: () => import('@/pages/data/UserDataMonitor.vue'),
    meta: { title: '用户数据监控' }
  },
  {
    path: '/data/engagement', name: 'EngagementMonitor',
    component: () => import('@/pages/data/EngagementMonitor.vue'),
    meta: { title: '交互数据监控' }
  },
  {
    path: '/data/geo', name: 'GeoDistribution',
    component: () => import('@/pages/data/GeoDistribution.vue'),
    meta: { title: '地域分布' }
  },
  {
    path: '/data/search-hot', name: 'SearchHotWords',
    component: () => import('@/pages/data/SearchHotWords.vue'),
    meta: { title: '搜索热门词' }
  },

  // === 用户中心 ===
  {
    path: '/users', name: 'Users',
    component: () => import('@/pages/UserManage.vue'),
    meta: { title: '用户管理' }
  },
  {
    path: '/users/portraits', name: 'UserPortraits',
    component: () => import('@/pages/users/UserPortraits.vue'),
    meta: { title: '用户画像' }
  },

  // === 作品中心 ===
  {
    path: '/works/review', name: 'Review',
    component: () => import('@/pages/Review.vue'),
    meta: { title: '作品审核' }
  },
  {
    path: '/works/manage', name: 'VideoManage',
    component: () => import('@/pages/works/VideoManage.vue'),
    meta: { title: '作品管理' }
  },
  {
    path: '/works/tags', name: 'TagManage',
    component: () => import('@/pages/works/TagManage.vue'),
    meta: { title: '标签管理' }
  },
  {
    path: '/works/reports', name: 'Reports',
    component: () => import('@/pages/Reports.vue'),
    meta: { title: '举报处理' }
  },

  // === 系统配置 ===
  {
    path: '/config/search', name: 'SearchAlias',
    component: () => import('@/pages/SearchAlias.vue'),
    meta: { title: '搜索配置' }
  },
  {
    path: '/config/system', name: 'Config',
    component: () => import('@/pages/SystemConfig.vue'),
    meta: { title: '系统设置' }
  },

  { path: '/:pathMatch(.*)*', redirect: '/big-screen' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  if (to.meta.noAuth) return true

  const auth = useAuthStore()
  if (!auth.isAdmin) {
    return '/login'
  }
  return true
})

export default router
