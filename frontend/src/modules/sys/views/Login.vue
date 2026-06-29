<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { NCard, NForm, NFormItem, NInput, NButton, NSpace, useMessage } from 'naive-ui'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const message = useMessage()

const loading = ref(false)
const form = ref({ username: 'admin', password: 'admin123' })

async function handleLogin() {
  loading.value = true
  try {
    await authStore.login(form.value.username, form.value.password)
    message.success('登录成功')
    router.push('/')
  } catch (e: any) {
    message.error(e.response?.data?.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="min-h-screen flex items-center justify-center bg-[rgb(var(--color-background))]">
    <NCard class="w-[400px]" title="My Platform" :bordered="true">
      <NForm @keyup.enter="handleLogin">
        <NFormItem label="用户名">
          <NInput v-model:value="form.username" placeholder="请输入用户名" />
        </NFormItem>
        <NFormItem label="密码">
          <NInput v-model:value="form.password" type="password" placeholder="请输入密码" />
        </NFormItem>
        <NSpace vertical>
          <NButton type="primary" block :loading="loading" @click="handleLogin">
            登录
          </NButton>
        </NSpace>
      </NForm>
    </NCard>
  </div>
</template>
