import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { vPermission } from '@/shared/directives/permission'
import { useAuthStore } from '@/stores/auth'

/**
 * v-permission 指令测试。
 */
describe('v-permission 指令', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  function createEl(): HTMLElement {
    const parent = document.createElement('div')
    const el = document.createElement('button')
    el.textContent = '操作'
    parent.appendChild(el)
    return el
  }

  function createMockBinding(value: string | string[]) {
    return { value } as any
  }

  it('should keep element when user has permission', () => {
    const store = useAuthStore()
    store.permissions = new Set(['sys:user:add'])

    const el = createEl()
    vPermission.mounted(el, createMockBinding('sys:user:add'))

    expect(el.parentNode).not.toBeNull()
  })

  it('should remove element when user lacks permission', () => {
    const store = useAuthStore()
    store.permissions = new Set(['sys:user:list'])

    const el = createEl()
    vPermission.mounted(el, createMockBinding('sys:user:delete'))

    expect(el.parentNode).toBeNull()
  })

  it('should keep element when user has any of the array permissions', () => {
    const store = useAuthStore()
    store.permissions = new Set(['sys:user:edit'])

    const el = createEl()
    vPermission.mounted(el, createMockBinding(['sys:user:add', 'sys:user:edit']))

    expect(el.parentNode).not.toBeNull()
  })

  it('should remove element when user has none of the array permissions', () => {
    const store = useAuthStore()
    store.permissions = new Set(['sys:user:list'])

    const el = createEl()
    vPermission.mounted(el, createMockBinding(['sys:user:add', 'sys:user:delete']))

    expect(el.parentNode).toBeNull()
  })

  it('should remove element when user has no permissions at all', () => {
    const el = createEl()
    vPermission.mounted(el, createMockBinding('sys:user:add'))

    expect(el.parentNode).toBeNull()
  })
})
