<script setup lang="ts">
import { ref, onMounted, h, computed } from 'vue'
import {
  NCard, NDataTable, NButton, NSpace, NInput, NSelect, NModal, NForm,
  NFormItem, NSwitch, NTag, NPopconfirm, useMessage,
  type DataTableColumns, type FormInst, type FormRules,
} from 'naive-ui'
import { userApi, type UserQuery } from '@/modules/sys/api/user'
import { roleApi } from '@/modules/sys/api/role'
import { unitApi } from '@/modules/sys/api/unit'
import type { UserVO, SysRole, UnitTreeNode } from '@/modules/sys/api/types'
import { useAuthStore } from '@/stores/auth'
import {
  requiredRule, lengthRule, maxLengthRule, patternRule, emailRule,
  USERNAME_PATTERN, PHONE_PATTERN,
} from '@/shared/utils/validation'

const authStore = useAuthStore()
const message = useMessage()

const loading = ref(false)
const data = ref<UserVO[]>([])
const total = ref(0)
const query = ref<UserQuery>({ pageNum: 1, pageSize: 10 })

const showModal = ref(false)
const editingId = ref<number | null>(null)
const roles = ref<SysRole[]>([])
const unitTree = ref<UnitTreeNode[]>([])

const form = ref({
  username: '',
  password: '',
  realName: '',
  email: '',
  phone: '',
  unitId: null as number | null,
  status: 1,
  roleIds: [] as number[],
})
const formRef = ref<FormInst | null>(null)
const rules = computed<FormRules>(() => {
  // 编辑态不展示用户名/密码字段，对应规则置空，避免误触发 required。
  const isEdit = !!editingId.value
  return {
    username: isEdit
      ? []
      : [
          requiredRule('用户名不能为空'),
          lengthRule(3, 32, '用户名长度需在3-32之间'),
          patternRule(USERNAME_PATTERN, '用户名只能包含字母、数字、下划线'),
        ],
    password: isEdit
      ? []
      : [requiredRule('密码不能为空'), lengthRule(6, 32, '密码长度需在6-32之间')],
    realName: [maxLengthRule(50, '姓名长度不能超过50')],
    email: [emailRule()],
    phone: [
      patternRule(PHONE_PATTERN, '手机号格式不正确'),
      maxLengthRule(20, '手机号长度不能超过20'),
    ],
  }
})

async function fetchData() {
  loading.value = true
  try {
    const res = await userApi.list(query.value)
    data.value = res.data.list
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

async function fetchInit() {
  const [roleRes, unitRes] = await Promise.all([roleApi.list(), unitApi.tree()])
  roles.value = roleRes.data
  unitTree.value = unitRes.data
}

function handleAdd() {
  editingId.value = null
  form.value = {
    username: '', password: '', realName: '', email: '', phone: '',
    unitId: null, status: 1, roleIds: [],
  }
  showModal.value = true
}

async function handleEdit(row: UserVO) {
  editingId.value = row.id
  form.value = {
    username: row.username,
    password: '',
    realName: row.realName || '',
    email: row.email || '',
    phone: row.phone || '',
    unitId: row.unitId,
    status: row.status,
    roleIds: [],
  }
  const res = await userApi.getUserRoles(row.id)
  form.value.roleIds = res.data
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
      await userApi.update(editingId.value, {
        realName: form.value.realName || undefined,
        email: form.value.email || undefined,
        phone: form.value.phone || undefined,
        unitId: form.value.unitId || undefined,
        status: form.value.status,
      })
      await userApi.assignRoles(editingId.value, form.value.roleIds)
      message.success('修改成功')
    } else {
      await userApi.create({
        username: form.value.username,
        password: form.value.password,
        realName: form.value.realName || undefined,
        email: form.value.email || undefined,
        phone: form.value.phone || undefined,
        unitId: form.value.unitId || undefined,
        roleIds: form.value.roleIds,
      })
      message.success('新增成功')
    }
    showModal.value = false
    fetchData()
  } catch (e: any) {
    message.error(e.response?.data?.message || '操作失败')
  }
}

async function handleDelete(row: UserVO) {
  try {
    await userApi.delete(row.id)
    message.success('删除成功')
    fetchData()
  } catch (e: any) {
    message.error(e.response?.data?.message || '删除失败')
  }
}

async function handleUnlock(row: UserVO) {
  try {
    await userApi.unlock(row.id)
    message.success('解锁成功')
    fetchData()
  } catch (e: any) {
    message.error(e.response?.data?.message || '解锁失败')
  }
}

async function handleResetPassword(row: UserVO) {
  try {
    await userApi.resetPassword(row.id, 'User@123456')
    message.success('密码已重置为 User@123456')
    fetchData()
  } catch (e: any) {
    message.error(e.response?.data?.message || '重置失败')
  }
}

function flattenUnits(units: UnitTreeNode[]): any[] {
  return units.map(u => ({
    label: u.unitName,
    value: u.id,
    children: u.children?.length ? flattenUnits(u.children) : undefined,
  }))
}

const columns: DataTableColumns<UserVO> = [
  { title: '用户名', key: 'username', width: 120 },
  { title: '姓名', key: 'realName', width: 100 },
  { title: '邮箱', key: 'email', width: 180 },
  { title: '电话', key: 'phone', width: 130 },
  { title: '单位', key: 'unitName', width: 120 },
  {
    title: '状态', key: 'status', width: 90,
    render: (row) => {
      if (row.locked) {
        return h(NTag, { type: 'error', size: 'small' }, { default: () => '锁定' })
      }
      return h(NTag, { type: row.status === 1 ? 'success' : 'warning', size: 'small' },
        { default: () => row.status === 1 ? '正常' : '禁用' })
    },
  },
  {
    title: '操作', key: 'actions', width: 340,
    render: (row) => h(NSpace, {}, {
      default: () => [
        authStore.hasPermission('sys:user:edit') && h(NButton, {
          size: 'small', onClick: () => handleEdit(row),
        }, { default: () => '编辑' }),
        row.locked && authStore.hasPermission('sys:user:unlock') && h(NButton, {
          size: 'small', type: 'warning', onClick: () => handleUnlock(row),
        }, { default: () => '解锁' }),
        authStore.hasPermission('sys:user:reset') && h(NPopconfirm, {
          onPositiveClick: () => handleResetPassword(row),
        }, {
          trigger: () => h(NButton, { size: 'small', type: 'info' }, { default: () => '重置密码' }),
          default: () => '确认重置密码为 User@123456？',
        }),
        authStore.hasPermission('sys:user:delete') && h(NButton, {
          size: 'small', type: 'error', onClick: () => handleDelete(row),
        }, { default: () => '删除' }),
      ],
    }),
  },
]

onMounted(() => {
  fetchInit()
  fetchData()
})
</script>

<template>
  <NCard>
    <NSpace class="mb-4" justify="space-between">
      <NSpace>
        <NInput
          v-model:value="query.keyword" placeholder="搜索用户名/姓名/电话" clearable
          class="w-[250px]" @clear="fetchData" @keyup.enter="fetchData"
        />
        <NButton type="primary" @click="fetchData">查询</NButton>
      </NSpace>
      <NButton v-permission="'sys:user:add'" type="primary" @click="handleAdd">新增用户</NButton>
    </NSpace>

    <NDataTable
      :columns="columns"
      :data="data"
      :loading="loading"
      :scroll-x="1100"
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

  <NModal v-model:show="showModal" :title="editingId ? '编辑用户' : '新增用户'" preset="card" :style="{ width: '500px' }">
    <NForm ref="formRef" :model="form" :rules="rules" label-placement="left" :label-width="80">
      <NFormItem v-if="!editingId" label="用户名" required path="username">
        <NInput v-model:value="form.username" placeholder="请输入用户名" />
      </NFormItem>
      <NFormItem v-if="!editingId" label="密码" required path="password">
        <NInput v-model:value="form.password" type="password" placeholder="请输入密码" />
      </NFormItem>
      <NFormItem label="姓名" path="realName">
        <NInput v-model:value="form.realName" placeholder="请输入姓名" />
      </NFormItem>
      <NFormItem label="邮箱" path="email">
        <NInput v-model:value="form.email" placeholder="请输入邮箱" />
      </NFormItem>
      <NFormItem label="电话" path="phone">
        <NInput v-model:value="form.phone" placeholder="请输入电话" />
      </NFormItem>
      <NFormItem label="单位">
        <NSelect
          v-model:value="form.unitId" :options="flattenUnits(unitTree)"
          placeholder="请选择单位" clearable
        />
      </NFormItem>
      <NFormItem label="角色">
        <NSelect
          v-model:value="form.roleIds" :multiple="true"
          :options="roles.map(r => ({ label: r.roleName, value: r.id }))"
          placeholder="请选择角色"
        />
      </NFormItem>
      <NFormItem v-if="editingId" label="状态">
        <NSwitch v-model:value="form.status" :checked-value="1" :unchecked-value="0" />
      </NFormItem>
      <NSpace justify="end">
        <NButton @click="showModal = false">取消</NButton>
        <NButton type="primary" @click="handleSave">保存</NButton>
      </NSpace>
    </NForm>
  </NModal>
</template>
