import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { request } from '@/utils/request'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') || '')
  const role = ref(localStorage.getItem('role') || '')
  const nickname = ref(localStorage.getItem('nickname') || '')
  const userId = ref(localStorage.getItem('userId') || '')

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => isLoggedIn.value && role.value === 'ADMIN')

  function saveAuth(t: string, r: string, nick: string, uid: string | number) {
    token.value = t
    role.value = r
    nickname.value = nick || ''
    userId.value = String(uid)
    localStorage.setItem('token', t)
    localStorage.setItem('role', r)
    localStorage.setItem('nickname', nick || '')
    localStorage.setItem('userId', String(uid))
  }

  function clearAuth() {
    token.value = ''
    role.value = ''
    nickname.value = ''
    userId.value = ''
    localStorage.removeItem('token')
    localStorage.removeItem('role')
    localStorage.removeItem('nickname')
    localStorage.removeItem('userId')
  }

  async function login(email: string, password: string) {
    const res: any = await request({
      url: '/login',
      method: 'post',
      data: { email, password }
    })
    if (res.success && res.data?.token) {
      const { token: t, role: r, userId: uid } = res.data
      if (r !== 'ADMIN') {
        return { success: false, msg: '此账号不是管理员，无法登录运营平台' }
      }
      saveAuth(t, r, '', uid)
      try {
        const profileRes: any = await request({
          url: '/user/profile',
          method: 'get',
          headers: { Authorization: 'Bearer ' + t }
        })
        if (profileRes.success && profileRes.data) {
          const nick = profileRes.data.nickname || ''
          nickname.value = nick
          localStorage.setItem('nickname', nick)
        }
      } catch (e) { /* ignore */ }
      return { success: true }
    }
    return { success: false, msg: res.msg || res.data?.msg || '登录失败' }
  }

  async function register(data: { email: string; password: string; nickname: string; code: string }) {
    const res: any = await request({
      url: '/register',
      method: 'post',
      data: { ...data, role: 'ADMIN' }
    })
    if (res.success) return { success: true }
    return { success: false, msg: res.msg || '注册失败' }
  }

  async function sendVerifyCode(email: string) {
    const res: any = await request({
      url: '/email/send-code',
      method: 'post',
      data: { email }
    })
    return { success: res.success, msg: res.msg }
  }

  function logout() {
    clearAuth()
    window.location.reload()
  }

  return { token, role, nickname, userId, isLoggedIn, isAdmin, login, register, sendVerifyCode, logout, clearAuth }
})
