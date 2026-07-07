import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { ref } from 'vue'
import AppIndex from '@/modules/sys/views/app/index.vue'
import { openAppApi } from '@/shared/api/openapp'

/**
 * 开放应用管理视图表单校验边界值测试。
 *
 * 覆盖：
 *  - clientName 必填 + 长度上限；
 *  - redirectUris 至少一个非空值；scopes / grantTypes 至少一项；
 *  - 合法表单时调用 openAppApi.create。
 *
 * 说明：scopes / grantTypes 为 NSelect 多选，难以通过 DOM 驱动，
 * 故合法用例中通过 setupState.form 直接注入数组值触发校验。
 */

vi.mock('@/shared/api/openapp', () => ({
  openAppApi: {
    list: vi.fn().mockResolvedValue({ data: { list: [], total: 0 } }),
    create: vi.fn().mockResolvedValue({
      data: { id: 1, clientId: 'cid-1', clientSecret: 'secret-1' },
    }),
    update: vi.fn().mockResolvedValue({}),
    delete: vi.fn().mockResolvedValue({}),
    resetSecret: vi.fn().mockResolvedValue({
      data: { id: 1, clientId: 'cid-1', clientSecret: 'secret-1' },
    }),
  },
}))

vi.mock('@/shared/composables/useBreakpoint', () => ({
  useBreakpoint: () => ({ isMobile: ref(false) }),
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

function mountApp() {
  return mount(AppIndex, { global: { plugins: [createPinia()] } })
}

/** 打开“新增应用”对话框并等待表单挂载。 */
async function openCreateModal(wrapper: ReturnType<typeof mountApp>) {
  const addBtn = wrapper.findAll('button').find((b) => b.text().includes('新增应用'))
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

interface AppFormState {
  redirectUris: string[]
  scopes: string[]
  grantTypes: string[]
}

function getFormState(wrapper: ReturnType<typeof mountApp>): AppFormState {
  return (wrapper.vm as unknown as { $: { setupState: { form: AppFormState } } }).$.setupState.form
}

describe('app/index.vue 表单校验', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    // NModal 通过 teleport 渲染到 document.body，组件 unmount 后内容可能残留；
    // 清空 body 避免跨用例污染（保留 vue-test-utils 注入的根容器）。
    document.body.innerHTML = ''
    vi.clearAllMocks()
  })

  it('should reject empty clientName without calling openAppApi.create', async () => {
    const wrapper = await mountApp()
    await flushPromises()
    await openCreateModal(wrapper)

    await clickSave()

    expect(openAppApi.create).not.toHaveBeenCalled()
  })

  it('should reject clientName longer than 100 chars', async () => {
    const wrapper = await mountApp()
    await flushPromises()
    await openCreateModal(wrapper)

    await setField('如：移动端 App', 'x'.repeat(101))
    await clickSave()

    expect(openAppApi.create).not.toHaveBeenCalled()
  })

  it('should reject when all redirectUris are empty', async () => {
    const wrapper = await mountApp()
    await flushPromises()
    await openCreateModal(wrapper)

    await setField('如：移动端 App', 'My App')
    getFormState(wrapper).redirectUris = ['']
    await flushPromises()

    await clickSave()

    expect(openAppApi.create).not.toHaveBeenCalled()
  })

  it('should reject when no scope is selected', async () => {
    const wrapper = await mountApp()
    await flushPromises()
    await openCreateModal(wrapper)

    await setField('如：移动端 App', 'My App')
    getFormState(wrapper).redirectUris = ['https://app.example.com/callback']
    await flushPromises()

    await clickSave()

    expect(openAppApi.create).not.toHaveBeenCalled()
  })

  it('should reject when no grantType is selected', async () => {
    const wrapper = await mountApp()
    await flushPromises()
    await openCreateModal(wrapper)

    await setField('如：移动端 App', 'My App')
    const form = getFormState(wrapper)
    form.redirectUris = ['https://app.example.com/callback']
    form.scopes = ['notify:publish']
    await flushPromises()

    await clickSave()

    expect(openAppApi.create).not.toHaveBeenCalled()
  })

  it('should call openAppApi.create when form is valid', async () => {
    const wrapper = await mountApp()
    await flushPromises()
    await openCreateModal(wrapper)

    await setField('如：移动端 App', 'My App')
    const form = getFormState(wrapper)
    form.redirectUris = ['https://app.example.com/callback']
    form.scopes = ['notify:publish']
    form.grantTypes = ['client_credentials']
    await flushPromises()

    await clickSave()

    expect(openAppApi.create).toHaveBeenCalledTimes(1)
    expect(openAppApi.create).toHaveBeenCalledWith(
      expect.objectContaining({
        clientName: 'My App',
        redirectUris: ['https://app.example.com/callback'],
        scopes: ['notify:publish'],
        grantTypes: ['client_credentials'],
      }),
    )
  })
})
