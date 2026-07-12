<script setup lang="ts">
import { ref, computed, onMounted, h, type VNode } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  NCard, NDataTable, NButton, NSpace, NInput, NSelect, NDatePicker,
  NGrid, NGi, NTag, NEmpty, NCode, type DataTableColumns,
} from 'naive-ui'
import { auditApi, type AuditLogVO, type AuditLogQuery } from '@/modules/sys/api/audit'
import { formatDateTime } from '@/shared/utils/datetime'

const { t } = useI18n()

const loading = ref(false)
const data = ref<AuditLogVO[]>([])
const total = ref(0)

const filters = ref<{
  actor: string | null
  action: string | null
  result: 'success' | 'fail' | null
  targetType: string | null
  range: [number, number] | null
}>({
  actor: null,
  action: null,
  result: null,
  targetType: null,
  range: null,
})

const query = ref<AuditLogQuery>({ pageNum: 1, pageSize: 20 })

const actionOptions = [
  { label: 'LOGIN', value: 'LOGIN' },
  { label: 'LOGOUT', value: 'LOGOUT' },
  { label: 'UNLOCK', value: 'UNLOCK' },
  { label: 'PUBLISH', value: 'PUBLISH' },
  { label: 'CREATE', value: 'CREATE' },
  { label: 'UPDATE', value: 'UPDATE' },
  { label: 'DELETE', value: 'DELETE' },
]

const resultOptions = [
  { label: t('sys.audit.success'), value: 'success' },
  { label: t('sys.audit.fail'), value: 'fail' },
]

const actionTagType: Record<string, 'default' | 'info' | 'success' | 'warning' | 'error'> = {
  LOGIN: 'info',
  LOGOUT: 'info',
  CREATE: 'success',
  PUBLISH: 'success',
  UPDATE: 'warning',
  UNLOCK: 'warning',
  DELETE: 'error',
}

async function fetchData() {
  loading.value = true
  try {
    const params: AuditLogQuery = {
      ...query.value,
      actor: filters.value.actor || undefined,
      action: filters.value.action || undefined,
      result: filters.value.result || undefined,
      targetType: filters.value.targetType || undefined,
    }
    if (filters.value.range) {
      params.startTime = new Date(filters.value.range[0]).toISOString()
      params.endTime = new Date(filters.value.range[1]).toISOString()
    }
    const res = await auditApi.list(params)
    data.value = res.data.list
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.value.pageNum = 1
  fetchData()
}

function handleReset() {
  filters.value = { actor: null, action: null, result: null, targetType: null, range: null }
  query.value.pageNum = 1
  fetchData()
}

function prettyParams(row: AuditLogVO): string {
  if (!row.params) return ''
  try {
    return JSON.stringify(JSON.parse(row.params), null, 2)
  } catch {
    return row.params
  }
}

function truncateUa(ua: string, max = 45): string {
  return ua.length > max ? ua.slice(0, max) + '…' : ua
}

const columns = computed<DataTableColumns<AuditLogVO>>(() => [
  { title: t('sys.audit.actor'), key: 'actor', width: 120 },
  {
    title: t('sys.audit.actionType'), key: 'action', width: 110,
    render: (row) => h(NTag, {
      type: actionTagType[row.action] || 'default',
      size: 'small',
    }, { default: () => row.action }),
  },
  { title: t('sys.audit.targetType'), key: 'targetType', width: 120 },
  { title: t('sys.audit.targetId'), key: 'targetId', width: 100 },
  { title: t('sys.audit.ip'), key: 'ip', width: 130 },
  {
    title: t('sys.audit.device'), key: 'userAgent', width: 150,
    render: (row) => row.userAgent
      ? h('span', { class: 'text-xs text-gray-600' }, truncateUa(row.userAgent))
      : '-',
  },
  {
    title: t('sys.audit.result'), key: 'result', width: 80,
    render: (row) => h(NTag, {
      type: row.result === 'success' ? 'success' : 'error',
      size: 'small',
    }, { default: () => row.result === 'success' ? t('sys.audit.success') : t('sys.audit.fail') }),
  },
  {
    title: t('sys.audit.time'), key: 'createdAt', width: 170,
    render: (row) => formatDateTime(row.createdAt),
  },
])

const expandedKeys = ref<Array<string | number>>([])

function rowKey(row: AuditLogVO): number {
  return row.id
}

function handleExpandKeys(keys: Array<string | number>) {
  expandedKeys.value = keys
}

function renderExpand(row: AuditLogVO) {
  const params = prettyParams(row)
  const children: VNode[] = []
  if (params) {
    children.push(h('div', { class: 'mb-2' }, [
      h('span', { class: 'inline-block w-20 align-top text-gray-500' }, t('sys.audit.params')),
      h(NCode, { code: params, language: 'json', wordWrap: true }),
    ]))
  }
  if (row.errorMsg) {
    children.push(h('div', { class: 'mb-2' }, [
      h('span', { class: 'inline-block w-20 align-top text-gray-500' }, t('sys.audit.failReason')),
      h('span', { class: 'text-red-600' }, row.errorMsg),
    ]))
  }
  if (row.userAgent) {
    children.push(h('div', {}, [
      h('span', { class: 'inline-block w-20 align-top text-gray-500' }, 'UA'),
      h('span', { class: 'break-all text-xs text-gray-600' }, row.userAgent),
    ]))
  }
  if (!params && !row.errorMsg && !row.userAgent) {
    return h(NEmpty, { description: t('sys.audit.noDetails'), size: 'small' })
  }
  return h('div', { class: 'px-4 py-2 bg-gray-50' }, children)
}

onMounted(fetchData)
</script>

<template>
  <NCard>
    <NGrid cols="1 s:2 m:4" responsive="screen" :x-gap="12" :y-gap="12">
      <NGi>
        <NInput
          v-model:value="filters.actor" :placeholder="t('sys.audit.placeholders.actor')" clearable
          @keyup.enter="handleSearch"
        />
      </NGi>
      <NGi>
        <NSelect
          v-model:value="filters.action" :options="actionOptions"
          :placeholder="t('sys.audit.placeholders.actionType')" clearable
        />
      </NGi>
      <NGi>
        <NSelect
          v-model:value="filters.result" :options="resultOptions"
          :placeholder="t('sys.audit.placeholders.result')" clearable
        />
      </NGi>
      <NGi>
        <NInput
          v-model:value="filters.targetType" :placeholder="t('sys.audit.placeholders.targetType')" clearable
          @keyup.enter="handleSearch"
        />
      </NGi>
    </NGrid>

    <NSpace class="mt-3" align="center" wrap>
      <NDatePicker
        v-model:value="filters.range" type="daterange" clearable
        class="max-w-[420px]"
      />
      <NButton type="primary" @click="handleSearch">{{ t('common.search') }}</NButton>
      <NButton @click="handleReset">{{ t('common.reset') }}</NButton>
    </NSpace>

    <NDataTable
      class="mt-4"
      :columns="columns"
      :data="data"
      :loading="loading"
      :scroll-x="1100"
      :row-key="rowKey"
      :expanded-row-keys="expandedKeys"
      :render-expand="renderExpand"
      :pagination="{
        page: query.pageNum,
        pageSize: query.pageSize,
        itemCount: total,
        showSizePicker: true,
        pageSizes: [20, 50, 100],
        onChange: (p: number) => { query.pageNum = p; fetchData() },
        onUpdatePageSize: (s: number) => { query.pageSize = s; query.pageNum = 1; fetchData() },
      }"
      remote
      @update:expanded-row-keys="handleExpandKeys"
    />
  </NCard>
</template>
