import { beforeEach } from 'vitest'
import { config } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import i18n from '@/i18n'

/**
 * 全局测试 setup：为所有 mount() 调用自动安装 i18n 插件。
 *
 * 根因：组件中使用 useI18n() 需要 Vue app 通过 app.use(i18n) 安装 vue-i18n 插件，
 * 否则运行时抛出 "Need to install with app.use function"。
 * 此处通过 @vue/test-utils 的 config.global.plugins 统一注入，避免每个测试文件单独配置。
 *
 * 使用 vi.mock('vue-i18n') 的测试（如 i18n.test.ts）不受影响 —— vi.mock 优先级高于插件注入。
 */
config.global.plugins = [i18n]

/** 每个测试用例前重置 Pinia 实例，避免跨用例状态污染。 */
beforeEach(() => {
  setActivePinia(createPinia())
})
