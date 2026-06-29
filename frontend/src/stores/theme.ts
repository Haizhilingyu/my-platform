import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export interface ThemeToken {
  primary: string
  background: string
  surface: string
  text: string
  border: string
}

const LIGHT_TOKENS: ThemeToken = {
  primary: '64 158 255',
  background: '240 242 245',
  surface: '255 255 255',
  text: '32 34 37',
  border: '229 231 235',
}

const DARK_TOKENS: ThemeToken = {
  primary: '64 158 255',
  background: '15 23 42',
  surface: '30 41 59',
  text: '241 245 249',
  border: '51 65 85',
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
