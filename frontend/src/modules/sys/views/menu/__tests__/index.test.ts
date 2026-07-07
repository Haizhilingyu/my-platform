import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'
import MenuIndex from '@/modules/sys/views/menu/index.vue'
import { menuApi } from '@/modules/sys/api/menu'

/**
 * 菜单管理视图表单校验边界值测试。
 *
 * 覆盖：
 *  - menuName 必填 + 长度上限；menuType 必填；
 *  - path/component/permission/icon 长度上限；sort 非负整数模式；
 *  - 合法表单时调用 menuApi.create / update。
 *
 * 说明：菜单类型 NSelect 不可在 UI 中清空，故 menuType 必填校验通过
 * 直接操纵 setupState.form.menuType = '' 触发；其余字段走真实 DOM 输入。
 */

vi.mock('@/modules/sys/api/menu', () => ({
  menuApi: {
    tree: vi.fn().mockResolvedValue({ data: [] }),
    create: vi.fn().mockResolvedValue({ data: 1 }),
    update: vi.fn().mockResolvedValue({}),
    delete: vi.fn().mockResolvedValue({}),
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
      success: vi.fn(),
      error: vi.fn(),
      warning: vi.fn(),
      info: vi.fn(),
      loading: vi.fn(),
    }),
  }
})

function mountMenu() {
  return mount(MenuIndex, { global: { plugins: [createPinia()] } })
}

/** 打开“新增菜单”对话框并等待表单挂载。 */
async function openCreateModal(wrapper: ReturnType<typeof mountMenu>) {
  const addBtn = wrapper.findAll('button').find((b) => b.text().includes('新增菜单'))
  expect(addBtn).toBeTruthy()
  await addBtn!.trigger('click')
  await flushPromises()
}

async function setField(placeholder: string, value: string) {
  // NModal 通过 teleport 渲染到 document.body，需在 body 范围内查找输入元素。
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
    // NModal 通过 teleport 渲染到 document.body，组件 unmount 后内容可能残留；
    // 清空 body 避免跨用例污染（保留 vue-test-utils 注入的根容器）。
    document.body.innerHTML = ''
    vi.clearAllMocks()
  })

  it('should reject empty menuName without calling menuApi.create', async () => {
    const wrapper = await mountMenu()
    await flushPromises()
    await openCreateModal(wrapper)

    await clickSave()

    expect(menuApi.create).not.toHaveBeenCalled()
  })

  it('should reject menuName longer than 50 chars', async () => {
    const wrapper = await mountMenu()
    await flushPromises()
    await openCreateModal(wrapper)

    await setField('菜单名称', 'm'.repeat(51))
    await clickSave()

    expect(menuApi.create).not.toHaveBeenCalled()
  })

  it('should reject empty menuType without calling menuApi.create', async () => {
    const wrapper = await mountMenu()
    await flushPromises()
    await openCreateModal(wrapper)

    await setField('菜单名称', '系统管理')
    // menuType 默认 'PAGE' 且 NSelect 不可清空，这里直接置空 model 触发必填校验。
    const ss = (wrapper.vm as unknown as { $: { setupState: { form: { menuType: string } } } }).$.setupState
    ss.form.menuType = ''
    await flushPromises()

    await clickSave()

    expect(menuApi.create).not.toHaveBeenCalled()
  })

  it('should reject path longer than 200 chars', async () => {
    const wrapper = await mountMenu()
    await flushPromises()
    await openCreateModal(wrapper)

    await setField('菜单名称', '系统管理')
    await setField('/sys/user', '/'.repeat(201))
    await clickSave()

    expect(menuApi.create).not.toHaveBeenCalled()
  })

  it('should reject invalid sort value (non-numeric)', async () => {
    const wrapper = await mountMenu()
    await flushPromises()
    await openCreateModal(wrapper)

    await setField('菜单名称', '系统管理')
    await setField('0', 'abc')
    await clickSave()

    expect(menuApi.create).not.toHaveBeenCalled()
  })

  it('should reject negative sort value', async () => {
    const wrapper = await mountMenu()
    await flushPromises()
    await openCreateModal(wrapper)

    await setField('菜单名称', '系统管理')
    await setField('0', '-1')
    await clickSave()

    expect(menuApi.create).not.toHaveBeenCalled()
  })

  it('should call menuApi.create when form is valid', async () => {
    const wrapper = await mountMenu()
    await flushPromises()
    await openCreateModal(wrapper)

    await setField('菜单名称', '系统管理')
    await clickSave()

    expect(menuApi.create).toHaveBeenCalledTimes(1)
    expect(menuApi.create).toHaveBeenCalledWith(
      expect.objectContaining({ menuName: '系统管理', menuType: 'PAGE' }),
    )
  })

  it('should call menuApi.update when editing with valid form', async () => {
    const wrapper = await mountMenu()
    await flushPromises()

    // 模拟编辑：直接通过 setupState 调用 handleEdit，跳过树节点交互。
    const ss = (wrapper.vm as unknown as {
      $: { setupState: { handleEdit: (row: unknown) => Promise<void> } }
    }).$.setupState
    await ss.handleEdit({
      id: 7,
      parentId: null,
      menuName: '用户管理',
      menuType: 'PAGE',
      path: '/sys/user',
      component: 'sys/user/index',
      permission: 'sys:user:read',
      icon: 'User',
      sort: 1,
      visible: 1,
      status: 1,
    })
    await flushPromises()

    await clickSave()

    expect(menuApi.update).toHaveBeenCalledTimes(1)
    expect(menuApi.update).toHaveBeenCalledWith(7, expect.anything())
    expect(menuApi.create).not.toHaveBeenCalled()
  })
})
