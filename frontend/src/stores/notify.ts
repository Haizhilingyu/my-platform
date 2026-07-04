import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export interface UrgentPayload {
  messageId: number
  title: string
  content: string
}

export const useNotifyStore = defineStore('notify', () => {
  const unreadCount = ref(0)
  const urgentQueue = ref<UrgentPayload[]>([])

  const currentUrgent = computed<UrgentPayload | null>(() => urgentQueue.value[0] ?? null)

  function setUnread(n: number): void {
    unreadCount.value = Math.max(0, Math.floor(n))
  }

  function incrementUnread(step = 1): void {
    unreadCount.value += step
  }

  function decrementUnread(step = 1): void {
    unreadCount.value = Math.max(0, unreadCount.value - step)
  }

  function pushUrgent(payload: UrgentPayload): void {
    urgentQueue.value = [...urgentQueue.value, payload]
  }

  function dismissUrgent(): void {
    urgentQueue.value = urgentQueue.value.slice(1)
  }

  function clearUrgent(): void {
    urgentQueue.value = []
  }

  return {
    unreadCount,
    urgentQueue,
    currentUrgent,
    setUnread,
    incrementUnread,
    decrementUnread,
    pushUrgent,
    dismissUrgent,
    clearUrgent,
  }
})
