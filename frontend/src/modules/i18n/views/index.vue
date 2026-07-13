<script setup lang="ts">
import { ref, onMounted, h, computed, watch } from 'vue'
import {
  NCard, NDataTable, NButton, NSpace, NInput, NSelect, NModal, NForm,
  NFormItem, NUpload, NTooltip, useMessage,
  type DataTableColumns, type FormInst, type FormRules, type UploadFileInfo,
} from 'naive-ui'
import { i18nApi, type I18nMessageVO, type I18nMessageQuery, type AppLocale } from '../api/i18n'
import { useI18n } from 'vue-i18n'
import { requiredRule, maxLengthRule } from '@/shared/utils/validation'
import { debounce } from 'lodash-es'

const { t, locale } = useI18n()
const message = useMessage()

const loading = ref(false)
const data = ref<I18nMessageVO[]>([])
const total = ref(0)

const localeOptions = computed(() => [
  { label: '中文', value: 'zh-CN' as AppLocale },
  { label: 'English', value: 'en' as AppLocale },
])

const moduleOptions = computed(() => {
  const modules = new Set(data.value.map(item => item.module))
  return Array.from(modules).map(m => ({ label: m, value: m }))
})

const query = ref<I18nMessageQuery>({
  locale: (locale.value as AppLocale) || 'zh-CN',
  pageNum: 1,
  pageSize: 10,
})

// 编辑 Modal
const showEditModal = ref(false)
const editingId = ref<number | null>(null)
const editForm = ref({ value: '' })
const editFormRef = ref<FormInst | null>(null)
const editRules = computed<FormRules>(() => ({
  value: [
    requiredRule(t('i18n.editModal.valueRequired')),
    maxLengthRule(5000, t('i18n.editModal.valueMaxLength')),
  ],
}))

// 导入 Modal
const showImportModal = ref(false)
const importLocale = ref<AppLocale>('zh-CN')
const importFile = ref<UploadFileInfo | null>(null)

async function fetchData() {
  loading.value = true
  try {
    const res = await i18nApi.list(query.value)
    data.value = res.data.list
    total.value = res.data.total
  } catch (e: unknown) {
    const error = e as { response?: { data?: { message?: string } } }
    message.error(error.response?.data?.message || t('i18n.message.loadFailed'))
  } finally {
    loading.value = false
  }
}

const handleKeyLikeSearch = debounce(() => {
  query.value.pageNum = 1
  fetchData()
}, 300)

watch(() => [query.value.locale, query.value.module], () => {
  query.value.pageNum = 1
  fetchData()
})

function handleEdit(row: I18nMessageVO) {
  editingId.value = row.id
  editForm.value = { value: row.value }
  showEditModal.value = true
}

async function handleSaveEdit() {
  try {
    await editFormRef.value?.validate()
  } catch {
    return
  }
  try {
    await i18nApi.update(editingId.value!, { value: editForm.value.value })
    message.success(t('i18n.message.updateSuccess'))
    showEditModal.value = false
    fetchData()
  } catch (e: unknown) {
    const error = e as { response?: { data?: { message?: string } } }
    message.error(error.response?.data?.message || t('i18n.message.updateFailed'))
  }
}

function handleImport() {
  importFile.value = null
  showImportModal.value = true
}

async function handleImportSubmit() {
  if (!importFile.value?.file) {
    message.warning(t('i18n.importModal.fileRequired'))
    return
  }

  try {
    const isJson = importFile.value.file.name.endsWith('.json')
    if (isJson) {
      // JSON 导入需要读取文件内容并解析
      const text = await importFile.value.file.text()
      const json = JSON.parse(text)
      const items: { messageKey: string; value: string }[] = Object.entries(json).map(([k, v]) => ({
        messageKey: k,
        value: String(v),
      }))
      const res = await i18nApi.importJson({ locale: importLocale.value, items })
      message.success(t('i18n.message.importSuccess', { count: res.data }))
    } else {
      const res = await i18nApi.importXlsx(importFile.value.file, importLocale.value)
      message.success(t('i18n.message.importSuccess', { count: res.data }))
    }
    showImportModal.value = false
    fetchData()
  } catch (e: unknown) {
    const error = e as { response?: { data?: { message?: string } } }
    message.error(error.response?.data?.message || t('i18n.message.importFailed'))
  }
}

async function handleExport(format: 'json' | 'xlsx') {
  try {
    const blob = await (format === 'json'
      ? i18nApi.exportJson(query.value.locale || 'zh-CN')
      : i18nApi.exportXlsx(query.value.locale || 'zh-CN'))
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `i18n_${query.value.locale || 'zh-CN'}.${format}`
    a.click()
    URL.revokeObjectURL(url)
    message.success(t('i18n.message.exportSuccess'))
  } catch (e: unknown) {
    const error = e as { response?: { data?: { message?: string } } }
    message.error(error.response?.data?.message || t('i18n.message.exportFailed'))
  }
}

const columns = computed<DataTableColumns<I18nMessageVO>>(() => [
  {
    title: t('i18n.column.key'),
    key: 'messageKey',
    width: 250,
    ellipsis: { tooltip: true },
  },
  {
    title: t('i18n.column.module'),
    key: 'module',
    width: 120,
  },
  {
    title: t('i18n.column.description'),
    key: 'description',
    width: 200,
    ellipsis: { tooltip: true },
  },
  {
    title: t('i18n.column.value'),
    key: 'value',
    ellipsis: { tooltip: true },
    render: (row) => h(NTooltip, {}, {
      trigger: () => row.value,
      default: () => row.value,
    }),
  },
  {
    title: t('i18n.column.updatedAt'),
    key: 'updatedAt',
    width: 180,
    render: (row) => new Date(row.updatedAt).toLocaleString(),
  },
  {
    title: t('i18n.column.actions'),
    key: 'actions',
    width: 100,
    render: (row) => h(NButton, {
      size: 'small',
      onClick: () => handleEdit(row),
    }, { default: () => t('i18n.action.edit') }),
  },
])

onMounted(() => {
  fetchData()
})
</script>

<template>
  <NCard :title="t('i18n.title')">
    <NSpace class="mb-4" justify="space-between">
      <NSpace>
        <NSelect
          v-model:value="query.locale"
          :options="localeOptions"
          class="w-[120px]"
          :placeholder="t('i18n.filter.locale')"
        />
        <NSelect
          v-model:value="query.module"
          :options="moduleOptions"
          class="w-[150px]"
          :placeholder="t('i18n.filter.module')"
          clearable
        />
        <NInput
          v-model:value="query.keyLike"
          :placeholder="t('i18n.filter.placeholder')"
          class="w-[250px]"
          clearable
          @input="handleKeyLikeSearch"
        />
        <NButton @click="fetchData">{{ t('i18n.action.refresh') }}</NButton>
      </NSpace>
      <NSpace>
        <NButton v-permission="'sys:i18n:import'" type="default" @click="handleImport">
          {{ t('i18n.action.import') }}
        </NButton>
        <NButton v-permission="'sys:i18n:export'" type="default" @click="handleExport('json')">
          {{ t('i18n.action.exportJson') }}
        </NButton>
        <NButton v-permission="'sys:i18n:export'" type="default" @click="handleExport('xlsx')">
          {{ t('i18n.action.exportXlsx') }}
        </NButton>
      </NSpace>
    </NSpace>

    <NDataTable
      :columns="columns"
      :data="data"
      :loading="loading"
      :scroll-x="1000"
      :pagination="{
        page: query.pageNum,
        pageSize: query.pageSize,
        itemCount: total,
        showSizePicker: true,
        pageSizes: [10, 20, 50],
        onChange: (p: number) => { query.pageNum = p; fetchData() },
        onUpdatePageSize: (s: number) => { query.pageSize = s; query.pageNum = 1; fetchData() },
      }"
      remote
    />
  </NCard>

  <!-- 编辑 Modal -->
  <NModal v-model:show="showEditModal" :title="t('i18n.editModal.title')" preset="card" :style="{ width: '600px' }">
    <NForm ref="editFormRef" :model="editForm" :rules="editRules" label-placement="left" :label-width="120">
      <NFormItem :label="t('i18n.editModal.key')">
        <NInput :value="data.find(d => d.id === editingId)?.messageKey" disabled />
      </NFormItem>
      <NFormItem :label="t('i18n.editModal.module')">
        <NInput :value="data.find(d => d.id === editingId)?.module" disabled />
      </NFormItem>
      <NFormItem :label="t('i18n.editModal.value')" required path="value">
        <NInput
          v-model:value="editForm.value"
          type="textarea"
          :placeholder="t('i18n.editModal.value')"
          :rows="6"
        />
      </NFormItem>
      <NSpace justify="end">
        <NButton @click="showEditModal = false">{{ t('common.cancel') }}</NButton>
        <NButton v-permission="'sys:i18n:edit'" type="primary" @click="handleSaveEdit">{{ t('common.save') }}</NButton>
      </NSpace>
    </NForm>
  </NModal>

  <!-- 导入 Modal -->
  <NModal v-model:show="showImportModal" :title="t('i18n.importModal.title')" preset="card" :style="{ width: '500px' }">
    <NForm label-placement="left" :label-width="120">
      <NFormItem :label="t('i18n.importModal.locale')" required>
        <NSelect v-model:value="importLocale" :options="localeOptions" />
      </NFormItem>
      <NFormItem :label="t('i18n.importModal.file')" required>
        <NUpload
          :max="1"
          accept=".json,.xlsx"
          :default-file-list="importFile ? [importFile] : []"
          :on-update:file-list="(list: UploadFileInfo[]) => { importFile = list[0] || null }"
        >
          <NButton>{{ t('i18n.importModal.file') }}</NButton>
        </NUpload>
        <template #feedback>
          <span class="text-gray-500 text-sm">{{ t('i18n.importModal.fileHint') }}</span>
        </template>
      </NFormItem>
      <NSpace justify="end">
        <NButton @click="showImportModal = false">{{ t('common.cancel') }}</NButton>
        <NButton v-permission="'sys:i18n:import'" type="primary" @click="handleImportSubmit">
          {{ t('common.confirm') }}
        </NButton>
      </NSpace>
    </NForm>
  </NModal>
</template>