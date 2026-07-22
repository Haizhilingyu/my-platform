<script setup lang="ts">
import { ref, onMounted, watch, h, computed } from 'vue'
import { useRoute } from 'vue-router'
import {
  NCard, NDataTable, NButton, NSpace, NInput, NSelect, NModal, NForm,
  NFormItem, NSwitch, NTag, NPopconfirm, useMessage,
  type DataTableColumns, type FormInst, type FormRules,
} from 'naive-ui'
import { userApi, type UserQuery } from '@/modules/sys/api/user'
import { roleApi } from '@/modules/sys/api/role'
import { unitApi } from '@/modules/sys/api/unit'
import type { UserVO, SysRole, UnitTreeNode } from '@/modules/sys/api/types'
import { useAuthStore } from '@/stores/auth'
import { useI18n } from 'vue-i18n'
import {
  requiredRule, lengthRule, maxLengthRule, patternRule, emailRule,
  USERNAME_PATTERN, PHONE_PATTERN,
} from '@/shared/utils/validation'

const { t } = useI18n()
const authStore = useAuthStore()
const message = useMessage()

const loading = ref(false)
const data = ref<UserVO[]>([])
const total = ref(0)
const query = ref<UserQuery>({ pageNum: 1, pageSize: 10 })
const route = useRoute()
const highlightId = ref<number | null>(null)

const showModal = ref(false)
const editingId = ref<number | null>(null)
const roles = ref<SysRole[]>([])
const unitTree = ref<UnitTreeNode[]>([])

const form = ref({
  username: '',
  password: '',
  realName: '',
  email: '',
  phone: '',
  unitId: null as number | null,
  status: 1,
  roleIds: [] as number[],
})
const formRef = ref<FormInst | null>(null)
const rules = computed<FormRules>(() => {
  // 编辑态不展示用户名/密码字段，对应规则置空，避免误触发 required。
  const isEdit = !!editingId.value
  return {
    username: isEdit
      ? []
      : [
          requiredRule(t('sys.user.usernameRequired')),
          lengthRule(3, 32, t('sys.user.usernameLength')),
          patternRule(USERNAME_PATTERN, t('sys.user.usernamePattern')),
        ],
    password: isEdit
      ? []
      : [requiredRule(t('sys.user.passwordRequired')), lengthRule(6, 32, t('sys.user.passwordLength'))],
    realName: [maxLengthRule(50, t('sys.user.realNameLength'))],
    email: [emailRule()],
    phone: [
      patternRule(PHONE_PATTERN, t('sys.user.phonePattern')),
      maxLengthRule(20, t('sys.user.phoneLength')),
    ],
  }
})

async function fetchData() {
  loading.value = true
  try {
    const res = await userApi.list(query.value)
    data.value = res.data.list
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

async function fetchInit() {
  const [roleRes, unitRes] = await Promise.all([roleApi.list(), unitApi.tree()])
  roles.value = roleRes.data
  unitTree.value = unitRes.data
}

function handleAdd() {
  editingId.value = null
  form.value = {
    username: '', password: '', realName: '', email: '', phone: '',
    unitId: null, status: 1, roleIds: [],
  }
  showModal.value = true
}

async function handleEdit(row: UserVO) {
  editingId.value = row.id
  form.value = {
    username: row.username,
    password: '',
    realName: row.realName || '',
    email: row.email || '',
    phone: row.phone || '',
    unitId: row.unitId,
    status: row.status,
    roleIds: [],
  }
  const res = await userApi.getUserRoles(row.id)
  form.value.roleIds = res.data
  showModal.value = true
}

async function handleSave() {
  try {
    await formRef.value?.validate()
  } catch {
    // 校验失败：Naive UI 已在字段下方渲染错误提示，直接中断保存。
    return
  }
  try {
    if (editingId.value) {
      await userApi.update(editingId.value, {
        realName: form.value.realName || undefined,
        email: form.value.email || undefined,
        phone: form.value.phone || undefined,
        unitId: form.value.unitId || undefined,
        status: form.value.status,
      })
      if (form.value.roleIds.length > 0) {
        await userApi.assignRoles(editingId.value, form.value.roleIds)
      }
      message.success(t('common.modifySuccess'))
    } else {
      await userApi.create({
        username: form.value.username,
        password: form.value.password,
        realName: form.value.realName || undefined,
        email: form.value.email || undefined,
        phone: form.value.phone || undefined,
        unitId: form.value.unitId || undefined,
        roleIds: form.value.roleIds,
      })
      message.success(t('common.createSuccess'))
    }
    showModal.value = false
    fetchData()
  } catch (e: any) {
    message.error(e.response?.data?.message || t('common.operationFailed'))
  }
}

async function handleDelete(row: UserVO) {
  try {
    await userApi.delete(row.id)
    message.success(t('common.deleteSuccess'))
    fetchData()
  } catch (e: any) {
    message.error(e.response?.data?.message || t('common.deleteFailed'))
  }
}

async function handleUnlock(row: UserVO) {
  try {
    await userApi.unlock(row.id)
    message.success(t('sys.user.unlockSuccess'))
    fetchData()
  } catch (e: any) {
    message.error(e.response?.data?.message || t('sys.user.unlockFailed'))
  }
}

async function handleResetPassword(row: UserVO) {
  try {
    await userApi.resetPassword(row.id, 'User@123456')
    message.success(t('sys.user.passwordReset'))
    fetchData()
  } catch (e: any) {
    message.error(e.response?.data?.message || t('sys.user.resetFailed'))
  }
}

function flattenUnits(units: UnitTreeNode[]): any[] {
  return units.map(u => ({
    label: u.unitName,
    value: u.id,
    children: u.children?.length ? flattenUnits(u.children) : undefined,
  }))
}

const columns = computed<DataTableColumns<UserVO>>(() => [
  { title: t('sys.user.username'), key: 'username', width: 120 },
  { title: t('sys.user.realName'), key: 'realName', width: 100 },
  { title: t('sys.user.email'), key: 'email', width: 180 },
  { title: t('sys.user.phone'), key: 'phone', width: 130 },
  { title: t('sys.user.unit'), key: 'unitName', width: 120 },
  {
    title: t('common.status'), key: 'status', width: 90,
    render: (row) => {
      if (row.locked) {
        return h(NTag, { type: 'error', size: 'small' }, { default: () => t('sys.user.locked') })
      }
      return h(NTag, { type: row.status === 1 ? 'success' : 'warning', size: 'small' },
        { default: () => row.status === 1 ? t('sys.user.normal') : t('sys.user.disabled') })
    },
  },
  {
    title: t('common.operation'), key: 'actions', width: 340,
    render: (row) => h(NSpace, {}, {
      default: () => [
        authStore.hasPermission('sys:user:edit') && h(NButton, {
          size: 'small', onClick: () => handleEdit(row),
        }, { default: () => t('common.edit') }),
        row.locked && authStore.hasPermission('sys:user:unlock') && h(NButton, {
          size: 'small', type: 'warning', onClick: () => handleUnlock(row),
        }, { default: () => t('sys.user.unlock') }),
        authStore.hasPermission('sys:user:reset') && h(NPopconfirm, {
          onPositiveClick: () => handleResetPassword(row),
        }, {
          trigger: () => h(NButton, { size: 'small', type: 'info' }, { default: () => t('sys.user.resetPassword') }),
          default: () => t('sys.user.confirmResetPassword'),
        }),
        authStore.hasPermission('sys:user:delete') && h(NButton, {
          size: 'small', type: 'error', onClick: () => handleDelete(row),
        }, { default: () => t('common.delete') }),
      ],
    }),
  },
])

// AI 助手跳转带 ?highlight=<id>：高亮目标行并刷新列表
watch(
  () => route.query.highlight,
  (val) => {
    if (val != null && val !== '') {
      highlightId.value = Number(val)
      fetchData()
      setTimeout(() => {
        highlightId.value = null
      }, 4000)
    }
  },
  { immediate: true },
)

onMounted(() => {
  fetchInit()
  fetchData()
})
</script>

<template>
  <NCard>
    <NSpace class="mb-4" justify="space-between">
      <NSpace>
        <NInput
          v-model:value="query.keyword" :placeholder="t('sys.user.searchPlaceholder')" clearable
          class="w-[250px]" @clear="fetchData" @keyup.enter="fetchData"
        />
        <NButton type="primary" @click="fetchData">{{ t('common.search') }}</NButton>
      </NSpace>
      <NButton v-permission="'sys:user:add'" type="primary" @click="handleAdd">{{ t('sys.user.addUserTitle') }}</NButton>
    </NSpace>

    <NDataTable
      :columns="columns"
      :data="data"
      :loading="loading"
      :scroll-x="1100"
      :row-class-name="(row) => (row.id === highlightId ? 'ai-highlight-row' : '')"
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

  <NModal v-model:show="showModal" :title="editingId ? t('sys.user.editUserTitle') : t('sys.user.addUserTitle')" preset="card" :style="{ width: '500px' }">
    <NForm ref="formRef" :model="form" :rules="rules" label-placement="left" :label-width="80">
      <NFormItem v-if="!editingId" :label="t('sys.user.username')" required path="username">
        <NInput v-model:value="form.username" :placeholder="t('sys.user.usernamePlaceholder')" />
      </NFormItem>
      <NFormItem v-if="!editingId" :label="t('sys.user.password')" required path="password">
        <NInput v-model:value="form.password" type="password" :placeholder="t('sys.user.passwordPlaceholder')" />
      </NFormItem>
      <NFormItem :label="t('sys.user.realName')" path="realName">
        <NInput v-model:value="form.realName" :placeholder="t('sys.user.realNamePlaceholder')" />
      </NFormItem>
      <NFormItem :label="t('sys.user.email')" path="email">
        <NInput v-model:value="form.email" :placeholder="t('sys.user.emailPlaceholder')" />
      </NFormItem>
      <NFormItem :label="t('sys.user.phone')" path="phone">
        <NInput v-model:value="form.phone" :placeholder="t('sys.user.phonePlaceholder')" />
      </NFormItem>
      <NFormItem :label="t('sys.user.unit')">
        <NSelect
          v-model:value="form.unitId" :options="flattenUnits(unitTree)"
          :placeholder="t('sys.user.unitPlaceholder')" clearable
        />
      </NFormItem>
      <NFormItem :label="t('sys.user.role')">
        <NSelect
          v-model:value="form.roleIds" :multiple="true"
          :options="roles.map(r => ({ label: r.roleName, value: r.id }))"
          :placeholder="t('sys.user.rolePlaceholder')"
        />
      </NFormItem>
      <NFormItem v-if="editingId" :label="t('common.status')">
        <NSwitch v-model:value="form.status" :checked-value="1" :unchecked-value="0" />
      </NFormItem>
      <NSpace justify="end">
        <NButton @click="showModal = false">{{ t('common.cancel') }}</NButton>
        <NButton type="primary" @click="handleSave">{{ t('common.save') }}</NButton>
      </NSpace>
    </NForm>
  </NModal>
</template>

<style scoped>
:deep(.ai-highlight-row td) {
  background-color: rgba(var(--color-primary), 0.14) !important;
  transition: background-color 0.5s ease;
}
</style>
