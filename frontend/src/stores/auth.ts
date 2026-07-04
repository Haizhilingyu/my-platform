import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/modules/sys/api/auth'
import type { UserVO, MenuTreeNode, LoginRequest } from '@/modules/sys/api/types'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') || '')
  const user = ref<UserVO | null>(null)
  const permissions = ref<Set<string>>(new Set())
  const menus = ref<MenuTreeNode[]>([])

  const isLoggedIn = computed(() => !!token.value)

  function setToken(t: string) {
    token.value = t
    localStorage.setItem('token', t)
  }

  // 接收完整 LoginRequest（method/captcha 等），但 LoginVO 处理逻辑保持不变：
  // 仅 setToken + 写入 user + 拉取权限/菜单。锁定的 423 / 验证码 400 由调用方 catch。
  async function login(payload: LoginRequest) {
    const res = await authApi.login(payload)
    setToken(res.data.token)
    user.value = res.data.user
    await fetchUserInfo()
  }

  async function fetchUserInfo() {
    const [permRes, menuRes, userRes] = await Promise.all([
      authApi.getPermissions(),
      authApi.getMenus(),
      authApi.getMe(),
    ])
    permissions.value = new Set(permRes.data)
    menus.value = menuRes.data
    user.value = userRes.data
  }

  function hasPermission(perm: string): boolean {
    // admin 角色拥有所有权限
    if (permissions.value.has('*')) return true
    return permissions.value.has(perm)
  }

  function logout() {
    token.value = ''
    user.value = null
    permissions.value = new Set()
    menus.value = []
    localStorage.removeItem('token')
  }

  return {
    token,
    user,
    permissions,
    menus,
    isLoggedIn,
    setToken,
    login,
    fetchUserInfo,
    hasPermission,
    logout,
  }
})
