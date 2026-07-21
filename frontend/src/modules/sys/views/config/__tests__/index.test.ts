import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'
import ConfigIndex from '@/modules/sys/views/config/index.vue'
import { configApi } from '@/modules/sys/api/config'

/**
 * 配置管理视图 CRUD 表单校验边界值测试。
 *
 * 覆盖：
 *  - 新建态：configKey 必填 + 长度 + 模式；configValue 长度上限；
 *    非法时不调用 create；合法时调用 create。
 */

vi.mock('@/modules/sys/api/config', () => ({
  configApi: {
    list: vi.fn().mockResolvedValue({ data: [] }),
    create: vi.fn().mockResolvedValue({ data: 1 }),
    update: vi.fn().mockResolvedValue({}),
    batchUpdate: vi.fn().mockResolvedValue({}),
  },
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

function mountConfig() {
  return mount(ConfigIndex, { global: { plugins: [createPinia()] } })
}

/** 打开"新增配置"对话框并等待表单挂载。 */
async function openCreateModal(wrapper: ReturnType<typeof mountConfig>) {
  const addBtn = wrapper.findAll('button').find((b) => b.text().includes('新增配置'))
  expect(addBtn).toBeTruthy()
  await addBtn!.trigger('click')
  await flushPromises()
}

async function setField(_wrapper: ReturnType<typeof mountConfig>, placeholder: string, value: string) {
  // NModal 通过 teleport 渲染到 document.body，需在 body 范围内查找输入元素。
  const inputs = document.body.querySelectorAll<HTMLInputElement>(`input[placeholder="${placeholder}"], textarea[placeholder="${placeholder}"]`)
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

describe('config/index.vue 表单校验', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    document.body.innerHTML = ''
    vi.clearAllMocks()
  })

  describe('新建态', () => {
    it('should reject empty configKey without calling configApi.create', async () => {
      const wrapper = await mountConfig()
      await flushPromises()
      await openCreateModal(wrapper)

      await clickSave()

      expect(configApi.create).not.toHaveBeenCalled()
    })

    it('should reject configKey with invalid pattern (space)', async () => {
      const wrapper = await mountConfig()
      await flushPromises()
      await openCreateModal(wrapper)

      await setField(wrapper, '如：sys.password.min-length', 'config key')
      await clickSave()

      expect(configApi.create).not.toHaveBeenCalled()
    })

    it('should reject configKey longer than 100 chars', async () => {
      const wrapper = await mountConfig()
      await flushPromises()
      await openCreateModal(wrapper)

      await setField(wrapper, '如：sys.password.min-length', 'a'.repeat(101))
      await clickSave()

      expect(configApi.create).not.toHaveBeenCalled()
    })

    it('should reject configValue longer than 2000 chars', async () => {
      const wrapper = await mountConfig()
      await flushPromises()
      await openCreateModal(wrapper)

      await setField(wrapper, '如：sys.password.min-length', 'sys.valid.key')
      await setField(wrapper, '配置值', 'v'.repeat(2001))
      await clickSave()

      expect(configApi.create).not.toHaveBeenCalled()
    })

    it('should call configApi.create when full form is valid', async () => {
      const wrapper = await mountConfig()
      await flushPromises()
      await openCreateModal(wrapper)

      await setField(wrapper, '如：sys.password.min-length', 'sys.valid.key')
      await clickSave()

      expect(configApi.create).toHaveBeenCalledTimes(1)
      expect(configApi.create).toHaveBeenCalledWith(
        expect.objectContaining({
          configKey: 'sys.valid.key',
        }),
      )
    })
  })
})


describe('功能交互', () => {
  const mockRow = {
    id: 1,
    configKey: 'test.key',
    configValue: 'test.value',
    configType: 'STRING' as const,
    category: 'default',
    description: 'Test config description',
    createTime: '2024-01-01T00:00:00Z',
    updateTime: '2024-01-01T00:00:00Z',
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('should render column render functions and open edit modal when handleEdit is called', async () => {
    configApi.list.mockResolvedValue({ data: [mockRow] })

    const wrapper = await mountConfig()
    await flushPromises()

    // Table should render with data (triggers all column render functions)
    expect(configApi.list).toHaveBeenCalledTimes(1)

    // Get setupState to call handleEdit directly
    const ss = (wrapper.vm as unknown as { $: { setupState: Record<string, unknown> } }).$.setupState

    // Call handleEdit via setupState
    await (ss.handleEdit as (row: unknown) => void)(mockRow)
    await flushPromises()

    // Verify edit modal opened and form data is set correctly
    expect(ss.showModal).toBe(true)
    expect(ss.editingId).toBe(1)
    expect(ss.form).toEqual(expect.objectContaining({
      id: 1,
      configKey: 'test.key',
      configValue: 'test.value',
      configType: 'STRING',
      category: 'default',
      description: 'Test config description',
    }))

    // Verify form input with configKey exists in document.body (teleported modal)
    const configKeyInput = document.body.querySelector<HTMLInputElement>('input[value="test.key"]')
    expect(configKeyInput).toBeTruthy()
  })

  it('should render SECRET type config value as masked dots', async () => {
    const secretRow = {
      ...mockRow,
      id: 2,
      configType: 'SECRET' as const,
      configValue: 'super.secret.api.key',
    }

    configApi.list.mockResolvedValue({ data: [secretRow] })

    const wrapper = await mountConfig()
    await flushPromises()

    // Verify table contains masked value (triggers render function for SECRET type)
    expect(wrapper.text()).toContain('••••••••••••')
    expect(wrapper.text()).not.toContain('super.secret.api.key')
  })

  it('点击表格行编辑按钮触发 handleEdit 并打开编辑弹窗', async () => {
    configApi.list.mockResolvedValue({ data: [mockRow] })
    const wrapper = await mountConfig()
    await flushPromises()

    // 表格 actions 列渲染的「编辑」按钮（来自 render 中的 onClick 内联 handler）
    const editBtn = wrapper.findAll('button').find((b) => b.text().trim() === '编辑')
    expect(editBtn).toBeTruthy()
    await editBtn!.trigger('click')
    await flushPromises()

    // 编辑弹窗打开后 setupState.editingId 被置为当前行 id
    const ss = (wrapper.vm as unknown as { $: { setupState: Record<string, unknown> } }).$.setupState
    expect(ss.editingId).toBe(mockRow.id)

    // 关闭弹窗：NModal preset=card 右上角有 n-base-close 按钮；点击触发 v-model:update:show(false)
    const closeBtn = wrapper.find('.n-base-close')
    if (closeBtn.exists()) {
      await closeBtn.trigger('click')
      await flushPromises()
      expect(ss.showModal).toBe(false)
    }
  })
})