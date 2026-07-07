<script setup lang="ts">
import { ref, onMounted, h } from 'vue'
import {
  NCard, NButton, NSpace, NModal, NForm, NFormItem,
  NInput, NSelect, NTag, useMessage, NDataTable,
  type DataTableColumns, type FormInst, type FormRules,
} from 'naive-ui'
import { configApi, type ConfigDTO } from '@/modules/sys/api/config'
import type { SysConfig } from '@/modules/sys/api/types'
import {
  requiredRule, maxLengthRule, patternRule, CONFIG_KEY_PATTERN,
} from '@/shared/utils/validation'

const message = useMessage()
const loading = ref(false)
const data = ref<SysConfig[]>([])

const showModal = ref(false)
const editingId = ref<number | null>(null)
const form = ref<ConfigDTO>({
  configKey: '', configType: 'STRING', category: 'default',
})
const formRef = ref<FormInst | null>(null)
const rules: FormRules = {
  configKey: [
    requiredRule('配置键不能为空'),
    maxLengthRule(100, '配置键长度不能超过100'),
    patternRule(CONFIG_KEY_PATTERN, '配置键只能包含字母、数字、点、下划线、连字符'),
  ],
  configValue: [maxLengthRule(2000, '配置值长度不能超过2000')],
  description: [maxLengthRule(500, '描述长度不能超过500')],
  category: [maxLengthRule(50, '分类长度不能超过50')],
}

const configTypes = [
  { label: '字符串', value: 'STRING' },
  { label: '数字', value: 'NUMBER' },
  { label: '布尔', value: 'BOOLEAN' },
  { label: 'JSON', value: 'JSON' },
]

async function fetchData() {
  loading.value = true
  try {
    const res = await configApi.list()
    data.value = res.data
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  editingId.value = null
  form.value = { configKey: '', configType: 'STRING', category: 'default' }
  showModal.value = true
}

function handleEdit(row: SysConfig) {
  editingId.value = row.id
  form.value = {
    id: row.id,
    configKey: row.configKey,
    configValue: row.configValue || undefined,
    configType: row.configType,
    description: row.description || undefined,
    category: row.category,
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
      await configApi.update(editingId.value, form.value)
      message.success('修改成功')
    } else {
      await configApi.create(form.value)
      message.success('新增成功')
    }
    showModal.value = false
    fetchData()
  } catch (e: any) {
    message.error(e.response?.data?.message || '操作失败')
  }
}

// 配置暂不支持删除

const columns: DataTableColumns<SysConfig> = [
  { title: '配置键', key: 'configKey', width: 200 },
  { title: '配置值', key: 'configValue', width: 200 },
  {
    title: '类型', key: 'configType', width: 100,
    render: (row) => h(NTag, { size: 'small', type: 'info' }, { default: () => row.configType }),
  },
  { title: '分类', key: 'category', width: 100 },
  { title: '描述', key: 'description', width: 200 },
  {
    title: '操作', key: 'actions', width: 100,
    render: (row) => h(NButton, {
      size: 'small', onClick: () => handleEdit(row),
    }, { default: () => '编辑' }),
  },
]

onMounted(fetchData)
</script>

<template>
  <NCard>
    <NSpace class="mb-4" justify="end">
      <NButton v-permission="'sys:config:add'" type="primary" @click="handleAdd">新增配置</NButton>
    </NSpace>

    <NDataTable :columns="columns" :data="data" :loading="loading" :scroll-x="900" />
  </NCard>

  <NModal v-model:show="showModal" :title="editingId ? '编辑配置' : '新增配置'" preset="card" :style="{ width: '500px' }">
    <NForm ref="formRef" :model="form" :rules="rules" label-placement="left" :label-width="80">
      <NFormItem label="配置键" required path="configKey">
        <NInput v-model:value="form.configKey" :disabled="!!editingId" placeholder="sys.password.min-length" />
      </NFormItem>
      <NFormItem label="配置值" path="configValue">
        <NInput v-model:value="form.configValue" type="textarea" placeholder="配置值" />
      </NFormItem>
      <NFormItem label="类型">
        <NSelect v-model:value="form.configType" :options="configTypes" />
      </NFormItem>
      <NFormItem label="分类" path="category">
        <NInput v-model:value="form.category" placeholder="default" />
      </NFormItem>
      <NFormItem label="描述" path="description">
        <NInput v-model:value="form.description" placeholder="配置描述" />
      </NFormItem>
      <NSpace justify="end">
        <NButton @click="showModal = false">取消</NButton>
        <NButton type="primary" @click="handleSave">保存</NButton>
      </NSpace>
    </NForm>
  </NModal>
</template>
