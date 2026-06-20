import { createApp } from 'vue'
import App from './App.vue'
import './assets/less/index.less'
import router from './router'
import mixin from './utils/mixin'
import VueLazyload from '@jambonn/vue-lazyload'
import { createPinia } from 'pinia'
import { useClick } from '@/utils/hooks/useClick'
import bus, { EVENT_KEY } from '@/utils/bus'

window.isMoved = false
;(window as any).isMovedEl = null
window.isMuted = true
window.showMutedNotice = true
HTMLElement.prototype.addEventListener = new Proxy(HTMLElement.prototype.addEventListener, {
  apply(target, ctx, args) {
    const eventName = args[0]
    const listener = args[1]
    if (listener instanceof Function && eventName === 'click') {
      args[1] = new Proxy(listener, {
        apply(target1, ctx1, args1) {
          if (window.isMoved) {
            // 仅当点击发生在触发滑动的元素内部时才抑制，避免误伤其他页面
            const movedEl = (window as any).isMovedEl
            if (movedEl && movedEl.contains && movedEl.contains(args1[0]?.target)) return
            if (!movedEl) return // 没有记录元素则全局抑制（保持兼容）
          }
          try {
            return target1.apply(ctx1, args1)
          } catch (e) {
            console.error(`[proxyPlayerEvent][${eventName}]`, listener, e)
          }
        }
      })
    }
    return target.apply(ctx, args)
  }
})

const vClick = useClick()
const pinia = createPinia()
const app = createApp(App)
app.config.errorHandler = (err: any, instance: any, info: string) => {
  console.error('[Vue Error]', err, '\n  component:', instance?.$?.type?.name || instance?.type?.name || 'unknown', '\n  info:', info)
}
app.mixin(mixin)
const loadImage = new URL('./assets/img/icon/img-loading.png', import.meta.url).href
app.use(VueLazyload, {
  preLoad: 1.3,
  loading: loadImage,
  attempt: 1
})
app.use(pinia)
app.use(router)
app.mount('#app')
app.directive('click', vClick)

// 真实后端: http://localhost:8080/api
// v1.0 曾使用 startMock() 本地数据
setTimeout(() => {
  bus.emit(EVENT_KEY.HIDE_MUTED_NOTICE)
  window.showMutedNotice = false
}, 2000)
bus.on(EVENT_KEY.REMOVE_MUTED, () => {
  window.isMuted = false
})
