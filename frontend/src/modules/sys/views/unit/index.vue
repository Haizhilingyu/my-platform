<script setup lang="ts">
import { ref, onMounted, h } from 'vue'
import {
  NCard, NButton, NSpace, NModal, NForm, NFormItem,
  NInput, NSwitch, NTag, useMessage,
} from 'naive-ui'
import { unitApi, type UnitDTO } from '@/modules/sys/api/unit'
import type { UnitTreeNode } from '@/modules/sys/api/types'

const message = useMessage()
const loading = ref(false)
const tree = ref<UnitTreeNode[]>([])

const showModal = ref(false)
const editingId = ref<number | null>(null)
const form = ref<UnitDTO>({ unitCode: '', unitName: '', sort: 0, status: 1 })

async function fetchData() {
  loading.value = true
  try {
    const res = await unitApi.tree()
    tree.value = res.data
  } finally {
    loading.value = false
  }
}

function flattenUnits(units: UnitTreeNode[], prefix = ''): any[] {
  return units.flatMap(u => [
    { label: prefix + u.unitName, value: u.id },
    ...(u.children?.length ? flattenUnits(u.children, prefix + '  ') : []),
  ])
}

function handleAdd(parentId?: number) {
  editingId.value = null
  form.value = { parentId: parentId, unitCode: '', unitName: '', sort: 0, status: 1 }
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

function renderTree(units: UnitTreeNode[]): any[] {
  return units.map(u => ({
    key: u.id,
    label: () => h('div', { class: 'flex items-center justify-between py-1' }, [
      h('div', { class: 'flex items-center gap-2' }, [
        h(NTag, { size: 'small' }, { default: () => u.unitCode }),
        h('span', { class: u.status === 0 ? 'line-through opacity-50' : '' }, u.unitName),
      ]),
      h(NSpace, { size: 'small' }, {
        default: () => [
          h(NButton, { size: 'tiny', text: true, type: 'primary',
            onClick: () => handleAdd(u.id) }, { default: () => '新增' }),
          h(NButton, { size: 'tiny', text: true, type: 'primary',
            onClick: () => handleEdit(u) }, { default: () => '编辑' }),
          h(NButton, { size: 'tiny', text: true, type: 'error',
            onClick: () => handleDelete(u) }, { default: () => '删除' }),
        ],
      }),
    ]),
    children: u.children?.length ? renderTree(u.children) : undefined,
  }))
}

onMounted(fetchData)
</script>

<template>
  <NCard>
    <NSpace class="mb-4" justify="end">
      <NButton v-permission="'sys:unit:add'" type="primary" @click="handleAdd()">新增单位</NButton>
    </NSpace>

    <div v-if="!loading">
      <template v-for="node in renderTree(tree)" :key="node.key">
        <div class="border-b border-[rgb(var(--color-border))] last:border-0 py-2">
          <div class="font-medium">{{ node.label() }}</div>
          <div v-if="node.children" class="pl-8">
            <div v-for="child in node.children" :key="child.key" class="border-b border-[rgb(var(--color-border))] last:border-0 py-2">
              {{ child.label() }}
            </div>
          </div>
        </div>
      </template>
    </div>
  </NCard>

  <NModal v-model:show="showModal" :title="editingId ? '编辑单位' : '新增单位'" preset="card" style="width: 500px">
    <NForm label-placement="left" :label-width="80">
      <NFormItem label="上级单位">
        <NSelect v-model:value="form.parentId" :options="flattenUnits(tree)" placeholder="顶级单位" clearable />
      </NFormItem>
      <NFormItem label="单位编码" required>
        <NInput v-model:value="form.unitCode" :disabled="!!editingId" placeholder="如 HQ" />
      </NFormItem>
      <NFormItem label="单位名称" required>
        <NInput v-model:value="form.unitName" placeholder="如 总部" />
      </NFormItem>
      <NFormItem label="排序">
        <NInput :value="String(form.sort ?? '')" @update:value="(v: string) => form.sort = v ? Number(v) : undefined" placeholder="0" />
      </NFormItem>
      <NFormItem label="状态">
        <NSwitch v-model:value="form.status" :checked-value="1" :unchecked-value="0" />
      </NFormItem>
      <NFormItem label="备注">
        <NInput v-model:value="form.remark" type="textarea" />
      </NFormItem>
      <NSpace justify="end">
        <NButton @click="showModal = false">取消</NButton>
        <NButton type="primary" @click="handleSave">保存</NButton>
      </NSpace>
    </NForm>
  </NModal>
</template>
