<script setup lang="ts">
import { onMounted, onUnmounted, watch, computed } from 'vue'
import { useRouter } from 'vue-router'
import { NModal, NButton, NSpace, NText, useNotification } from 'naive-ui'
import { useAuthStore } from '@/stores/auth'
import { useNotifyStore, type UrgentPayload } from '@/stores/notify'
import { useWebSocket } from '@/shared/composables/useWebSocket'
import { useBreakpoint } from '@/shared/composables/useBreakpoint'
import { notifyApi } from '@/shared/api/notify'
import { useI18n } from 'vue-i18n'

interface PushEnvelope {
  type?: string
  level?: 'URGENT' | 'IMPORTANT' | 'NORMAL'
  messageId?: number
  seq?: number
  title?: string
  content?: string
}

const authStore = useAuthStore()
const notifyStore = useNotifyStore()
const router = useRouter()
const notification = useNotification()
const { isMobile } = useBreakpoint()
const { connect, disconnect, onMessage } = useWebSocket()
const { t } = useI18n()

function buildWsUrl(): string {
  const explicit = import.meta.env.VITE_WS_URL as string | undefined
  if (explicit) return explicit
  if (typeof window === 'undefined') return ''
  const loc = window.location
  const proto = loc.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${proto}//${loc.host}/ws/notify`
}

function handleMessage(raw: unknown): void {
  const msg = raw as PushEnvelope
  if (!msg || typeof msg !== 'object') return
  notifyStore.incrementUnread()
  const level = msg.level
  const title = msg.title || t('layout.newMessage')
  const content = msg.content || ''
  if (level === 'URGENT') {
    const payload: UrgentPayload = { messageId: msg.messageId ?? 0, title, content }
    notifyStore.pushUrgent(payload)
  } else if (level === 'IMPORTANT') {
    notification.info({ title, content, duration: 5000 })
  }
}

async function refreshUnread(): Promise<void> {
  try {
    const res = await notifyApi.unreadCount()
    if (typeof res.data === 'number') notifyStore.setUnread(res.data)
  } catch {
    // 收件箱端点尚未上线：保持当前徽标值，不影响 WS 实时推送
  }
}

function connectIfLoggedIn(): void {
  if (!authStore.isLoggedIn || !authStore.token) return
  const url = buildWsUrl()
  if (!url) return
  connect(url, authStore.token)
  void refreshUnread()
}

onMessage(handleMessage)

const showUrgent = computed<boolean>({
  get: () => !!notifyStore.currentUrgent,
  set: (v: boolean) => {
    if (!v) notifyStore.dismissUrgent()
  },
})

const modalStyle = computed(() =>
  isMobile.value
    ? { width: '100vw', maxWidth: '100vw' }
    : { width: '520px' },
)

function goToInbox(): void {
  showUrgent.value = false
  router.push('/sys/message')
}

onMounted(() => {
  if (authStore.isLoggedIn) connectIfLoggedIn()
})

watch(
  () => authStore.isLoggedIn,
  (loggedIn) => {
    if (loggedIn) connectIfLoggedIn()
    else disconnect()
  },
)

onUnmounted(() => disconnect())
</script>

<template>
  <NModal
    v-model:show="showUrgent"
    preset="card"
    :title="notifyStore.currentUrgent?.title || t('layout.urgentNotification')"
    :style="modalStyle"
    :mask-closable="false"
    size="huge"
    role="alertdialog"
    aria-modal="true"
  >
    <NText>{{ notifyStore.currentUrgent?.content }}</NText>
    <template #footer>
      <NSpace justify="end">
        <NButton @click="showUrgent = false">{{ t('layout.handleLater') }}</NButton>
        <NButton type="primary" @click="goToInbox">{{ t('layout.viewDetails') }}</NButton>
      </NSpace>
    </template>
  </NModal>
</template>
