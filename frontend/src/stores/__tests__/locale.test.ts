import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useLocaleStore } from '@/stores/locale'
import i18n from '@/i18n'

describe('Locale Store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    i18n.global.locale.value = 'zh-CN'
    document.documentElement.lang = ''
  })

  describe('初始状态', () => {
    it('should default to zh-CN when localStorage is empty', () => {
      const store = useLocaleStore()
      expect(store.currentLocale).toBe('zh-CN')
    })

    it('should read en from localStorage if present', () => {
      localStorage.setItem('locale', 'en')
      const store = useLocaleStore()
      expect(store.currentLocale).toBe('en')
    })

    it('should fall back to zh-CN when localStorage has unsupported value', () => {
      localStorage.setItem('locale', 'fr')
      const store = useLocaleStore()
      expect(store.currentLocale).toBe('zh-CN')
    })

    it('should set document.documentElement.lang on store creation', () => {
      useLocaleStore()
      expect(document.documentElement.lang).toBe('zh-CN')
    })
  })

  describe('setLocale', () => {
    it('should update store state when switching to en', () => {
      const store = useLocaleStore()
      store.setLocale('en')
      expect(store.currentLocale).toBe('en')
    })

    it('should update i18n global locale when switching to en', () => {
      const store = useLocaleStore()
      store.setLocale('en')
      expect(i18n.global.locale.value).toBe('en')
    })

    it('should persist locale to localStorage when switching to en', () => {
      const store = useLocaleStore()
      store.setLocale('en')
      expect(localStorage.getItem('locale')).toBe('en')
    })

    it('should update document.documentElement.lang when switching to en', () => {
      const store = useLocaleStore()
      store.setLocale('en')
      expect(document.documentElement.lang).toBe('en')
    })

    it('should switch back from en to zh-CN', () => {
      const store = useLocaleStore()
      store.setLocale('en')
      store.setLocale('zh-CN')

      expect(store.currentLocale).toBe('zh-CN')
      expect(i18n.global.locale.value).toBe('zh-CN')
      expect(localStorage.getItem('locale')).toBe('zh-CN')
      expect(document.documentElement.lang).toBe('zh-CN')
    })
  })
})
