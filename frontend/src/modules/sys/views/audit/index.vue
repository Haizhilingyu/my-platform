<script setup lang="ts">
import { ref, computed, onMounted, h, type VNode } from 'vue'
import {
  NCard, NDataTable, NButton, NSpace, NInput, NSelect, NDatePicker,
  NGrid, NGi, NTag, NEmpty, NCode, type DataTableColumns,
} from 'naive-ui'
import { auditApi, type AuditLogVO, type AuditLogQuery } from '@/modules/sys/api/audit'
import { formatDateTime } from '@/shared/utils/datetime'

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
  { label: '成功', value: 'success' },
  { label: '失败', value: 'fail' },
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
  { title: '操作人', key: 'actor', width: 120 },
  {
    title: '操作类型', key: 'action', width: 110,
    render: (row) => h(NTag, {
      type: actionTagType[row.action] || 'default',
      size: 'small',
    }, { default: () => row.action }),
  },
  { title: '对象类型', key: 'targetType', width: 120 },
  { title: '对象 ID', key: 'targetId', width: 100 },
  { title: 'IP', key: 'ip', width: 130 },
  {
    title: '设备', key: 'userAgent', width: 150,
    render: (row) => row.userAgent
      ? h('span', { class: 'text-xs text-gray-600' }, truncateUa(row.userAgent))
      : '-',
  },
  {
    title: '结果', key: 'result', width: 80,
    render: (row) => h(NTag, {
      type: row.result === 'success' ? 'success' : 'error',
      size: 'small',
    }, { default: () => row.result === 'success' ? '成功' : '失败' }),
  },
  {
    title: '时间', key: 'createdAt', width: 170,
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
      h('span', { class: 'inline-block w-20 align-top text-gray-500' }, '参数'),
      h(NCode, { code: params, language: 'json', wordWrap: true }),
    ]))
  }
  if (row.errorMsg) {
    children.push(h('div', { class: 'mb-2' }, [
      h('span', { class: 'inline-block w-20 align-top text-gray-500' }, '失败原因'),
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
    return h(NEmpty, { description: '无详细信息', size: 'small' })
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
          v-model:value="filters.actor" placeholder="操作人" clearable
          @keyup.enter="handleSearch"
        />
      </NGi>
      <NGi>
        <NSelect
          v-model:value="filters.action" :options="actionOptions"
          placeholder="操作类型" clearable
        />
      </NGi>
      <NGi>
        <NSelect
          v-model:value="filters.result" :options="resultOptions"
          placeholder="结果" clearable
        />
      </NGi>
      <NGi>
        <NInput
          v-model:value="filters.targetType" placeholder="对象类型" clearable
          @keyup.enter="handleSearch"
        />
      </NGi>
    </NGrid>

    <NSpace class="mt-3" align="center" wrap>
      <NDatePicker
        v-model:value="filters.range" type="daterange" clearable
        class="max-w-[420px]"
      />
      <NButton type="primary" @click="handleSearch">查询</NButton>
      <NButton @click="handleReset">重置</NButton>
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
