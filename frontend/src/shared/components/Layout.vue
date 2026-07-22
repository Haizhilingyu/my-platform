<script setup lang="ts">
import { h, computed, ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  NLayout,
  NLayoutHeader,
  NLayoutSider,
  NLayoutContent,
  NMenu,
  NButton,
  NIcon,
  NSpace,
  NDropdown,
  NAvatar,
  NText,
  NDrawer,
  NDrawerContent,
  NBadge,
  NPopover,
  NTag,
  NSpin,
  NTooltip,
  type MenuOption,
} from 'naive-ui'
import {
  SettingsOutline,
  MoonOutline,
  SunnyOutline,
  LogOutOutline,
  PersonOutline,
  MenuOutline,
  GlobeOutline,
  NotificationsOutline,
  ShieldCheckmarkOutline,
  BusinessOutline,
  BuildOutline,
  DocumentTextOutline,
  AppsOutline,
  SparklesOutline,
  CloseOutline,
} from '@vicons/ionicons5'
import { useThemeStore } from '@/stores/theme'
import { useAuthStore } from '@/stores/auth'
import { useNotifyStore } from '@/stores/notify'
import { useLocaleStore } from '@/stores/locale'
import { SUPPORTED_LOCALES, type AppLocale } from '@/i18n'
import ChatPanel from '@/modules/ai/views/ChatPanel.vue'
import type { AiActionEvent } from '@/modules/ai/api/ai'
import { useBreakpoint } from '@/shared/composables/useBreakpoint'
import { formatDateTime } from '@/shared/utils/datetime'
import { notifyApi, type NotifyInboxVO, type NotifyLevel } from '@/shared/api/notify'
import type { MenuTreeNode } from '@/modules/sys/api/types'
import { useI18n } from 'vue-i18n'
import { useMessage } from 'naive-ui'
import { type DropdownOption } from 'naive-ui'

const themeStore = useThemeStore()
const authStore = useAuthStore()
const notifyStore = useNotifyStore()
const localeStore = useLocaleStore()
const router = useRouter()
const route = useRoute()
const collapsed = ref(false)
const drawerVisible = ref(false)
const aiBubbleVisible = ref(false)

const { t } = useI18n()
const message = useMessage()

// 消息中心：铃铛下拉的最近未读列表（懒加载，打开时拉取）
const bellPopoverShow = ref(false)
const recentMessages = ref<NotifyInboxVO[]>([])
const recentLoading = ref(false)

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
    UserFilled: ShieldCheckmarkOutline,
    Menu: MenuOutline,
    OfficeBuilding: BusinessOutline,
    Tools: BuildOutline,
    Globe: GlobeOutline,
    Document: DocumentTextOutline,
    Apps: AppsOutline,
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

const userOptions = [{ label: t('layout.logout'), key: 'logout', icon: renderIcon(LogOutOutline) }]

function handleUserAction(key: string) {
  if (key === 'logout') {
    authStore.logout()
    router.push('/login')
  }
}

// 消息级别 → 标签类型/文案（与 message/index.vue 约定一致）
function levelTagType(level: NotifyLevel): 'error' | 'warning' | 'info' {
  if (level === 'URGENT') return 'error'
  if (level === 'IMPORTANT') return 'warning'
  return 'info'
}

function levelLabel(level: NotifyLevel): string {
  if (level === 'URGENT') return t('layout.levelUrgent')
  if (level === 'IMPORTANT') return t('layout.levelImportant')
  return t('layout.levelNormal')
}

// 打开铃铛下拉时拉取最近 5 条未读；端点未上线时静默置空
async function fetchRecentMessages(): Promise<void> {
  recentLoading.value = true
  try {
    const res = await notifyApi.inbox({ pageNum: 1, pageSize: 5, readStatus: false })
    recentMessages.value = res.data.list
  } catch {
    recentMessages.value = []
  } finally {
    recentLoading.value = false
  }
}

function handleBellPopoverShow(show: boolean): void {
  bellPopoverShow.value = show
  if (show) void fetchRecentMessages()
}

function goToInbox(): void {
  bellPopoverShow.value = false
  router.push('/sys/message')
}

// Language switcher
const localeOptions = computed<DropdownOption[]>(() =>
  SUPPORTED_LOCALES.map((l) => ({ label: l.label, key: l.value })),
)

function handleLocaleChange(key: string) {
  localeStore.setLocale(key as AppLocale)
  message.success(t('common.localeChanged'))
}

// AI 助手操作结果跳转：保持对话框开启，路由到目标页（带高亮 id）
// 用户需看到 AI 回复，页面在面板下方跳转 —— 对话框不随跳转关闭
function handleAiAction(a: AiActionEvent): void {
  const location: { path: string; query?: Record<string, string> } = { path: a.path }
  if (a.highlightId != null) {
    location.query = { highlight: String(a.highlightId) }
  }
  router.push(location)
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
      :native-scrollbar="false"
      @collapse="collapsed = true"
      @expand="collapsed = false"
    >
      <div class="py-5 flex flex-col h-full border-b border-[rgb(var(--color-border))]">
        <div :class="['px-3 mb-4 flex items-center', collapsed ? 'justify-center' : 'gap-2']">
          <div
            class="w-8 h-8 rounded-lg flex items-center justify-center bg-[rgb(var(--color-primary))]"
          >
            <NIcon size="20" color="white">
              <SettingsOutline />
            </NIcon>
          </div>
          <NText v-if="!collapsed" class="font-display text-[1.05rem] font-bold">My Platform</NText>
        </div>
        <NMenu
          :collapsed="collapsed"
          :collapsed-width="64"
          :collapsed-icon-size="22"
          :options="menuOptions"
          :value="activeKey"
          @update:value="handleMenuUpdate"
        />
        <div v-if="!collapsed" class="px-3 pb-4 mt-auto">
          <span class="micro-label font-mono-data text-[rgb(var(--color-text-secondary))]">
            v1.0.0
          </span>
        </div>
      </div>
    </NLayoutSider>

    <!-- 移动端：NDrawer 抽屉 -->
    <NDrawer v-if="isMobile" v-model:show="drawerVisible" :width="240" placement="left">
      <NDrawerContent title="My Platform" :native-scrollbar="false">
        <NMenu :options="menuOptions" :value="activeKey" @update:value="handleMenuUpdate" />
      </NDrawerContent>
    </NDrawer>

    <NLayout>
      <!-- 顶栏 -->
      <NLayoutHeader
        bordered
        class="px-4 py-2 flex items-center justify-between bg-[rgb(var(--color-surface))]"
      >
        <div class="flex items-center gap-2 min-w-0">
          <!-- 移动端：hamburger 触发抽屉 -->
          <NButton v-if="isMobile" quaternary circle @click="drawerVisible = true">
            <template #icon>
              <NIcon>
                <MenuOutline />
              </NIcon>
            </template>
          </NButton>
          <NText class="font-display font-semibold truncate">
            {{ t(route.meta.titleKey || 'route.dashboard') }}
          </NText>
        </div>

        <NSpace align="center" :wrap="false">
          <!-- Language switcher -->
          <NDropdown :options="localeOptions" trigger="click" @select="handleLocaleChange">
            <NButton quaternary circle>
              <template #icon>
                <NIcon>
                  <GlobeOutline />
                </NIcon>
              </template>
            </NButton>
          </NDropdown>

          <!-- 主题切换 -->
          <NButton quaternary circle @click="themeStore.toggle()">
            <template #icon>
              <NIcon>
                <SunnyOutline v-if="themeStore.isDark" />
                <MoonOutline v-else />
              </NIcon>
            </template>
          </NButton>

          <!-- 消息中心：铃铛 + 未读徽标 + 最近未读下拉 -->
          <NPopover
            v-model:show="bellPopoverShow"
            trigger="click"
            placement="bottom-end"
            :width="320"
            @update:show="handleBellPopoverShow"
          >
            <template #trigger>
              <NBadge :value="notifyStore.unreadCount" :max="99" :offset="[-4, 4]">
                <NButton quaternary circle :aria-label="t('layout.messageCenter')">
                  <template #icon>
                    <NIcon>
                      <NotificationsOutline />
                    </NIcon>
                  </template>
                </NButton>
              </NBadge>
            </template>

            <div class="py-1">
              <div
                class="px-3 py-2 text-sm font-semibold border-b border-[rgb(var(--color-border))]"
              >
                {{ t('layout.unreadMessages') }}
              </div>
              <NSpin :show="recentLoading">
                <div
                  v-if="!recentLoading && !recentMessages.length"
                  class="px-3 py-6 text-center text-sm opacity-60"
                >
                  {{ t('layout.noUnreadMessages') }}
                </div>
                <ul v-else class="max-h-[300px] overflow-auto">
                  <li
                    v-for="m in recentMessages"
                    :key="m.id"
                    class="px-3 py-2 cursor-pointer hover:bg-[rgb(var(--color-surface-hover))] border-b border-[rgb(var(--color-border))] last:border-b-0"
                    @click="goToInbox"
                  >
                    <div class="flex items-center gap-2 mb-1">
                      <NTag :type="levelTagType(m.level)" size="small" :bordered="false">
                        {{ levelLabel(m.level) }}
                      </NTag>
                      <NText class="truncate text-sm">{{ m.title || t('layout.noTitle') }}</NText>
                    </div>
                    <NText depth="3" class="text-xs">{{ formatDateTime(m.createdAt) }}</NText>
                  </li>
                </ul>
              </NSpin>
              <div class="px-3 py-2 border-t border-[rgb(var(--color-border))]">
                <NButton text type="primary" block @click="goToInbox">
                  {{ t('layout.viewAll') }}
                </NButton>
              </div>
            </div>
          </NPopover>

          <!-- 用户菜单 -->
          <NDropdown :options="userOptions" trigger="click" @select="handleUserAction">
            <NSpace align="center" :wrap="false" class="cursor-pointer">
              <NAvatar round size="small" color="rgb(var(--color-primary))">
                {{ authStore.user?.username?.charAt(0).toUpperCase() || 'U' }}
              </NAvatar>
              <!-- 移动端隐藏用户名文字，仅保留头像 -->
              <NText v-if="!isMobile">
                {{ authStore.user?.realName || authStore.user?.username }}
              </NText>
            </NSpace>
          </NDropdown>
        </NSpace>
      </NLayoutHeader>

      <!-- 内容区 -->
      <NLayoutContent class="p-4" :native-scrollbar="false" content-style="min-height: 100%;">
        <RouterView />
      </NLayoutContent>
    </NLayout>
    <!-- AI 助手：右下角悬浮气泡按钮（面板开启时隐藏，关闭后复现） -->
    <Teleport to="body">
      <Transition name="ai-bubble">
        <NTooltip v-if="!aiBubbleVisible" trigger="hover" placement="top-end">
          <template #trigger>
            <NButton
              circle
              type="primary"
              size="large"
              :aria-label="t('ai.bubbleTooltip')"
              class="fixed right-6 bottom-6 z-[2000] shadow-lg"
              @click="aiBubbleVisible = true"
            >
              <template #icon>
                <NIcon size="24">
                  <SparklesOutline />
                </NIcon>
              </template>
            </NButton>
          </template>
          {{ t('ai.bubbleTooltip') }}
        </NTooltip>
      </Transition>
      <!-- AI 助手悬浮气泡面板 -->
      <Transition name="ai-bubble">
        <div
          v-if="aiBubbleVisible"
          class="fixed z-[2000] flex flex-col bg-[rgb(var(--color-surface))] shadow-2xl"
          :style="{
            right: isMobile ? '8px' : '24px',
            bottom: isMobile ? '8px' : '24px',
            width: isMobile ? 'calc(100vw - 16px)' : '380px',
            height: isMobile ? 'calc(100vh - 16px)' : 'min(560px, 70vh)',
            borderRadius: isMobile ? '12px' : '16px',
            border: '1px solid rgb(var(--color-border))',
            overflow: 'hidden',
          }"
        >
          <!-- 头部 -->
          <div
            class="flex items-center justify-between px-4 py-2.5 border-b border-[rgb(var(--color-border))] shrink-0"
          >
            <div class="flex items-center gap-2">
              <NIcon size="18" class="text-[rgb(var(--color-primary))]">
                <SparklesOutline />
              </NIcon>
              <span class="font-medium text-sm">{{ t('ai.title') }}</span>
            </div>
            <div class="flex items-center gap-1">
              <NButton
                quaternary
                circle
                size="tiny"
                :title="t('ai.closeChat')"
                @click="aiBubbleVisible = false"
              >
                <template #icon>
                  <NIcon size="16"><CloseOutline /></NIcon>
                </template>
              </NButton>
            </div>
          </div>
          <!-- 聊天内容 -->
          <div class="flex-1 min-h-0">
            <ChatPanel @action="handleAiAction" />
          </div>
        </div>
      </Transition>
    </Teleport>
  </NLayout>
</template>

<style scoped>
.ai-bubble-enter-active,
.ai-bubble-leave-active {
  transition: all 0.25s ease;
}
.ai-bubble-enter-from,
.ai-bubble-leave-to {
  opacity: 0;
  transform: translateY(20px) scale(0.95);
}
</style>
