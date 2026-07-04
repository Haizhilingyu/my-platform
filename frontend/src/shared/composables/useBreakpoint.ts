import { ref, onMounted, onUnmounted, type Ref } from 'vue'

/**
 * 响应式断点类型。
 *  - mobile  : < 768px
 *  - tablet  : 768px ~ 1023px
 *  - desktop : >= 1024px
 */
export type Breakpoint = 'mobile' | 'tablet' | 'desktop'

export interface UseBreakpointReturn {
  isMobile: Ref<boolean>
  isTablet: Ref<boolean>
  isDesktop: Ref<boolean>
  breakpoint: Ref<Breakpoint>
}

/** mobile 上界（不含） */
export const MOBILE_MAX = 767
/** tablet 下界（含） */
export const TABLET_MIN = 768
/** tablet 上界（不含） */
export const TABLET_MAX = 1023
/** desktop 下界（含） */
export const DESKTOP_MIN = 1024

function buildQuery(min: number, max: number): string {
  if (min <= 0) return `(max-width: ${max}px)`
  return `(min-width: ${min}px) and (max-width: ${max}px)`
}

/**
 * 响应式断点 composable，基于 window.matchMedia。
 *
 * - 在 onMounted 中订阅媒体查询，在 onUnmounted 中清理监听器，避免内存泄漏。
 * - SSR 安全：当 window 不存在时直接返回默认值（desktop），不抛错。
 *
 * @returns { isMobile, isTablet, isDesktop, breakpoint } 全部为响应式 ref。
 */
export function useBreakpoint(): UseBreakpointReturn {
  // 默认按 desktop 渲染：SSR / 无 JS 场景下桌面布局最稳妥，
  // 客户端挂载后会通过 matchMedia 立即修正为真实断点。
  const isMobile = ref(false)
  const isTablet = ref(false)
  const isDesktop = ref(true)
  const breakpoint = ref<Breakpoint>('desktop')

  let mobileMql: MediaQueryList | null = null
  let tabletMql: MediaQueryList | null = null
  let desktopMql: MediaQueryList | null = null

  function sync(): void {
    isMobile.value = mobileMql?.matches ?? false
    isTablet.value = tabletMql?.matches ?? false
    isDesktop.value = desktopMql?.matches ?? true

    if (isMobile.value) breakpoint.value = 'mobile'
    else if (isTablet.value) breakpoint.value = 'tablet'
    else breakpoint.value = 'desktop'
  }

  onMounted(() => {
    // SSR 安全守卫：window / matchMedia 不可用时直接跳过。
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
      return
    }

    mobileMql = window.matchMedia(buildQuery(0, MOBILE_MAX))
    tabletMql = window.matchMedia(buildQuery(TABLET_MIN, TABLET_MAX))
    desktopMql = window.matchMedia(`(min-width: ${DESKTOP_MIN}px)`)

    sync()

    mobileMql.addEventListener('change', sync)
    tabletMql.addEventListener('change', sync)
    desktopMql.addEventListener('change', sync)
  })

  onUnmounted(() => {
    mobileMql?.removeEventListener('change', sync)
    tabletMql?.removeEventListener('change', sync)
    desktopMql?.removeEventListener('change', sync)
    mobileMql = null
    tabletMql = null
    desktopMql = null
  })

  return { isMobile, isTablet, isDesktop, breakpoint }
}
