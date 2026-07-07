<script setup lang="ts">
import { ref, computed, onMounted, h } from 'vue'
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
import { useBreakpoint } from '@/shared/composables/useBreakpoint'
import {
  requiredRule, maxLengthRule,
} from '@/shared/utils/validation'

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
    requiredRule('应用名称不能为空'),
    maxLengthRule(100, '应用名称长度不能超过100'),
  ],
  redirectUris: [
    {
      validator: (_rule: unknown, value: string[]) => {
        if (!value || value.length === 0) return new Error('至少需要一个重定向URI')
        const nonEmpty = value.filter((v) => v && v.trim())
        if (nonEmpty.length === 0) return new Error('至少需要一个重定向URI')
        return true
      },
      trigger: ['blur', 'change'],
    },
  ],
  scopes: [
    {
      validator: (_rule: unknown, value: string[]) => {
        if (!value || value.length === 0) return new Error('请至少选择一个权限范围')
        return true
      },
      trigger: ['blur', 'change'],
    },
  ],
  grantTypes: [
    {
      validator: (_rule: unknown, value: string[]) => {
        if (!value || value.length === 0) return new Error('请至少选择一个授权类型')
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
  { label: 'authorization_code（授权码）', value: 'authorization_code' },
  { label: 'refresh_token（刷新令牌）', value: 'refresh_token' },
  { label: 'client_credentials（客户端凭据）', value: 'client_credentials' },
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
    message.error(e.response?.data?.message || '查询失败')
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
    // 校验失败：Naive UI 已在字段下方渲染错误提示，直接中断保存。
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
      message.success('修改成功')
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
    message.error(e.response?.data?.message || '操作失败')
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
    message.success(value ? '已启用' : '已禁用')
  } catch (e: any) {
    message.error(e.response?.data?.message || '操作失败')
  }
}

async function handleResetSecret(row: OpenAppClientVO) {
  try {
    const res = await openAppApi.resetSecret(row.id)
    secretResult.value = res.data
    showSecretModal.value = true
  } catch (e: any) {
    message.error(e.response?.data?.message || '重置密钥失败')
  }
}

async function handleDelete(row: OpenAppClientVO) {
  try {
    await openAppApi.delete(row.id)
    message.success('删除成功')
    fetchData()
  } catch (e: any) {
    message.error(e.response?.data?.message || '删除失败')
  }
}

async function copyText(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    message.success('已复制到剪贴板')
  } catch {
    message.error('复制失败，请手动选择复制')
  }
}

function formatTime(ts: string | null): string {
  if (!ts) return '-'
  return ts.replace('T', ' ').slice(0, 19)
}

const columns = computed<DataTableColumns<OpenAppClientVO>>(() => [
  { title: 'Client ID', key: 'clientId', width: 220, ellipsis: { tooltip: true } },
  { title: '应用名称', key: 'clientName', width: 150 },
  {
    title: '授权范围',
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
    title: '状态',
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
            { default: () => (row.enabled ? '启用' : '禁用') },
          ),
  },
  { title: '创建时间', key: 'createdAt', width: 160, render: (row) => formatTime(row.createdAt) },
  {
    title: '操作',
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
              { default: () => '编辑' },
            ),
          authStore.hasPermission('sys:openapp:edit') &&
            h(
              NButton,
              { size: 'small', quaternary: true, onClick: () => handleResetSecret(row) },
              { default: () => '重置密钥' },
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
              { default: () => '删除' },
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
          placeholder="搜索 Client ID / 应用名称"
          clearable
          class="w-[250px]"
          @clear="fetchData"
          @keyup.enter="fetchData"
        />
        <NButton type="primary" @click="fetchData">查询</NButton>
      </NSpace>
      <NButton v-permission="'sys:openapp:add'" type="primary" @click="handleAdd">
        新增应用
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
    :title="editingId ? '编辑应用' : '新增应用'"
    preset="card"
    :style="modalStyle"
  >
    <NForm ref="formRef" :model="form" :rules="rules" :label-placement="labelPlacement" :label-width="110">
      <NFormItem label="应用名称" required path="clientName">
        <NInput v-model:value="form.clientName" placeholder="如：移动端 App" />
      </NFormItem>

      <NFormItem label="回调地址" path="redirectUris">
        <div class="w-full space-y-2">
          <div
            v-for="(_, idx) in form.redirectUris"
            :key="`ru-${idx}`"
            class="flex gap-2"
          >
            <NInput
              v-model:value="form.redirectUris[idx]"
              placeholder="https://example.com/callback"
            />
            <NButton quaternary type="error" @click="removeRedirectUri(idx)">
              <template #icon><NIcon :component="TrashOutline" /></template>
            </NButton>
          </div>
          <NButton dashed block @click="addRedirectUri">
            <template #icon><NIcon :component="AddOutline" /></template>
            添加回调地址
          </NButton>
        </div>
      </NFormItem>

      <NFormItem label="登出回调">
        <div class="w-full space-y-2">
          <div
            v-for="(_, idx) in form.postLogoutRedirectUris"
            :key="`pl-${idx}`"
            class="flex gap-2"
          >
            <NInput
              v-model:value="form.postLogoutRedirectUris[idx]"
              placeholder="https://example.com/post-logout"
            />
            <NButton quaternary type="error" @click="removePostLogoutUri(idx)">
              <template #icon><NIcon :component="TrashOutline" /></template>
            </NButton>
          </div>
          <NButton dashed block @click="addPostLogoutUri">
            <template #icon><NIcon :component="AddOutline" /></template>
            添加登出回调
          </NButton>
        </div>
      </NFormItem>

      <NFormItem label="授权范围" path="scopes">
        <NSelect
          v-model:value="form.scopes"
          multiple
          tag
          filterable
          :options="scopeOptions"
          placeholder="选择或输入 scope"
        />
      </NFormItem>

      <NFormItem label="授权类型" path="grantTypes">
        <NSelect
          v-model:value="form.grantTypes"
          multiple
          :options="grantTypeOptions"
          placeholder="选择授权类型"
        />
      </NFormItem>

      <NFormItem v-if="editingId" label="启用状态">
        <NSwitch v-model:value="form.enabled" />
      </NFormItem>

      <NSpace justify="end">
        <NButton @click="showModal = false">取消</NButton>
        <NButton type="primary" :loading="saving" @click="handleSave">保存</NButton>
      </NSpace>
    </NForm>
  </NModal>

  <NModal
    v-model:show="showSecretModal"
    title="Client Secret（仅显示一次）"
    preset="card"
    :style="modalStyle"
    :mask-closable="false"
    :close-on-esc="false"
  >
    <NAlert type="warning" class="mb-4" :show-icon="true">
      此密钥仅显示一次，关闭后将无法再次查看。请立即复制并妥善保存。
    </NAlert>

    <NSpace vertical :size="'large' as any">
      <div class="w-full">
        <div class="mb-1 text-sm opacity-70">Client ID</div>
        <NInput
          v-if="secretResult"
          :value="secretResult.clientId"
          readonly
        />
      </div>
      <div class="w-full">
        <div class="mb-1 text-sm opacity-70">Client Secret</div>
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
            复制
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
          我已保存
        </NButton>
      </NSpace>
    </template>
  </NModal>
</template>
