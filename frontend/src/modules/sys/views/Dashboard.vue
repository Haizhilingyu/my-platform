<script setup lang="ts">
import { NCard, NGrid, NGi, NIcon } from 'naive-ui'
import { PersonOutline, ShieldCheckmarkOutline, MenuOutline, DocumentTextOutline } from '@vicons/ionicons5'
import { useAuthStore } from '@/stores/auth'
import { useI18n } from 'vue-i18n'

const authStore = useAuthStore()
const { t } = useI18n()
</script>

<template>
  <div class="space-y-4">
    <div class="px-2 py-3">
      <span class="micro-label text-[rgb(var(--color-text-secondary))]">OVERVIEW</span>
      <h1 class="font-display text-2xl font-semibold mt-1">
        {{ t('dashboard.welcome') }}，<span class="text-[rgb(var(--color-primary))]">{{ authStore.user?.realName || authStore.user?.username }}</span>
      </h1>
    </div>

    <NGrid cols="1 s:2 m:4" responsive="screen" :x-gap="16" :y-gap="16">
      <NGi>
        <NCard>
          <div class="relative">
            <NIcon size="20" class="absolute top-0 right-0 text-[rgb(var(--color-text-secondary))] opacity-40">
              <PersonOutline />
            </NIcon>
            <span class="micro-label text-[rgb(var(--color-text-secondary))]">USERS</span>
            <div class="font-mono-data text-3xl font-semibold mt-2 text-[rgb(var(--color-text))]">0</div>
          </div>
        </NCard>
      </NGi>
      <NGi>
        <NCard>
          <div class="relative">
            <NIcon size="20" class="absolute top-0 right-0 text-[rgb(var(--color-text-secondary))] opacity-40">
              <ShieldCheckmarkOutline />
            </NIcon>
            <span class="micro-label text-[rgb(var(--color-text-secondary))]">ROLES</span>
            <div class="font-mono-data text-3xl font-semibold mt-2 text-[rgb(var(--color-text))]">0</div>
          </div>
        </NCard>
      </NGi>
      <NGi>
        <NCard>
          <div class="relative">
            <NIcon size="20" class="absolute top-0 right-0 text-[rgb(var(--color-text-secondary))] opacity-40">
              <MenuOutline />
            </NIcon>
            <span class="micro-label text-[rgb(var(--color-text-secondary))]">MENUS</span>
            <div class="font-mono-data text-3xl font-semibold mt-2 text-[rgb(var(--color-text))]">{{ authStore.menus.length }}</div>
          </div>
        </NCard>
      </NGi>
      <NGi>
        <NCard>
          <div class="relative">
            <NIcon size="20" class="absolute top-0 right-0 text-[rgb(var(--color-text-secondary))] opacity-40">
              <DocumentTextOutline />
            </NIcon>
            <span class="micro-label text-[rgb(var(--color-text-secondary))]">PERMISSIONS</span>
            <div class="font-mono-data text-3xl font-semibold mt-2 text-[rgb(var(--color-text))]">{{ authStore.permissions.size }}</div>
          </div>
        </NCard>
      </NGi>
    </NGrid>
  </div>
</template>
