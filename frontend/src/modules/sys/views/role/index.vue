<script setup lang="ts">
import { ref, computed, onMounted, h } from 'vue'
import {
  NCard, NDataTable, NButton, NSpace, NModal, NForm, NFormItem,
  NInput, NSelect, NTag, useMessage, NTree, type DataTableColumns,
  type FormInst, type FormRules,
} from 'naive-ui'
import { roleApi, type RoleDTO } from '@/modules/sys/api/role'
import { menuApi } from '@/modules/sys/api/menu'
import { unitApi } from '@/modules/sys/api/unit'
import type { SysRole, MenuTreeNode, UnitTreeNode } from '@/modules/sys/api/types'
import { useBreakpoint } from '@/shared/composables/useBreakpoint'
import { useI18n } from 'vue-i18n'
import {
  requiredRule, lengthRule, maxLengthRule, patternRule, USERNAME_PATTERN,
} from '@/shared/utils/validation'

const { t } = useI18n()
const message = useMessage()
const { isMobile } = useBreakpoint()
const labelPlacement = computed(() => (isMobile.value ? 'top' : 'left'))

const loading = ref(false)
const data = ref<SysRole[]>([])

const showModal = ref(false)
const editingId = ref<number | null>(null)
const form = ref<RoleDTO>({ roleCode: '', roleName: '', dataScope: 'SELF', status: 1 })
const formRef = ref<FormInst | null>(null)
const rules: FormRules = {
  roleCode: [
    requiredRule(t('sys.role.roleCodeRequired')),
    lengthRule(3, 50, t('sys.role.roleCodeLength')),
    patternRule(USERNAME_PATTERN, t('sys.role.roleCodePattern')),
  ],
  roleName: [
    requiredRule(t('sys.role.roleNameRequired')),
    maxLengthRule(100, t('sys.role.roleNameLength')),
  ],
  dataScope: [requiredRule(t('sys.role.dataScopeRequired'))],
  remark: [maxLengthRule(200, t('sys.role.remarkLength'))],
}

const unitTree = ref<UnitTreeNode[]>([])
const customUnitIds = ref<number[]>([])
const isCustomScope = computed(() => form.value.dataScope === 'CUSTOM')

const showPermModal = ref(false)
const permRoleId = ref<number | null>(null)
const menuTree = ref<MenuTreeNode[]>([])
const checkedKeys = ref<string[]>([])

const dataScopes = computed(() => [
  { label: t('sys.role.scopeAll'), value: 'ALL' },
  { label: t('sys.role.scopeUnit'), value: 'UNIT' },
  { label: t('sys.role.scopeUnitBelow'), value: 'UNIT_BELOW' },
  { label: t('sys.role.scopeSelf'), value: 'SELF' },
  { label: t('sys.role.scopeCustom'), value: 'CUSTOM' },
])

async function fetchData() {
  loading.value = true
  try {
    const res = await roleApi.list()
    data.value = res.data
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  editingId.value = null
  form.value = { roleCode: '', roleName: '', dataScope: 'SELF', status: 1 }
  customUnitIds.value = []
  showModal.value = true
}

async function handleEdit(row: SysRole) {
  editingId.value = row.id
  form.value = {
    roleCode: row.roleCode,
    roleName: row.roleName,
    dataScope: row.dataScope,
    status: row.status,
    remark: row.remark || undefined,
  }
  customUnitIds.value = []
  if (row.dataScope === 'CUSTOM') {
    try {
      const res = await roleApi.getCustomUnits(row.id)
      customUnitIds.value = res.data
    } catch {
      // 后端暂未暴露自定义数据范围查询端点（T24 known limitation）
    }
  }
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
      await roleApi.update(editingId.value, form.value)
      await saveCustomScope(editingId.value)
      message.success(t('common.modifySuccess'))
    } else {
      const res = await roleApi.create(form.value)
      await saveCustomScope(res.data)
      message.success(t('common.createSuccess'))
    }
    showModal.value = false
    fetchData()
  } catch (e: any) {
    message.error(e.response?.data?.message || t('common.operationFailed'))
  }
}

async function saveCustomScope(roleId: number) {
  if (!isCustomScope.value) return
  try {
    await roleApi.saveCustomUnits(roleId, customUnitIds.value)
  } catch {
    message.warning(t('sys.role.customScopeFailed'))
  }
}

async function handleDelete(row: SysRole) {
  try {
    await roleApi.delete(row.id)
    message.success(t('common.deleteSuccess'))
    fetchData()
  } catch (e: any) {
    message.error(e.response?.data?.message || t('common.deleteFailed'))
  }
}

async function handlePermission(row: SysRole) {
  permRoleId.value = row.id
  const [menuRes, roleMenuRes] = await Promise.all([
    menuApi.tree(),
    roleApi.getRoleMenus(row.id),
  ])
  menuTree.value = menuRes.data
  checkedKeys.value = roleMenuRes.data.map(String)
  showPermModal.value = true
}

async function handleSavePermission() {
  if (!permRoleId.value) return
  const menuIds = checkedKeys.value.map(Number)
  await roleApi.assignMenus(permRoleId.value, menuIds)
  message.success(t('sys.role.assignPermissionsSuccess'))
  showPermModal.value = false
}

function flattenMenuTree(menus: MenuTreeNode[]): any[] {
  return menus.map(m => ({
    key: String(m.id),
    label: m.menuName,
    children: m.children?.length ? flattenMenuTree(m.children) : undefined,
  }))
}

function flattenUnitTree(units: UnitTreeNode[]): any[] {
  return units.map(u => ({
    key: u.id,
    label: u.unitName,
    children: u.children?.length ? flattenUnitTree(u.children) : undefined,
  }))
}

const dataScopeLabel = (scope: string): string =>
  dataScopes.value.find(d => d.value === scope)?.label || scope

const columns = computed<DataTableColumns<SysRole>>(() => [
  { title: t('sys.role.roleCode'), key: 'roleCode', width: 150 },
  { title: t('sys.role.roleName'), key: 'roleName', width: 150 },
  {
    title: t('sys.role.dataScope'), key: 'dataScope', width: 130,
    render: (row) => h(NTag, { size: 'small', type: 'info' },
      { default: () => dataScopeLabel(row.dataScope) }),
  },
  {
    title: t('common.status'), key: 'status', width: 80,
    render: (row) => h(NTag, { type: row.status === 1 ? 'success' : 'error', size: 'small' },
      { default: () => row.status === 1 ? t('sys.role.enabled') : t('sys.user.disabled') }),
  },
  { title: t('common.remark'), key: 'remark', width: 200 },
  {
    title: t('common.operation'), key: 'actions', width: 250,
    render: (row) => h(NSpace, {}, {
      default: () => [
        h(NButton, { size: 'small', onClick: () => handlePermission(row) }, { default: () => t('sys.role.permissions') }),
        h(NButton, { size: 'small', onClick: () => handleEdit(row) }, { default: () => t('common.edit') }),
        h(NButton, { size: 'small', type: 'error', onClick: () => handleDelete(row) }, { default: () => t('common.delete') }),
      ],
    }),
  },
])

onMounted(async () => {
  fetchData()
  try {
    const unitRes = await unitApi.tree()
    unitTree.value = unitRes.data
  } catch {
    // 单位树加载失败不阻断角色管理主流程
  }
})
</script>

<template>
  <NCard>
    <NSpace class="mb-4" justify="end">
      <NButton v-permission="'sys:role:add'" type="primary" @click="handleAdd">{{ t('sys.role.addRole') }}</NButton>
    </NSpace>

    <NDataTable :columns="columns" :data="data" :loading="loading" :scroll-x="950" />
  </NCard>

  <NModal v-model:show="showModal" :title="editingId ? t('sys.role.editRole') : t('sys.role.addRole')" preset="card" :style="{ width: '500px' }">
    <NForm ref="formRef" :model="form" :rules="rules" :label-placement="labelPlacement" :label-width="80">
      <NFormItem :label="t('sys.role.roleCode')" required path="roleCode">
        <NInput v-model:value="form.roleCode" :disabled="!!editingId" :placeholder="t('sys.role.roleCodePlaceholder')" />
      </NFormItem>
      <NFormItem :label="t('sys.role.roleName')" required path="roleName">
        <NInput v-model:value="form.roleName" :placeholder="t('sys.role.roleNamePlaceholder')" />
      </NFormItem>
      <NFormItem :label="t('sys.role.dataScope')" path="dataScope">
        <NSelect v-model:value="form.dataScope" :options="dataScopes" />
      </NFormItem>
      <NFormItem v-if="isCustomScope" :label="t('sys.role.customUnit')">
        <NTree
          key-field="key"
          :data="flattenUnitTree(unitTree)"
          checkable
          cascade
          :checked-keys="customUnitIds"
          expand-on-click
          block-line
          @update:checked-keys="(keys: number[]) => customUnitIds = keys"
        />
      </NFormItem>
      <NFormItem :label="t('common.remark')" path="remark">
        <NInput v-model:value="form.remark" type="textarea" :placeholder="t('sys.role.remarkPlaceholder')" />
      </NFormItem>
      <NSpace justify="end">
        <NButton @click="showModal = false">{{ t('common.cancel') }}</NButton>
        <NButton type="primary" @click="handleSave">{{ t('common.save') }}</NButton>
      </NSpace>
    </NForm>
  </NModal>

  <NModal v-model:show="showPermModal" :title="t('sys.role.assignPermissions')" preset="card" :style="{ width: '500px' }">
    <NTree
      key-field="key"
      :data="flattenMenuTree(menuTree)"
      checkable
      :checked-keys="checkedKeys"
      cascade
      expand-on-click
      block-line
      @update:checked-keys="(keys: any) => checkedKeys = keys"
    />
    <NSpace justify="end" class="mt-4">
      <NButton @click="showPermModal = false">{{ t('common.cancel') }}</NButton>
      <NButton type="primary" @click="handleSavePermission">{{ t('sys.role.savePermissions') }}</NButton>
    </NSpace>
  </NModal>
</template>
