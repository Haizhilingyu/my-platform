<script setup lang="ts">
import { NConfigProvider, NMessageProvider, NDialogProvider, NLoadingBarProvider } from 'naive-ui'
import { darkTheme, type GlobalThemeOverrides } from 'naive-ui'
import { useThemeStore } from '@/stores/theme'
import { computed } from 'vue'

const themeStore = useThemeStore()

const naiveTheme = computed(() => (themeStore.isDark ? darkTheme : null))

const themeOverrides = computed<GlobalThemeOverrides>(() => ({
  common: {
    primaryColor: `rgb(${themeStore.token.primary})`,
    primaryColorHover: `rgb(${themeStore.token.primary})`,
    bodyColor: `rgb(${themeStore.token.background})`,
    cardColor: `rgb(${themeStore.token.surface})`,
    textColorBase: `rgb(${themeStore.token.text})`,
    borderColor: `rgb(${themeStore.token.border})`,
  },
}))
</script>

<template>
  <NConfigProvider :theme="naiveTheme" :theme-overrides="themeOverrides">
    <NLoadingBarProvider>
      <NMessageProvider>
        <NDialogProvider>
          <RouterView />
        </NDialogProvider>
      </NMessageProvider>
    </NLoadingBarProvider>
  </NConfigProvider>
</template>
