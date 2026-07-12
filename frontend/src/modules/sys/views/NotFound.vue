<script setup lang="ts">
import { NResult } from 'naive-ui'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <div class="min-h-screen flex items-center justify-center">
    <NResult status="404" title="404" :description="t('notFound.message')">
    </NResult>
  </div>
</template>
