<script setup lang="ts">
import { ref, onMounted, h, computed } from 'vue'
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
import { useI18n } from 'vue-i18n'
import { requiredRule, maxLengthRule, patternRule } from '@/shared/utils/validation'

const { t } = useI18n()
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
    requiredRule(t('sys.menu.menuNameRequired')),
    maxLengthRule(50, t('sys.menu.menuNameLength')),
  ],
  sort: [patternRule(/^\d*$/, t('sys.menu.sortPattern'))],
}

const iconOptions = computed(() => [
  { label: t('sys.menu.iconSettings'), value: 'Settings', icon: SettingsOutline },
  { label: t('sys.menu.iconUser'), value: 'User', icon: PersonOutline },
  { label: t('sys.menu.iconRole'), value: 'UserFilled', icon: ShieldCheckmarkOutline },
  { label: t('sys.menu.iconMenu'), value: 'Menu', icon: MenuOutline },
  { label: t('sys.menu.iconBuilding'), value: 'OfficeBuilding', icon: BusinessOutline },
  { label: t('sys.menu.iconTools'), value: 'Tools', icon: BuildOutline },
  { label: t('sys.menu.iconGlobe'), value: 'Globe', icon: GlobeOutline },
  { label: t('sys.menu.iconDocument'), value: 'Document', icon: DocumentTextOutline },
  { label: t('sys.menu.iconApps'), value: 'Apps', icon: AppsOutline },
])

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
      message.success(t('common.modifySuccess'))
    }
    showModal.value = false
    fetchData()
  } catch (e: any) {
    message.error(e.response?.data?.message || t('common.operationFailed'))
  }
}

function handleExpand(keys: Array<string | number>) {
  expandedKeys.value = keys as number[]
}

function typeLabel(type: string) {
  return { DIRECTORY: t('sys.menu.typeDirectory'), PAGE: t('sys.menu.typePage'), BUTTON: t('sys.menu.typeButton') }[type] || type
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
      }, { default: () => t('sys.menu.edit') }),
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
    <NEmpty v-else-if="!loading" :description="t('sys.menu.noMenuData')" />
  </NCard>

  <NModal v-model:show="showModal" :title="t('sys.menu.editMenu')" preset="card" :style="{ width: '500px' }">
    <NForm ref="formRef" :model="form" :rules="rules" label-placement="left" :label-width="80">
      <NFormItem :label="t('sys.menu.menuName')" required path="menuName">
        <NInput v-model:value="form.menuName" :placeholder="t('sys.menu.menuNamePlaceholder')" />
      </NFormItem>
      <NFormItem :label="t('sys.menu.icon')" path="icon">
        <NSelect
          v-model:value="form.icon"
          :options="iconOptions"
          :render-label="renderIconLabel"
          clearable
          :placeholder="t('sys.menu.iconPlaceholder')"
        />
      </NFormItem>
      <NFormItem :label="t('sys.menu.sort')" path="sort">
        <NInput :value="String(form.sort ?? '')" :placeholder="t('sys.menu.sortPlaceholder')" @update:value="(v: string) => form.sort = v ? Number(v) : undefined" />
      </NFormItem>
      <NFormItem v-if="form.menuType !== 'BUTTON'" :label="t('sys.menu.isShow')">
        <NSwitch v-model:value="form.visible" :checked-value="1" :unchecked-value="0" />
      </NFormItem>
      <NFormItem :label="t('sys.menu.status')">
        <NSwitch v-model:value="form.status" :checked-value="1" :unchecked-value="0" />
      </NFormItem>
      <NSpace justify="end">
        <NButton @click="showModal = false">{{ t('common.cancel') }}</NButton>
        <NButton type="primary" @click="handleSave">{{ t('common.save') }}</NButton>
      </NSpace>
    </NForm>
  </NModal>
</template>
