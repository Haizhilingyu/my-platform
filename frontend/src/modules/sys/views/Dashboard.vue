<script setup lang="ts">
import { NCard, NGrid, NGi, NStatistic, NText } from 'naive-ui'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
</script>

<template>
  <div class="space-y-4">
    <NCard>
      <NText>欢迎回来，{{ authStore.user?.realName || authStore.user?.username }}！</NText>
    </NCard>

    <NGrid cols="1 s:2 m:4" responsive="screen" :x-gap="16" :y-gap="16">
      <NGi>
        <NCard>
          <NStatistic label="用户数量" :value="0" />
        </NCard>
      </NGi>
      <NGi>
        <NCard>
          <NStatistic label="角色数量" :value="0" />
        </NCard>
      </NGi>
      <NGi>
        <NCard>
          <NStatistic label="菜单数量" :value="authStore.menus.length" />
        </NCard>
      </NGi>
      <NGi>
        <NCard>
          <NStatistic label="权限数量" :value="authStore.permissions.size" />
        </NCard>
      </NGi>
    </NGrid>
  </div>
</template>
