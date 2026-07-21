import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import Dashboard from '@/modules/sys/views/Dashboard.vue'
import NotFound from '@/modules/sys/views/NotFound.vue'

/**
 * Dashboard / NotFound 静态视图渲染测试。
 *
 * Dashboard：展示当前用户名与菜单/权限计数；
 * NotFound：404 文案与返回首页链接。
 */

const { authState } = vi.hoisted(() => ({
  authState: {
    user: { username: 'admin', realName: '管理员' } as unknown,
    menus: [{ id: 1 }, { id: 2 }] as unknown[],
    permissions: new Set(['sys:user:list', 'sys:role:list', 'sys:menu:list']),
  },
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => authState,
}))

describe('Dashboard.vue', () => {
  it('渲染欢迎语与用户姓名', () => {
    const wrapper = mount(Dashboard, { global: { plugins: [createPinia()] } })
    expect(wrapper.text()).toContain('管理员')
  })

  it('用户名缺失 realName 时回退 username', () => {
    authState.user = { username: 'bob' }
    const wrapper = mount(Dashboard, { global: { plugins: [createPinia()] } })
    expect(wrapper.text()).toContain('bob')
    authState.user = { username: 'admin', realName: '管理员' }
  })

  it('统计卡片展示菜单数与权限数', () => {
    const wrapper = mount(Dashboard, { global: { plugins: [createPinia()] } })
    expect(wrapper.text()).toContain('2') // menus.length
    expect(wrapper.text()).toContain('3') // permissions.size
  })
})

describe('NotFound.vue', () => {
  it('渲染 404 提示', () => {
    const wrapper = mount(NotFound, {
      global: {
        plugins: [createPinia()],
        stubs: { RouterLink: { template: '<a><slot /></a>' } },
      },
    })
    expect(wrapper.text()).toContain('404')
  })
})
