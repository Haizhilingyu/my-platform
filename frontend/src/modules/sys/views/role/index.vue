<script setup lang="ts">
import { ref, onMounted, h } from 'vue'
import {
  NCard, NDataTable, NButton, NSpace, NModal, NForm, NFormItem,
  NInput, NSelect, NTag, useMessage, NTree, type DataTableColumns,
} from 'naive-ui'
import { roleApi, type RoleDTO } from '@/modules/sys/api/role'
import { menuApi } from '@/modules/sys/api/menu'
import type { SysRole, MenuTreeNode } from '@/modules/sys/api/types'

const message = useMessage()

const loading = ref(false)
const data = ref<SysRole[]>([])

const showModal = ref(false)
const editingId = ref<number | null>(null)
const form = ref<RoleDTO>({ roleCode: '', roleName: '', dataScope: 'SELF', status: 1 })

const showPermModal = ref(false)
const permRoleId = ref<number | null>(null)
const menuTree = ref<MenuTreeNode[]>([])
const checkedKeys = ref<string[]>([])

const dataScopes = [
  { label: '全部数据', value: 'ALL' },
  { label: '本单位', value: 'UNIT' },
  { label: '本单位及下属', value: 'UNIT_BELOW' },
  { label: '仅本人', value: 'SELF' },
]

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
  showModal.value = true
}

function handleEdit(row: SysRole) {
  editingId.value = row.id
  form.value = {
    roleCode: row.roleCode,
    roleName: row.roleName,
    dataScope: row.dataScope,
    status: row.status,
    remark: row.remark || undefined,
  }
  showModal.value = true
}

async function handleSave() {
  try {
    if (editingId.value) {
      await roleApi.update(editingId.value, form.value)
      message.success('修改成功')
    } else {
      await roleApi.create(form.value)
      message.success('新增成功')
    }
    showModal.value = false
    fetchData()
  } catch (e: any) {
    message.error(e.response?.data?.message || '操作失败')
  }
}

async function handleDelete(row: SysRole) {
  try {
    await roleApi.delete(row.id)
    message.success('删除成功')
    fetchData()
  } catch (e: any) {
    message.error(e.response?.data?.message || '删除失败')
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
  message.success('权限分配成功')
  showPermModal.value = false
}

function flattenMenuTree(menus: MenuTreeNode[]): any[] {
  return menus.map(m => ({
    key: String(m.id),
    label: m.menuName,
    children: m.children?.length ? flattenMenuTree(m.children) : undefined,
  }))
}

const columns: DataTableColumns<SysRole> = [
  { title: '角色编码', key: 'roleCode', width: 150 },
  { title: '角色名称', key: 'roleName', width: 150 },
  { title: '数据范围', key: 'dataScope', width: 120 },
  {
    title: '状态', key: 'status', width: 80,
    render: (row) => h(NTag, { type: row.status === 1 ? 'success' : 'error', size: 'small' },
      { default: () => row.status === 1 ? '启用' : '禁用' }),
  },
  { title: '备注', key: 'remark', width: 200 },
  {
    title: '操作', key: 'actions', width: 250,
    render: (row) => h(NSpace, {}, {
      default: () => [
        h(NButton, { size: 'small', onClick: () => handlePermission(row) }, { default: () => '权限' }),
        h(NButton, { size: 'small', onClick: () => handleEdit(row) }, { default: () => '编辑' }),
        h(NButton, { size: 'small', type: 'error', onClick: () => handleDelete(row) }, { default: () => '删除' }),
      ],
    }),
  },
]

onMounted(fetchData)
</script>

<template>
  <NCard>
    <NSpace class="mb-4" justify="end">
      <NButton v-permission="'sys:role:add'" type="primary" @click="handleAdd">新增角色</NButton>
    </NSpace>

    <NDataTable :columns="columns" :data="data" :loading="loading" :scroll-x="950" />
  </NCard>

  <NModal v-model:show="showModal" :title="editingId ? '编辑角色' : '新增角色'" preset="card" :style="{ width: '500px' }">
    <NForm label-placement="left" :label-width="80">
      <NFormItem label="角色编码" required>
        <NInput v-model:value="form.roleCode" :disabled="!!editingId" placeholder="如 admin" />
      </NFormItem>
      <NFormItem label="角色名称" required>
        <NInput v-model:value="form.roleName" placeholder="如 超级管理员" />
      </NFormItem>
      <NFormItem label="数据范围">
        <NSelect v-model:value="form.dataScope" :options="dataScopes" />
      </NFormItem>
      <NFormItem label="备注">
        <NInput v-model:value="form.remark" type="textarea" placeholder="备注" />
      </NFormItem>
      <NSpace justify="end">
        <NButton @click="showModal = false">取消</NButton>
        <NButton type="primary" @click="handleSave">保存</NButton>
      </NSpace>
    </NForm>
  </NModal>

  <NModal v-model:show="showPermModal" title="分配权限" preset="card" :style="{ width: '500px' }">
    <NTree
      key-field="key"
      :data="flattenMenuTree(menuTree)"
      checkable
      :checked-keys="checkedKeys"
      @update:checked-keys="(keys: any) => checkedKeys = keys"
      cascade
      expand-on-click
      block-line
    />
    <NSpace justify="end" class="mt-4">
      <NButton @click="showPermModal = false">取消</NButton>
      <NButton type="primary" @click="handleSavePermission">保存</NButton>
    </NSpace>
  </NModal>
</template>
