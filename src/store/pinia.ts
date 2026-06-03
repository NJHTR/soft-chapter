import { defineStore } from 'pinia'
import { friends, panel } from '@/api/user'
import { login as loginApi, register as registerApi } from '@/api/auth'
import enums from '@/utils/enums'
import resource from '@/assets/data/resource'
import { _notice } from '@/utils'

export const useBaseStore = defineStore('base', {
  state: () => {
    return {
      bodyHeight: document.body.clientHeight,
      bodyWidth: document.body.clientWidth,
      maskDialog: false,
      maskDialogMode: 'dark',
      version: '17.1.0',
      excludeNames: [],
      judgeValue: 20,
      homeRefresh: 60,
      loading: false,
      routeData: null,
      users: [],
      token: localStorage.getItem('token') || '',
      isLoggedIn: !!localStorage.getItem('token'),
      userinfo: {
        nickname: '',
        desc: '',
        user_age: '',
        signature: '',
        unique_id: '',
        province: '',
        city: '',
        gender: '',
        school: {
          name: '',
          department: null,
          joinTime: null,
          education: null,
          displayType: enums.DISPLAY_TYPE.ALL
        },
        avatar_168x168: {
          url_list: []
        },
        avatar_300x300: {
          url_list: []
        },
        cover_url: [
          {
            url_list: []
          }
        ],
        white_cover_url: [
          {
            url_list: []
          }
        ]
      },
      friends: resource.users,
      message: ''
    }
  },
  getters: {
    selectFriends() {
      return this.friends.all.filter((v) => v.select)
    }
  },
  actions: {
    async init() {
      if (this.token) {
        this.isLoggedIn = true
      }
      const r = await panel()
      if (r.success) {
        this.userinfo = Object.assign(this.userinfo, r.data)
      }
      const r2 = await friends()
      if (r2.success) {
        this.users = r2.data
      }
    },
    async login(email: string, password: string): Promise<{ success: boolean; msg?: string }> {
      const r = await loginApi(email, password)
      if (r.success) {
        const token = r.data.token
        if (token) {
          this.token = token
          this.isLoggedIn = true
          localStorage.setItem('token', token)
          const p = await panel()
          if (p.success) {
            this.userinfo = Object.assign(this.userinfo, p.data)
          }
          return { success: true }
        }
      }
      return { success: false, msg: (r.data as any)?.msg || '登录失败' }
    },
    async register(email: string, password: string, nickname?: string): Promise<{ success: boolean; msg?: string }> {
      const r = await registerApi(email, password, nickname)
      if (r.success) {
        _notice('注册成功，请登录')
        return { success: true }
      }
      return { success: false, msg: (r.data as any)?.msg || '注册失败' }
    },
    logout() {
      this.token = ''
      this.isLoggedIn = false
      localStorage.removeItem('token')
      this.userinfo = {
        nickname: '',
        desc: '',
        user_age: '',
        signature: '',
        unique_id: '',
        province: '',
        city: '',
        gender: '',
        school: { name: '', department: null, joinTime: null, education: null, displayType: enums.DISPLAY_TYPE.ALL },
        avatar_168x168: { url_list: [] },
        avatar_300x300: { url_list: [] },
        cover_url: [{ url_list: [] }],
        white_cover_url: [{ url_list: [] }]
      }
      _notice('已退出登录')
    },
    setUserinfo(val) {
      this.userinfo = Object.assign(this.userinfo, val)
    },
    setMaskDialog(val) {
      this.maskDialog = val.state
      if (val.mode) {
        this.maskDialogMode = val.mode
      }
    },
    updateExcludeNames(val) {
      if (val.type === 'add') {
        if (!this.excludeNames.find((v) => v === val.value)) {
          this.excludeNames.push(val.value)
        }
      } else {
        const resIndex = this.excludeNames.findIndex((v) => v === val.value)
        if (resIndex !== -1) {
          this.excludeNames.splice(resIndex, 1)
        }
      }
    }
  }
})
