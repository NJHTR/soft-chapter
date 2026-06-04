<template>
  <div class="other-login" id="other-login">
    <BaseHeader mode="light" backMode="dark" backImg="back">
      <template v-slot:right>
        <span class="f14" @click="$router.push('/login/help')">帮助与设置</span>
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

      <div class="notice" v-if="notice">{{ notice }}</div>

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

      <dy-button type="primary" :loading="loading" :active="false" :disabled="!email.includes('@')" @click="getCode">
        获取邮箱验证码
      </dy-button>

      <div class="options">
        <span class="link" @click="$router.push('/login/password')">密码登录</span>
        <span class="link" @click="otherLogin">其他方式登录</span>
      </div>

      <from-bottom-dialog page-id="other-login" v-model="isOtherLogin" :show-heng-gang="false" height="270rem" mode="white">
        <div class="block-dialog">
          <div class="item" @click="_no"><img src="../../assets/img/icon/login/toutiao-round.png" alt="" /><span>今日头条登录</span></div>
          <div class="item" @click="_no"><img src="../../assets/img/icon/login/qq.webp" alt="" /><span>QQ登录</span></div>
          <div class="item" @click="_no"><img src="../../assets/img/icon/login/wechat.webp" alt="" /><span>微信登录</span></div>
          <div class="item" @click="_no"><img src="../../assets/img/icon/login/weibo.webp" alt="" /><span>微博登录</span></div>
          <div class="space"></div>
          <div class="item" @click="isOtherLogin = false">取消</div>
        </div>
      </from-bottom-dialog>
    </div>
  </div>
</template>
<script>
import Check from '../../components/Check'
import Tooltip from './components/Tooltip'
import Base from './Base.js'
import FromBottomDialog from '../../components/dialog/FromBottomDialog'
import { _no } from '@/utils'
import { sendEmailCode } from '@/api/auth'

export default {
  name: 'OtherLogin',
  extends: Base,
  components: { Check, Tooltip, FromBottomDialog },
  data() {
    return { email: '', notice: '' }
  },
  methods: {
    _no,
    async getCode() {
      const ok = await this.check()
      if (!ok) return
      this.loading = true
      this.notice = ''
      try {
        const res = await sendEmailCode(this.email)
        if (res.success) {
          this.$router.push('/login/verification-code?email=' + encodeURIComponent(this.email) + '&mode=email')
        } else {
          this.notice = '发送失败，请检查邮箱地址'
        }
      } finally {
        this.loading = false
      }
    },
    async otherLogin() {
      const ok = await this.check()
      if (ok) this.isOtherLogin = true
    }
  }
}
</script>

<style scoped lang="less">
@import '../../assets/less/index';
@import 'Base.less';

.other-login {
  position: fixed; left: 0; right: 0; bottom: 0; top: 0;
  overflow: auto; color: black; font-size: 14rem; background: white;

  .content {
    padding: 60rem 30rem;
    .desc { margin-bottom: 40rem; margin-top: 120rem; display: flex; align-items: center; flex-direction: column;
      .title { margin-bottom: 20rem; font-size: 20rem; }
      .sub-title { font-size: 12rem; color: var(--second-text-color); }
    }
    .input-number { display: flex; background: whitesmoke; padding: 15rem 10rem; font-size: 14rem;
      .right.flex1 { flex: 1;
        input { width: 100%; outline: none; border: none; background: whitesmoke; caret-color: red; }
      }
    }
    .button { width: 100%; margin-bottom: 5rem; }
    .protocol { position: relative; color: gray; margin-top: 20rem; font-size: 12rem; display: flex;
      .left { padding-top: 1rem; margin-right: 5rem; }
    }
    .options { position: relative; font-size: 14rem; display: flex; justify-content: space-between; margin-top: 15rem; }
    .block-dialog { color: black;
      .item { height: 50rem; display: flex; justify-content: center; align-items: center; border-top: 1px solid gainsboro;
        img { height: 25rem; margin-right: 10rem; }
        &:nth-last-child(1) { border-top: none; }
      }
      .space { height: 10rem; background: whitesmoke; }
    }
    .notice { color: #ff4d4f; font-size: 13rem; margin-top: 10rem; }
  }
}
</style>
