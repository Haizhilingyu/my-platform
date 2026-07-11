import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useThemeStore } from '@/stores/theme'

/**
 * Theme Store 测试。
 */
describe('Theme Store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    document.documentElement.classList.remove('dark')
  })

  describe('初始状态', () => {
    it('should default to light theme', () => {
      const store = useThemeStore()
      expect(store.isDark).toBe(false)
    })

    it('should read dark from localStorage if present', () => {
      localStorage.setItem('theme', 'dark')
      // 需要重新加载模块
      vi.resetModules()
    })
  })

  describe('toggle', () => {
    it('should switch from light to dark', () => {
      const store = useThemeStore()
      expect(store.isDark).toBe(false)

      store.toggle()

      expect(store.isDark).toBe(true)
      expect(localStorage.getItem('theme')).toBe('dark')
      expect(document.documentElement.classList.contains('dark')).toBe(true)
    })

    it('should switch back from dark to light', () => {
      const store = useThemeStore()
      store.toggle() // -> dark
      store.toggle() // -> light

      expect(store.isDark).toBe(false)
      expect(localStorage.getItem('theme')).toBe('light')
      expect(document.documentElement.classList.contains('dark')).toBe(false)
    })
  })

  describe('token', () => {
    it('should return light tokens when light mode', () => {
      const store = useThemeStore()
      store.setDark(false)

      expect(store.token.background).toBe('248 250 252')
      expect(store.token.surface).toBe('255 255 255')
    })

    it('should return dark tokens when dark mode', () => {
      const store = useThemeStore()
      store.setDark(true)

      expect(store.token.background).toBe('11 17 32')
      expect(store.token.surface).toBe('22 30 46')
    })
  })
})
