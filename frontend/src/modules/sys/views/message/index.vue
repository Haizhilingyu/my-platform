<script setup lang="ts">
import { ref, onMounted, h, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  NCard, NDataTable, NButton, NSpace, NInput, NSelect, NTag,
  useMessage, type DataTableColumns,
} from 'naive-ui'
import { notifyApi, type NotifyInboxQuery, type NotifyInboxVO, type NotifyLevel } from '@/shared/api/notify'
import { useNotifyStore } from '@/stores/notify'
import { formatDateTime } from '@/shared/utils/datetime'

const { t } = useI18n()
const message = useMessage()
const notifyStore = useNotifyStore()

const loading = ref(false)
const data = ref<NotifyInboxVO[]>([])
const total = ref(0)
const checkedKeys = ref<number[]>([])
const pageNum = ref(1)
const pageSize = ref(10)

const keyword = ref('')
const levelFilter = ref<string>('')
const readFilter = ref<string>('')

const levelOptions = [
  { label: t('sys.message.allLevels'), value: '' },
  { label: t('sys.message.levelUrgent'), value: 'URGENT' },
  { label: t('sys.message.levelImportant'), value: 'IMPORTANT' },
  { label: t('sys.message.levelNormal'), value: 'NORMAL' },
]

const readOptions = [
  { label: t('sys.message.allStatus'), value: '' },
  { label: t('sys.message.unread'), value: 'false' },
  { label: t('sys.message.read'), value: 'true' },
]

function levelTagType(level: NotifyLevel): 'error' | 'warning' | 'info' {
  if (level === 'URGENT') return 'error'
  if (level === 'IMPORTANT') return 'warning'
  return 'info'
}

function levelLabel(level: NotifyLevel): string {
  if (level === 'URGENT') return t('sys.message.levelUrgent')
  if (level === 'IMPORTANT') return t('sys.message.levelImportant')
  return t('sys.message.levelNormal')
}

async function fetchData(): Promise<void> {
  loading.value = true
  try {
    const query: NotifyInboxQuery = { pageNum: pageNum.value, pageSize: pageSize.value }
    if (levelFilter.value) query.level = levelFilter.value as NotifyLevel
    if (readFilter.value !== '') query.readStatus = readFilter.value === 'true'
    if (keyword.value) query.keyword = keyword.value
    const res = await notifyApi.inbox(query)
    data.value = res.data.list
    total.value = res.data.total
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    message.error(err.response?.data?.message || t('sys.message.loadFailed'))
  } finally {
    loading.value = false
  }
}

async function handleMarkRead(row: NotifyInboxVO): Promise<void> {
  if (row.readStatus) return
  try {
    await notifyApi.markRead(row.id)
    row.readStatus = true
    notifyStore.decrementUnread()
    message.success(t('sys.message.markedAsRead'))
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    message.error(err.response?.data?.message || t('common.operationFailed'))
  }
}

async function handleBatchRead(): Promise<void> {
  const unreadIds = checkedKeys.value.filter((id) => {
    const row = data.value.find((d) => d.id === id)
    return row && !row.readStatus
  })
  if (!unreadIds.length) {
    message.warning(t('sys.message.selectUnreadMessages'))
    return
  }
  try {
    await notifyApi.batchMarkRead(unreadIds)
    data.value.forEach((d) => {
      if (unreadIds.includes(d.id)) d.readStatus = true
    })
    notifyStore.decrementUnread(unreadIds.length)
    checkedKeys.value = []
    message.success(t('sys.message.markedCountAsRead', { count: unreadIds.length }))
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    message.error(err.response?.data?.message || t('common.operationFailed'))
  }
}

function handleSearch(): void {
  pageNum.value = 1
  fetchData()
}

function handleCheckedRowKeys(keys: (string | number)[]): void {
  checkedKeys.value = keys.map((k) => Number(k))
}

const columns = computed<DataTableColumns<NotifyInboxVO>>(() => [
  { type: 'selection' },
  {
    title: t('sys.message.title'),
    key: 'title',
    minWidth: 220,
    render: (row) =>
      h(
        'span',
        {
          class: ['cursor-pointer', row.readStatus ? '' : 'font-semibold'],
          onClick: () => handleMarkRead(row),
        },
        { default: () => row.title || t('sys.message.noTitle') },
      ),
  },
  {
    title: t('sys.message.level'),
    key: 'level',
    width: 90,
    render: (row) =>
      h(
        NTag,
        { type: levelTagType(row.level), size: 'small' },
        { default: () => levelLabel(row.level) },
      ),
  },
  { title: t('sys.message.businessType'), key: 'businessType', width: 120 },
  { title: t('sys.message.sentTime'), key: 'createdAt', width: 180, render: (row) => formatDateTime(row.createdAt) },
  {
    title: t('sys.message.status'),
    key: 'readStatus',
    width: 90,
    render: (row) =>
      h(
        NTag,
        { type: row.readStatus ? 'default' : 'success', size: 'small' },
        { default: () => (row.readStatus ? t('sys.message.read') : t('sys.message.unread')) },
      ),
  },
  {
    title: t('common.operation'),
    key: 'actions',
    width: 110,
    render: (row) =>
      row.readStatus
        ? h('span', { class: 'opacity-50' }, { default: () => t('sys.message.dash') })
        : h(
            NButton,
            {
              size: 'small',
              type: 'primary',
              onClick: (e: Event) => {
                e.stopPropagation()
                handleMarkRead(row)
              },
            },
            { default: () => t('sys.message.markRead') },
          ),
  },
])

const rowKey = (row: NotifyInboxVO): number => row.id

onMounted(fetchData)
</script>

<template>
  <NCard>
    <NSpace class="mb-4" justify="space-between" :wrap="false">
      <NSpace :wrap="false">
        <NInput
          v-model:value="keyword"
          :placeholder="t('sys.message.searchTitle')"
          clearable
          class="w-[200px]"
          @clear="handleSearch"
          @keyup.enter="handleSearch"
        />
        <NSelect
          v-model:value="levelFilter"
          :options="levelOptions"
          class="w-[140px]"
          @update:value="handleSearch"
        />
        <NSelect
          v-model:value="readFilter"
          :options="readOptions"
          class="w-[140px]"
          @update:value="handleSearch"
        />
      </NSpace>
      <NButton
        type="primary"
        :disabled="!checkedKeys.length"
        @click="handleBatchRead"
      >
        {{ t('sys.message.batchMarkRead') }}
      </NButton>
    </NSpace>

    <NDataTable
      :columns="columns"
      :data="data"
      :loading="loading"
      :row-key="rowKey"
      :checked-row-keys="checkedKeys"
      :scroll-x="1000"
      :pagination="{
        page: pageNum,
        pageSize: pageSize,
        itemCount: total,
        showSizePicker: true,
        pageSizes: [10, 20, 50],
        onChange: (p: number) => { pageNum = p; fetchData() },
        onUpdatePageSize: (s: number) => { pageSize = s; pageNum = 1; fetchData() },
      }"
      remote
      @update:checked-row-keys="handleCheckedRowKeys"
    />
  </NCard>
</template>
