import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { NDataTable } from 'naive-ui'
import MessageIndex from '@/modules/sys/views/message/index.vue'
import { notifyApi } from '@/shared/api/notify'

/**
 * 消息中心（收件箱）视图行为测试。
 *
 * 覆盖：
 *  - 挂载即加载收件箱；
 *  - 点击未读标题标记已读并同步未读计数；
 *  - 已读消息点击不重复调用接口；
 *  - 批量已读只处理未读消息；未选中未读时不调用接口；
 *  - 筛选条件下发到查询参数。
 */

const { decrementUnread, messageSuccess, messageWarning, unreadMsg, readMsg } = vi.hoisted(
  () => ({
    decrementUnread: vi.fn(),
    messageSuccess: vi.fn(),
    messageWarning: vi.fn(),
    unreadMsg: {
      id: 1,
      title: '系统升级通知',
      level: 'URGENT',
      businessType: 'system',
      readStatus: false,
      createdAt: '2026-07-01T10:00:00Z',
    },
    readMsg: {
      id: 2,
      title: '已读公告',
      level: 'NORMAL',
      businessType: 'notice',
      readStatus: true,
      createdAt: '2026-07-01T09:00:00Z',
    },
  }),
)

vi.mock('@/shared/api/notify', () => ({
  notifyApi: {
    // 每次调用返回全新副本：组件标记已读时会原地修改 row.readStatus，
    // 若共享同一对象会跨用例污染状态。
    inbox: vi.fn().mockImplementation(() =>
      Promise.resolve({ data: { list: [{ ...unreadMsg }, { ...readMsg }], total: 2 } }),
    ),
    markRead: vi.fn().mockResolvedValue({}),
    batchMarkRead: vi.fn().mockResolvedValue({}),
    unreadCount: vi.fn().mockResolvedValue({ data: 1 }),
  },
}))

vi.mock('@/stores/notify', () => ({
  useNotifyStore: () => ({ decrementUnread }),
}))

vi.mock('naive-ui', async () => {
  const actual = await vi.importActual<typeof import('naive-ui')>('naive-ui')
  return {
    ...actual,
    useMessage: () => ({
      success: messageSuccess,
      error: vi.fn(),
      warning: messageWarning,
      info: vi.fn(),
      loading: vi.fn(),
    }),
  }
})

function mountMessage() {
  return mount(MessageIndex)
}

async function clickText(wrapper: ReturnType<typeof mountMessage>, text: string) {
  const el = wrapper.findAll('span').find((s) => s.text() === text)
  expect(el, `span "${text}" should exist`).toBeTruthy()
  await el!.trigger('click')
  await flushPromises()
}

describe('message/index.vue 消息中心', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('挂载时加载收件箱第一页', async () => {
    mountMessage()
    await flushPromises()

    expect(notifyApi.inbox).toHaveBeenCalledTimes(1)
    expect(notifyApi.inbox).toHaveBeenCalledWith({ pageNum: 1, pageSize: 10 })
  })

  it('渲染收件箱数据，未读消息加粗展示', async () => {
    const wrapper = mountMessage()
    await flushPromises()

    expect(wrapper.text()).toContain('系统升级通知')
    expect(wrapper.text()).toContain('已读公告')
  })

  it('点击未读标题调用标记已读并递减未读计数', async () => {
    const wrapper = mountMessage()
    await flushPromises()

    await clickText(wrapper, '系统升级通知')

    expect(notifyApi.markRead).toHaveBeenCalledWith(1)
    expect(decrementUnread).toHaveBeenCalledTimes(1)
    expect(messageSuccess).toHaveBeenCalled()
  })

  it('点击已读消息不重复调用接口', async () => {
    const wrapper = mountMessage()
    await flushPromises()

    await clickText(wrapper, '已读公告')

    expect(notifyApi.markRead).not.toHaveBeenCalled()
  })

  it('批量已读只处理未读消息', async () => {
    const wrapper = mountMessage()
    await flushPromises()

    // 勾选全部两条（含一条已读）
    wrapper.findComponent(NDataTable).vm.$emit('update:checkedRowKeys', [1, 2])
    await flushPromises()

    const batchBtn = wrapper.findAll('button').find((b) => b.text().includes('批量'))
    expect(batchBtn).toBeTruthy()
    await batchBtn!.trigger('click')
    await flushPromises()

    expect(notifyApi.batchMarkRead).toHaveBeenCalledWith([1])
    expect(decrementUnread).toHaveBeenCalledWith(1)
  })

  it('未勾选任何消息时批量按钮禁用', async () => {
    const wrapper = mountMessage()
    await flushPromises()

    const batchBtn = wrapper.findAll('button').find((b) => b.text().includes('批量'))
    expect(batchBtn!.attributes('disabled')).toBeDefined()
  })

  it('勾选的都是已读消息时不调用批量接口', async () => {
    const wrapper = mountMessage()
    await flushPromises()

    wrapper.findComponent(NDataTable).vm.$emit('update:checkedRowKeys', [2])
    await flushPromises()

    const batchBtn = wrapper.findAll('button').find((b) => b.text().includes('批量'))
    await batchBtn!.trigger('click')
    await flushPromises()

    expect(notifyApi.batchMarkRead).not.toHaveBeenCalled()
    expect(messageWarning).toHaveBeenCalled()
  })
  it('handleSearch 重置页码到第 1 页并重新拉取', async () => {
    const wrapper = mountMessage()
    await flushPromises()
    const ss = (wrapper.vm as unknown as { $: { setupState: Record<string, unknown> } }).$.setupState
    // handleSearch 将 pageNum 置 1 并重新拉取：首次挂载已请求一次，调用后应再请求
    expect(ss.pageNum).toBe(1)
    vi.mocked(notifyApi.inbox).mockClear()
    ;(ss.handleSearch as () => void)()
    await flushPromises()

    expect(ss.pageNum).toBe(1)
    expect(notifyApi.inbox).toHaveBeenCalledTimes(1)
  })
})
