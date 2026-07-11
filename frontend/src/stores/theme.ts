import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export interface ThemeToken {
  primary: string
  background: string
  surface: string
  text: string
  textSecondary: string
  border: string
}

const LIGHT_TOKENS: ThemeToken = {
  primary: '13 148 136',
  background: '248 250 252',
  surface: '255 255 255',
  text: '15 23 42',
  textSecondary: '100 116 139',
  border: '226 232 240',
}

const DARK_TOKENS: ThemeToken = {
  primary: '45 212 191',
  background: '11 17 32',
  surface: '22 30 46',
  text: '226 232 240',
  textSecondary: '148 163 184',
  border: '30 41 59',
}

export const useThemeStore = defineStore('theme', () => {
  const isDark = ref(localStorage.getItem('theme') === 'dark')

  const token = computed<ThemeToken>(() => (isDark.value ? DARK_TOKENS : LIGHT_TOKENS))

  function toggle() {
    isDark.value = !isDark.value
    persist()
  }

  function setDark(dark: boolean) {
    isDark.value = dark
    persist()
  }

  function persist() {
    localStorage.setItem('theme', isDark.value ? 'dark' : 'light')
    document.documentElement.classList.toggle('dark', isDark.value)
  }

  // 初始化时应用
  document.documentElement.classList.toggle('dark', isDark.value)

  return { isDark, token, toggle, setDark }
})
