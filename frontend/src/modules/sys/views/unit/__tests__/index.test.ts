import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'
import UnitIndex from '@/modules/sys/views/unit/index.vue'
import { unitApi } from '@/modules/sys/api/unit'

/**
 * 单位管理视图 CRUD 表单校验边界值测试。
 *
 * 覆盖：
 *  - 新建态：unitCode 必填 + 长度 + 模式；unitName 必填 + 长度；sort 非负整数模式；
 *    非法时不调用 create；合法时调用 create。
 */

vi.mock('@/modules/sys/api/unit', () => ({
  unitApi: {
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

function mountUnit() {
  return mount(UnitIndex, { global: { plugins: [createPinia()] } })
}

/** 打开"新增单位"对话框并等待表单挂载。 */
async function openCreateModal(wrapper: ReturnType<typeof mountUnit>) {
  const addBtn = wrapper.findAll('button').find((b) => b.text().includes('新增单位'))
  expect(addBtn).toBeTruthy()
  await addBtn!.trigger('click')
  await flushPromises()
}

async function setField(_wrapper: ReturnType<typeof mountUnit>, placeholder: string, value: string) {
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

describe('unit/index.vue 表单校验', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    document.body.innerHTML = ''
    vi.clearAllMocks()
  })

  describe('新建态', () => {
    it('should reject empty unitCode without calling unitApi.create', async () => {
      const wrapper = await mountUnit()
      await flushPromises()
      await openCreateModal(wrapper)

      await setField(wrapper, '如 总部', '总部')
      await clickSave()

      expect(unitApi.create).not.toHaveBeenCalled()
    })

    it('should reject unitCode shorter than 3 chars', async () => {
      const wrapper = await mountUnit()
      await flushPromises()
      await openCreateModal(wrapper)

      await setField(wrapper, '如 HQ', 'ab')
      await setField(wrapper, '如 总部', '总部')
      await clickSave()

      expect(unitApi.create).not.toHaveBeenCalled()
    })

    it('should reject unitCode with invalid pattern (hyphen)', async () => {
      const wrapper = await mountUnit()
      await flushPromises()
      await openCreateModal(wrapper)

      await setField(wrapper, '如 HQ', 'unit-1')
      await setField(wrapper, '如 总部', '总部')
      await clickSave()

      expect(unitApi.create).not.toHaveBeenCalled()
    })

    it('should reject empty unitName', async () => {
      const wrapper = await mountUnit()
      await flushPromises()
      await openCreateModal(wrapper)

      await setField(wrapper, '如 HQ', 'HQ001')
      await clickSave()

      expect(unitApi.create).not.toHaveBeenCalled()
    })

    it('should reject unitName longer than 100 chars', async () => {
      const wrapper = await mountUnit()
      await flushPromises()
      await openCreateModal(wrapper)

      await setField(wrapper, '如 HQ', 'HQ001')
      await setField(wrapper, '如 总部', 'x'.repeat(101))
      await clickSave()

      expect(unitApi.create).not.toHaveBeenCalled()
    })

    it('should reject invalid sort value (non-numeric)', async () => {
      const wrapper = await mountUnit()
      await flushPromises()
      await openCreateModal(wrapper)

      await setField(wrapper, '如 HQ', 'HQ001')
      await setField(wrapper, '如 总部', '总部')
      await setField(wrapper, '0', 'abc')
      await clickSave()

      expect(unitApi.create).not.toHaveBeenCalled()
    })

    it('should call unitApi.create when full form is valid', async () => {
      const wrapper = await mountUnit()
      await flushPromises()
      await openCreateModal(wrapper)

      await setField(wrapper, '如 HQ', 'HQ001')
      await setField(wrapper, '如 总部', '总部')
      await clickSave()

      expect(unitApi.create).toHaveBeenCalledTimes(1)
      expect(unitApi.create).toHaveBeenCalledWith(
        expect.objectContaining({
          unitCode: 'HQ001',
          unitName: '总部',
        }),
      )
    })
  })
})
