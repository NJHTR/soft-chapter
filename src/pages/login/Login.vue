<template>
  <div class="login">
    <BaseHeader mode="light" backMode="dark" backImg="close">
      <template v-slot:right>
        <span class="f14" @click="nav('/login/help')">帮助与设置</span>
      </template>
    </BaseHeader>
    <div class="content">
      <div class="desc">
        <div class="title">登录看朋友内容</div>
        <div class="sub-title">未注册的邮箱验证通过后将自动注册</div>
      </div>

      <div class="input-number">
        <div class="right flex1">
          <input v-model="email" type="text" placeholder="请输入邮箱地址" />
        </div>
      </div>

      <div class="protocol" :class="data.showAnim ? 'anim-bounce' : ''">
        <Tooltip style="top: -100%; left: -10rem" v-model="data.showTooltip" />
        <div class="left">
          <Check v-model="data.isAgree" />
        </div>
        <div class="right">
          我已阅读并同意
          <span class="link" @click="nav('/service-protocol', { type: '&quot;SeekFlow&quot;用户服务协议' })">用户协议</span>
          和
          <span class="link" @click="nav('/service-protocol', { type: '&quot;SeekFlow&quot;隐私政策' })">隐私政策</span>
          <div>
            以及
            <span class="link" @click="nav('/service-protocol', { type: '中国移动认证服务协议' })">《中国移动认证服务条款》</span>
            ，同时登录并使用SeekFlow火山版（原"火山小视频"）和SeekFlow
          </div>
        </div>
      </div>

      <dy-button
        type="primary"
        :loading="data.loading.login"
        :active="false"
        :loadingWithText="true"
        :disabled="!email.includes('@')"
        @click="login"
      >
        {{ data.loading.login ? '发送中...' : '获取邮箱验证码' }}
      </dy-button>
      <dy-button :active="false" type="white" @click="nav('/login/password')">
        邮箱密码登录
      </dy-button>

      <div class="other-login">
        <transition name="fade">
          <div v-if="data.isOtherLogin" class="icons">
            <img @click="_no" src="../../assets/img/icon/login/toutiao-round.png" alt="" />
            <img @click="_no" src="../../assets/img/icon/login/qq-round.webp" alt="" />
            <img @click="_no" src="../../assets/img/icon/login/wechat-round.png" alt="" />
            <img @click="_no" src="../../assets/img/icon/login/weibo-round.webp" alt="" />
          </div>
        </transition>
      </div>
      <transition name="fade">
        <span
          v-if="!data.isOtherLogin"
          class="other-login-text link"
          @click="data.isOtherLogin = !data.isOtherLogin"
        >其他方式登录</span>
      </transition>
    </div>
  </div>
</template>
<script setup lang="ts">
import Check from '../../components/Check.vue'
import Tooltip from './components/Tooltip.vue'
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useNav } from '@/utils/hooks/useNav'
import { _no } from '@/utils'
import { sendEmailCode } from '@/api/auth'

defineOptions({ name: 'login' })

const router = useRouter()
const nav = useNav()
const email = ref(localStorage.getItem('login_email') || '')

const data = reactive({
  isAgree: false,
  isOtherLogin: false,
  showAnim: false,
  showTooltip: false,
  loading: {
    login: false
  }
})

async function login() {
  if (!data.isAgree) {
    if (!data.showAnim && !data.showTooltip) {
      data.showAnim = true
      setTimeout(() => { data.showAnim = false; data.showTooltip = true }, 500)
      setTimeout(() => { data.showTooltip = false }, 3000)
    }
    return
  }
  if (!email.value.includes('@')) return

  data.loading.login = true
  const res = await sendEmailCode(email.value)
  data.loading.login = false
  if (res.success) {
    localStorage.setItem('login_email', email.value)
    router.push('/login/verification-code?email=' + encodeURIComponent(email.value) + '&mode=email')
  }
}
</script>

<style scoped lang="less">
@import '../../assets/less/index';

.login {
  position: fixed;
  left: 0; right: 0; bottom: 0; top: 0;
  overflow: auto;
  color: black;
  font-size: 14rem;
  background: white;

  .content {
    padding: 60rem 30rem;

    .desc {
      margin-bottom: 40rem; margin-top: 120rem;
      display: flex; align-items: center; flex-direction: column;

      .title { margin-bottom: 20rem; font-size: 20rem; }
      .sub-title { font-size: 12rem; color: var(--second-text-color); }
    }

    .input-number {
      display: flex; background: whitesmoke; padding: 15rem 10rem; font-size: 14rem; margin-bottom: 10rem;
      .right.flex1 { flex: 1;
        input { width: 100%; outline: none; border: none; background: whitesmoke; caret-color: red; }
      }
    }

    .button { width: 100%; margin-bottom: 5rem; }

    .protocol {
      position: relative; color: gray; margin: 15rem 0; font-size: 12rem; display: flex;
      .left { padding-top: 1rem; margin-right: 5rem; }
    }

    .other-login {
      position: absolute; bottom: 40rem; font-size: 12rem;
      display: flex; justify-content: center;
      width: calc(100vw - 60rem); transform: translateX(-50%); left: 50%;
      .icons img { width: 40rem; margin-right: 15rem; &:nth-last-child(1) { margin-right: 0; } }
    }

    .other-login-text {
      position: absolute; bottom: 55rem; font-size: 12rem;
      transform: translateX(-50%); left: 50%;
    }
  }
}
</style>
