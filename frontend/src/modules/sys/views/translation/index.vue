<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { NCard, NDataTable, NButton, NSpace, NModal, NForm, NFormItem, NInput, NUpload, NSelect, useMessage } from 'naive-ui'
import type { DataTableColumns, UploadCustomRequestOptions } from 'naive-ui'
import { translationApi, type MenuTranslationVO, type MenuTranslationImportDTO } from '@/modules/sys/api/translation'

const { t } = useI18n()
const message = useMessage()

const allTranslations = ref<MenuTranslationVO[]>([])
const loading = ref(false)
const selectedLocale = ref('zh-CN')
const showModal = ref(false)
const editingItem = ref<MenuTranslationVO | null>(null)
const editDisplayName = ref('')

const locales = [
  { label: '中文', value: 'zh-CN' },
  { label: 'English', value: 'en' },
]

const filteredData = computed(() =>
  allTranslations.value.filter(item => item.locale === selectedLocale.value)
)

const columns = computed<DataTableColumns<MenuTranslationVO>>(() => [
  { title: t('sys.translation.menuId'), key: 'menuId', width: 60 },
  { title: t('sys.translation.originalName'), key: 'menuName', width: 200 },
  { title: t('sys.translation.displayName'), key: 'displayName', width: 200 },
  {
    title: t('common.operation'), key: 'actions', width: 100,
    render: (row) => h(NButton, { size: 'small', onClick: () => openEdit(row) }, { default: () => t('sys.translation.edit') }),
  },
])

import { h } from 'vue'

function openEdit(row: MenuTranslationVO) {
  editingItem.value = row
  editDisplayName.value = row.displayName
  showModal.value = true
}

async function saveEdit() {
  if (!editingItem.value || !editDisplayName.value.trim()) return
  try {
    await translationApi.update(editingItem.value.id, editDisplayName.value.trim())
    editingItem.value.displayName = editDisplayName.value.trim()
    message.success(t('common.modifySuccess'))
    showModal.value = false
  } catch {
    message.error(t('common.operationFailed'))
  }
}

async function fetchData() {
  loading.value = true
  try {
    const res = await translationApi.list()
    allTranslations.value = res.data || []
  } catch {
    message.error(t('common.queryFailed'))
  } finally {
    loading.value = false
  }
}

async function handleExport() {
  try {
    const res = await translationApi.export(selectedLocale.value)
    const rows = res.data || []
    const csv = 'menu_id,display_name\n' + rows.map((r: MenuTranslationVO) => `${r.menuId},${r.displayName}`).join('\n')
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `menu-translations-${selectedLocale.value}.csv`
    a.click()
    URL.revokeObjectURL(url)
    message.success(t('common.saveSuccess'))
  } catch {
    message.error(t('common.operationFailed'))
  }
}

async function handleImportFile(options: UploadCustomRequestOptions) {
  const file = options.file.file
  if (!file) return
  if (!file) return
  try {
    const text = await file.text()
    const lines = text.split('\n').filter(l => l.trim() && !l.startsWith('menu_id'))
    const items = lines.map(line => {
      const parts = line.split(',')
      return { menuId: Number(parts[0]), displayName: parts.slice(1).join(',').trim() }
    }).filter(item => item.menuId && item.displayName)

    if (items.length === 0) {
      message.error('CSV format invalid')
      return
    }

    const dto: MenuTranslationImportDTO = { locale: selectedLocale.value, items }
    await translationApi.import(dto)
    message.success(t('sys.translation.importSuccess'))
    await fetchData()
  } catch {
    message.error(t('common.operationFailed'))
  }
}

onMounted(fetchData)
</script>

<template>
  <NCard :title="t('sys.translation.title')">
    <NSpace justify="space-between" align="center" style="margin-bottom: 16px">
      <NSelect
        v-model:value="selectedLocale"
        :options="locales"
        :placeholder="t('sys.translation.selectLocale')"
        style="width: 200px"
      />
      <NSpace>
        <NButton @click="handleExport">{{ t('sys.translation.export') }}</NButton>
        <NUpload :show-file-list="false" :custom-request="handleImportFile" accept=".csv">
          <NButton type="primary">{{ t('sys.translation.import') }}</NButton>
        </NUpload>
      </NSpace>
    </NSpace>

    <NDataTable
      :columns="columns"
      :data="filteredData"
      :loading="loading"
      :bordered="false"
      size="small"
    />
  </NCard>

  <NModal v-model:show="showModal" :title="t('sys.translation.editTitle')" preset="card" style="width: 400px">
    <NForm label-placement="top">
      <NFormItem :label="t('sys.translation.displayName')">
        <NInput v-model:value="editDisplayName" />
      </NFormItem>
    </NForm>
    <template #footer>
      <NSpace justify="end">
        <NButton @click="showModal = false">{{ t('common.cancel') }}</NButton>
        <NButton type="primary" @click="saveEdit">{{ t('common.save') }}</NButton>
      </NSpace>
    </template>
  </NModal>
</template>
