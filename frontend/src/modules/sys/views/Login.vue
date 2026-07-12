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
import { RefreshOutline, LockClosedOutline, PersonOutline } from '@vicons/ionicons5'
import { useAuthStore } from '@/stores/auth'
import { authApi } from '@/modules/sys/api/auth'
import { requiredRule, maxLengthRule } from '@/shared/utils/validation'
import type { LoginMethodDescriptor } from '@/modules/sys/api/types'
import type { AxiosError } from 'axios'
import { useI18n } from 'vue-i18n'

const router = useRouter()
const authStore = useAuthStore()
const message = useMessage()
const { t } = useI18n()

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
  username: [requiredRule(t('login.usernameRequired')), maxLengthRule(32, t('login.usernameTooLong'))],
  password: [requiredRule(t('login.passwordRequired')), maxLengthRule(32, t('login.passwordTooLong'))],
  captchaCode: [maxLengthRule(6, t('login.captchaTooLong'))],
}
const captchaId = ref<string>('')
const captchaImage = ref<string>('')
const captchaLoading = ref(false)

const errorMessage = ref('')

// 按 order 升序渲染 Tab（后端已排序，这里防御性再排一次保证契约）。
const sortedMethods = computed(() =>
  [...loginMethods.value].sort((a, b) => a.order - b.order)
)

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
  return [{ method: 'password', label: t('login.passwordMethod'), icon: 'password', order: 0 }]
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
    message.error(t('login.captchaLoadFailed'))
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
    message.success(t('login.loginSuccess'))
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
  if (status === 423) return t('login.accountLocked')
  if (status === 400) return serverMsg.includes('验证码') ? t('login.captchaError') : serverMsg || t('login.requestError')
  if (status === 401) return t('login.invalidCredentials')
  return serverMsg || t('login.loginFailed')
}
</script>

<template>
  <div class="min-h-screen flex flex-col md:flex-row">
    <!-- Left panel: Branding -->
    <div class="w-full md:w-[55%] min-h-[180px] md:min-h-screen flex flex-col justify-between p-8 md:p-12 bg-[rgb(var(--color-surface))] bg-blueprint">
      <!-- Top content: Logo and features -->
      <div class="flex flex-col justify-center">
        <h1 class="font-display text-4xl md:text-[2.5rem] font-bold text-[rgb(var(--color-primary))] mb-2">
          My Platform
        </h1>
        <p class="text-[rgb(var(--color-text-secondary))] text-lg mb-8 md:mb-12">
          {{ t('login.subtitle') }}
        </p>

        <!-- Feature bullets - hide on mobile, show on desktop -->
        <div class="hidden md:flex flex-col gap-6">
          <div>
            <div class="micro-label mb-1">MODULES</div>
            <p class="text-[rgb(var(--color-text-secondary))] text-sm">
              {{ t('login.featureModules') }}
            </p>
          </div>
          <div>
            <div class="micro-label mb-1">SECURITY</div>
            <p class="text-[rgb(var(--color-text-secondary))] text-sm">
              {{ t('login.featureSecurity') }}
            </p>
          </div>
          <div>
            <div class="micro-label mb-1">AUDIT</div>
            <p class="text-[rgb(var(--color-text-secondary))] text-sm">
              {{ t('login.featureAudit') }}
            </p>
          </div>
        </div>
      </div>

      <!-- Bottom: Version label -->
      <div class="font-mono-data text-[rgb(var(--color-text-secondary))] text-sm mt-4 md:mt-0">
        v1.0.0
      </div>
    </div>

    <!-- Right panel: Login form -->
    <div class="w-full md:w-[45%] min-h-screen flex items-center justify-center p-6 md:p-8 bg-[rgb(var(--color-background))]">
      <div class="w-full max-w-[360px]">
        <!-- Section header -->
        <div class="mb-6">
          <div class="micro-label mb-2">SIGN IN</div>
        </div>

        <!-- Login card without header -->
        <NCard class="shadow-md" :bordered="true">
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
                  <NFormItem :label="t('login.username')" path="username">
                    <NInput
                      v-model:value="form.username"
                      :placeholder="t('login.usernamePlaceholder')"
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

                  <NFormItem :label="t('login.password')" path="password">
                    <NInput
                      v-model:value="form.password"
                      type="password"
                      show-password-on="click"
                      :placeholder="t('login.passwordPlaceholder')"
                      :input-props="{ autocomplete: 'current-password' }"
                    >
                      <template #prefix>
                        <NIcon class="text-[rgb(var(--color-text-secondary))]">
                          <LockClosedOutline />
                        </NIcon>
                      </template>
                    </NInput>
                  </NFormItem>

                  <NFormItem :label="t('login.captchaCode')" path="captchaCode">
                    <div class="flex items-stretch gap-2 w-full">
                      <NInput
                        v-model:value="form.captchaCode"
                        :placeholder="t('login.captchaPlaceholder')"
                        maxlength="6"
                        class="flex-1"
                      />
                      <!-- 点击图片刷新验证码：把刷新动作挂在最直观的视觉目标上 -->
                      <button
                        type="button"
                        class="flex-shrink-0 rounded overflow-hidden border border-[rgb(var(--color-border))] cursor-pointer hover:border-[rgb(var(--color-primary))] transition-colors bg-[rgb(var(--color-surface-hover))] flex items-center justify-center h-[34px] min-w-[110px]"
                        :title="t('login.refreshCaptcha')"
                        @click="refreshCaptcha"
                      >
                        <NSpin v-if="captchaLoading" size="small" />
                        <img
                          v-else-if="captchaImage"
                          :src="captchaImage"
                          :alt="t('login.refreshCaptcha')"
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
                      {{ t('login.login') }}
                    </NButton>
                  </NSpace>
                </NForm>
              </NTabPane>
            </NTabs>
          </NSpin>
        </NCard>
      </div>
    </div>
  </div>
</template>
