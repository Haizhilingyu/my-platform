import type { Directive } from 'vue'
import { useAuthStore } from '@/stores/auth'

/**
 * v-permission 指令：控制元素显示/隐藏
 * @example v-permission="'sys:user:add'"
 * @example v-permission="['sys:user:add', 'sys:user:edit']"
 */
export const vPermission: Directive<HTMLElement, string | string[]> = {
  mounted(el, binding) {
    const auth = useAuthStore()
    const value = binding.value

    const perms = Array.isArray(value) ? value : [value]
    const hasPermission = perms.some((p) => auth.hasPermission(p))

    if (!hasPermission) {
      el.parentNode?.removeChild(el)
    }
  },
}
