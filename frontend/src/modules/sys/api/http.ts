import axios from 'axios'
import i18n from '@/i18n'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  config.headers['Accept-Language'] = localStorage.getItem('locale') || 'zh-CN'
  return config
})

http.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const result = error.response?.data
    if (result?.messageKey && i18n.global.te(result.messageKey)) {
      result.message = i18n.global.t(result.messageKey)
    }

    if (error.response?.status === 401) {
      const hadToken = !!error.config?.headers?.Authorization
      if (hadToken) {
        localStorage.removeItem('token')
        window.location.href = '/login'
      }
    }
    return Promise.reject(error)
  }
)

export { http }
