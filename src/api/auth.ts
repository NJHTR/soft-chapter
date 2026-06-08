import { request } from '@/utils/request'
import { deviceInfoToHeader } from '@/utils/device'

export function login(email: string, password: string) {
  return request({
    url: '/login',
    method: 'post',
    data: { email, password },
    headers: { 'X-Device-Info': deviceInfoToHeader() }
  })
}

export function register(
  email: string,
  code: string,
  password?: string,
  nickname?: string,
  role?: string,
  shopName?: string
) {
  return request({
    url: '/register',
    method: 'post',
    data: { email, code, password, nickname, role, shopName }
  })
}

export function setUserPassword(password: string) {
  return request({ url: '/user/password', method: 'put', data: { password } })
}

export function hasPassword() {
  return request({ url: '/user/has-password', method: 'get' })
}

export function sendEmailCode(email: string) {
  return request({ url: '/email/send-code', method: 'post', data: { email } })
}

export function loginByEmail(email: string, code: string) {
  return request({
    url: '/email/login',
    method: 'post',
    data: { email, code },
    headers: { 'X-Device-Info': deviceInfoToHeader() }
  })
}
