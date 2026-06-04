<template>
  <div class="Register">
    <BaseHeader mode="light" backMode="dark" backImg="back">
      <template v-slot:right>
        <span class="f14" @click="$router.push('/login/help')">帮助与设置</span>
      </template>
    </BaseHeader>
    <div class="content">
      <div class="desc">
        <div class="title">注册SeekFlow账号</div>
        <div class="sub-title">注册后即可登录看朋友内容</div>
      </div>

      <div class="input-number">
        <div class="right flex1">
          <input v-model="email" type="text" placeholder="请输入邮箱地址" />
        </div>
      </div>
      <div class="input-number mt1r">
        <div class="right flex1">
          <input v-model="nickname" type="text" placeholder="请输入昵称（选填）" />
        </div>
      </div>
      <div class="input-number mt1r">
        <div class="right flex1">
          <input v-model="password" type="password" autocomplete="new-password" placeholder="请设置密码" />
        </div>
      </div>

      <div class="protocol" :class="showAnim ? 'anim-bounce' : ''">
        <Tooltip style="top: -150%; left: -10rem" v-model="showTooltip" />
        <div class="left"><Check v-model="isAgree" /></div>
        <div class="right">
          已阅读并同意
          <span class="link" @click="$router.push('/service-protocol', { type: '&quot;SeekFlow&quot;用户服务协议' })">用户协议</span>
          和
          <span class="link" @click="$router.push('/service-protocol', { type: '&quot;SeekFlow&quot;隐私政策' })">隐私政策</span>
          ，同时登录并使用SeekFlow火山版（原"火山小视频"）和SeekFlow
        </div>
      </div>

      <div class="notice" v-if="notice">{{ notice }}</div>

      <dy-button type="primary" :loading="loading" :active="false" :disabled="disabled" @click="doRegister">
        {{ loading ? '注册中' : '注册' }}
      </dy-button>

      <div class="options">
        <span>已有账号？<span class="link" @click="$router.push('/login/password')">去登录</span></span>
      </div>
    </div>
  </div>
</template>
<script>
import Check from '../../components/Check'
import Tooltip from './components/Tooltip'
import Base from './Base.js'
import { useBaseStore } from '@/store/pinia'

export default {
  name: 'Register',
  extends: Base,
  components: { Check, Tooltip },
  data() {
    return { email: '', password: '', nickname: '', notice: '' }
  },
  computed: {
    disabled() {
      return !(this.email.includes('@') && this.password)
    }
  },
  methods: {
    async doRegister() {
      const ok = await this.check()
      if (!ok) return
      this.loading = true
      this.notice = ''
      const store = useBaseStore()
      const result = await store.register(this.email, this.password, this.nickname || undefined)
      this.loading = false
      if (result.success) {
        this.$router.replace('/login/password')
      } else {
        this.notice = result.msg || '注册失败，请检查邮箱是否已被注册'
      }
    }
  }
}
</script>

<style scoped lang="less">
@import '../../assets/less/index';
@import 'Base.less';

.Register {
  position: fixed; left: 0; right: 0; bottom: 0; top: 0;
  overflow: auto; color: black; font-size: 14rem; background: white;

  .content {
    padding: 60rem 30rem;
    .desc { margin-bottom: 60rem; margin-top: 120rem; display: flex; align-items: center; flex-direction: column;
      .title { margin-bottom: 20rem; font-size: 20rem; }
      .sub-title { font-size: 12rem; color: var(--second-text-color); }
    }
    .input-number { display: flex; background: whitesmoke; padding: 15rem 10rem; font-size: 14rem;
      .right.flex1 { flex: 1;
        input { width: 100%; outline: none; border: none; background: whitesmoke; caret-color: red; }
      }
    }
    .mt1r { margin-top: 1rem; }
    .button { width: 100%; margin-bottom: 5rem; }
    .protocol { position: relative; color: gray; margin-top: 20rem; font-size: 12rem; display: flex;
      .left { padding-top: 1rem; margin-right: 5rem; }
    }
    .options { position: relative; font-size: 14rem; display: flex; justify-content: center; margin-top: 15rem; }
    .notice { color: #ff4d4f; font-size: 13rem; margin-top: 10rem; }
  }
}
</style>
