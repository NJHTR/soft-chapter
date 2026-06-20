<template>
  <div class="auth-page">
    <div class="auth-card">
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
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.auth-card {
  background: #fff;
  border-radius: 12px;
  padding: 40px;
  width: 400px;
  box-shadow: 0 20px 60px rgba(0,0,0,0.2);
}
.auth-header {
  text-align: center;
  margin-bottom: 32px;
}
.auth-header h1 {
  font-size: 28px;
  color: #fe2c55;
  margin: 0;
}
.auth-header p {
  color: #999;
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
  margin-bottom: 18px;
}
.auth-form label {
  display: block;
  font-size: 13px;
  color: #666;
  margin-bottom: 6px;
}
.auth-form input[type="email"],
.auth-form input[type="password"],
.auth-form input[type="text"] {
  width: 100%;
  padding: 10px 14px;
  border: 1px solid #ddd;
  border-radius: 6px;
  font-size: 14px;
  outline: none;
  transition: border-color 0.2s;
  box-sizing: border-box;
}
.auth-form input:focus {
  border-color: #fe2c55;
  box-shadow: 0 0 0 3px rgba(254,44,85,0.1);
}
.form-extra {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 18px;
  font-size: 13px;
  color: #888;
}
.remember {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
}
.auth-btn {
  width: 100%;
  padding: 12px;
  background: linear-gradient(135deg, #fe2c55, #ff4d6a);
  color: #fff;
  border: none;
  border-radius: 6px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.2s;
}
.auth-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}
.auth-btn:hover:not(:disabled) {
  opacity: 0.9;
}
.auth-footer {
  text-align: center;
  margin-top: 24px;
  font-size: 13px;
  color: #999;
}
.auth-footer a {
  color: #fe2c55;
  text-decoration: none;
  font-weight: 500;
}
</style>
