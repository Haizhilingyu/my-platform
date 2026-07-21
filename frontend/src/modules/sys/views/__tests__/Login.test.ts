import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { ref } from 'vue'
import Login from '@/modules/sys/views/Login.vue'

/**
 * Login 视图表单校验边界值测试。
 *
 * 策略：
 *  - mock authApi（避免真实网络请求）、useBreakpoint（绕过 matchMedia 复杂度）、
 *    useAuthStore（断言 login 是否被调用）、useMessage（脱离 NMessageProvider 上下文）；
 *  - 通过真实挂载 Naive UI 表单组件，触发 `formRef.value?.validate()`，
 *    断言非法输入时 authStore.login 不被调用、合法输入时被调用。
 */

vi.mock('@/modules/sys/api/auth', () => ({
  authApi: {
    getLoginMethods: vi.fn().mockResolvedValue({
      data: [{ method: 'password', label: '密码', icon: 'password', order: 0 }],
    }),
    getCaptcha: vi
      .fn()
      .mockResolvedValue({ data: { captchaId: 'cap-1', image: 'data:image/png;base64,AAA' } }),
  },
}))

vi.mock('@/shared/composables/useBreakpoint', () => ({
  useBreakpoint: () => ({ isMobile: ref(false) }),
}))

const loginSpy = vi.fn().mockResolvedValue(undefined)
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    login: loginSpy,
    logout: vi.fn(),
    hasPermission: () => true,
  }),
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

const routerPushSpy = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: routerPushSpy, replace: routerPushSpy }),
}))

function mountLogin() {
  return mount(Login, { global: { plugins: [createPinia()] } })
}

async function setFieldValue(
  wrapper: ReturnType<typeof mount>,
  placeholder: string,
  value: string,
) {
  const input = wrapper.find(`input[placeholder="${placeholder}"]`)
  await input.setValue(value)
}

async function clickLogin(wrapper: ReturnType<typeof mount>) {
  // 登录按钮是表单内 type="primary" 的 NButton；用 button 文本定位最稳。
  const btn = wrapper.findAll('button').find((b) => b.text().includes('登录'))
  expect(btn).toBeTruthy()
  await btn!.trigger('click')
  await flushPromises()
}

describe('Login.vue 表单校验', () => {
  beforeEach(() => {
    loginSpy.mockClear()
    routerPushSpy.mockClear()
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('should reject empty username and password without calling authStore.login', async () => {
    const wrapper = await mountLogin()
    await flushPromises()

    await clickLogin(wrapper)

    expect(loginSpy).not.toHaveBeenCalled()
  })

  it('should reject username longer than 32 chars', async () => {
    const wrapper = await mountLogin()
    await flushPromises()

    await setFieldValue(wrapper, '请输入用户名', 'a'.repeat(33))
    await setFieldValue(wrapper, '请输入密码', 'validpass')
    await clickLogin(wrapper)

    expect(loginSpy).not.toHaveBeenCalled()
  })

  it('should reject password longer than 32 chars', async () => {
    const wrapper = await mountLogin()
    await flushPromises()

    await setFieldValue(wrapper, '请输入用户名', 'alice')
    await setFieldValue(wrapper, '请输入密码', 'p'.repeat(33))
    await clickLogin(wrapper)

    expect(loginSpy).not.toHaveBeenCalled()
  })

  it('should reject captcha code longer than 6 chars', async () => {
    const wrapper = await mountLogin()
    await flushPromises()

    await setFieldValue(wrapper, '请输入用户名', 'alice')
    await setFieldValue(wrapper, '请输入密码', 'validpass')
    await setFieldValue(wrapper, '请输入验证码', '1234567')
    await clickLogin(wrapper)

    expect(loginSpy).not.toHaveBeenCalled()
  })

  it('should call authStore.login when username and password are valid', async () => {
    const wrapper = await mountLogin()
    await flushPromises()

    await setFieldValue(wrapper, '请输入用户名', 'alice')
    await setFieldValue(wrapper, '请输入密码', 'secret123')
    await clickLogin(wrapper)

    expect(loginSpy).toHaveBeenCalledTimes(1)
    expect(loginSpy).toHaveBeenCalledWith(
      expect.objectContaining({ username: 'alice', password: 'secret123' }),
    )
  })
})

describe('Login.vue 错误映射与兜底登录方式', () => {
  const setupStateOf = (wrapper: ReturnType<typeof mountLogin>) =>
    (wrapper.vm as unknown as { $: { setupState: Record<string, unknown> } }).$.setupState

  it('mapLoginError 将 HTTP 状态码映射为对应文案 key', async () => {
    const wrapper = await mountLogin()
    await flushPromises()
    const mapLoginError = setupStateOf(wrapper).mapLoginError as (e: unknown) => string

    // 423 → 账户锁定
    expect(mapLoginError({ response: { status: 423 } })).toBeTruthy()
    // 401 → 凭证错误
    expect(mapLoginError({ response: { status: 401 } })).toBeTruthy()
    // 400 + 验证码关键字 → 验证码错误
    expect(mapLoginError({ response: { status: 400, data: { message: '验证码错误' } } })).toBeTruthy()
    // 400 + 服务端其它消息 → 原样回传
    expect(mapLoginError({ response: { status: 400, data: { message: '自定义错误' } } })).toContain('自定义错误')
    // 无 status → 登录失败兜底
    expect(mapLoginError({})).toBeTruthy()
  })

  it('fallbackMethods 返回密码登录兜底项', async () => {
    const wrapper = await mountLogin()
    await flushPromises()
    const fallbackMethods = setupStateOf(wrapper).fallbackMethods as () => Array<{ method: string }>

    const methods = fallbackMethods()
    expect(methods).toHaveLength(1)
    expect(methods[0].method).toBe('password')
  })
})
