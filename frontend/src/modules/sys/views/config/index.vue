<script setup lang="ts">
import { ref, onMounted, h } from 'vue'
import { useI18n } from 'vue-i18n'
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

const { t } = useI18n()
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
    requiredRule(t('common.required')),
    maxLengthRule(100, t('common.maxLengthRule', { max: 100 })),
    patternRule(CONFIG_KEY_PATTERN, t('sys.config.configKeyInvalid')),
  ],
  configValue: [maxLengthRule(2000, t('sys.config.configValueMaxLength'))],
  description: [maxLengthRule(500, t('sys.config.descriptionMaxLength'))],
  category: [maxLengthRule(50, t('sys.config.categoryMaxLength'))],
}

const configTypes = [
  { label: t('sys.config.typeString'), value: 'STRING' },
  { label: t('sys.config.typeNumber'), value: 'NUMBER' },
  { label: t('sys.config.typeBoolean'), value: 'BOOLEAN' },
  { label: t('sys.config.typeJson'), value: 'JSON' },
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
      message.success(t('common.modifySuccess'))
    } else {
      await configApi.create(form.value)
      message.success(t('common.createSuccess'))
    }
    showModal.value = false
    fetchData()
  } catch (e: any) {
    message.error(e.response?.data?.message || t('common.operationFailed'))
  }
}

// 配置暂不支持删除

const columns: DataTableColumns<SysConfig> = [
  { title: t('sys.config.key'), key: 'configKey', width: 200 },
  { title: t('sys.config.value'), key: 'configValue', width: 200 },
  {
    title: t('sys.config.type'), key: 'configType', width: 100,
    render: (row) => h(NTag, { size: 'small', type: 'info' }, { default: () => row.configType }),
  },
  { title: t('sys.config.category'), key: 'category', width: 100 },
  { title: t('sys.config.description'), key: 'description', width: 200 },
  {
    title: t('common.operation'), key: 'actions', width: 100,
    render: (row) => h(NButton, {
      size: 'small', onClick: () => handleEdit(row),
    }, { default: () => t('common.edit') }),
  },
]

onMounted(fetchData)
</script>

<template>
  <NCard>
    <NSpace class="mb-4" justify="end">
      <NButton v-permission="'sys:config:add'" type="primary" @click="handleAdd">{{ t('sys.config.addConfig') }}</NButton>
    </NSpace>

    <NDataTable :columns="columns" :data="data" :loading="loading" :scroll-x="900" />
  </NCard>

  <NModal v-model:show="showModal" :title="editingId ? t('sys.config.editConfig') : t('sys.config.addConfig')" preset="card" :style="{ width: '500px' }">
    <NForm ref="formRef" :model="form" :rules="rules" label-placement="left" :label-width="80">
      <NFormItem :label="t('sys.config.configKey')" required path="configKey">
        <NInput v-model:value="form.configKey" :disabled="!!editingId" :placeholder="t('sys.config.configKeyPlaceholder')" />
      </NFormItem>
      <NFormItem :label="t('sys.config.configValue')" path="configValue">
        <NInput v-model:value="form.configValue" type="textarea" :placeholder="t('sys.config.configValuePlaceholder')" />
      </NFormItem>
      <NFormItem :label="t('sys.config.configType')">
        <NSelect v-model:value="form.configType" :options="configTypes" />
      </NFormItem>
      <NFormItem :label="t('sys.config.category')" path="category">
        <NInput v-model:value="form.category" :placeholder="t('sys.config.categoryPlaceholder')" />
      </NFormItem>
      <NFormItem :label="t('sys.config.description')" path="description">
        <NInput v-model:value="form.description" :placeholder="t('sys.config.descriptionPlaceholder')" />
      </NFormItem>
      <NSpace justify="end">
        <NButton @click="showModal = false">{{ t('common.cancel') }}</NButton>
        <NButton type="primary" @click="handleSave">{{ t('common.save') }}</NButton>
      </NSpace>
    </NForm>
  </NModal>
</template>
