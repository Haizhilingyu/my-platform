import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'
import { useBreakpoint } from '@/shared/composables/useBreakpoint'

/**
 * useBreakpoint composable 测试。
 *
 * 通过 mock window.matchMedia 模拟不同视口宽度，断言：
 *  - 三档断点（mobile / tablet / desktop）边界判定正确；
 *  - matchMedia 监听器在挂载时订阅、卸载时清理，避免内存泄漏；
 *  - SSR 守卫：matchMedia 不可用时不抛错并回退到 desktop 默认值。
 */
describe('useBreakpoint', () => {
  let matchMediaSpy: ReturnType<typeof vi.fn>
  let addEventListenerSpy: ReturnType<typeof vi.fn>
  let removeEventListenerSpy: ReturnType<typeof vi.fn>

  beforeEach(() => {
    addEventListenerSpy = vi.fn()
    removeEventListenerSpy = vi.fn()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  /** 按给定视口宽度构造 matchMedia mock，依据 query 中的 min/max 计算是否匹配。 */
  function installMatchMediaMock(viewportWidth: number) {
    matchMediaSpy = vi.fn((query: string) => {
      const minMatch = query.match(/min-width:\s*(\d+)/)
      const maxMatch = query.match(/max-width:\s*(\d+)/)
      const min = minMatch ? Number(minMatch[1]) : 0
      const max = maxMatch ? Number(maxMatch[1]) : Infinity
      const matches = viewportWidth >= min && viewportWidth <= max
      return {
        matches,
        media: query,
        onchange: null,
        addEventListener: addEventListenerSpy,
        removeEventListener: removeEventListenerSpy,
        dispatchEvent: vi.fn(),
      } as unknown as MediaQueryList
    })
    vi.stubGlobal('matchMedia', matchMediaSpy)
  }

  /** 用一个占位组件挂载 composable，返回其暴露的断点状态。 */
  function mountWithBreakpoint() {
    const Probe = defineComponent({
      setup() {
        const bp = useBreakpoint()
        return { bp }
      },
      template: '<div />',
    })
    return mount(Probe)
  }

  describe('断点判定', () => {
    it('should report mobile when viewport < 768', () => {
      installMatchMediaMock(375)
      const wrapper = mountWithBreakpoint()
      const { bp } = wrapper.vm as any

      expect(bp.isMobile.value).toBe(true)
      expect(bp.isTablet.value).toBe(false)
      expect(bp.isDesktop.value).toBe(false)
      expect(bp.breakpoint.value).toBe('mobile')
    })

    it('should report tablet when 768 <= viewport < 1024', () => {
      installMatchMediaMock(800)
      const wrapper = mountWithBreakpoint()
      const { bp } = wrapper.vm as any

      expect(bp.isMobile.value).toBe(false)
      expect(bp.isTablet.value).toBe(true)
      expect(bp.isDesktop.value).toBe(false)
      expect(bp.breakpoint.value).toBe('tablet')
    })

    it('should report desktop when viewport >= 1024', () => {
      installMatchMediaMock(1280)
      const wrapper = mountWithBreakpoint()
      const { bp } = wrapper.vm as any

      expect(bp.isMobile.value).toBe(false)
      expect(bp.isTablet.value).toBe(false)
      expect(bp.isDesktop.value).toBe(true)
      expect(bp.breakpoint.value).toBe('desktop')
    })
  })

  describe('边界值', () => {
    it('should treat 767 as mobile', () => {
      installMatchMediaMock(767)
      const wrapper = mountWithBreakpoint()
      expect((wrapper.vm as any).bp.breakpoint.value).toBe('mobile')
    })

    it('should treat 768 as tablet', () => {
      installMatchMediaMock(768)
      const wrapper = mountWithBreakpoint()
      expect((wrapper.vm as any).bp.breakpoint.value).toBe('tablet')
    })

    it('should treat 1023 as tablet', () => {
      installMatchMediaMock(1023)
      const wrapper = mountWithBreakpoint()
      expect((wrapper.vm as any).bp.breakpoint.value).toBe('tablet')
    })

    it('should treat 1024 as desktop', () => {
      installMatchMediaMock(1024)
      const wrapper = mountWithBreakpoint()
      expect((wrapper.vm as any).bp.breakpoint.value).toBe('desktop')
    })
  })

  describe('监听器生命周期', () => {
    it('should subscribe to matchMedia change events on mount', () => {
      installMatchMediaMock(1280)
      mountWithBreakpoint()

      // mobile / tablet / desktop 三条媒体查询各订阅一次
      expect(addEventListenerSpy).toHaveBeenCalledTimes(3)
      expect(addEventListenerSpy).toHaveBeenCalledWith('change', expect.any(Function))
    })

    it('should unsubscribe from matchMedia change events on unmount', () => {
      installMatchMediaMock(1280)
      const wrapper = mountWithBreakpoint()

      expect(removeEventListenerSpy).not.toHaveBeenCalled()

      wrapper.unmount()

      expect(removeEventListenerSpy).toHaveBeenCalledTimes(3)
      expect(removeEventListenerSpy).toHaveBeenCalledWith('change', expect.any(Function))
    })
  })

  describe('SSR 安全', () => {
    it('should fall back to desktop defaults when matchMedia is unavailable', () => {
      vi.stubGlobal('matchMedia', undefined)
      const wrapper = mountWithBreakpoint()
      const { bp } = wrapper.vm as any

      expect(bp.isDesktop.value).toBe(true)
      expect(bp.isMobile.value).toBe(false)
      expect(bp.isTablet.value).toBe(false)
      expect(bp.breakpoint.value).toBe('desktop')
    })
  })
})
