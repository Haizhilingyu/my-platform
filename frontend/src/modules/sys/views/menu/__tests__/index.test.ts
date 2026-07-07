import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'
import MenuIndex from '@/modules/sys/views/menu/index.vue'
import { menuApi } from '@/modules/sys/api/menu'

vi.mock('@/modules/sys/api/menu', () => ({
  menuApi: {
    tree: vi.fn().mockResolvedValue({
      data: [{
        id: 1, parentId: null, menuName: '系统管理', menuType: 'DIRECTORY',
        path: '/sys', component: null, permission: null, icon: 'Settings',
        sort: 1, visible: 1, status: 1,
        children: [{
          id: 2, parentId: 1, menuName: '用户管理', menuType: 'PAGE',
          path: '/sys/user', component: 'sys/user/index', permission: 'sys:user:list',
          icon: 'User', sort: 1, visible: 1, status: 1,
        }],
      }],
    }),
    update: vi.fn().mockResolvedValue({}),
  },
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ hasPermission: () => true }),
}))

vi.mock('naive-ui', async () => {
  const actual = await vi.importActual<typeof import('naive-ui')>('naive-ui')
  return {
    ...actual,
    useMessage: () => ({
      success: vi.fn(), error: vi.fn(), warning: vi.fn(),
      info: vi.fn(), loading: vi.fn(),
    }),
  }
})

function mountMenu() {
  return mount(MenuIndex, { global: { plugins: [createPinia()] } })
}

const EDIT_ROW = {
  id: 2, parentId: 1, menuName: '用户管理', menuType: 'PAGE',
  path: '/sys/user', component: 'sys/user/index', permission: 'sys:user:list',
  icon: 'User', sort: 1, visible: 1, status: 1,
}

async function openEditModal(wrapper: ReturnType<typeof mountMenu>) {
  const ss = (wrapper.vm as unknown as {
    $: { setupState: { handleEdit: (row: unknown) => void } }
  }).$.setupState
  ss.handleEdit(EDIT_ROW)
  await flushPromises()
}

async function setField(placeholder: string, value: string) {
  const inputs = document.body.querySelectorAll<HTMLInputElement>(`input[placeholder="${placeholder}"]`)
  const input = inputs[inputs.length - 1]
  expect(input, `input with placeholder="${placeholder}" should exist`).toBeTruthy()
  input.value = value
  input.dispatchEvent(new Event('input', { bubbles: true }))
  await flushPromises()
}

async function clickSave() {
  const saveBtns = Array.from(document.body.querySelectorAll('button')).filter((b) =>
    b.textContent?.includes('保存'),
  )
  const saveBtn = saveBtns[saveBtns.length - 1]
  expect(saveBtn, '保存 button should exist').toBeTruthy()
  saveBtn!.dispatchEvent(new Event('click', { bubbles: true }))
  await flushPromises()
  await flushPromises()
}

describe('menu/index.vue 表单校验', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    document.body.innerHTML = ''
    vi.clearAllMocks()
  })

  it('should reject empty menuName without calling menuApi.update', async () => {
    const wrapper = await mountMenu()
    await flushPromises()
    await openEditModal(wrapper)

    await setField('菜单名称', '')

    await clickSave()
    expect(menuApi.update).not.toHaveBeenCalled()
  })

  it('should reject menuName longer than 50 chars', async () => {
    const wrapper = await mountMenu()
    await flushPromises()
    await openEditModal(wrapper)

    await setField('菜单名称', 'm'.repeat(51))
    await clickSave()

    expect(menuApi.update).not.toHaveBeenCalled()
  })

  it('should reject invalid sort value (non-numeric)', async () => {
    const wrapper = await mountMenu()
    await flushPromises()
    await openEditModal(wrapper)

    await setField('0', 'abc')
    await clickSave()

    expect(menuApi.update).not.toHaveBeenCalled()
  })

  it('should reject negative sort value', async () => {
    const wrapper = await mountMenu()
    await flushPromises()
    await openEditModal(wrapper)

    await setField('0', '-1')
    await clickSave()

    expect(menuApi.update).not.toHaveBeenCalled()
  })

  it('should call menuApi.update when editing with valid form', async () => {
    const wrapper = await mountMenu()
    await flushPromises()
    await openEditModal(wrapper)

    await setField('菜单名称', '用户管理改')
    await clickSave()

    expect(menuApi.update).toHaveBeenCalledTimes(1)
    expect(menuApi.update).toHaveBeenCalledWith(2, expect.objectContaining({
      menuName: '用户管理改',
      menuType: 'PAGE',
      path: '/sys/user',
      component: 'sys/user/index',
      permission: 'sys:user:list',
    }))
  })
})
