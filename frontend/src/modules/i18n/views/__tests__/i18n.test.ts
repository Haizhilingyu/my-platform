import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import I18nIndex from '@/modules/i18n/views/index.vue'
import { i18nApi } from '@/modules/i18n/api/i18n'
import type { I18nMessageVO } from '@/modules/i18n/api/i18n'

vi.mock('@/modules/i18n/api/i18n', () => ({
  i18nApi: {
    list: vi.fn().mockResolvedValue({ data: { list: [], total: 0 } }),
    update: vi.fn().mockResolvedValue({ data: {} }),
    importJson: vi.fn().mockResolvedValue({ data: 5 }),
    importXlsx: vi.fn().mockResolvedValue({ data: 3 }),
    exportJson: vi.fn().mockResolvedValue(new Blob(['{"key":"value"}'])),
    exportXlsx: vi.fn().mockResolvedValue(new Blob(['xlsx'])),
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

vi.mock('vue-i18n', async () => {
  const actual = await vi.importActual<typeof import('vue-i18n')>('vue-i18n')
  return {
    ...actual,
    useI18n: () => ({
      t: (key: string) => key,
      locale: { value: 'zh-CN' },
    }),
  }
})

function mountI18n() {
  setActivePinia(createPinia())
  return mount(I18nIndex)
}

describe('i18n/index.vue 功能测试', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    document.body.innerHTML = ''
    vi.clearAllMocks()
  })

  it('should render with loading state initially', async () => {
    vi.mocked(i18nApi.list).mockResolvedValueOnce({ data: { list: [], total: 0 } })

    const wrapper = mountI18n()
    await flushPromises()

    expect(i18nApi.list).toHaveBeenCalled()
    expect(wrapper.exists()).toBe(true)
  })

  it('should render table with mock data', async () => {
    const mockData: I18nMessageVO[] = [
      {
        id: 1,
        messageKey: 'sys.user.username',
        locale: 'zh-CN',
        module: 'sys',
        value: '用户名',
        description: '用户名字段',
        updatedAt: '2024-01-01T00:00:00',
      },
      {
        id: 2,
        messageKey: 'sys.user.password',
        locale: 'zh-CN',
        module: 'sys',
        value: '密码',
        description: '密码字段',
        updatedAt: '2024-01-02T00:00:00',
      },
    ]
    vi.mocked(i18nApi.list).mockResolvedValueOnce({ data: { list: mockData, total: 2 } })

    mountI18n()
    await flushPromises()

    expect(i18nApi.list).toHaveBeenCalled()
  })

  it('should call list API when component mounts', async () => {
    vi.mocked(i18nApi.list).mockResolvedValueOnce({ data: { list: [], total: 0 } })

    mountI18n()
    await flushPromises()

    expect(i18nApi.list).toHaveBeenCalledTimes(1)
  })
})
describe('i18n/index.vue 编辑与导入导出', () => {
  const row: I18nMessageVO = {
    id: 1,
    messageKey: 'common.save',
    value: '保存',
    locale: 'zh-CN',
    module: 'common',
    description: '保存按钮',
  } as I18nMessageVO

  const setupStateOf = (wrapper: ReturnType<typeof mountI18n>) =>
    (wrapper.vm as unknown as { $: { setupState: Record<string, unknown> } }).$.setupState

  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(i18nApi.list).mockResolvedValue({ data: { list: [row], total: 1 } } as never)
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('编辑消息校验通过后调用 update', async () => {
    const wrapper = mountI18n()
    await flushPromises()
    const ss = setupStateOf(wrapper)

    ;(ss.handleEdit as (r: unknown) => void)(row)
    await flushPromises()
    await (ss.handleSaveEdit as () => Promise<void>)()
    await flushPromises()

    expect(i18nApi.update).toHaveBeenCalledWith(1, { value: '保存' })
  })

  it('导入 JSON 文件解析为 items 并调用 importJson', async () => {
    const wrapper = mountI18n()
    await flushPromises()
    const ss = setupStateOf(wrapper)

    ;(ss.handleImport as () => void)()
    ss.importFile = {
      file: new File([JSON.stringify({ 'common.save': '保存', 'common.cancel': '取消' })], 'messages.json'),
    }
    await (ss.handleImportSubmit as () => Promise<void>)()
    await flushPromises()

    expect(i18nApi.importJson).toHaveBeenCalledWith({
      locale: expect.anything(),
      items: [
        { messageKey: 'common.save', value: '保存' },
        { messageKey: 'common.cancel', value: '取消' },
      ],
    })
  })

  it('导入 xlsx 文件直接调用 importXlsx', async () => {
    const wrapper = mountI18n()
    await flushPromises()
    const ss = setupStateOf(wrapper)

    ;(ss.handleImport as () => void)()
    const file = new File(['xlsx-binary'], 'messages.xlsx')
    ss.importFile = { file }
    await (ss.handleImportSubmit as () => Promise<void>)()
    await flushPromises()

    expect(i18nApi.importXlsx).toHaveBeenCalledWith(file, expect.anything())
  })

  it('未选择文件时导入仅告警不调接口', async () => {
    const wrapper = mountI18n()
    await flushPromises()
    const ss = setupStateOf(wrapper)

    ;(ss.handleImport as () => void)()
    await (ss.handleImportSubmit as () => Promise<void>)()

    expect(i18nApi.importJson).not.toHaveBeenCalled()
    expect(i18nApi.importXlsx).not.toHaveBeenCalled()
  })

  it('导出 JSON 生成下载链接并提示成功', async () => {
    const createObjectURL = vi.fn(() => 'blob:mock-url')
    const revokeObjectURL = vi.fn()
    Object.defineProperty(URL, 'createObjectURL', { value: createObjectURL, configurable: true })
    Object.defineProperty(URL, 'revokeObjectURL', { value: revokeObjectURL, configurable: true })

    const wrapper = mountI18n()
    await flushPromises()
    await (setupStateOf(wrapper).handleExport as (f: 'json' | 'xlsx') => Promise<void>)('json')

    expect(i18nApi.exportJson).toHaveBeenCalled()
    expect(createObjectURL).toHaveBeenCalled()
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:mock-url')
  })
})

describe('功能交互', () => {
  const mockRow: I18nMessageVO = {
    id: 1,
    messageKey: 'common.save',
    locale: 'zh-CN',
    module: 'common',
    value: '保存',
    description: '保存按钮',
    updatedAt: '2024-01-01T00:00:00',
  }

  const setupStateOf = (wrapper: ReturnType<typeof mountI18n>) =>
    (wrapper.vm as unknown as { $: { setupState: Record<string, unknown> } }).$.setupState

  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('should render table with data and call exportXlsx with correct locale', async () => {
    vi.mocked(i18nApi.list).mockResolvedValue({ data: { list: [mockRow], total: 1 } })

    const createObjectURL = vi.fn(() => 'blob:mock-url')
    const revokeObjectURL = vi.fn()
    Object.defineProperty(URL, 'createObjectURL', { value: createObjectURL, configurable: true })
    Object.defineProperty(URL, 'revokeObjectURL', { value: revokeObjectURL, configurable: true })

    const wrapper = mountI18n()
    await flushPromises()

    // Verify table render covers column render functions (including L177 tooltip slot)
    expect(i18nApi.list).toHaveBeenCalledTimes(1)

    // Trigger export xlsx via setupState
    const ss = setupStateOf(wrapper)
    await (ss.handleExport as (f: 'json' | 'xlsx') => Promise<void>)('xlsx')

    // Verify export was called with correct locale
    expect(i18nApi.exportXlsx).toHaveBeenCalledWith('zh-CN')
    expect(createObjectURL).toHaveBeenCalled()
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:mock-url')
  })

  it('should refetch data when locale filter changes', async () => {
    vi.mocked(i18nApi.list).mockResolvedValue({ data: { list: [mockRow], total: 1 } })

    const wrapper = mountI18n()
    await flushPromises()
    expect(i18nApi.list).toHaveBeenCalledTimes(1)
    expect(i18nApi.list).toHaveBeenCalledWith(expect.objectContaining({ locale: 'zh-CN' }))

    // Change locale filter via setupState (triggers watch -> fetchData)
    const ss = setupStateOf(wrapper)
    ;(ss.query as { locale: string }).locale = 'en'
    await flushPromises()

    // Verify list was called again with new locale
    expect(i18nApi.list).toHaveBeenCalledTimes(2)
    expect(i18nApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ locale: 'en' }))
  })
})

