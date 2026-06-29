<script setup lang="ts">
import { h, computed, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  NLayout, NLayoutHeader, NLayoutSider, NLayoutContent,
  NMenu, NButton, NIcon, NSpace, NDropdown, NAvatar, NText,
  type MenuOption,
} from 'naive-ui'
import {
  SettingsOutline, MoonOutline, SunnyOutline, LogOutOutline,
  PersonOutline,
} from '@vicons/ionicons5'
import { useThemeStore } from '@/stores/theme'
import { useAuthStore } from '@/stores/auth'
import type { MenuTreeNode } from '@/modules/sys/api/types'

const themeStore = useThemeStore()
const authStore = useAuthStore()
const router = useRouter()
const route = useRoute()
const collapsed = ref(false)

function renderIcon(icon: any) {
  return () => h(NIcon, null, { default: () => h(icon) })
}

const menuOptions = computed<MenuOption[]>(() => {
  function build(menus: MenuTreeNode[]): MenuOption[] {
    return menus
      .filter((m) => m.visible === 1)
      .map((m) => {
        const children = m.children?.length ? build(m.children) : undefined
        return {
          label: m.menuName,
          key: m.path || String(m.id),
          icon: m.icon ? renderIcon(getIcon(m.icon)) : undefined,
          children: children?.length ? children : undefined,
        }
      })
  }
  return build(authStore.menus)
})

function getIcon(iconName: string) {
  const icons: Record<string, any> = {
    Settings: SettingsOutline,
    User: PersonOutline,
  }
  return icons[iconName] || SettingsOutline
}

const activeKey = computed(() => route.path)

function handleMenuUpdate(key: string) {
  router.push(key)
}

const userOptions = [
  { label: '退出登录', key: 'logout', icon: renderIcon(LogOutOutline) },
]

function handleUserAction(key: string) {
  if (key === 'logout') {
    authStore.logout()
    router.push('/login')
  }
}
</script>

<template>
  <NLayout has-sider style="height: 100vh">
    <!-- 侧边栏 -->
    <NLayoutSider
      bordered
      collapse-mode="width"
      :collapsed-width="64"
      :width="240"
      :collapsed="collapsed"
      show-trigger
      @collapse="collapsed = true"
      @expand="collapsed = false"
      :native-scrollbar="false"
    >
      <div class="py-4 px-3 flex items-center gap-2 border-b border-[rgb(var(--color-border))]">
        <NIcon size="24" color="rgb(var(--color-primary))">
          <SettingsOutline />
        </NIcon>
        <NText v-if="!collapsed" strong>My Platform</NText>
      </div>
      <NMenu
        :collapsed="collapsed"
        :collapsed-width="64"
        :collapsed-icon-size="22"
        :options="menuOptions"
        :value="activeKey"
        @update:value="handleMenuUpdate"
      />
    </NLayoutSider>

    <NLayout>
      <!-- 顶栏 -->
      <NLayoutHeader bordered class="px-4 py-2 flex items-center justify-between bg-[rgb(var(--color-surface))]">
        <NText strong>{{ (route.meta.title as string) || '首页' }}</NText>

        <NSpace align="center">
          <!-- 主题切换 -->
          <NButton quaternary circle @click="themeStore.toggle()">
            <template #icon>
              <NIcon>
                <SunnyOutline v-if="themeStore.isDark" />
                <MoonOutline v-else />
              </NIcon>
            </template>
          </NButton>

          <!-- 用户菜单 -->
          <NDropdown :options="userOptions" trigger="click" @select="handleUserAction">
            <NSpace align="center" style="cursor: pointer">
              <NAvatar round size="small" color="rgb(var(--color-primary))">
                {{ authStore.user?.username?.charAt(0).toUpperCase() || 'U' }}
              </NAvatar>
              <NText>{{ authStore.user?.realName || authStore.user?.username }}</NText>
            </NSpace>
          </NDropdown>
        </NSpace>
      </NLayoutHeader>

      <!-- 内容区 -->
      <NLayoutContent
        class="p-4"
        :native-scrollbar="false"
        content-style="min-height: 100%;"
      >
        <RouterView />
      </NLayoutContent>
    </NLayout>
  </NLayout>
</template>
