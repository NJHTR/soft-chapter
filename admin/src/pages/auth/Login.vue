<template>
  <div class="auth-page">
    <!-- 右半区：渐变白遮罩 + 表单 -->
    <div class="auth-form-area">
      <div class="form-content">
        <div class="auth-header">
          <h1>SeekFlow</h1>
          <p>运营管理平台</p>
        </div>

        <div v-if="errorMsg" class="auth-error">{{ errorMsg }}</div>

        <form @submit.prevent="handleLogin" class="auth-form">
          <div class="form-group">
            <label>邮箱</label>
            <input
              v-model="form.email"
              type="email"
              placeholder="请输入管理员邮箱"
              autocomplete="email"
              required
            />
          </div>
          <div class="form-group">
            <label>密码</label>
            <input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              autocomplete="current-password"
              required
            />
          </div>
          <div class="form-extra">
            <label class="remember">
              <input type="checkbox" v-model="rememberMe" /> 记住我
            </label>
          </div>
          <button type="submit" class="auth-btn" :disabled="loading">
            {{ loading ? '登录中...' : '登 录' }}
          </button>
        </form>

        <div class="auth-footer">
          还没有账号？
          <router-link to="/register">注册管理员</router-link>
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

const form = reactive({ email: '', password: '' })
const rememberMe = ref(true)
const loading = ref(false)
const errorMsg = ref('')

async function handleLogin() {
  errorMsg.value = ''
  if (!form.email || !form.password) {
    errorMsg.value = '请填写邮箱和密码'
    return
  }
  loading.value = true
  try {
    const res = await auth.login(form.email, form.password)
    if (res.success) {
      router.replace('/big-screen')
    } else {
      errorMsg.value = res.msg || '登录失败，请检查账号密码'
    }
  } catch (e) {
    errorMsg.value = '网络错误，请稍后重试'
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

/* 右半区渐变白遮罩：右边不透明，到插画位置（左38%）渐隐 */
.auth-form-area {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  width: 70%;
  min-width: 520px;
  padding: 60px 8vw 60px 10vw;
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
  margin-bottom: 36px;
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

.auth-form .form-group {
  margin-bottom: 20px;
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

.form-extra {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  font-size: 13px;
  color: #777;
}
.remember {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
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

/* 窄屏回退到居中卡片 */
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
