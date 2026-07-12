<script setup lang="ts">
import { NConfigProvider, NMessageProvider, NDialogProvider, NNotificationProvider, NLoadingBarProvider } from 'naive-ui'
import { darkTheme, zhCN, dateZhCN, enUS, dateEnUS, type GlobalThemeOverrides } from 'naive-ui'
import { useThemeStore } from '@/stores/theme'
import { useLocaleStore } from '@/stores/locale'
import { computed } from 'vue'
import MessageCenter from '@/shared/components/MessageCenter.vue'

const themeStore = useThemeStore()
const localeStore = useLocaleStore()

const naiveTheme = computed(() => (themeStore.isDark ? darkTheme : null))
const naiveLocale = computed(() => (localeStore.currentLocale === 'zh-CN' ? zhCN : enUS))
const naiveDateLocale = computed(() => (localeStore.currentLocale === 'zh-CN' ? dateZhCN : dateEnUS))

const rgb = (value: string) => `rgb(${value.replace(/\s+/g, ', ')})`
const rgbAlpha = (value: string, alpha: number) => `rgba(${value.replace(/\s+/g, ', ')}, ${alpha})`

const themeOverrides = computed<GlobalThemeOverrides>(() => {
  const t = themeStore.token
  const primary = rgb(t.primary)
  const primaryHover = themeStore.isDark ? 'rgb(56, 217, 200)' : 'rgb(15, 118, 110)'
  const primaryPressed = themeStore.isDark ? 'rgb(38, 196, 181)' : 'rgb(13, 130, 121)'
  const primarySuppl = primaryHover

  return {
    common: {
      primaryColor: primary,
      primaryColorHover: primaryHover,
      primaryColorPressed: primaryPressed,
      primaryColorSuppl: primarySuppl,
      bodyColor: rgb(t.background),
      cardColor: rgb(t.surface),
      modalColor: rgb(t.surface),
      popoverColor: rgb(t.surface),
      tableColor: rgb(t.surface),
      inputColor: rgb(themeStore.isDark ? '22 30 46' : '248 250 252'),
      textColorBase: rgb(t.text),
      textColor1: rgb(t.text),
      textColor2: rgb(t.text),
      textColor3: rgb(t.textSecondary ?? t.border),
      placeholderColor: rgbAlpha(t.border, 0.7),
      borderColor: rgb(t.border),
      dividerColor: rgb(t.border),
      hoverColor: rgb(themeStore.isDark ? '30 41 59' : '241 245 249'),
      fontFamily: "'Space Grotesk', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', system-ui, sans-serif",
      fontFamilyMono: "'JetBrains Mono', 'SF Mono', 'Menlo', monospace",
      fontWeight: '400',
      fontWeightStrong: '600',
      borderRadius: '8px',
      borderRadiusSmall: '6px',
    },
    Button: {
      textColorPrimary: themeStore.isDark ? '#0B1120' : '#FFFFFF',
      textColorHoverPrimary: themeStore.isDark ? '#0B1120' : '#FFFFFF',
      textColorFocusPrimary: themeStore.isDark ? '#0B1120' : '#FFFFFF',
      textColorPressedPrimary: themeStore.isDark ? '#0B1120' : '#FFFFFF',
      fontWeight: '500',
    },
    Card: {
      borderRadius: '12px',
      paddingMedium: '20px 24px',
    },
    Menu: {
      itemTextColor: rgbAlpha(t.text, 0.7),
      itemTextColorHover: primary,
      itemTextColorActive: primary,
      itemTextColorActiveHover: primary,
      itemColorActive: rgbAlpha(t.primary, themeStore.isDark ? 0.12 : 0.08),
      itemColorActiveHover: rgbAlpha(t.primary, themeStore.isDark ? 0.15 : 0.1),
      itemColorActiveCollapsed: rgbAlpha(t.primary, themeStore.isDark ? 0.12 : 0.08),
      itemIconColor: rgbAlpha(t.text, 0.6),
      itemIconColorHover: primary,
      itemIconColorActive: primary,
      itemIconColorActiveHover: primary,
      itemHeight: '40px',
    },
    DataTable: {
      thColor: rgb(themeStore.isDark ? '22 30 46' : '248 250 252'),
      thTextColor: rgbAlpha(t.text, 0.55),
      tdColorHover: rgb(themeStore.isDark ? '26 35 50' : '248 250 252'),
      borderRadius: '10px',
      fontSizeMedium: '14px',
    },
    Input: {
      borderRadius: '8px',
      borderHover: primary,
      borderFocus: primary,
      boxShadowFocus: rgbAlpha(t.primary, 0.15),
    },
    Tag: {
      borderRadius: '6px',
      fontWeightStrong: '600',
    },
    Tabs: {
      tabTextColorActiveLine: primary,
      tabTextColorHoverLine: primary,
      barColor: primary,
    },
    Modal: {
      borderRadius: '12px',
    },
    Form: {
      labelTextColor: rgbAlpha(t.text, 0.55),
      labelFontWeight: '500',
    },
  }
})
</script>

<template>
  <NConfigProvider :theme="naiveTheme" :theme-overrides="themeOverrides" :locale="naiveLocale" :date-locale="naiveDateLocale">
    <NLoadingBarProvider>
      <NMessageProvider>
        <NDialogProvider>
          <NNotificationProvider>
            <MessageCenter />
            <RouterView />
          </NNotificationProvider>
        </NDialogProvider>
      </NMessageProvider>
    </NLoadingBarProvider>
  </NConfigProvider>
</template>
