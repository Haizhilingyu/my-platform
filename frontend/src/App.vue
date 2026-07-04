<script setup lang="ts">
import { NConfigProvider, NMessageProvider, NDialogProvider, NNotificationProvider, NLoadingBarProvider } from 'naive-ui'
import { darkTheme, type GlobalThemeOverrides } from 'naive-ui'
import { useThemeStore } from '@/stores/theme'
import { computed } from 'vue'

const themeStore = useThemeStore()

const naiveTheme = computed(() => (themeStore.isDark ? darkTheme : null))

// token 以空格分隔（兼容 Tailwind 的 `rgb(var(--x) / <alpha>)` 语法），
// 而 Naive UI 底层的 seemly/rgba 只接受逗号分隔的 `rgb(r, g, b)`，这里做一次转换。
const rgb = (value: string) => `rgb(${value.replace(/\s+/g, ', ')})`

const themeOverrides = computed<GlobalThemeOverrides>(() => ({
  common: {
    primaryColor: rgb(themeStore.token.primary),
    primaryColorHover: rgb(themeStore.token.primary),
    bodyColor: rgb(themeStore.token.background),
    cardColor: rgb(themeStore.token.surface),
    textColorBase: rgb(themeStore.token.text),
    borderColor: rgb(themeStore.token.border),
  },
}))
</script>

<template>
  <NConfigProvider :theme="naiveTheme" :theme-overrides="themeOverrides">
    <NLoadingBarProvider>
      <NMessageProvider>
        <NDialogProvider>
          <NNotificationProvider>
            <RouterView />
          </NNotificationProvider>
        </NDialogProvider>
      </NMessageProvider>
    </NLoadingBarProvider>
  </NConfigProvider>
</template>
