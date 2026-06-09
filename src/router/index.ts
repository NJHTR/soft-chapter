import { createRouter, createWebHashHistory, createWebHistory } from 'vue-router'
import routes from './routes'
import { useBaseStore } from '@/store/pinia'
import { IS_SUB_DOMAIN } from '@/config'

const router = createRouter({
  history: IS_SUB_DOMAIN ? createWebHashHistory() : createWebHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    // console.log('savedPosition', savedPosition)
    if (savedPosition) {
      return savedPosition
    } else {
      return { top: 0 }
    }
  }
})
router.beforeEach((to, from) => {
  // 管理员路由鉴权
  if (to.path.startsWith('/admin')) {
    const baseStore = useBaseStore()
    if (!baseStore.isAdmin) {
      return { path: '/home', replace: true }
    }
  }
  const baseStore = useBaseStore()
  //footer涓嬮潰鐨?涓寜閽紝瀵硅烦涓嶈鐢ㄥ姩鐢?
  const noAnimation = ['/', '/home', '/me', '/shop', '/message', '/publish', '/home/live', '/test', '/slide']
  if (noAnimation.indexOf(from.path) !== -1 && noAnimation.indexOf(to.path) !== -1) {
    return true
  }

  const toDepth = routes.findIndex((v) => v.path === to.path)
  const fromDepth = routes.findIndex((v) => v.path === from.path)
  // const fromDepth = routeDeep.indexOf(from.path)

  if (toDepth > fromDepth) {
    if (to.matched && to.matched.length) {
      const toComponentName = to.matched[0].components?.default.name
      baseStore.updateExcludeNames({ type: 'remove', value: toComponentName })
      // console.log('鍓嶈繘')
      // console.log('鍒犻櫎', toComponentName)
    }
  } else {
    if (from.matched && from.matched.length) {
      const fromComponentName = from.matched[0].components?.default.name
      baseStore.updateExcludeNames({ type: 'add', value: fromComponentName })

      // console.log('鍚庨��')
      // console.log('娣诲姞', fromComponentName)
    }
  }
  return true
})

export default router
