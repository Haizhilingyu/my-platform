import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/modules/sys/api/auth'
import type { UserVO, MenuTreeNode, LoginRequest } from '@/modules/sys/api/types'
import { mergeBackendMessages, resetOverlayCache } from '@/i18n'
import i18n from '@/i18n'

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

  async function login(payload: LoginRequest) {
    const res = await authApi.login(payload)
    setToken(res.data.token)
    user.value = res.data.user
    await fetchUserInfo()

    const currentLocale = i18n.global.locale.value as 'zh-CN' | 'en'
    mergeBackendMessages(currentLocale)
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
    if (permissions.value.has('*')) return true
    return permissions.value.has(perm)
  }

  function logout() {
    token.value = ''
    user.value = null
    permissions.value = new Set()
    menus.value = []
    localStorage.removeItem('token')
    resetOverlayCache()
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
