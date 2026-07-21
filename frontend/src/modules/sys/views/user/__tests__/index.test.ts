import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'
import UserIndex from '@/modules/sys/views/user/index.vue'
import { userApi } from '@/modules/sys/api/user'

/**
 * 用户管理视图 CRUD 表单校验边界值测试。
 *
 * 覆盖：
 *  - 新建态：username/password 必填 + 长度 + 模式；email/phone 格式；非法时不调用 create；
 *  - 编辑态：username/password 字段不渲染，无 required 触发，update 直接放行。
 */

vi.mock('@/modules/sys/api/user', () => ({
  userApi: {
    list: vi.fn().mockResolvedValue({ data: { list: [], total: 0 } }),
    getUserRoles: vi.fn().mockResolvedValue({ data: [] }),
    create: vi.fn().mockResolvedValue({ data: 1 }),
    update: vi.fn().mockResolvedValue({}),
    assignRoles: vi.fn().mockResolvedValue({}),
    delete: vi.fn().mockResolvedValue({}),
    unlock: vi.fn().mockResolvedValue({}),
    resetPassword: vi.fn().mockResolvedValue({}),
  },
}))

vi.mock('@/modules/sys/api/role', () => ({
  roleApi: { list: vi.fn().mockResolvedValue({ data: [] }) },
}))

vi.mock('@/modules/sys/api/unit', () => ({
  unitApi: { tree: vi.fn().mockResolvedValue({ data: [] }) },
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ hasPermission: () => true }),
}))

// 组件通过 useRoute().query.highlight 支持 AI 助手跳转高亮，测试环境无路由需 mock。
vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
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

function mountUser() {
  return mount(UserIndex, { global: { plugins: [createPinia()] } })
}

/** 打开“新增用户”对话框并等待表单挂载。 */
async function openCreateModal(wrapper: ReturnType<typeof mountUser>) {
  const addBtn = wrapper.findAll('button').find((b) => b.text().includes('新增用户'))
  expect(addBtn).toBeTruthy()
  await addBtn!.trigger('click')
  await flushPromises()
}

async function setField(wrapper: ReturnType<typeof mountUser>, placeholder: string, value: string) {
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

describe('user/index.vue 表单校验', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    // NModal 通过 teleport 渲染到 document.body，组件 unmount 后内容可能残留；
    // 清空 body 避免跨用例污染（保留 vue-test-utils 注入的根容器）。
    document.body.innerHTML = ''
    vi.clearAllMocks()
  })

  describe('新建态', () => {
    it('should reject empty username without calling userApi.create', async () => {
      const wrapper = await mountUser()
      await flushPromises()
      await openCreateModal(wrapper)

      await setField(wrapper, '请输入密码', 'validpass')
      await clickSave()

      expect(userApi.create).not.toHaveBeenCalled()
    })

    it('should reject username shorter than 3 chars', async () => {
      const wrapper = await mountUser()
      await flushPromises()
      await openCreateModal(wrapper)

      await setField(wrapper, '请输入用户名', 'ab')
      await setField(wrapper, '请输入密码', 'validpass')
      await clickSave()

      expect(userApi.create).not.toHaveBeenCalled()
    })

    it('should reject username with invalid pattern (hyphen)', async () => {
      const wrapper = await mountUser()
      await flushPromises()
      await openCreateModal(wrapper)

      await setField(wrapper, '请输入用户名', 'abc-def')
      await setField(wrapper, '请输入密码', 'validpass')
      await clickSave()

      expect(userApi.create).not.toHaveBeenCalled()
    })

    it('should reject password shorter than 6 chars', async () => {
      const wrapper = await mountUser()
      await flushPromises()
      await openCreateModal(wrapper)

      await setField(wrapper, '请输入用户名', 'alice')
      await setField(wrapper, '请输入密码', '12345')
      await clickSave()

      expect(userApi.create).not.toHaveBeenCalled()
    })

    it('should reject invalid email format', async () => {
      const wrapper = await mountUser()
      await flushPromises()
      await openCreateModal(wrapper)

      await setField(wrapper, '请输入用户名', 'alice')
      await setField(wrapper, '请输入密码', 'validpass')
      await setField(wrapper, '请输入邮箱', 'notanemail')
      await clickSave()

      expect(userApi.create).not.toHaveBeenCalled()
    })

    it('should reject invalid phone format', async () => {
      const wrapper = await mountUser()
      await flushPromises()
      await openCreateModal(wrapper)

      await setField(wrapper, '请输入用户名', 'alice')
      await setField(wrapper, '请输入密码', 'validpass')
      await setField(wrapper, '请输入电话', '12345')
      await clickSave()

      expect(userApi.create).not.toHaveBeenCalled()
    })

    it('should call userApi.create when full form is valid', async () => {
      const wrapper = await mountUser()
      await flushPromises()
      await openCreateModal(wrapper)

      await setField(wrapper, '请输入用户名', 'alice')
      await setField(wrapper, '请输入密码', 'validpass')
      await setField(wrapper, '请输入邮箱', 'alice@example.com')
      await setField(wrapper, '请输入电话', '13800138000')
      await clickSave()

      expect(userApi.create).toHaveBeenCalledTimes(1)
      expect(userApi.create).toHaveBeenCalledWith(
        expect.objectContaining({
          username: 'alice',
          password: 'validpass',
          email: 'alice@example.com',
          phone: '13800138000',
        }),
      )
    })
  })

  describe('编辑态', () => {
    it('should skip username/password validation and call userApi.update', async () => {
      const wrapper = await mountUser()
      await flushPromises()

      // 模拟数据行：直接通过 setupState 调用 handleEdit，跳过表格交互。
      const ss = (wrapper.vm as unknown as { $: { setupState: Record<string, unknown> } }).$.setupState
      await (ss.handleEdit as (row: unknown) => Promise<void>)({
        id: 42,
        username: 'existing',
        realName: 'Alice',
        email: 'alice@example.com',
        phone: '13800138000',
        unitId: null,
        unitName: null,
        avatar: null,
        status: 1,
        locked: false,
        remark: null,
        createdAt: '2024-01-01',
      })
      await flushPromises()

      // 编辑态无 username/password 字段：模态框不应包含这些 placeholder。
      const usernameInputs = document.body.querySelectorAll(
        'input[placeholder="请输入用户名"]',
      )
      expect(usernameInputs.length).toBe(0)

      await clickSave()
      expect(userApi.update).toHaveBeenCalledTimes(1)
      expect(userApi.update).toHaveBeenCalledWith(42, expect.anything())
      expect(userApi.create).not.toHaveBeenCalled()
    })
  })

  describe('功能交互', () => {
    const mockUser = {
      id: 1,
      username: 'testuser',
      realName: 'Test User',
      email: 'test@example.com',
      phone: '13800138000',
      unitName: 'Test Unit',
      status: 1,
      locked: true,
    }

    it('should call API handlers when functions are invoked with data', async () => {
      userApi.list.mockResolvedValue({ data: { list: [mockUser], total: 1 } })
      const wrapper = await mountUser()
      await flushPromises()

      const ss = (wrapper.vm as unknown as { $: { setupState: Record<string, unknown> } }).$.setupState

      await (ss.handleDelete as (row: unknown) => Promise<void>)(mockUser)
      await flushPromises()
      expect(userApi.delete).toHaveBeenCalledWith(1)

      await (ss.handleUnlock as (row: unknown) => Promise<void>)(mockUser)
      await flushPromises()
      expect(userApi.unlock).toHaveBeenCalledWith(1)

      await (ss.handleResetPassword as (row: unknown) => Promise<void>)(mockUser)
      await flushPromises()
      expect(userApi.resetPassword).toHaveBeenCalledWith(1, 'User@123456')
    })
  })
})
