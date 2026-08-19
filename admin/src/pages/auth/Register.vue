<template>
  <div class="auth-page">
    <!-- 右半区：渐变白遮罩 + 注册表单 -->
    <div class="auth-form-area">
      <div class="form-content">
        <div class="auth-header">
          <h1>SeekFlow</h1>
          <p>注册管理员账号</p>
        </div>

        <div v-if="errorMsg" class="auth-error">{{ errorMsg }}</div>
        <div v-if="successMsg" class="auth-success">{{ successMsg }}</div>

        <form @submit.prevent="handleRegister" class="auth-form">
          <div class="form-group">
            <label>邮箱</label>
            <input v-model="form.email" type="email" placeholder="请输入邮箱" required />
          </div>
          <div class="form-group">
            <label>昵称</label>
            <input v-model="form.nickname" type="text" placeholder="请输入昵称" required />
          </div>
          <div class="form-group">
            <label>密码</label>
            <input v-model="form.password" type="password" placeholder="至少6位密码" minlength="6" required />
          </div>
          <div class="form-group">
            <label>验证码</label>
            <div class="code-row">
              <input v-model="form.code" type="text" placeholder="邮箱验证码" required style="flex:1;" />
              <button type="button" class="code-btn" :disabled="codeSending || countdown > 0" @click="sendCode">
                {{ countdown > 0 ? countdown + 's' : codeSending ? '发送中...' : '获取验证码' }}
              </button>
            </div>
          </div>
          <button type="submit" class="auth-btn" :disabled="loading">
            {{ loading ? '注册中...' : '注 册' }}
          </button>
        </form>

        <div class="auth-footer">
          已有账号？
          <router-link to="/login">返回登录</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

const form = reactive({ email: '', nickname: '', password: '', code: '' })
const loading = ref(false)
const codeSending = ref(false)
const countdown = ref(0)
const errorMsg = ref('')
const successMsg = ref('')

async function sendCode() {
  if (!form.email) {
    errorMsg.value = '请先填写邮箱'
    return
  }
  codeSending.value = true
  errorMsg.value = ''
  try {
    const res = await auth.sendVerifyCode(form.email)
    if (res.success) {
      countdown.value = 60
      const timer = setInterval(() => {
        countdown.value--
        if (countdown.value <= 0) clearInterval(timer)
      }, 1000)
    } else {
      errorMsg.value = res.msg || '验证码发送失败'
    }
  } catch (e) {
    errorMsg.value = '网络错误'
  } finally {
    codeSending.value = false
  }
}

async function handleRegister() {
  errorMsg.value = ''
  successMsg.value = ''
  if (!form.email || !form.password || !form.nickname || !form.code) {
    errorMsg.value = '请填写所有字段'
    return
  }
  if (form.password.length < 6) {
    errorMsg.value = '密码至少6位'
    return
  }
  loading.value = true
  try {
    const res = await auth.register({ ...form })
    if (res.success) {
      successMsg.value = '注册成功！3秒后跳转到登录页...'
      setTimeout(() => router.replace('/login'), 3000)
    } else {
      errorMsg.value = res.msg || '注册失败'
    }
  } catch (e) {
    errorMsg.value = '网络错误'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page {
  position: relative;
  display: flex;
  justify-content: flex-end;
  min-height: 100vh;
  background: url('@/assets/img/icon_background2.png') center / cover no-repeat;
  image-rendering: auto;
}

.auth-form-area {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  width: 70%;
  min-width: 520px;
  padding: 50px 8vw 50px 10vw;
  background: linear-gradient(
    to left,
    rgba(255,255,255,0.97) 0%,
    rgba(255,255,255,0.93) 20%,
    rgba(255,255,255,0.82) 40%,
    rgba(255,255,255,0.55) 60%,
    rgba(255,255,255,0.15) 80%,
    transparent 100%
  );
}

.form-content {
  width: 380px;
  max-width: 100%;
}

.auth-header {
  margin-bottom: 32px;
}
.auth-header h1 {
  font-size: 32px;
  color: #fe2c55;
  margin: 0;
  font-weight: 700;
}
.auth-header p {
  color: #888;
  margin-top: 8px;
  font-size: 14px;
}

.auth-error {
  background: #fef2f2;
  color: #dc2626;
  padding: 10px 14px;
  border-radius: 6px;
  font-size: 13px;
  margin-bottom: 16px;
}
.auth-success {
  background: #f0fdf4;
  color: #16a34a;
  padding: 10px 14px;
  border-radius: 6px;
  font-size: 13px;
  margin-bottom: 16px;
}

.auth-form .form-group {
  margin-bottom: 18px;
}
.auth-form label {
  display: block;
  font-size: 13px;
  color: #555;
  margin-bottom: 6px;
  font-weight: 500;
}
.auth-form input[type="email"],
.auth-form input[type="password"],
.auth-form input[type="text"] {
  width: 100%;
  padding: 12px 16px;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  font-size: 14px;
  outline: none;
  transition: border-color 0.2s, box-shadow 0.2s;
  box-sizing: border-box;
  background: rgba(255,255,255,0.7);
}
.auth-form input:focus {
  border-color: #fe2c55;
  box-shadow: 0 0 0 3px rgba(254,44,85,0.08);
  background: #fff;
}

.code-row {
  display: flex;
  gap: 10px;
}
.code-btn {
  flex-shrink: 0;
  padding: 10px 16px;
  background: #fff;
  color: #fe2c55;
  border: 1px solid #fe2c55;
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.2s, color 0.2s;
}
.code-btn:hover:not(:disabled) {
  background: #fe2c55;
  color: #fff;
}
.code-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.auth-btn {
  width: 100%;
  padding: 13px;
  background: linear-gradient(135deg, #fe2c55, #ff4d6a);
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.2s, transform 0.15s;
}
.auth-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}
.auth-btn:hover:not(:disabled) {
  opacity: 0.92;
  transform: translateY(-1px);
}

.auth-footer {
  text-align: center;
  margin-top: 28px;
  font-size: 13px;
  color: #999;
}
.auth-footer a {
  color: #fe2c55;
  text-decoration: none;
  font-weight: 500;
}

@media (max-width: 768px) {
  .auth-page {
    justify-content: center;
  }
  .auth-form-area {
    width: 100%;
    min-width: unset;
    padding: 40px 24px;
    justify-content: center;
    background: rgba(255,255,255,0.88);
    backdrop-filter: blur(8px);
  }
  .form-content {
    width: 100%;
    max-width: 400px;
  }
}
</style>
