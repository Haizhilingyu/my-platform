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