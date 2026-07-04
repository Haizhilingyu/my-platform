<script setup lang="ts">
import { ref, onMounted, h, computed } from 'vue'
import {
  NCard, NDataTable, NButton, NSpace, NInput, NSelect, NTag,
  useMessage, type DataTableColumns,
} from 'naive-ui'
import { notifyApi, type NotifyInboxQuery, type NotifyInboxVO, type NotifyLevel } from '@/shared/api/notify'
import { useNotifyStore } from '@/stores/notify'

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
  { label: '全部级别', value: '' },
  { label: '紧急', value: 'URGENT' },
  { label: '重要', value: 'IMPORTANT' },
  { label: '普通', value: 'NORMAL' },
]

const readOptions = [
  { label: '全部状态', value: '' },
  { label: '未读', value: 'false' },
  { label: '已读', value: 'true' },
]

function levelTagType(level: NotifyLevel): 'error' | 'warning' | 'info' {
  if (level === 'URGENT') return 'error'
  if (level === 'IMPORTANT') return 'warning'
  return 'info'
}

function levelLabel(level: NotifyLevel): string {
  if (level === 'URGENT') return '紧急'
  if (level === 'IMPORTANT') return '重要'
  return '普通'
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
    message.error(err.response?.data?.message || '加载失败')
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
    message.success('已标记为已读')
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    message.error(err.response?.data?.message || '操作失败')
  }
}

async function handleBatchRead(): Promise<void> {
  const unreadIds = checkedKeys.value.filter((id) => {
    const row = data.value.find((d) => d.id === id)
    return row && !row.readStatus
  })
  if (!unreadIds.length) {
    message.warning('请选择未读消息')
    return
  }
  try {
    await notifyApi.batchMarkRead(unreadIds)
    data.value.forEach((d) => {
      if (unreadIds.includes(d.id)) d.readStatus = true
    })
    notifyStore.decrementUnread(unreadIds.length)
    checkedKeys.value = []
    message.success(`已标记 ${unreadIds.length} 条为已读`)
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    message.error(err.response?.data?.message || '操作失败')
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
    title: '标题',
    key: 'title',
    minWidth: 220,
    render: (row) =>
      h(
        'span',
        {
          class: ['cursor-pointer', row.readStatus ? '' : 'font-semibold'],
          onClick: () => handleMarkRead(row),
        },
        { default: () => row.title || '(无标题)' },
      ),
  },
  {
    title: '级别',
    key: 'level',
    width: 90,
    render: (row) =>
      h(
        NTag,
        { type: levelTagType(row.level), size: 'small' },
        { default: () => levelLabel(row.level) },
      ),
  },
  { title: '业务类型', key: 'businessType', width: 120 },
  { title: '发送时间', key: 'createdAt', width: 180 },
  {
    title: '状态',
    key: 'readStatus',
    width: 90,
    render: (row) =>
      h(
        NTag,
        { type: row.readStatus ? 'default' : 'success', size: 'small' },
        { default: () => (row.readStatus ? '已读' : '未读') },
      ),
  },
  {
    title: '操作',
    key: 'actions',
    width: 110,
    render: (row) =>
      row.readStatus
        ? h('span', { class: 'opacity-50' }, { default: () => '—' })
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
            { default: () => '标记已读' },
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
          placeholder="搜索标题"
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
        批量标记已读
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
