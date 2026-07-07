import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'
import RoleIndex from '@/modules/sys/views/role/index.vue'
import { roleApi } from '@/modules/sys/api/role'

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
