<script setup lang="ts">
import { h, computed, ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  NLayout, NLayoutHeader, NLayoutSider, NLayoutContent,
  NMenu, NButton, NIcon, NSpace, NDropdown, NAvatar, NText,
  NDrawer, NDrawerContent,
  type MenuOption,
} from 'naive-ui'
import {
  SettingsOutline, MoonOutline, SunnyOutline, LogOutOutline,
  PersonOutline, MenuOutline,
} from '@vicons/ionicons5'
import { useThemeStore } from '@/stores/theme'
import { useAuthStore } from '@/stores/auth'
import { useBreakpoint } from '@/shared/composables/useBreakpoint'
import type { MenuTreeNode } from '@/modules/sys/api/types'

const themeStore = useThemeStore()
const authStore = useAuthStore()
const router = useRouter()
const route = useRoute()
const collapsed = ref(false)
const drawerVisible = ref(false)

// 响应式断点：mobile <768, tablet 768-1023, desktop >=1024。
// - desktop: 用户可折叠的 240px sider（默认展开）
// - tablet : 64px 折叠 sider（默认折叠，仍可手动展开）
// - mobile : 不渲染 sider，改用 NDrawer，顶栏 hamburger 触发
const { isMobile, breakpoint } = useBreakpoint()

// 断点切换时同步默认折叠态：
// - 进入 tablet → 强制折叠（任务要求“collapsed by default”）
// - 进入 desktop → 展开回 240px
// - 进入 mobile → 关闭抽屉（防止抽屉在桌面态残留）
watch(breakpoint, (bp) => {
  if (bp === 'tablet') {
    collapsed.value = true
  } else if (bp === 'desktop') {
    collapsed.value = false
  }
  if (bp !== 'mobile') {
    drawerVisible.value = false
  }
})

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
  // 移动端选中后立即关闭抽屉，避免遮挡内容
  if (isMobile.value) {
    drawerVisible.value = false
  }
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
  <NLayout has-sider class="h-screen">
    <!-- 桌面/平板：固定 sider（240px 展开 / 64px 折叠） -->
    <NLayoutSider
      v-if="!isMobile"
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

    <!-- 移动端：NDrawer 抽屉 -->
    <NDrawer
      v-if="isMobile"
      v-model:show="drawerVisible"
      :width="240"
      placement="left"
    >
      <NDrawerContent title="My Platform" :native-scrollbar="false">
        <NMenu
          :options="menuOptions"
          :value="activeKey"
          @update:value="handleMenuUpdate"
        />
      </NDrawerContent>
    </NDrawer>

    <NLayout>
      <!-- 顶栏 -->
      <NLayoutHeader bordered class="px-4 py-2 flex items-center justify-between bg-[rgb(var(--color-surface))]">
        <div class="flex items-center gap-2 min-w-0">
          <!-- 移动端：hamburger 触发抽屉 -->
          <NButton v-if="isMobile" quaternary circle @click="drawerVisible = true">
            <template #icon>
              <NIcon>
                <MenuOutline />
              </NIcon>
            </template>
          </NButton>
          <NText strong class="truncate">{{ (route.meta.title as string) || '首页' }}</NText>
        </div>

        <NSpace align="center" :wrap="false">
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
            <NSpace align="center" :wrap="false" class="cursor-pointer">
              <NAvatar round size="small" color="rgb(var(--color-primary))">
                {{ authStore.user?.username?.charAt(0).toUpperCase() || 'U' }}
              </NAvatar>
              <!-- 移动端隐藏用户名文字，仅保留头像 -->
              <NText v-if="!isMobile">{{ authStore.user?.realName || authStore.user?.username }}</NText>
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
