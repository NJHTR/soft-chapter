<template>
  <router-view v-slot="{ Component }">
    <transition :name="transitionName">
      <keep-alive :exclude="store.excludeNames">
        <component :is="Component" />
      </keep-alive>
    </transition>
  </router-view>
  <Call />
  <CallPanel />
</template>
<script setup lang="ts">
/*
* try {navigator.control.gesture(false);} catch (e) {} //UC浏览器关闭默认手势事件
try {navigator.control.longpressMenu(false);} catch (e) {} //关闭长按弹出菜单
* */
import routes from './router/routes'
import Call from './components/Call.vue'
import CallPanel from '@/modules/rtc/components/CallPanel.vue'
import { useBaseStore } from '@/store/pinia.js'
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { connectSocket } from '@/utils/socket'
import bus from '@/utils/bus'
import {
  installNotificationFeedbackUnlock,
  notificationKindFromType,
  playNotificationFeedback
} from '@/utils/notificationFeedback'

const store = useBaseStore()
const route = useRoute()
const transitionName = ref('go')
let removeFeedbackUnlock = () => {}

function isFromCurrentUser(message: any): boolean {
  const currentUid = String(store.userinfo?.uid ?? '')
  if (!currentUid) return false
  return [message?.from_user_id, message?.fromUserId]
    .filter((value) => value !== undefined && value !== null)
    .some((value) => String(value) === currentUid)
}

function playIncomingFeedback(kind: Parameters<typeof playNotificationFeedback>[0], message: any) {
  if (isFromCurrentUser(message)) return
  void playNotificationFeedback(kind)
}

function onChatMessage(message: any) {
  playIncomingFeedback('chat', message)
}

function onGroupMessage(message: any) {
  playIncomingFeedback('group', message)
}

function onNotification(message: any) {
  playIncomingFeedback(notificationKindFromType(message?.type), message)
}

function onCallSignal(message: any) {
  // RTC 通话的持续回铃/来电提示由 useRtcStore 按生命周期管理，避免
  // call_request 在这里再额外播放一次导致双重声音。
  void message
}

// watch $route 决定使用哪种过渡
watch(
  () => route.path,
  (to, from) => {
    store.setMaskDialog({ state: false, mode: store.maskDialogMode })
    //底部tab的按钮，跳转是不需要用动画的
    let noAnimation = [
      '/',
      '/home',
      '/slide',
      '/me',
      '/shop',
      '/message',
      '/publish',
      '/home/live',
      'slide',
      '/test'
    ]
    if (noAnimation.indexOf(from) !== -1 && noAnimation.indexOf(to) !== -1) {
      return (transitionName.value = '')
    }
    const toDepth = routes.findIndex((v: RouteRecordRaw) => v.path === to)
    const fromDepth = routes.findIndex((v: RouteRecordRaw) => v.path === from)
    transitionName.value = toDepth > fromDepth ? 'go' : 'back'
  }
)

function resetVhAndPx() {
  let vh = window.innerHeight * 0.01
  document.documentElement.style.setProperty('--vh', `${vh}px`)
  //document.documentElement.style.fontSize = document.documentElement.clientWidth / 375 + 'px'
}

onMounted(() => {
  store.init()
  connectSocket().catch(() => {})
  removeFeedbackUnlock = installNotificationFeedbackUnlock()
  bus.on('CHAT_MESSAGE', onChatMessage)
  bus.on('GROUP_MESSAGE', onGroupMessage)
  bus.on('NEW_NOTIFICATION', onNotification)
  bus.on('CALL_SIGNAL', onCallSignal)
  resetVhAndPx()
  // 监听resize事件 视图大小发生变化就重新计算1vh的值
  window.addEventListener('resize', () => {
    resetVhAndPx()
  })
})

onUnmounted(() => {
  removeFeedbackUnlock()
  bus.off('CHAT_MESSAGE', onChatMessage)
  bus.off('GROUP_MESSAGE', onGroupMessage)
  bus.off('NEW_NOTIFICATION', onNotification)
  bus.off('CALL_SIGNAL', onCallSignal)
})
</script>

<style lang="less">
@import './assets/less/index';

* {
  user-select: none;
}

input,
textarea {
  user-select: auto;
}

#app {
  height: 100%;
  width: 100%;
  position: relative;
  font-size: 14rem;
}

@media screen and (min-width: 500px) {
  #app {
    width: 500px !important;
    position: relative;
    left: 50%;
    transform: translateX(-50%);
  }
}

.go-enter-from {
  transform: translate3d(100%, 0, 0);
}

//最终状态
.back-enter-to,
.back-enter-from,
.go-enter-to,
.go-leave-from {
  transform: translate3d(0, 0, 0);
}

.go-leave-to {
  transform: translate3d(-100%, 0, 0);
}

.go-enter-active,
.go-leave-active,
.back-enter-active,
.back-leave-active {
  transition: all 0.3s;
}

.back-enter-from {
  transform: translate3d(-100%, 0, 0);
}

.back-leave-to {
  transform: translate3d(100%, 0, 0);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
