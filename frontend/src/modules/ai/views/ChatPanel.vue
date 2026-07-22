<script setup lang="ts">
import { ref, nextTick, computed, watch } from 'vue'
import { NInput, NButton, NIcon, NScrollbar, NSpin } from 'naive-ui'
import {
  SendOutline, SparklesOutline, OpenOutline,
  PersonOutline, ShieldCheckmarkOutline, NavigateOutline,
} from '@vicons/ionicons5'
import { useAiChat, type ChatMessage } from '@/modules/ai/composables/useAiChat'
import type { AiActionEvent } from '@/modules/ai/api/ai'
import { useI18n } from 'vue-i18n'

const emit = defineEmits<{ (e: 'action', payload: AiActionEvent): void }>()
const { t } = useI18n()
const { messages, streaming, send, confirmExecute, confirmCancel } = useAiChat()
const input = ref('')
const scrollbarRef = ref<InstanceType<typeof NScrollbar> | null>(null)

const hasHistory = computed(() => messages.value.length > 0)

const capabilities = computed(() => [
  { icon: PersonOutline, title: t('ai.capUserMgmt'), desc: t('ai.capUserMgmtDesc') },
  { icon: ShieldCheckmarkOutline, title: t('ai.capRoleMgmt'), desc: t('ai.capRoleMgmtDesc') },
  { icon: NavigateOutline, title: t('ai.capNavigation'), desc: t('ai.capNavigationDesc') },
])

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
  if (a) emit('action', a)
}

async function onConfirmExecute(m: ChatMessage): Promise<void> {
  if (!m.confirm) return
  await confirmExecute(m, m.confirm, (a) => emit('action', a))
  await scrollToBottom()
}

function runExample(text: string): void {
  input.value = text
  void submit()
}


watch(messages, scrollToBottom, { deep: true })
</script>

<template>
  <div class="flex flex-col h-full">
    <!-- 消息区 -->
    <NScrollbar ref="scrollbarRef" class="flex-1 px-3 py-3 min-h-0">
      <!-- 空状态：能力引导 -->
      <div v-if="!hasHistory" class="px-2 py-4">
        <div class="text-center mb-4">
          <NIcon size="28" class="opacity-80">
            <SparklesOutline />
          </NIcon>
          <p class="text-sm mt-2 opacity-80">{{ t('ai.welcome') }}</p>
        </div>
        <div class="text-xs font-medium opacity-60 mb-2 px-1">{{ t('ai.capabilities') }}：</div>
        <div class="flex flex-col gap-2 mb-4">
          <div
            v-for="cap in capabilities"
            :key="cap.title"
            class="flex items-start gap-2 rounded-lg bg-[rgb(var(--color-surface-hover))] px-3 py-2"
          >
            <NIcon size="16" class="mt-0.5 opacity-70 shrink-0">
              <component :is="cap.icon" />
            </NIcon>
            <div>
              <div class="text-sm font-medium">{{ cap.title }}</div>
              <div class="text-xs opacity-60">{{ cap.desc }}</div>
            </div>
          </div>
        </div>
        <div class="flex flex-col gap-1.5">
          <NButton size="small" secondary block @click="runExample(t('ai.exampleList'))">
            {{ t('ai.exampleList') }}
          </NButton>
          <NButton size="small" secondary block @click="runExample(t('ai.exampleCreate'))">
            {{ t('ai.exampleCreate') }}
          </NButton>
          <NButton size="small" secondary block @click="runExample(t('ai.exampleDelete'))">
            {{ t('ai.exampleDelete') }}
          </NButton>
        </div>
      </div>

      <!-- 消息列表 -->
      <div
        v-for="(m, i) in messages"
        :key="i"
        :class="['mb-2.5 flex flex-col', m.role === 'user' ? 'items-end' : 'items-start']"
      >
        <div
          :class="[
            'max-w-[88%] rounded-2xl px-3 py-2 text-sm whitespace-pre-wrap break-words leading-relaxed',
            m.role === 'user'
              ? 'bg-[rgb(var(--color-primary))] text-white rounded-br-md'
              : m.error
                ? 'bg-red-500/15 rounded-bl-md'
                : 'bg-[rgb(var(--color-surface-hover))] rounded-bl-md',
          ]"
        >
          <div v-if="m.tool" class="mb-1 flex items-center gap-1 opacity-70 text-xs">
            <NIcon size="12"><SparklesOutline /></NIcon>
            {{ t('ai.callingTool') }}: {{ m.tool }}
          </div>
          <span v-if="m.text">{{ m.text }}</span>
          <div v-else-if="m.pending" class="flex items-center gap-2 opacity-70 py-0.5">
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
            <template #icon><NIcon><OpenOutline /></NIcon></template>
            {{ t('ai.viewResult') }}
          </NButton>
          <!-- 破坏性工具二次确认 -->
          <div
            v-if="m.confirm && m.confirmState === 'pending'"
            class="mt-2 rounded-lg bg-amber-500/10 px-2.5 py-2"
          >
            <div class="text-xs mb-2">{{ m.confirm.message }}</div>
            <div class="flex gap-2">
              <NButton size="tiny" type="error" :loading="streaming" :disabled="streaming" @click="onConfirmExecute(m)">
                {{ t('ai.confirmExecute') }}
              </NButton>
              <NButton size="tiny" quaternary :disabled="streaming" @click="confirmCancel(m)">
                {{ t('ai.confirmCancel') }}
              </NButton>
            </div>
          </div>
          <div v-else-if="m.confirm && m.confirmState === 'executed'" class="mt-1 text-xs opacity-50">
            {{ t('ai.confirmExecuted') }}
          </div>
          <div v-else-if="m.confirm && m.confirmState === 'cancelled'" class="mt-1 text-xs opacity-50">
            {{ t('ai.confirmCancelled') }}
          </div>
        </div>
      </div>
    </NScrollbar>

    <!-- 输入区 -->
    <div class="px-3 py-2.5 border-t border-[rgb(var(--color-border))]">
      <div class="flex gap-2 items-end">
        <NInput
          v-model:value="input"
          :placeholder="t('ai.placeholder')"
          :disabled="streaming"
          type="textarea"
          :autosize="{ minRows: 1, maxRows: 3 }"
          @keydown.enter.exact.prevent="submit"
        />
        <NButton type="primary" circle :loading="streaming" :disabled="!input.trim()" @click="submit">
          <template #icon><NIcon><SendOutline /></NIcon></template>
        </NButton>
      </div>
    </div>
  </div>
</template>
