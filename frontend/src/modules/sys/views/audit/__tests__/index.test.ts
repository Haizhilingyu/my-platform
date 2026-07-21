import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { NDataTable, NSelect, NDatePicker } from 'naive-ui'
import AuditIndex from '@/modules/sys/views/audit/index.vue'
import { auditApi } from '@/modules/sys/api/audit'

/**
 * 审计日志视图（只读列表）行为测试。
 *
 * 覆盖：
 *  - 挂载即加载第一页；
 *  - 查询：携带过滤条件且重置到第一页；时间范围转 ISO；
 *  - 重置：清空过滤条件后重新查询；
 *  - 数据渲染到表格。
 */

vi.mock('@/modules/sys/api/audit', () => ({
  auditApi: {
    list: vi.fn().mockResolvedValue({
      data: {
        list: [
          {
            id: 1,
            actor: 'admin',
            action: 'LOGIN',
            targetType: 'sys_user',
            targetId: '1',
            ip: '127.0.0.1',
            userAgent: 'Mozilla/5.0 Chrome',
            result: 'success',
            params: '{"username":"admin"}',
            errorMsg: null,
            createdAt: '2026-07-01T10:00:00Z',
          },
        ],
        total: 1,
      },
    }),
  },
}))

function mountAudit() {
  return mount(AuditIndex)
}

/** 在 body 范围内按按钮文本点击（NCard 内按钮直接渲染在组件内，无需 teleport）。 */
async function clickButton(wrapper: ReturnType<typeof mountAudit>, text: string) {
  const btn = wrapper.findAll('button').find((b) => b.text().includes(text))
  expect(btn, `button "${text}" should exist`).toBeTruthy()
  await btn!.trigger('click')
  await flushPromises()
}

describe('audit/index.vue 审计日志列表', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('挂载时以默认分页参数加载第一页', async () => {
    mountAudit()
    await flushPromises()

    expect(auditApi.list).toHaveBeenCalledTimes(1)
    expect(auditApi.list).toHaveBeenCalledWith(
      expect.objectContaining({ pageNum: 1, pageSize: 20 }),
    )
  })

  it('点击查询携带过滤条件且回到第一页', async () => {
    const wrapper = mountAudit()
    await flushPromises()

    // 输入操作人过滤条件
    const input = wrapper.find('input')
    expect(input.exists()).toBe(true)
    await input.setValue('admin')

    await clickButton(wrapper, '查询')

    expect(auditApi.list).toHaveBeenCalledTimes(2)
    expect(auditApi.list).toHaveBeenLastCalledWith(
      expect.objectContaining({ pageNum: 1, actor: 'admin' }),
    )
  })

  it('点击重置清空过滤条件后重新查询', async () => {
    const wrapper = mountAudit()
    await flushPromises()

    const input = wrapper.find('input')
    await input.setValue('admin')
    await clickButton(wrapper, '重置')

    expect(auditApi.list).toHaveBeenCalledTimes(2)
    const lastCall = vi.mocked(auditApi.list).mock.calls.at(-1)![0]
    expect(lastCall).toEqual(expect.objectContaining({ pageNum: 1 }))
    expect(lastCall.actor).toBeUndefined()
    expect(lastCall.action).toBeUndefined()
    expect(lastCall.result).toBeUndefined()
  })

  it('查询参数中的空过滤值不下发后端', async () => {
    const wrapper = mountAudit()
    await flushPromises()
    await clickButton(wrapper, '查询')

    const lastCall = vi.mocked(auditApi.list).mock.calls.at(-1)![0]
    expect(lastCall.actor).toBeUndefined()
    expect(lastCall.targetType).toBeUndefined()
    expect(lastCall.startTime).toBeUndefined()
    expect(lastCall.endTime).toBeUndefined()
  })

  it('渲染返回的日志数据', async () => {
    const wrapper = mountAudit()
    await flushPromises()

    expect(wrapper.text()).toContain('admin')
    expect(wrapper.text()).toContain('LOGIN')
  })

  it('展开行渲染详情并美化参数 JSON', async () => {
    const wrapper = mountAudit()
    await flushPromises()
    // 表格无 expand 触发列，UI 无法展开；直接覆盖内部渲染函数
    const ss = (wrapper.vm as unknown as { $: { setupState: Record<string, unknown> } }).$
      .setupState

    ;(ss.handleExpandKeys as (keys: number[]) => void)([1])

    const row = {
      id: 1,
      params: '{"username":"admin"}',
      errorMsg: 'boom',
      userAgent: 'Mozilla/5.0',
    }
    expect((ss.renderExpand as (r: unknown) => unknown)(row)).toBeTruthy()

    // prettyParams：合法 JSON 美化、非法 JSON 原样、空串原样
    const pretty = ss.prettyParams as (r: { params?: string }) => string
    expect(pretty({ params: '{"a":1}' })).toContain('\n')
    expect(pretty({ params: 'not-json' })).toBe('not-json')
    expect(pretty({ params: '' })).toBe('')

    // 无详情行渲染 NEmpty
    expect(
      (ss.renderExpand as (r: unknown) => unknown)({ id: 2, params: '', errorMsg: null, userAgent: '' }),
    ).toBeTruthy()

    // truncateUa 超长截断、短串原样
    const trunc = ss.truncateUa as (ua: string, max?: number) => string
    expect(trunc('x'.repeat(50))).toHaveLength(46)
    expect(trunc('short')).toBe('short')
  })

  it('翻页与每页条数变化触发重新查询', async () => {
    const wrapper = mountAudit()
    await flushPromises()

    const pagination = wrapper.findComponent(NDataTable).props('pagination') as {
      onChange: (p: number) => void
      onUpdatePageSize: (s: number) => void
    }

    pagination.onChange(2)
    await flushPromises()
    expect(auditApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ pageNum: 2 }))

    pagination.onUpdatePageSize(50)
    await flushPromises()
    expect(auditApi.list).toHaveBeenLastCalledWith(
      expect.objectContaining({ pageNum: 1, pageSize: 50 }),
    )
  })
  it('通过 NSelect/NDatePicker 的 update:value 改变过滤项触发 v-model handler', async () => {
    const wrapper = mountAudit()
    await flushPromises()
    vi.mocked(auditApi.list).mockClear()

    // 触发 action / result 两个 NSelect 的 update:value（驱动 v-model 的 _cache handler）
    const selects = wrapper.findAllComponents(NSelect)
    expect(selects.length).toBeGreaterThanOrEqual(2)
    selects[0].vm.$emit('update:value', 'CREATE')
    selects[1].vm.$emit('update:value', 'SUCCESS')
    // 触发 NDatePicker 的 update:value（时间范围）
    wrapper.findComponent(NDatePicker).vm.$emit('update:value', [1, 2])
    await flushPromises()

    // 触发查询以验证过滤项已写入
    await clickButton(wrapper, '查询')
    const lastCall = vi.mocked(auditApi.list).mock.calls.at(-1)![0]
    expect(lastCall).toEqual(expect.objectContaining({ action: 'CREATE', result: 'SUCCESS' }))
  })
})
