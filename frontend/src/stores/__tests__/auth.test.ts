import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'

/**
 * Auth Store 测试。
 *
 * TDD 示范：测试命名 should...when... 结构。
 */
describe('Auth Store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  describe('初始状态', () => {
    it('should have empty token when initialized', () => {
      const store = useAuthStore()
      expect(store.token).toBe('')
      expect(store.isLoggedIn).toBe(false)
    })

    it('should have empty permissions when initialized', () => {
      const store = useAuthStore()
      expect(store.permissions.size).toBe(0)
    })
  })

  describe('hasPermission', () => {
    it('should return true when user has the permission', () => {
      const store = useAuthStore()
      store.permissions = new Set(['sys:user:add', 'sys:role:list'])

      expect(store.hasPermission('sys:user:add')).toBe(true)
    })

    it('should return false when user lacks the permission', () => {
      const store = useAuthStore()
      store.permissions = new Set(['sys:user:add'])

      expect(store.hasPermission('sys:user:delete')).toBe(false)
    })

    it('should return false when permissions set is empty', () => {
      const store = useAuthStore()

      expect(store.hasPermission('sys:user:add')).toBe(false)
    })
  })

  describe('logout', () => {
    it('should clear all state when logout', () => {
      const store = useAuthStore()
      store.token = 'some-token'
      store.permissions = new Set(['sys:user:add'])
      localStorage.setItem('token', 'some-token')

      store.logout()

      expect(store.token).toBe('')
      expect(store.permissions.size).toBe(0)
      expect(store.isLoggedIn).toBe(false)
      expect(localStorage.getItem('token')).toBeNull()
    })
  })

  describe('setToken', () => {
    it('should store token in state and localStorage', () => {
      const store = useAuthStore()
      store.setToken('jwt-abc-123')

      expect(store.token).toBe('jwt-abc-123')
      expect(localStorage.getItem('token')).toBe('jwt-abc-123')
    })
  })
})
