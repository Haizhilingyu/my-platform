<script setup lang="ts">
import { ref, onMounted, h } from 'vue'
import {
  NCard, NButton, NSpace, NModal, NForm, NFormItem,
  NInput, NSelect, NSwitch, NTag, NTree, NEmpty,
  useMessage, type TreeOption,
} from 'naive-ui'
import { menuApi, type MenuDTO } from '@/modules/sys/api/menu'
import type { MenuTreeNode } from '@/modules/sys/api/types'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const message = useMessage()

const loading = ref(false)
const tree = ref<MenuTreeNode[]>([])
const expandedKeys = ref<number[]>([])

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
    // 默认展开第一层
    expandedKeys.value = tree.value.map(n => n.id)
  } finally {
    loading.value = false
  }
}

function flattenMenus(menus: MenuTreeNode[], prefix = ''): { label: string, value: number }[] {
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

function handleExpand(keys: Array<string | number>) {
  expandedKeys.value = keys as number[]
}

function typeLabel(type: string) {
  return { DIRECTORY: '目录', PAGE: '页面', BUTTON: '按钮' }[type] || type
}

function typeColor(type: string) {
  return ({ DIRECTORY: 'info', PAGE: 'success', BUTTON: 'warning' } as const)[type as 'DIRECTORY' | 'PAGE' | 'BUTTON'] || 'default'
}

function renderLabel({ option }: { option: TreeOption }) {
  const node = option as unknown as MenuTreeNode
  return h('div', {
    class: 'flex items-center justify-between gap-2 w-full pr-2',
  }, [
    h('div', { class: 'flex items-center gap-2 min-w-0' }, [
      h(NTag, { size: 'small', type: typeColor(node.menuType) }, { default: () => typeLabel(node.menuType) }),
      h('span', {
        class: node.status === 0 ? 'line-through opacity-50' : '',
      }, node.menuName),
      node.permission && h('span', { class: 'text-xs opacity-50' }, node.permission),
    ]),
    h('div', {
      class: 'flex items-center gap-2 shrink-0',
      onClick: (e: Event) => e.stopPropagation(),
    }, [
      authStore.hasPermission('sys:menu:add') && h(NButton, {
        size: 'tiny', text: true, type: 'primary',
        onClick: () => handleAdd(node.id),
      }, { default: () => '新增' }),
      authStore.hasPermission('sys:menu:edit') && h(NButton, {
        size: 'tiny', text: true, type: 'primary',
        onClick: () => handleEdit(node),
      }, { default: () => '编辑' }),
      authStore.hasPermission('sys:menu:delete') && h(NButton, {
        size: 'tiny', text: true, type: 'error',
        onClick: () => handleDelete(node),
      }, { default: () => '删除' }),
    ]),
  ])
}

onMounted(fetchData)
</script>

<template>
  <NCard>
    <NSpace class="mb-4" justify="end">
      <NButton v-permission="'sys:menu:add'" type="primary" @click="handleAdd()">新增菜单</NButton>
    </NSpace>

    <NTree
      v-if="tree.length"
      :data="tree"
      :expanded-keys="expandedKeys"
      :render-label="renderLabel"
      key-field="id"
      label-field="menuName"
      children-field="children"
      block-line
      expand-on-click
      @update:expanded-keys="handleExpand"
    />
    <NEmpty v-else-if="!loading" description="暂无菜单数据" />
  </NCard>

  <NModal v-model:show="showModal" :title="editingId ? '编辑菜单' : '新增菜单'" preset="card" :style="{ width: '550px' }">
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
