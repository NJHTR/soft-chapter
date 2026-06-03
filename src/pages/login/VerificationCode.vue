<template>
  <div class="VerificationCode">
    <BaseHeader mode="light" backMode="dark" backImg="back">
      <template v-slot:right>
        <span class="f14" @click="nav('/login/help')">帮助与设置</span>
      </template>
    </BaseHeader>
    <div class="content">
      <div class="desc">
        <div class="title">请输入验证码</div>
        <div class="sub-title">验证码已发送到 {{ target }}</div>
      </div>

      <LoginInput autofocus type="code" v-model="data.code" placeholder="请输入6位验证码"
        v-model:isSendVerificationCode="data.isSendVerificationCode" @send="sendCode" />
      <div class="options" v-if="data.showVoiceCode">
        <span>收不到验证码？<span class="link" @click="sendCode">重新发送</span></span>
      </div>

      <div class="notice" v-if="data.notice">{{ data.notice }}</div>

      <dy-button type="primary" :loading="data.loading" :active="false" :disabled="data.code.length < 4" @click="login">
        {{ data.loading ? '登录中' : '登录' }}
      </dy-button>
    </div>
  </div>
</template>
<script setup lang="ts">
import LoginInput from './components/LoginInput.vue'
import { onMounted, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useNav } from '@/utils/hooks/useNav'
import { _showLoading, _hideLoading } from '@/utils'
import { loginByEmail, sendEmailCode } from '@/api/auth'
import { useBaseStore } from '@/store/pinia'
import { panel } from '@/api/user'

defineOptions({ name: 'VerificationCode' })

const route = useRoute()
const router = useRouter()
const nav = useNav()
const store = useBaseStore()
const target = (route.query.email as string) || ''

const data = reactive({
  loading: false, code: '', notice: '',
  isSendVerificationCode: true, showVoiceCode: false
})

onMounted(() => {
  setTimeout(() => { data.showVoiceCode = true }, 3000)
})

async function sendCode() {
  if (!target) return
  _showLoading()
  await sendEmailCode(target)
  _hideLoading()
  data.isSendVerificationCode = true
}

async function login() {
  if (data.code.length < 4) return
  data.loading = true
  data.notice = ''
  try {
    const res = await loginByEmail(target, data.code)
    if (res.success) {
      store.token = res.data.token
      store.isLoggedIn = true
      localStorage.setItem('token', res.data.token)
      localStorage.setItem('login_email', target)
      const p = await panel()
      if (p.success) {
        store.setUserinfo(p.data)
      }
      router.replace('/me')
    } else {
      data.notice = '验证码错误或已过期'
    }
  } finally {
    data.loading = false
  }
}
</script>

<style scoped lang="less">
@import '../../assets/less/index';
@import 'Base.less';

.VerificationCode {
  position: fixed; left: 0; right: 0; bottom: 0; top: 0;
  overflow: auto; color: black; font-size: 14rem; background: white;

  .content {
    padding: 60rem 30rem;
    .desc { margin-bottom: 60rem; margin-top: 120rem; display: flex; align-items: center; flex-direction: column;
      .title { margin-bottom: 20rem; font-size: 20rem; }
      .sub-title { font-size: 12rem; color: var(--second-text-color); }
    }
    .button { width: 100%; margin: 20rem 0 5rem; }
  }
  .options { margin-top: 10rem; padding: 0 30rem; font-size: 14rem; }
  .notice { color: #ff4d4f; font-size: 13rem; margin-top: 10rem; padding: 0 30rem; }
}
</style>
