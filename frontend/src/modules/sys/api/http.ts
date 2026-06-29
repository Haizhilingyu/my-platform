import axios from 'axios'
// types are used in the generic signatures of calling modules

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
})

// 请求拦截器：注入 token
http.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截器：统一处理错误
http.interceptors.response.use(
  (response) => response.data,
  (error) => {
    if (error.response?.status === 401) {
      // 区分两种 401：
      // 1) 登录请求（本身不带 token）密码错误 → 业务自行 catch 显示 toast，不跳转
      // 2) 已登录用户 token 失效（请求带过 token）→ 清除登录态并跳转登录页
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
