<script setup lang="ts">
import { ref, onMounted, h } from 'vue'
import {
  NCard, NButton, NSpace, NModal, NForm, NFormItem,
  NInput, NSelect, NSwitch, NTag, useMessage,
} from 'naive-ui'
import { menuApi, type MenuDTO } from '@/modules/sys/api/menu'
import type { MenuTreeNode } from '@/modules/sys/api/types'

const message = useMessage()

const loading = ref(false)
const tree = ref<MenuTreeNode[]>([])

const showModal = ref(false)
const editingId = ref<number | null>(null)
const form = ref<MenuDTO>({
  menuName: '', menuType: 'PAGE', sort: 0, visible: 1, status: 1,
})

const menuTypes = [
  { label: '目录', value: 'DIRECTORY' },
  { label: '页面', value: 'PAGE' },
  { label: '按钮', value: 'BUTTON' },
]

async function fetchData() {
  loading.value = true
  try {
    const res = await menuApi.tree()
    tree.value = res.data
  } finally {
    loading.value = false
  }
}

function flattenMenus(menus: MenuTreeNode[], prefix = ''): any[] {
  return menus.flatMap(m => [
    { label: prefix + m.menuName, value: m.id },
    ...(m.children?.length ? flattenMenus(m.children, prefix + '  ') : []),
  ])
}

function handleAdd(parentId?: number) {
  editingId.value = null
  form.value = {
    parentId: parentId || undefined,
    menuName: '', menuType: 'PAGE', sort: 0, visible: 1, status: 1,
  }
  showModal.value = true
}

function handleEdit(row: MenuTreeNode) {
  editingId.value = row.id
  form.value = {
    parentId: row.parentId || undefined,
    menuName: row.menuName,
    menuType: row.menuType,
    path: row.path || undefined,
    component: row.component || undefined,
    permission: row.permission || undefined,
    icon: row.icon || undefined,
    sort: row.sort,
    visible: row.visible,
    status: row.status,
  }
  showModal.value = true
}

async function handleSave() {
  try {
    if (editingId.value) {
      await menuApi.update(editingId.value, form.value)
      message.success('修改成功')
    } else {
      await menuApi.create(form.value)
      message.success('新增成功')
    }
    showModal.value = false
    fetchData()
  } catch (e: any) {
    message.error(e.response?.data?.message || '操作失败')
  }
}

async function handleDelete(row: MenuTreeNode) {
  try {
    await menuApi.delete(row.id)
    message.success('删除成功')
    fetchData()
  } catch (e: any) {
    message.error(e.response?.data?.message || '删除失败')
  }
}

function typeLabel(type: string) {
  return { DIRECTORY: '目录', PAGE: '页面', BUTTON: '按钮' }[type] || type
}

function typeColor(type: string) {
  return { DIRECTORY: 'info', PAGE: 'success', BUTTON: 'warning' }[type] as any || 'default'
}

function renderTree(menus: MenuTreeNode[]): any[] {
  return menus.map(m => ({
    key: m.id,
    label: () => h('div', { class: 'flex items-center justify-between py-1' }, [
      h('div', { class: 'flex items-center gap-2' }, [
        h(NTag, { size: 'small', type: typeColor(m.menuType) }, { default: () => typeLabel(m.menuType) }),
        h('span', { class: m.status === 0 ? 'line-through opacity-50' : '' }, m.menuName),
        m.permission && h('span', { class: 'text-xs opacity-50' }, m.permission),
      ]),
      h(NSpace, { size: 'small' }, {
        default: () => [
          h(NButton, { size: 'tiny', text: true, type: 'primary',
            onClick: () => handleAdd(m.id) }, { default: () => '新增' }),
          h(NButton, { size: 'tiny', text: true, type: 'primary',
            onClick: () => handleEdit(m) }, { default: () => '编辑' }),
          h(NButton, { size: 'tiny', text: true, type: 'error',
            onClick: () => handleDelete(m) }, { default: () => '删除' }),
        ],
      }),
    ]),
    children: m.children?.length ? renderTree(m.children) : undefined,
  }))
}

onMounted(fetchData)
</script>

<template>
  <NCard>
    <NSpace class="mb-4" justify="end">
      <NButton v-permission="'sys:menu:add'" type="primary" @click="handleAdd()">新增菜单</NButton>
    </NSpace>

    <div v-if="!loading">
      <template v-for="node in renderTree(tree)" :key="node.key">
        <div class="border-b border-[rgb(var(--color-border))] last:border-0">
          <div class="py-2 font-medium">{{ node.label() }}</div>
          <div v-if="node.children" class="pl-8">
            <div v-for="child in node.children" :key="child.key" class="border-b border-[rgb(var(--color-border))] last:border-0">
              <div class="py-2">{{ child.label() }}</div>
            </div>
          </div>
        </div>
      </template>
    </div>
  </NCard>

  <NModal v-model:show="showModal" :title="editingId ? '编辑菜单' : '新增菜单'" preset="card" style="width: 550px">
    <NForm label-placement="left" :label-width="90">
      <NFormItem label="上级菜单">
        <NSelect v-model:value="form.parentId" :options="flattenMenus(tree)" placeholder="顶级菜单" clearable />
      </NFormItem>
      <NFormItem label="菜单类型">
        <NSelect v-model:value="form.menuType" :options="menuTypes" />
      </NFormItem>
      <NFormItem label="菜单名称" required>
        <NInput v-model:value="form.menuName" placeholder="菜单名称" />
      </NFormItem>
      <NFormItem v-if="form.menuType !== 'BUTTON'" label="路由路径">
        <NInput v-model:value="form.path" placeholder="/sys/user" />
      </NFormItem>
      <NFormItem v-if="form.menuType === 'PAGE'" label="组件路径">
        <NInput v-model:value="form.component" placeholder="sys/user/index" />
      </NFormItem>
      <NFormItem label="权限标识">
        <NInput v-model:value="form.permission" placeholder="sys:user:add" />
      </NFormItem>
      <NFormItem label="图标">
        <NInput v-model:value="form.icon" placeholder="Settings" />
      </NFormItem>
      <NFormItem label="排序">
        <NInput :value="String(form.sort ?? '')" @update:value="(v: string) => form.sort = v ? Number(v) : undefined" placeholder="0" />
      </NFormItem>
      <NFormItem v-if="form.menuType !== 'BUTTON'" label="是否显示">
        <NSwitch v-model:value="form.visible" :checked-value="1" :unchecked-value="0" />
      </NFormItem>
      <NFormItem label="状态">
        <NSwitch v-model:value="form.status" :checked-value="1" :unchecked-value="0" />
      </NFormItem>
      <NSpace justify="end">
        <NButton @click="showModal = false">取消</NButton>
        <NButton type="primary" @click="handleSave">保存</NButton>
      </NSpace>
    </NForm>
  </NModal>
</template>
