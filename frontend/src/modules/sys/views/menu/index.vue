<script setup lang="ts">
import { ref, onMounted, h } from 'vue'
import {
  NCard, NButton, NSpace, NModal, NForm, NFormItem,
  NInput, NSelect, NSwitch, NTag, NTree, NEmpty, NIcon,
  useMessage, type TreeOption, type FormInst, type FormRules,
  type SelectOption,
} from 'naive-ui'
import {
  SettingsOutline, PersonOutline, ShieldCheckmarkOutline,
  MenuOutline, BusinessOutline, BuildOutline,
  GlobeOutline, DocumentTextOutline, AppsOutline,
} from '@vicons/ionicons5'
import { menuApi, type MenuDTO } from '@/modules/sys/api/menu'
import type { MenuTreeNode } from '@/modules/sys/api/types'
import { useAuthStore } from '@/stores/auth'
import { requiredRule, maxLengthRule, patternRule } from '@/shared/utils/validation'

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
const formRef = ref<FormInst | null>(null)
const rules: FormRules = {
  menuName: [
    requiredRule('菜单名称不能为空'),
    maxLengthRule(50, '菜单名称长度不能超过50'),
  ],
  sort: [patternRule(/^\d*$/, '排序值必须是非负整数')],
}

const iconOptions: { label: string; value: string; icon: any }[] = [
  { label: '设置', value: 'Settings', icon: SettingsOutline },
  { label: '用户', value: 'User', icon: PersonOutline },
  { label: '角色权限', value: 'UserFilled', icon: ShieldCheckmarkOutline },
  { label: '菜单', value: 'Menu', icon: MenuOutline },
  { label: '办公楼', value: 'OfficeBuilding', icon: BusinessOutline },
  { label: '工具', value: 'Tools', icon: BuildOutline },
  { label: '地球', value: 'Globe', icon: GlobeOutline },
  { label: '文档', value: 'Document', icon: DocumentTextOutline },
  { label: '应用', value: 'Apps', icon: AppsOutline },
]

function renderIconLabel(option: SelectOption) {
  const opt = option as { label: string; icon: any }
  return h(NSpace, { align: 'center', size: 'small', wrap: false }, {
    default: () => [
      h(NIcon, { size: 18 }, { default: () => h(opt.icon) }),
      opt.label,
    ],
  })
}

async function fetchData() {
  loading.value = true
  try {
    const res = await menuApi.tree()
    tree.value = res.data
    expandedKeys.value = tree.value.map(n => n.id)
  } finally {
    loading.value = false
  }
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
    await formRef.value?.validate()
  } catch {
    return
  }
  try {
    if (editingId.value) {
      await menuApi.update(editingId.value, form.value)
      message.success('修改成功')
    }
    showModal.value = false
    fetchData()
  } catch (e: any) {
    message.error(e.response?.data?.message || '操作失败')
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
      authStore.hasPermission('sys:menu:edit') && h(NButton, {
        size: 'tiny', text: true, type: 'primary',
        onClick: () => handleEdit(node),
      }, { default: () => '编辑' }),
    ]),
  ])
}

onMounted(fetchData)
</script>

<template>
  <NCard>
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

  <NModal v-model:show="showModal" title="编辑菜单" preset="card" :style="{ width: '500px' }">
    <NForm ref="formRef" :model="form" :rules="rules" label-placement="left" :label-width="80">
      <NFormItem label="菜单名称" required path="menuName">
        <NInput v-model:value="form.menuName" placeholder="菜单名称" />
      </NFormItem>
      <NFormItem label="图标" path="icon">
        <NSelect
          v-model:value="form.icon"
          :options="iconOptions"
          :render-label="renderIconLabel"
          clearable
          placeholder="选择图标"
        />
      </NFormItem>
      <NFormItem label="排序" path="sort">
        <NInput :value="String(form.sort ?? '')" placeholder="0" @update:value="(v: string) => form.sort = v ? Number(v) : undefined" />
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
