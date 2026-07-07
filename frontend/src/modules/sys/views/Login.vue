<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  NCard,
  NForm,
  NFormItem,
  NInput,
  NButton,
  NSpace,
  NTabs,
  NTabPane,
  NIcon,
  NSpin,
  NAlert,
  useMessage,
  type FormInst,
  type FormRules,
} from 'naive-ui'
import { RefreshOutline, LockClosedOutline, PersonOutline, KeyOutline } from '@vicons/ionicons5'
import { useAuthStore } from '@/stores/auth'
import { authApi } from '@/modules/sys/api/auth'
import { useBreakpoint } from '@/shared/composables/useBreakpoint'
import { requiredRule, maxLengthRule } from '@/shared/utils/validation'
import type { LoginMethodDescriptor } from '@/modules/sys/api/types'
import type { AxiosError } from 'axios'

const router = useRouter()
const authStore = useAuthStore()
const message = useMessage()
const { isMobile } = useBreakpoint()

const loading = ref(false)
const methodsLoading = ref(false)
const loginMethods = ref<LoginMethodDescriptor[]>([])
const activeMethod = ref<string>('password')

const form = ref({
  username: '',
  password: '',
  captchaCode: '',
})
const formRef = ref<FormInst | null>(null)
// 登录方式由后端下发，每种方式一个 Tab + 独立表单。由于 NForm 嵌在 v-for 内，
// 字符串 ref 会被 Vue 收集成数组；这里改用函数 ref 始终拿到当前激活表单实例。
function setFormRef(el: unknown): void {
  formRef.value = (el as FormInst | null) ?? null
}
const rules: FormRules = {
  username: [requiredRule('用户名不能为空'), maxLengthRule(32, '用户名长度不能超过32')],
  password: [requiredRule('密码不能为空'), maxLengthRule(32, '密码长度不能超过32')],
  captchaCode: [maxLengthRule(6, '验证码长度不能超过6')],
}
const captchaId = ref<string>('')
const captchaImage = ref<string>('')
const captchaLoading = ref(false)

const errorMessage = ref('')

// 按 order 升序渲染 Tab（后端已排序，这里防御性再排一次保证契约）。
const sortedMethods = computed(() =>
  [...loginMethods.value].sort((a, b) => a.order - b.order)
)

const cardWidth = computed(() => (isMobile.value ? '90%' : '400px'))

onMounted(() => {
  fetchLoginMethods()
  refreshCaptcha()
})

async function fetchLoginMethods() {
  methodsLoading.value = true
  try {
    const res = await authApi.getLoginMethods()
    loginMethods.value = res.data?.length ? res.data : fallbackMethods()
    activeMethod.value = sortedMethods.value[0]?.method ?? 'password'
  } catch {
    // 接口不可用时退化为默认密码登录，保证页面可用。
    loginMethods.value = fallbackMethods()
    activeMethod.value = 'password'
  } finally {
    methodsLoading.value = false
  }
}

function fallbackMethods(): LoginMethodDescriptor[] {
  return [{ method: 'password', label: '账号密码', icon: 'password', order: 0 }]
}

async function refreshCaptcha() {
  captchaLoading.value = true
  try {
    const res = await authApi.getCaptcha()
    captchaId.value = res.data.captchaId
    captchaImage.value = res.data.image
    form.value.captchaCode = ''
  } catch {
    // 验证码拉取失败不阻塞登录尝试，但提示用户。
    message.error('验证码加载失败，请刷新重试')
  } finally {
    captchaLoading.value = false
  }
}

async function handleLogin() {
  errorMessage.value = ''
  try {
    await formRef.value?.validate()
  } catch {
    // Naive UI 会在字段下方自动渲染错误信息，无需再设置 errorMessage。
    return
  }

  loading.value = true
  try {
    await authStore.login({
      method: activeMethod.value,
      username: form.value.username,
      password: form.value.password,
      captchaId: captchaId.value,
      captchaCode: form.value.captchaCode,
    })
    message.success('登录成功')
    router.push('/')
  } catch (e: unknown) {
    // 验证码单次使用，失败后必须刷新；同时映射 HTTP 状态到精确提示。
    refreshCaptcha()
    errorMessage.value = mapLoginError(e)
  } finally {
    loading.value = false
  }
}

// 状态码 → 提示文案映射。423 锁定 / 400 验证码 / 401 凭证 各自独立提示。
function mapLoginError(e: unknown): string {
  const status = (e as AxiosError)?.response?.status
  const serverMsg = ((e as AxiosError<{ message?: string }>)?.response?.data?.message) || ''
  if (status === 423) return '账号已锁定，联系管理员'
  if (status === 400) return serverMsg.includes('验证码') ? '验证码错误' : serverMsg || '请求参数错误'
  if (status === 401) return '用户名或密码错误'
  return serverMsg || '登录失败，请稍后重试'
}

// 按 descriptor.icon 选择 Tab 图标。当前支持 password / ldap，未知走默认。
function iconFor(icon: string | null) {
  if (icon === 'ldap') return KeyOutline
  return PersonOutline
}
</script>

<template>
  <div
    class="min-h-screen flex items-center justify-center bg-[rgb(var(--color-background))] px-4 py-8"
  >
    <NCard
      class="shadow-md"
      :bordered="true"
      :style="{ width: cardWidth, maxWidth: '400px' }"
    >
      <NSpin :show="methodsLoading">
        <NTabs
          v-if="sortedMethods.length"
          v-model:value="activeMethod"
          type="line"
          animated
          size="large"
          :tabs-padding="0"
        >
          <NTabPane
            v-for="m in sortedMethods"
            :key="m.method"
            :name="m.method"
            :tab="m.label"
          >
            <NForm :ref="setFormRef" :model="form" :rules="rules" class="mt-2" @keyup.enter="handleLogin">
              <NFormItem label="用户名" path="username">
                <NInput
                  v-model:value="form.username"
                  placeholder="请输入用户名"
                  :input-props="{ autocomplete: 'username' }"
                  clearable
                >
                  <template #prefix>
                    <NIcon class="text-[rgb(var(--color-text-secondary))]">
                      <PersonOutline />
                    </NIcon>
                  </template>
                </NInput>
              </NFormItem>

              <NFormItem label="密码" path="password">
                <NInput
                  v-model:value="form.password"
                  type="password"
                  show-password-on="click"
                  placeholder="请输入密码"
                  :input-props="{ autocomplete: 'current-password' }"
                >
                  <template #prefix>
                    <NIcon class="text-[rgb(var(--color-text-secondary))]">
                      <LockClosedOutline />
                    </NIcon>
                  </template>
                </NInput>
              </NFormItem>

              <NFormItem label="验证码" path="captchaCode">
                <div class="flex items-stretch gap-2 w-full">
                  <NInput
                    v-model:value="form.captchaCode"
                    placeholder="请输入验证码"
                    maxlength="6"
                    class="flex-1"
                  />
                  <!-- 点击图片刷新验证码：把刷新动作挂在最直观的视觉目标上 -->
                  <button
                    type="button"
                    class="flex-shrink-0 rounded overflow-hidden border border-[rgb(var(--color-border))] cursor-pointer hover:border-[rgb(var(--color-primary))] transition-colors bg-[rgb(var(--color-surface-hover))] flex items-center justify-center h-[34px] min-w-[110px]"
                    title="点击刷新验证码"
                    @click="refreshCaptcha"
                  >
                    <NSpin v-if="captchaLoading" size="small" />
                    <img
                      v-else-if="captchaImage"
                      :src="captchaImage"
                      alt="点击刷新验证码"
                      class="h-full w-full object-cover"
                    >
                    <NIcon v-else size="18" class="text-[rgb(var(--color-text-secondary))]">
                      <RefreshOutline />
                    </NIcon>
                  </button>
                </div>
              </NFormItem>

              <NAlert
                v-if="errorMessage"
                type="error"
                :show-icon="true"
                class="mb-2"
                closable
                @close="errorMessage = ''"
              >
                {{ errorMessage }}
              </NAlert>

              <NSpace vertical :size="12">
                <NButton
                  type="primary"
                  block
                  size="large"
                  :loading="loading"
                  :disabled="loading"
                  @click="handleLogin"
                >
                  登录
                </NButton>
              </NSpace>
            </NForm>
          </NTabPane>
        </NTabs>
      </NSpin>

      <template #header>
        <div class="flex items-center gap-2">
          <NIcon size="22" color="rgb(var(--color-primary))">
            <component :is="iconFor(sortedMethods[0]?.icon ?? 'password')" />
          </NIcon>
          <span class="text-lg font-semibold">My Platform</span>
        </div>
      </template>
    </NCard>
  </div>
</template>
