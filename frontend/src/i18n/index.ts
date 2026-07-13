import { createI18n } from 'vue-i18n'
import zhCN from './locales/zh-CN'
import en from './locales/en'

export type AppLocale = 'zh-CN' | 'en'

export const SUPPORTED_LOCALES: { value: AppLocale; label: string }[] = [
  { value: 'zh-CN', label: '中文' },
  { value: 'en', label: 'English' },
]

const i18n = createI18n({
  legacy: false,
  locale: localStorage.getItem('locale') || 'zh-CN',
  fallbackLocale: 'en',
  messages: {
    'zh-CN': zhCN,
    en,
  },
})

let overlayLoadedFor: string | null = null

export async function mergeBackendMessages(locale: AppLocale): Promise<void> {
  const token = localStorage.getItem('token')
  if (!token) return
  if (overlayLoadedFor === locale) return
  try {
    const axios = (await import('axios')).default
    const backendUrl = import.meta.env.VITE_API_BASE_URL || '/api'
    const { data } = await axios.get(`${backendUrl}/i18n/messages/all`, {
      params: { locale },
      headers: { Authorization: `Bearer ${token}`, 'Accept-Language': locale },
    })
    if (data?.code === 200 && data.data && typeof data.data === 'object') {
      i18n.global.mergeLocaleMessage(locale, data.data)
      overlayLoadedFor = locale
    }
  } catch (e) {
    console.warn('[i18n] Failed to load backend overlay:', e)
  }
}

export function resetOverlayCache(): void {
  overlayLoadedFor = null
}

export default i18n
