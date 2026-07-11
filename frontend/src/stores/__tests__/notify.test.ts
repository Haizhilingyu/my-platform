import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useNotifyStore } from '@/stores/notify'

/**
 * Notify Store 单元测试。
 *
 * 覆盖点：
 *  - unreadCount 的写入保护（下限 0、小数向下取整）；
 *  - increment / decrement 的边界（不为负）；
 *  - urgentQueue 的 push / dismiss（移除队首）/ clear；
 *  - currentUrgent 计算属性始终指向队首。
 */
describe('Notify Store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  describe('setUnread', () => {
    it('should set the given count', () => {
      const store = useNotifyStore()
      store.setUnread(5)
      expect(store.unreadCount).toBe(5)
    })

    it('should clamp negative values to 0', () => {
      const store = useNotifyStore()
      store.unreadCount = 3
      store.setUnread(-2)
      expect(store.unreadCount).toBe(0)
    })

    it('should floor fractional values', () => {
      const store = useNotifyStore()
      store.setUnread(3.9)
      expect(store.unreadCount).toBe(3)
    })

    it('should accept 0 without change', () => {
      const store = useNotifyStore()
      store.setUnread(0)
      expect(store.unreadCount).toBe(0)
    })
  })

  describe('incrementUnread', () => {
    it('should add 1 by default', () => {
      const store = useNotifyStore()
      store.setUnread(0)
      store.incrementUnread()
      expect(store.unreadCount).toBe(1)
    })

    it('should add the given step', () => {
      const store = useNotifyStore()
      store.setUnread(2)
      store.incrementUnread(3)
      expect(store.unreadCount).toBe(5)
    })
  })

  describe('decrementUnread', () => {
    it('should subtract 1 by default', () => {
      const store = useNotifyStore()
      store.setUnread(2)
      store.decrementUnread()
      expect(store.unreadCount).toBe(1)
    })

    it('should not go below 0', () => {
      const store = useNotifyStore()
      store.setUnread(0)
      store.decrementUnread()
      expect(store.unreadCount).toBe(0)
    })
  })

  describe('urgentQueue', () => {
    it('should append when pushUrgent is called', () => {
      const store = useNotifyStore()
      const payload = { messageId: 1, title: 'A', content: 'a' }
      store.pushUrgent(payload)
      expect(store.urgentQueue).toHaveLength(1)
      expect(store.currentUrgent).toEqual(payload)
    })

    it('should remove the head when dismissUrgent is called', () => {
      const store = useNotifyStore()
      const first = { messageId: 1, title: 'A', content: 'a' }
      const second = { messageId: 2, title: 'B', content: 'b' }
      store.pushUrgent(first)
      store.pushUrgent(second)

      store.dismissUrgent()

      expect(store.urgentQueue).toHaveLength(1)
      expect(store.currentUrgent).toEqual(second)
    })

    it('should clear all when clearUrgent is called', () => {
      const store = useNotifyStore()
      store.pushUrgent({ messageId: 1, title: 'A', content: 'a' })
      store.pushUrgent({ messageId: 2, title: 'B', content: 'b' })

      store.clearUrgent()

      expect(store.urgentQueue).toHaveLength(0)
      expect(store.currentUrgent).toBeNull()
    })

    it('should report null currentUrgent when empty', () => {
      const store = useNotifyStore()
      expect(store.currentUrgent).toBeNull()
    })
  })
})
