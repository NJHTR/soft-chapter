import { request } from '@/utils/request'

export function getBalance() {
  return request({ url: '/wallet/balance', method: 'get' })
}

export function recharge(amount: number) {
  return request({ url: '/wallet/recharge', method: 'post', data: { amount } })
}

export function getTransactions(params?: { pageNo?: number; pageSize?: number }) {
  return request({ url: '/wallet/transactions', method: 'get', params })
}
