import { defineStore } from 'pinia'
import { ref } from 'vue'
import i18n, { type AppLocale, mergeBackendMessages } from '@/i18n'
import { http } from '@/modules/sys/api/http'

export type { AppLocale }

const STORAGE_KEY = 'locale'
const DEFAULT_LOCALE: AppLocale = 'zh-CN'

function readStoredLocale(): AppLocale {
  const stored = localStorage.getItem(STORAGE_KEY)
  return stored === 'en' || stored === 'zh-CN' ? stored : DEFAULT_LOCALE
}

export const useLocaleStore = defineStore('locale', () => {
  const currentLocale = ref<AppLocale>(readStoredLocale())

  document.documentElement.lang = currentLocale.value

  async function setLocale(locale: AppLocale) {
    currentLocale.value = locale
    i18n.global.locale.value = locale
    localStorage.setItem(STORAGE_KEY, locale)
    document.documentElement.lang = locale

    // Sync with backend (fire-and-forget, don't block UI)
    const token = localStorage.getItem('token')
    if (token) {
      try {
        await http.put('/sys/user/profile/locale', { locale })
      } catch {
        // Backend may not have B3 endpoint yet — silently ignore
        // Once B3 is deployed, this will persist the preference
      }
    }

    mergeBackendMessages(locale)
  }

  return { currentLocale, setLocale }
})
