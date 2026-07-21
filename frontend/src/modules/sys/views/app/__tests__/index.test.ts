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

describe('app/index.vue 行操作', () => {
  function makeRow() {
    return {
      id: 9,
      clientId: 'cid-9',
      clientName: '已有应用',
      redirectUris: ['https://a.com/cb'],
      postLogoutRedirectUris: ['https://a.com/out'],
      scopes: ['read'],
      grantTypes: ['authorization_code'],
      enabled: true,
      createdAt: '2026-07-01T10:00:00Z',
    }
  }

  const setupStateOf = (wrapper: ReturnType<typeof mountApp>) =>
    (wrapper.vm as unknown as { $: { setupState: Record<string, unknown> } }).$.setupState

  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(openAppApi.list).mockResolvedValue({
      data: { list: [makeRow()], total: 1 },
    } as never)
  })

  it('行渲染 scope/grantType 标签与启用开关', async () => {
    const wrapper = mountApp()
    await flushPromises()

    expect(wrapper.text()).toContain('cid-9')
    expect(wrapper.text()).toContain('已有应用')
    expect(wrapper.text()).toContain('read')
  })

  it('切换启用状态调用 update 并原地更新行', async () => {
    const wrapper = mountApp()
    await flushPromises()
    const row = makeRow()

    await (setupStateOf(wrapper).handleToggleEnabled as (r: unknown, v: boolean) => Promise<void>)(
      row,
      false,
    )

    expect(openAppApi.update).toHaveBeenCalledWith(9, expect.objectContaining({ enabled: false }))
    expect(row.enabled).toBe(false)
  })

  it('编辑行预填表单，支持增删重定向 URI', async () => {
    const wrapper = mountApp()
    await flushPromises()
    const ss = setupStateOf(wrapper)

    await (ss.handleEdit as (r: unknown) => Promise<void>)(makeRow())
    await flushPromises()

    const form = ss.form as {
      clientName: string
      redirectUris: string[]
      postLogoutRedirectUris: string[]
    }
    // 表单已预填行数据（input 值不进 textContent，断言 setupState 的 form）
    expect(form.clientName).toBe('已有应用')
    expect(form.redirectUris).toEqual(['https://a.com/cb'])

    // 增删 redirectUri / postLogoutUri 表单项
    ;(ss.addRedirectUri as () => void)()
    expect(form.redirectUris).toHaveLength(2)
    ;(ss.removeRedirectUri as (i: number) => void)(1)
    expect(form.redirectUris).toEqual(['https://a.com/cb'])
    ;(ss.addPostLogoutUri as () => void)()
    expect(form.postLogoutRedirectUris).toHaveLength(2)
    ;(ss.removePostLogoutUri as (i: number) => void)(1)
    expect(form.postLogoutRedirectUris).toEqual(['https://a.com/out'])
  })

  it('重置密钥调用 resetSecret 并弹窗展示', async () => {
    const wrapper = mountApp()
    await flushPromises()

    await (setupStateOf(wrapper).handleResetSecret as (r: unknown) => Promise<void>)(makeRow())

    expect(openAppApi.resetSecret).toHaveBeenCalledWith(9)
  })

  it('删除应用调用 delete 并刷新列表', async () => {
    const wrapper = mountApp()
    await flushPromises()
    vi.mocked(openAppApi.list).mockClear()

    await (setupStateOf(wrapper).handleDelete as (r: unknown) => Promise<void>)(makeRow())
    await flushPromises()

    expect(openAppApi.delete).toHaveBeenCalledWith(9)
    expect(openAppApi.list).toHaveBeenCalledTimes(1)
  })

  it('复制文本写入剪贴板并提示成功', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(window.navigator, 'clipboard', {
      value: { writeText },
      configurable: true,
    })
    const wrapper = mountApp()
    await flushPromises()

    await (setupStateOf(wrapper).copyText as (t: string) => Promise<void>)('secret-value')

    expect(writeText).toHaveBeenCalledWith('secret-value')
  })
})
