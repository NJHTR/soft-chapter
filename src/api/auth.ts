import { request } from '@/utils/request'

export function login(email: string, password: string) {
  return request({ url: '/login', method: 'post', data: { email, password } })
}

export function register(email: string, password: string, nickname?: string) {
  return request({ url: '/register', method: 'post', data: { email, password, nickname } })
}

export function sendEmailCode(email: string) {
  return request({ url: '/email/send-code', method: 'post', data: { email } })
}

export function loginByEmail(email: string, code: string) {
  return request({ url: '/email/login', method: 'post', data: { email, code } })
}
