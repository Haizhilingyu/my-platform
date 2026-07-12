<script setup lang="ts">
import { ref, computed, onMounted, h } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  NCard, NDataTable, NButton, NSpace, NInput, NSelect, NModal, NForm,
  NFormItem, NSwitch, NTag, NAlert, NIcon, useMessage, type DataTableColumns,
  type FormInst, type FormRules,
} from 'naive-ui'
import { CopyOutline, TrashOutline, AddOutline } from '@vicons/ionicons5'
import {
  openAppApi,
  type OpenAppClientVO,
  type OpenAppClientQuery,
  type OpenAppSecretResult,
} from '@/shared/api/openapp'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime } from '@/shared/utils/datetime'
import { useBreakpoint } from '@/shared/composables/useBreakpoint'
import {
  requiredRule, maxLengthRule,
} from '@/shared/utils/validation'

const { t } = useI18n()
const authStore = useAuthStore()
const message = useMessage()
const { isMobile } = useBreakpoint()

const loading = ref(false)
const data = ref<OpenAppClientVO[]>([])
const total = ref(0)
const query = ref<OpenAppClientQuery>({ pageNum: 1, pageSize: 10 })

const showModal = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)

const form = ref({
  clientName: '',
  redirectUris: [] as string[],
  postLogoutRedirectUris: [] as string[],
  scopes: [] as string[],
  grantTypes: [] as string[],
  enabled: true,
})
const formRef = ref<FormInst | null>(null)
const rules: FormRules = {
  clientName: [
    requiredRule(t('sys.app.validation.appNameRequired')),
    maxLengthRule(100, t('sys.app.validation.appNameMaxLength')),
  ],
  redirectUris: [
    {
      validator: (_rule: unknown, value: string[]) => {
        if (!value || value.length === 0) return new Error(t('sys.app.validation.atLeastOneRedirectUri'))
        const nonEmpty = value.filter((v) => v && v.trim())
        if (nonEmpty.length === 0) return new Error(t('sys.app.validation.atLeastOneRedirectUri'))
        return true
      },
      trigger: ['blur', 'change'],
    },
  ],
  scopes: [
    {
      validator: (_rule: unknown, value: string[]) => {
        if (!value || value.length === 0) return new Error(t('sys.app.validation.atLeastOneScope'))
        return true
      },
      trigger: ['blur', 'change'],
    },
  ],
  grantTypes: [
    {
      validator: (_rule: unknown, value: string[]) => {
        if (!value || value.length === 0) return new Error(t('sys.app.validation.atLeastOneGrantType'))
        return true
      },
      trigger: ['blur', 'change'],
    },
  ],
}

const showSecretModal = ref(false)
const secretResult = ref<OpenAppSecretResult | null>(null)

const scopeOptions = [
  'notify:publish',
  'sys:user:read',
  'sys:user:write',
  'openid',
  'profile',
  'email',
].map((s) => ({ label: s, value: s }))

const grantTypeOptions = [
  { label: t('sys.app.grantTypes.authCode'), value: 'authorization_code' },
  { label: t('sys.app.grantTypes.refreshToken'), value: 'refresh_token' },
  { label: t('sys.app.grantTypes.clientCredentials'), value: 'client_credentials' },
]

const modalStyle = computed(() => ({
  width: isMobile.value ? 'calc(100vw - 24px)' : '640px',
  maxWidth: '640px',
}))
const labelPlacement = computed(() => (isMobile.value ? 'top' : 'left'))

async function fetchData() {
  loading.value = true
  try {
    const res = await openAppApi.list(query.value)
    data.value = res.data.list
    total.value = res.data.total
  } catch (e: any) {
    message.error(e.response?.data?.message || t('sys.app.toast.queryFailed'))
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.value = {
    clientName: '',
    redirectUris: [],
    postLogoutRedirectUris: [],
    scopes: [],
    grantTypes: [],
    enabled: true,
  }
}

function handleAdd() {
  editingId.value = null
  resetForm()
  showModal.value = true
}

async function handleEdit(row: OpenAppClientVO) {
  editingId.value = row.id
  form.value = {
    clientName: row.clientName || '',
    redirectUris: [...(row.redirectUris || [])],
    postLogoutRedirectUris: [...(row.postLogoutRedirectUris || [])],
    scopes: [...(row.scopes || [])],
    grantTypes: [...(row.grantTypes || [])],
    enabled: row.enabled,
  }
  showModal.value = true
}

function addRedirectUri() {
  form.value.redirectUris.push('')
}
function removeRedirectUri(idx: number) {
  form.value.redirectUris.splice(idx, 1)
}
function addPostLogoutUri() {
  form.value.postLogoutRedirectUris.push('')
}
function removePostLogoutUri(idx: number) {
  form.value.postLogoutRedirectUris.splice(idx, 1)
}

function compact(arr: string[]): string[] {
  return arr.map((s) => s.trim()).filter((s) => s.length > 0)
}

async function handleSave() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    const redirectUris = compact(form.value.redirectUris)
    const postLogoutRedirectUris = compact(form.value.postLogoutRedirectUris)
    const scopes = compact(form.value.scopes)
    const grantTypes = compact(form.value.grantTypes)

    if (editingId.value) {
      await openAppApi.update(editingId.value, {
        clientName: form.value.clientName.trim(),
        redirectUris,
        postLogoutRedirectUris,
        scopes,
        grantTypes,
        enabled: form.value.enabled,
      })
      message.success(t('sys.app.toast.modifySuccess'))
      showModal.value = false
      fetchData()
    } else {
      const res = await openAppApi.create({
        clientName: form.value.clientName.trim(),
        redirectUris,
        postLogoutRedirectUris,
        scopes,
        grantTypes,
      })
      showModal.value = false
      secretResult.value = res.data
      showSecretModal.value = true
      fetchData()
    }
  } catch (e: any) {
    message.error(e.response?.data?.message || t('sys.app.toast.operationFailed'))
  } finally {
    saving.value = false
  }
}

async function handleToggleEnabled(row: OpenAppClientVO, value: boolean) {
  try {
    await openAppApi.update(row.id, {
      clientName: row.clientName || '',
      redirectUris: row.redirectUris || [],
      postLogoutRedirectUris: row.postLogoutRedirectUris || [],
      scopes: row.scopes || [],
      grantTypes: row.grantTypes || [],
      enabled: value,
    })
    row.enabled = value
    message.success(value ? t('sys.app.toast.enabled') : t('sys.app.toast.disabled'))
  } catch (e: any) {
    message.error(e.response?.data?.message || t('sys.app.toast.operationFailed'))
  }
}

async function handleResetSecret(row: OpenAppClientVO) {
  try {
    const res = await openAppApi.resetSecret(row.id)
    secretResult.value = res.data
    showSecretModal.value = true
  } catch (e: any) {
    message.error(e.response?.data?.message || t('sys.app.toast.resetSecretFailed'))
  }
}

async function handleDelete(row: OpenAppClientVO) {
  try {
    await openAppApi.delete(row.id)
    message.success(t('sys.app.toast.deleteSuccess'))
    fetchData()
  } catch (e: any) {
    message.error(e.response?.data?.message || t('sys.app.toast.deleteFailed'))
  }
}

async function copyText(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    message.success(t('sys.app.toast.copied'))
  } catch {
    message.error(t('sys.app.toast.copyFailed'))
  }
}

const columns = computed<DataTableColumns<OpenAppClientVO>>(() => [
  { title: t('sys.app.clientId'), key: 'clientId', width: 220, ellipsis: { tooltip: true } },
  { title: t('sys.app.appName'), key: 'clientName', width: 150 },
  {
    title: t('sys.app.grantType'),
    key: 'scopes',
    width: 200,
    render: (row) =>
      h(NSpace, { size: 'small', wrap: true }, {
        default: () =>
          (row.scopes || []).map((s) =>
            h(NTag, { size: 'small', type: 'info' }, { default: () => s }),
          ),
      }),
  },
  {
    title: t('sys.app.status'),
    key: 'enabled',
    width: 90,
    render: (row) =>
      authStore.hasPermission('sys:openapp:edit')
        ? h(NSwitch, {
            value: row.enabled,
            size: 'small',
            onUpdateValue: (v: boolean) => handleToggleEnabled(row, v),
          })
        : h(
            NTag,
            { type: row.enabled ? 'success' : 'error', size: 'small' },
            { default: () => (row.enabled ? t('sys.app.enabled') : t('sys.app.disabled')) },
          ),
  },
  { title: t('sys.app.createTime'), key: 'createdAt', width: 160, render: (row) => formatDateTime(row.createdAt) },
  {
    title: t('common.operation'),
    key: 'actions',
    width: 220,
    fixed: 'right' as const,
    render: (row) =>
      h(NSpace, { size: 'small' }, {
        default: () => [
          authStore.hasPermission('sys:openapp:edit') &&
            h(
              NButton,
              { size: 'small', onClick: () => handleEdit(row) },
              { default: () => t('common.edit') },
            ),
          authStore.hasPermission('sys:openapp:edit') &&
            h(
              NButton,
              { size: 'small', quaternary: true, onClick: () => handleResetSecret(row) },
              { default: () => t('sys.app.buttons.resetSecret') },
            ),
          authStore.hasPermission('sys:openapp:delete') &&
            h(
              NButton,
              {
                size: 'small',
                type: 'error',
                quaternary: true,
                onClick: () => handleDelete(row),
              },
              { default: () => t('common.delete') },
            ),
        ],
      }),
  },
])

onMounted(fetchData)
</script>

<template>
  <NCard>
    <NSpace class="mb-4" justify="space-between" :wrap="!isMobile" :vertical="isMobile">
      <NSpace :vertical="isMobile">
        <NInput
          v-model:value="query.keyword"
          :placeholder="t('sys.app.placeholders.search')"
          clearable
          class="w-[250px]"
          @clear="fetchData"
          @keyup.enter="fetchData"
        />
        <NButton type="primary" @click="fetchData">{{ t('sys.app.buttons.search') }}</NButton>
      </NSpace>
      <NButton v-permission="'sys:openapp:add'" type="primary" @click="handleAdd">
        {{ t('sys.app.buttons.addApp') }}
      </NButton>
    </NSpace>

    <NDataTable
      :columns="columns"
      :data="data"
      :loading="loading"
      :scroll-x="1040"
      :pagination="{
        page: query.pageNum,
        pageSize: query.pageSize,
        itemCount: total,
        showSizePicker: true,
        pageSizes: [10, 20, 50],
        onChange: (p: number) => { query.pageNum = p; fetchData() },
        onUpdatePageSize: (s: number) => { query.pageSize = s; query.pageNum = 1; fetchData() },
      }"
      remote
    />
  </NCard>

  <NModal
    v-model:show="showModal"
    :title="editingId ? t('sys.app.buttons.editApp') : t('sys.app.buttons.addApp')"
    preset="card"
    :style="modalStyle"
  >
    <NForm ref="formRef" :model="form" :rules="rules" :label-placement="labelPlacement" :label-width="110">
      <NFormItem :label="t('sys.app.appName')" required path="clientName">
        <NInput v-model:value="form.clientName" :placeholder="t('sys.app.placeholders.clientName')" />
      </NFormItem>

      <NFormItem :label="t('sys.app.buttons.addRedirectUri')" path="redirectUris">
        <div class="w-full space-y-2">
          <div
            v-for="(_, idx) in form.redirectUris"
            :key="`ru-${idx}`"
            class="flex gap-2"
          >
            <NInput
              v-model:value="form.redirectUris[idx]"
              :placeholder="t('sys.app.placeholders.redirectUri')"
            />
            <NButton quaternary type="error" @click="removeRedirectUri(idx)">
              <template #icon><NIcon :component="TrashOutline" /></template>
            </NButton>
          </div>
          <NButton dashed block @click="addRedirectUri">
            <template #icon><NIcon :component="AddOutline" /></template>
            {{ t('sys.app.buttons.addRedirectUri') }}
          </NButton>
        </div>
      </NFormItem>

      <NFormItem :label="t('sys.app.buttons.addPostLogoutUri')">
        <div class="w-full space-y-2">
          <div
            v-for="(_, idx) in form.postLogoutRedirectUris"
            :key="`pl-${idx}`"
            class="flex gap-2"
          >
            <NInput
              v-model:value="form.postLogoutRedirectUris[idx]"
              :placeholder="t('sys.app.placeholders.postLogoutRedirectUri')"
            />
            <NButton quaternary type="error" @click="removePostLogoutUri(idx)">
              <template #icon><NIcon :component="TrashOutline" /></template>
            </NButton>
          </div>
          <NButton dashed block @click="addPostLogoutUri">
            <template #icon><NIcon :component="AddOutline" /></template>
            {{ t('sys.app.buttons.addPostLogoutUri') }}
          </NButton>
        </div>
      </NFormItem>

      <NFormItem :label="t('sys.app.grantType')" path="scopes">
        <NSelect
          v-model:value="form.scopes"
          multiple
          tag
          filterable
          :options="scopeOptions"
          :placeholder="t('sys.app.placeholders.scopes')"
        />
      </NFormItem>

      <NFormItem :label="t('sys.app.grantType')" path="grantTypes">
        <NSelect
          v-model:value="form.grantTypes"
          multiple
          :options="grantTypeOptions"
          :placeholder="t('sys.app.placeholders.grantTypes')"
        />
      </NFormItem>

      <NFormItem v-if="editingId" :label="t('sys.app.status')">
        <NSwitch v-model:value="form.enabled" />
      </NFormItem>

      <NSpace justify="end">
        <NButton @click="showModal = false">{{ t('common.cancel') }}</NButton>
        <NButton type="primary" :loading="saving" @click="handleSave">{{ t('common.save') }}</NButton>
      </NSpace>
    </NForm>
  </NModal>

  <NModal
    v-model:show="showSecretModal"
    :title="t('sys.app.secretModal.title')"
    preset="card"
    :style="modalStyle"
    :mask-closable="false"
    :close-on-esc="false"
  >
    <NAlert type="warning" class="mb-4" :show-icon="true">
      {{ t('sys.app.secretModal.warning') }}
    </NAlert>

    <NSpace vertical :size="'large'" as any>
      <div class="w-full">
        <div class="mb-1 text-sm opacity-70">{{ t('sys.app.secretModal.clientIdLabel') }}</div>
        <NInput
          v-if="secretResult"
          :value="secretResult.clientId"
          readonly
        />
      </div>
      <div class="w-full">
        <div class="mb-1 text-sm opacity-70">{{ t('sys.app.secretModal.clientSecretLabel') }}</div>
        <div class="flex gap-2">
          <NInput
            v-if="secretResult"
            :value="secretResult.clientSecret"
            readonly
            type="password"
            show-password-on="click"
          />
          <NButton
            v-if="secretResult"
            type="primary"
            @click="copyText(secretResult.clientSecret)"
          >
            <template #icon><NIcon :component="CopyOutline" /></template>
            {{ t('sys.app.buttons.copy') }}
          </NButton>
        </div>
      </div>
    </NSpace>

    <template #footer>
      <NSpace justify="end">
        <NButton
          type="primary"
          @click="
            () => {
              showSecretModal = false
              secretResult = null
            }
          "
        >
          {{ t('sys.app.buttons.saved') }}
        </NButton>
      </NSpace>
    </template>
  </NModal>
</template>
