<script setup lang="ts">
import { ref, onMounted, h } from 'vue'
import {
  NCard, NButton, NSpace, NModal, NForm, NFormItem,
  NInput, NSelect, NSwitch, NTag, NTree, NEmpty,
  useMessage, type TreeOption, type FormInst, type FormRules,
} from 'naive-ui'
import { unitApi, type UnitDTO } from '@/modules/sys/api/unit'
import type { UnitTreeNode } from '@/modules/sys/api/types'
import { useAuthStore } from '@/stores/auth'
import {
  requiredRule, lengthRule, maxLengthRule, patternRule, USERNAME_PATTERN,
} from '@/shared/utils/validation'

const authStore = useAuthStore()
const message = useMessage()
const loading = ref(false)
const tree = ref<UnitTreeNode[]>([])
const expandedKeys = ref<number[]>([])

const showModal = ref(false)
const editingId = ref<number | null>(null)
const form = ref<UnitDTO>({ unitCode: '', unitName: '', sort: 0, status: 1 })
const formRef = ref<FormInst | null>(null)
const rules: FormRules = {
  unitCode: [
    requiredRule('单位编码不能为空'),
    lengthRule(3, 50, '单位编码长度需在3-50之间'),
    patternRule(USERNAME_PATTERN, '单位编码只能包含字母、数字、下划线'),
  ],
  unitName: [
    requiredRule('单位名称不能为空'),
    maxLengthRule(100, '单位名称长度不能超过100'),
  ],
  sort: [patternRule(/^\d*$/, '排序值必须是非负整数')],
  remark: [maxLengthRule(200, '备注长度不能超过200')],
}

async function fetchData() {
  loading.value = true
  try {
    const res = await unitApi.tree()
    tree.value = res.data
    // 默认展开第一层
    expandedKeys.value = tree.value.map(n => n.id)
  } finally {
    loading.value = false
  }
}

function flattenUnits(units: UnitTreeNode[], prefix = ''): { label: string, value: number }[] {
  return units.flatMap(u => [
    { label: prefix + u.unitName, value: u.id },
    ...(u.children?.length ? flattenUnits(u.children, prefix + '  ') : []),
  ])
}

function handleAdd(parentId?: number) {
  editingId.value = null
  form.value = { parentId, unitCode: '', unitName: '', sort: 0, status: 1 }
  showModal.value = true
}

function handleEdit(row: UnitTreeNode) {
  editingId.value = row.id
  form.value = {
    parentId: row.parentId || undefined,
    unitCode: row.unitCode,
    unitName: row.unitName,
    sort: row.sort,
    status: row.status,
    remark: row.remark || undefined,
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
      await unitApi.update(editingId.value, form.value)
      message.success('修改成功')
    } else {
      await unitApi.create(form.value)
      message.success('新增成功')
    }
    showModal.value = false
    fetchData()
  } catch (e: any) {
    message.error(e.response?.data?.message || '操作失败')
  }
}

async function handleDelete(row: UnitTreeNode) {
  try {
    await unitApi.delete(row.id)
    message.success('删除成功')
    fetchData()
  } catch (e: any) {
    message.error(e.response?.data?.message || '删除失败')
  }
}

function handleExpand(keys: Array<string | number>) {
  expandedKeys.value = keys as number[]
}

function renderLabel({ option }: { option: TreeOption }) {
  const node = option as unknown as UnitTreeNode
  return h('div', {
    class: 'flex items-center justify-between gap-2 w-full pr-2',
  }, [
    h('div', { class: 'flex items-center gap-2 min-w-0' }, [
      h(NTag, { size: 'small' }, { default: () => node.unitCode }),
      h('span', {
        class: node.status === 0 ? 'line-through opacity-50' : '',
      }, node.unitName),
    ]),
    h('div', {
      class: 'flex items-center gap-2 shrink-0',
      onClick: (e: Event) => e.stopPropagation(),
    }, [
      authStore.hasPermission('sys:unit:add') && h(NButton, {
        size: 'tiny', text: true, type: 'primary',
        onClick: () => handleAdd(node.id),
      }, { default: () => '新增' }),
      authStore.hasPermission('sys:unit:edit') && h(NButton, {
        size: 'tiny', text: true, type: 'primary',
        onClick: () => handleEdit(node),
      }, { default: () => '编辑' }),
      authStore.hasPermission('sys:unit:delete') && h(NButton, {
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
      <NButton v-permission="'sys:unit:add'" type="primary" @click="handleAdd()">新增单位</NButton>
    </NSpace>

    <NTree
      v-if="tree.length"
      :data="tree"
      :expanded-keys="expandedKeys"
      :render-label="renderLabel"
      key-field="id"
      label-field="unitName"
      children-field="children"
      block-line
      expand-on-click
      @update:expanded-keys="handleExpand"
    />
    <NEmpty v-else-if="!loading" description="暂无单位数据" />
  </NCard>

  <NModal v-model:show="showModal" :title="editingId ? '编辑单位' : '新增单位'" preset="card" :style="{ width: '500px' }">
    <NForm ref="formRef" :model="form" :rules="rules" label-placement="left" :label-width="80">
      <NFormItem label="上级单位">
        <NSelect v-model:value="form.parentId" :options="flattenUnits(tree)" placeholder="顶级单位" clearable />
      </NFormItem>
      <NFormItem label="单位编码" required path="unitCode">
        <NInput v-model:value="form.unitCode" :disabled="!!editingId" placeholder="如 HQ" />
      </NFormItem>
      <NFormItem label="单位名称" required path="unitName">
        <NInput v-model:value="form.unitName" placeholder="如 总部" />
      </NFormItem>
      <NFormItem label="排序" path="sort">
        <NInput :value="String(form.sort ?? '')" placeholder="0" @update:value="(v: string) => form.sort = v ? Number(v) : undefined" />
      </NFormItem>
      <NFormItem label="状态">
        <NSwitch v-model:value="form.status" :checked-value="1" :unchecked-value="0" />
      </NFormItem>
      <NFormItem label="备注" path="remark">
        <NInput v-model:value="form.remark" type="textarea" />
      </NFormItem>
      <NSpace justify="end">
        <NButton @click="showModal = false">取消</NButton>
        <NButton type="primary" @click="handleSave">保存</NButton>
      </NSpace>
    </NForm>
  </NModal>
</template>
