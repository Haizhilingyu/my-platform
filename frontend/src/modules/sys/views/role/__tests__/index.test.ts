import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'
import RoleIndex from '@/modules/sys/views/role/index.vue'
import { roleApi } from '@/modules/sys/api/role'
import { menuApi } from '@/modules/sys/api/menu'
import { unitApi } from '@/modules/sys/api/unit'

/**
 * 角色管理视图 CRUD 表单校验边界值测试。
 *
 * 覆盖：
 *  - 新建态：roleCode 必填 + 长度 + 模式；roleName 必填 + 长度；dataScope 必填；
 *    非法时不调用 create；合法时调用 create。
 *  - 编辑态：roleCode 字段禁用但保留值，update 直接放行。
 */

vi.mock('@/modules/sys/api/role', () => ({
  roleApi: {
    list: vi.fn().mockResolvedValue({ data: [] }),
    create: vi.fn().mockResolvedValue({ data: 1 }),
    update: vi.fn().mockResolvedValue({}),
    delete: vi.fn().mockResolvedValue({}),
    getRoleMenus: vi.fn().mockResolvedValue({ data: [] }),
    assignMenus: vi.fn().mockResolvedValue({}),
    saveCustomUnits: vi.fn().mockResolvedValue({}),
    getCustomUnits: vi.fn().mockResolvedValue({ data: [] }),
  },
}))

vi.mock('@/modules/sys/api/menu', () => ({
  menuApi: { tree: vi.fn().mockResolvedValue({ data: [] }) },
}))

vi.mock('@/modules/sys/api/unit', () => ({
  unitApi: { tree: vi.fn().mockResolvedValue({ data: [] }) },
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

function mountRole() {
  return mount(RoleIndex, { global: { plugins: [createPinia()] } })
}

/** 打开"新增角色"对话框并等待表单挂载。 */
async function openCreateModal(wrapper: ReturnType<typeof mountRole>) {
  const addBtn = wrapper.findAll('button').find((b) => b.text().includes('新增角色'))
  expect(addBtn).toBeTruthy()
  await addBtn!.trigger('click')
  await flushPromises()
}

async function setField(_wrapper: ReturnType<typeof mountRole>, placeholder: string, value: string) {
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

describe('role/index.vue 表单校验', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // jsdom 不提供 matchMedia；useBreakpoint 依赖它，此处打桩为 desktop 默认值。
    vi.stubGlobal('matchMedia', () => ({
      matches: false,
      media: '',
      onchange: null,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }))
  })

  afterEach(() => {
    document.body.innerHTML = ''
    vi.clearAllMocks()
    vi.unstubAllGlobals()
  })

  describe('新建态', () => {
    it('should reject empty roleCode without calling roleApi.create', async () => {
      const wrapper = await mountRole()
      await flushPromises()
      await openCreateModal(wrapper)

      await setField(wrapper, '如 超级管理员', '测试角色')
      await clickSave()

      expect(roleApi.create).not.toHaveBeenCalled()
    })

    it('should reject roleCode shorter than 3 chars', async () => {
      const wrapper = await mountRole()
      await flushPromises()
      await openCreateModal(wrapper)

      await setField(wrapper, '如 admin', 'ab')
      await setField(wrapper, '如 超级管理员', '测试角色')
      await clickSave()

      expect(roleApi.create).not.toHaveBeenCalled()
    })

    it('should reject roleCode with invalid pattern (hyphen)', async () => {
      const wrapper = await mountRole()
      await flushPromises()
      await openCreateModal(wrapper)

      await setField(wrapper, '如 admin', 'role-1')
      await setField(wrapper, '如 超级管理员', '测试角色')
      await clickSave()

      expect(roleApi.create).not.toHaveBeenCalled()
    })

    it('should reject empty roleName', async () => {
      const wrapper = await mountRole()
      await flushPromises()
      await openCreateModal(wrapper)

      await setField(wrapper, '如 admin', 'ADMIN')
      await clickSave()

      expect(roleApi.create).not.toHaveBeenCalled()
    })

    it('should reject roleName longer than 100 chars', async () => {
      const wrapper = await mountRole()
      await flushPromises()
      await openCreateModal(wrapper)

      await setField(wrapper, '如 admin', 'ADMIN')
      await setField(wrapper, '如 超级管理员', 'x'.repeat(101))
      await clickSave()

      expect(roleApi.create).not.toHaveBeenCalled()
    })

    it('should reject empty dataScope', async () => {
      const wrapper = await mountRole()
      await flushPromises()
      await openCreateModal(wrapper)

      await setField(wrapper, '如 admin', 'ADMIN')
      await setField(wrapper, '如 超级管理员', '测试角色')

      // dataScope 是 NSelect 且无 clearable 属性，通过 setupState 直接置空触发 required 校验。
      const ss = (wrapper.vm as unknown as { $: { setupState: Record<string, unknown> } }).$.setupState
      ;(ss.form as { dataScope: string }).dataScope = ''
      await flushPromises()

      await clickSave()

      expect(roleApi.create).not.toHaveBeenCalled()
    })

    it('should call roleApi.create when full form is valid', async () => {
      const wrapper = await mountRole()
      await flushPromises()
      await openCreateModal(wrapper)

      await setField(wrapper, '如 admin', 'ADMIN')
      await setField(wrapper, '如 超级管理员', '超级管理员')
      await clickSave()

      expect(roleApi.create).toHaveBeenCalledTimes(1)
      expect(roleApi.create).toHaveBeenCalledWith(
        expect.objectContaining({
          roleCode: 'ADMIN',
          roleName: '超级管理员',
        }),
      )
    })
  })

  describe('编辑态', () => {
    it('should call roleApi.update with pre-filled valid values', async () => {
      const wrapper = await mountRole()
      await flushPromises()

      const ss = (wrapper.vm as unknown as { $: { setupState: Record<string, unknown> } }).$.setupState
      await (ss.handleEdit as (row: unknown) => Promise<void>)({
        id: 7,
        roleCode: 'EXISTING',
        roleName: '现有角色',
        dataScope: 'SELF',
        status: 1,
        remark: null,
        createdAt: '2024-01-01',
      })
      await flushPromises()

      await clickSave()

      expect(roleApi.update).toHaveBeenCalledTimes(1)
      expect(roleApi.update).toHaveBeenCalledWith(7, expect.anything())
      expect(roleApi.create).not.toHaveBeenCalled()
    })
  })
})

describe('role/index.vue 行操作与授权', () => {
  const roleRow = {
    id: 1,
    roleCode: 'sys_admin',
    roleName: '系统管理员',
    dataScope: 'ALL',
    status: 1,
    remark: '内置角色',
  }

  const setupStateOf = (wrapper: ReturnType<typeof mountRole>) =>
    (wrapper.vm as unknown as { $: { setupState: Record<string, unknown> } }).$.setupState

  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(roleApi.list).mockResolvedValue({ data: [roleRow] } as never)
  })

  it('行渲染 dataScope 与状态标签', async () => {
    const wrapper = mountRole()
    await flushPromises()

    expect(wrapper.text()).toContain('sys_admin')
    expect(wrapper.text()).toContain('全部数据')
  })

  it('授权流程：加载菜单树 → 打开弹窗 → 保存授权', async () => {
    vi.mocked(menuApi.tree).mockResolvedValue({
      data: [
        {
          id: 1,
          menuName: '系统管理',
          path: '',
          visible: 1,
          children: [
            { id: 2, menuName: '用户管理', path: '/sys/user', visible: 1, children: [] },
          ],
        },
      ],
    } as never)
    vi.mocked(roleApi.getRoleMenus).mockResolvedValue({ data: [2] } as never)
    const wrapper = mountRole()
    await flushPromises()

    const ss = setupStateOf(wrapper)
    await (ss.handlePermission as (row: unknown) => Promise<void>)(roleRow)
    await flushPromises()

    expect(menuApi.tree).toHaveBeenCalled()
    expect(roleApi.getRoleMenus).toHaveBeenCalledWith(1)
    // flattenMenuTree 在弹窗 NTree 渲染中执行
    expect(document.body.textContent).toContain('系统管理')

    await (ss.handleSavePermission as () => Promise<void>)()
    expect(roleApi.assignMenus).toHaveBeenCalledWith(1, [2])
  })

  it('删除角色后刷新列表', async () => {
    const wrapper = mountRole()
    await flushPromises()
    vi.mocked(roleApi.list).mockClear()

    const ss = setupStateOf(wrapper)
    await (ss.handleDelete as (row: unknown) => Promise<void>)(roleRow)
    await flushPromises()

    expect(roleApi.delete).toHaveBeenCalledWith(1)
    expect(roleApi.list).toHaveBeenCalledTimes(1)
  })

  it('编辑角色：加载表单并触发 flattenUnitTree（自定义数据范围）', async () => {
    // Mock nested unit tree to trigger flattenUnitTree when custom scope is selected
    vi.mocked(unitApi.tree).mockResolvedValue({
      data: [
        {
          id: 1,
          unitName: '总公司',
          children: [
            { id: 2, unitName: '技术部', children: [] },
          ],
        },
      ],
    } as never)
    const wrapper = mountRole()
    await flushPromises()

    const ss = setupStateOf(wrapper)
    await (ss.handleEdit as (row: unknown) => Promise<void>)(roleRow)
    await flushPromises()

    // Verify edit modal opens with role data
    expect(ss.editingId as number).toBe(1)
    expect((ss.form as Record<string, unknown>).roleCode).toBe('sys_admin')

    // 直接验证 flattenUnitTree（自定义数据范围时用于渲染单位下拉）：
    // NSelect 选项文本在关闭态下不渲染进 DOM，故不走 DOM 断言，改为直接调用纯函数。
    const flattened = (ss.flattenUnitTree as (units: unknown[]) => unknown[])([
      { id: 1, unitName: '总公司', children: [{ id: 2, unitName: '技术部', children: [] }] },
    ])
    expect(flattened).toHaveLength(1)
    expect((flattened as Array<{ children?: unknown[] }>)[0].children).toHaveLength(1)
    expect(ss.editingId as number).toBe(1)
  })
})
