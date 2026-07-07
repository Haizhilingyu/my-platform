<script setup lang="ts">
import { ref, computed, onMounted, h, watch } from 'vue'
import {
  NCard, NTabs, NTabPane, NDataTable, NButton, NSelect, NPopconfirm,
  NTooltip, NTag, NIcon, NSpace, NEmpty, useMessage,
  type DataTableColumns, type SelectOption,
} from 'naive-ui'
import {
  LogoChrome, LogoEdge, LogoFirefox, LogoApple,
  PhonePortraitOutline, LaptopOutline, CodeSlashOutline,
} from '@vicons/ionicons5'
import { sessionApi, type SessionInfo } from '@/modules/sys/api/session'
import { userApi } from '@/modules/sys/api/user'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime } from '@/shared/utils/datetime'

const authStore = useAuthStore()
const message = useMessage()

const canManageOthers = computed(() => authStore.hasPermission('sys:user:session'))

const activeTab = ref<'self' | 'admin'>('self')

const selfLoading = ref(false)
const selfSessions = ref<SessionInfo[]>([])

const adminLoading = ref(false)
const adminSessions = ref<SessionInfo[]>([])
const selectedUserId = ref<number | null>(null)
const userOptions = ref<SelectOption[]>([])
const userSearching = ref(false)

async function fetchSelfSessions() {
  selfLoading.value = true
  try {
    const res = await sessionApi.mySessions()
    selfSessions.value = res.data
  } catch (e: any) {
    message.error(e.response?.data?.message || '加载会话失败')
  } finally {
    selfLoading.value = false
  }
}

async function fetchAdminSessions() {
  if (selectedUserId.value == null) {
    adminSessions.value = []
    return
  }
  adminLoading.value = true
  try {
    const res = await sessionApi.userSessions(selectedUserId.value)
    adminSessions.value = res.data
  } catch (e: any) {
    message.error(e.response?.data?.message || '加载会话失败')
  } finally {
    adminLoading.value = false
  }
}

function mapUserOptions(list: { id: number; username: string; realName?: string }[]) {
  return list.map((u) => ({
    label: u.realName ? `${u.username}（${u.realName}）` : u.username,
    value: u.id,
  }))
}

async function initUserOptions() {
  try {
    const res = await userApi.list({ pageNum: 1, pageSize: 5 })
    userOptions.value = mapUserOptions(res.data.list)
  } catch {
    userOptions.value = []
  }
}

async function searchUsers(query: string) {
  if (!query) {
    await initUserOptions()
    return
  }
  userSearching.value = true
  try {
    const res = await userApi.list({ keyword: query, pageNum: 1, pageSize: 20 })
    userOptions.value = mapUserOptions(res.data.list)
  } catch {
    userOptions.value = []
  } finally {
    userSearching.value = false
  }
}

function handleUserChange(value: number | null) {
  selectedUserId.value = value
  fetchAdminSessions()
}

async function revokeOwn(jti: string) {
  try {
    await sessionApi.revokeMySession(jti)
    selfSessions.value = selfSessions.value.filter((s) => s.jti !== jti)
    message.success('已撤销该会话')
  } catch (e: any) {
    message.error(e.response?.data?.message || '撤销失败')
  }
}

async function revokeUser(jti: string) {
  if (selectedUserId.value == null) return
  try {
    await sessionApi.revokeUserSession(selectedUserId.value, jti)
    adminSessions.value = adminSessions.value.filter((s) => s.jti !== jti)
    message.success('已强制下线')
  } catch (e: any) {
    message.error(e.response?.data?.message || '强制下线失败')
  }
}

function deviceIcon(deviceType: string) {
  const map: Record<string, any> = {
    Chrome: LogoChrome,
    Edge: LogoEdge,
    Firefox: LogoFirefox,
    Safari: LogoApple,
    Mobile: PhonePortraitOutline,
    Postman: CodeSlashOutline,
  }
  return map[deviceType] || LaptopOutline
}

function deviceTagType(deviceType: string): 'default' | 'success' | 'info' | 'warning' | 'error' {
  if (deviceType === 'Unknown') return 'default'
  if (deviceType === 'Mobile') return 'warning'
  if (deviceType === 'Postman') return 'error'
  return 'info'
}

function truncate(ua: string, max = 40): string {
  if (!ua) return '-'
  return ua.length > max ? ua.slice(0, max) + '…' : ua
}

function buildColumns(scope: 'self' | 'admin'): DataTableColumns<SessionInfo> {
  const cols: DataTableColumns<SessionInfo> = [
    {
      title: '设备', key: 'deviceType', width: 150,
      render: (row) => h(NSpace, { align: 'center', size: 'small', wrap: false }, {
        default: () => [
          h(NIcon, { size: 18 }, { default: () => h(deviceIcon(row.deviceType)) }),
          h(NTag, { size: 'small', type: deviceTagType(row.deviceType) },
            { default: () => row.deviceType || 'Unknown' }),
        ],
      }),
    },
    { title: 'IP', key: 'ip', width: 140 },
    {
      title: '登录时间', key: 'loginAt', width: 170,
      render: (row) => formatDateTime(row.loginAt),
    },
    {
      title: '过期时间', key: 'expiresAt', width: 170,
      render: (row) => formatDateTime(row.expiresAt),
    },
    {
      title: 'User-Agent', key: 'userAgent', minWidth: 220,
      render: (row) => h(NTooltip, { placement: 'top' }, {
        trigger: () => h('span', { class: 'text-xs break-all text-gray-600' },
          truncate(row.userAgent)),
        default: () => row.userAgent,
      }),
    },
    {
      title: '操作', key: 'actions', width: 130, fixed: 'right',
      render: (row) => h(NPopconfirm, {
        onPositiveClick: () => scope === 'self' ? revokeOwn(row.jti) : revokeUser(row.jti),
      }, {
        trigger: () => h(NButton, {
          size: 'small',
          type: scope === 'self' ? 'warning' : 'error',
        }, { default: () => scope === 'self' ? '下线' : '强制下线' }),
        default: () => scope === 'self' ? '确认下线该会话？' : '确认强制下线该会话？',
      }),
    },
  ]
  if (scope === 'admin') {
    cols.splice(1, 0, { title: '用户名', key: 'username', width: 120 })
  }
  return cols
}

const selfColumns = computed(() => buildColumns('self'))
const adminColumns = computed(() => buildColumns('admin'))

watch(activeTab, (tab) => {
  if (tab === 'self' && selfSessions.value.length === 0) {
    fetchSelfSessions()
  }
})

onMounted(() => {
  fetchSelfSessions()
  initUserOptions()
  if (!canManageOthers.value) {
    activeTab.value = 'self'
  }
})
</script>

<template>
  <NCard>
    <NTabs v-model:value="activeTab" type="line" animated>
      <NTabPane name="self" tab="我的会话">
        <NDataTable
          :columns="selfColumns"
          :data="selfSessions"
          :loading="selfLoading"
          :scroll-x="980"
          :row-key="(row: SessionInfo) => row.jti"
        >
          <template #empty>
            <NEmpty description="暂无活跃会话" />
          </template>
        </NDataTable>
      </NTabPane>

      <NTabPane v-if="canManageOthers" name="admin" tab="用户会话查询">
        <NSpace class="mb-4" align="center" wrap>
          <NSelect
            v-model:value="selectedUserId"
            :options="userOptions"
            :loading="userSearching"
            filterable
            remote
            clearable
            placeholder="搜索用户名 / 姓名"
            class="w-full sm:w-[320px]"
            @search="searchUsers"
            @update:value="handleUserChange"
          />
        </NSpace>

        <NDataTable
          v-if="selectedUserId != null"
          :columns="adminColumns"
          :data="adminSessions"
          :loading="adminLoading"
          :scroll-x="1100"
          :row-key="(row: SessionInfo) => row.jti"
        >
          <template #empty>
            <NEmpty description="该用户暂无活跃会话" />
          </template>
        </NDataTable>
        <NEmpty v-else description="请先选择要查询的用户" class="py-12" />
      </NTabPane>
    </NTabs>
  </NCard>
</template>
