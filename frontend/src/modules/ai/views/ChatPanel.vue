<script setup lang="ts">
import { ref, nextTick, watch } from 'vue'
import { NInput, NButton, NIcon, NScrollbar, NSpin } from 'naive-ui'
import { SendOutline, SparklesOutline, OpenOutline } from '@vicons/ionicons5'
import { useAiChat } from '@/modules/ai/composables/useAiChat'
import type { AiActionEvent } from '@/modules/ai/api/ai'
import { useI18n } from 'vue-i18n'

const emit = defineEmits<{ (e: 'action', payload: AiActionEvent): void }>()
const { t } = useI18n()
const { messages, streaming, send } = useAiChat()
const input = ref('')
const scrollbarRef = ref<InstanceType<typeof NScrollbar> | null>(null)

async function scrollToBottom(): Promise<void> {
  await nextTick()
  scrollbarRef.value?.scrollTo({ top: 999999, behavior: 'smooth' })
}

async function submit(): Promise<void> {
  const text = input.value.trim()
  if (!text || streaming.value) {
    return
  }
  input.value = ''
  await send(text, (a) => emit('action', a))
  await scrollToBottom()
}

function openAction(a?: AiActionEvent): void {
  if (a) {
    emit('action', a)
  }
}

function runExample(text: string): void {
  input.value = text
  void submit()
}

watch(messages, scrollToBottom, { deep: true })
</script>

<template>
  <div class="flex flex-col h-full">
    <NScrollbar ref="scrollbarRef" class="flex-1 px-4 py-3">
      <div v-if="!messages.length" class="text-center py-8 opacity-80">
        <NIcon size="32" class="mb-2">
          <SparklesOutline />
        </NIcon>
        <p class="text-sm mb-4">{{ t('ai.welcome') }}</p>
        <div class="flex flex-col gap-2 max-w-[280px] mx-auto">
          <NButton size="small" secondary @click="runExample(t('ai.exampleCreate'))">
            {{ t('ai.exampleCreate') }}
          </NButton>
          <NButton size="small" secondary @click="runExample(t('ai.exampleDelete'))">
            {{ t('ai.exampleDelete') }}
          </NButton>
        </div>
      </div>
      <div
        v-for="(m, i) in messages"
        :key="i"
        :class="['mb-3 flex', m.role === 'user' ? 'justify-end' : 'justify-start']"
      >
        <div
          :class="[
            'max-w-[85%] rounded-xl px-3 py-2 text-sm whitespace-pre-wrap break-words',
            m.role === 'user'
              ? 'bg-[rgb(var(--color-primary))] text-white'
              : m.error
                ? 'bg-red-500/15'
                : 'bg-[rgb(var(--color-surface-hover))]',
          ]"
        >
          <div v-if="m.tool" class="mb-1 flex items-center gap-1 opacity-70 text-xs">
            <NIcon size="12">
              <SparklesOutline />
            </NIcon>
            {{ t('ai.callingTool') }}: {{ m.tool }}
          </div>
          <span v-if="m.text">{{ m.text }}</span>
          <div v-else-if="m.pending" class="flex items-center gap-2 opacity-70">
            <NSpin size="small" />
            <span class="text-xs">{{ t('ai.thinking') }}</span>
          </div>
          <NButton
            v-if="m.action"
            size="tiny"
            type="primary"
            secondary
            class="mt-2"
            @click="openAction(m.action)"
          >
            <template #icon>
              <NIcon>
                <OpenOutline />
              </NIcon>
            </template>
            {{ t('ai.viewResult') }}
          </NButton>
        </div>
      </div>
    </NScrollbar>
    <div class="p-3 border-t border-[rgb(var(--color-border))] flex gap-2 items-end">
      <NInput
        v-model:value="input"
        :placeholder="t('ai.placeholder')"
        :disabled="streaming"
        type="textarea"
        :autosize="{ minRows: 1, maxRows: 4 }"
        @keydown.enter.exact.prevent="submit"
      />
      <NButton type="primary" :loading="streaming" :disabled="!input.trim()" @click="submit">
        <template #icon>
          <NIcon>
            <SendOutline />
          </NIcon>
        </template>
      </NButton>
    </div>
  </div>
</template>
