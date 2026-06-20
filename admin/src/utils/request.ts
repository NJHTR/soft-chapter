import axios, { type AxiosRequestConfig } from 'axios'

const axiosInstance = axios.create({
  baseURL: '/api',
  timeout: 60000
})

axiosInstance.interceptors.request.use((config) => {
  if (!config.headers['Content-Type'] && !(config.data instanceof FormData)) {
    config.headers['Content-Type'] = 'application/json'
  }
  const token = localStorage.getItem('token')
  if (token) {
    config.headers['Authorization'] = 'Bearer ' + token
  }
  return config
}, (error) => Promise.reject(error))

axiosInstance.interceptors.response.use(
  (response) => {
    const { data } = response
    if (data === undefined || data === null || data === '') {
      return { success: false, code: 500, data: [] }
    }
    if (typeof data === 'string') {
      return { success: true, code: 200, data }
    }
    if (!data.data) data.data = { ...data }
    let code = data.code
    if (code) {
      code = Number(code)
      if (code === 0) { code = 200; data.success = true }
      if (code !== 200) data.success = false
      else data.success = true
    } else {
      data.code = 200; data.success = true
    }
    return data
  },
  (error) => {
    if (!error.response) return { success: false, code: 500, msg: '服务器响应超时', data: [] }
    if (error.response.status === 401) {
      return { success: false, code: 401, msg: '请先登录', data: [] }
    }
    if (error.response.status >= 500) {
      return { success: false, code: 500, msg: '服务器错误', data: [] }
    }
    return { success: false, code: error.response.status, msg: '请求失败', data: [] }
  }
)

export async function request<T = any>(config: AxiosRequestConfig): Promise<{ success: boolean; data: T; code?: number }> {
  return axiosInstance.request<T>(config)
    .then((res: any) => ({ success: res.success === true, data: res.data ?? res }))
    .catch((err) => ({ success: false, data: err }))
}
