<template>
  <div class="grpc-response-panel flex h-full flex-col overflow-hidden border-l border-[var(--color-text-n8)] bg-[var(--color-bg-2)]">
    <div class="border-b border-[var(--color-text-n8)] px-[16px] py-[8px]">
      <div class="mb-[8px] flex items-center justify-between">
        <div class="font-medium">{{ t('apiTestGrpc.responseResult') }}</div>
        <a-spin v-if="loading" :size="16" />
      </div>
      <div v-if="response" class="flex flex-wrap items-center gap-[8px] text-[12px]">
        <a-tag :color="response.success ? 'green' : 'red'">{{ response.success ? t('apiTestGrpc.success') : t('apiTestGrpc.failed') }}</a-tag>
        <span>gRPC Status: {{ response.grpcStatus || '-' }}</span>
        <span>gRPC Code: {{ response.grpcCode ?? '-' }}</span>
        <span>{{ t('apiTestGrpc.duration') }}: {{ response.duration != null ? `${response.duration} ms` : '-' }}</span>
        <span>{{ t('apiTestGrpc.responseSize') }}: {{ responseSize }}</span>
      </div>
    </div>
    <div v-if="loading" class="flex flex-1 items-center justify-center text-[var(--color-text-3)]">{{ t('apiTestGrpc.sending') }}</div>
    <div v-else-if="!response" class="flex flex-1 items-center justify-center text-[var(--color-text-3)]">{{ t('apiTestGrpc.noResponseServerExec') }}</div>
    <div v-else class="flex min-h-0 flex-1 flex-col">
      <a-tabs v-model:active-key="activeTab" class="flex min-h-0 flex-1 flex-col px-[16px] pt-[8px]">
        <a-tab-pane key="body" :title="t('apiTestGrpc.responseBodyTab')">
          <div class="overflow-auto py-[8px]">
            <MsCodeEditor v-if="formattedResponseJson" :model-value="formattedResponseJson" theme="vs" :language="LanguageEnum.JSON" is-adaptive :show-full-screen="false" :show-theme-change="false" read-only />
            <div v-else class="text-[var(--color-text-3)]">-</div>
          </div>
        </a-tab-pane>
        <a-tab-pane key="metadata" :title="t('apiTestGrpc.responseMetadataTab')">
          <div class="py-[8px] text-[13px]">
            <div v-if="!metadataRows.length" class="text-[var(--color-text-3)]">{{ t('apiTestGrpc.responseMetadataEmpty') }}</div>
            <div v-for="row in metadataRows" :key="row.key" class="flex gap-[8px] border-b border-[var(--color-text-n8)] py-[6px]">
              <span class="min-w-[120px] text-[var(--color-text-3)]">{{ row.key }}</span>
              <span class="break-all">{{ row.value }}</span>
            </div>
          </div>
        </a-tab-pane>
        <a-tab-pane key="error" :title="t('apiTestGrpc.responseErrorTab')">
          <div class="py-[8px]">
            <a-alert v-if="response.errorMessage || response.success === false" type="error">{{ response.errorMessage || debugSummary }}</a-alert>
            <div v-else class="text-[var(--color-text-3)]">-</div>
          </div>
        </a-tab-pane>
        <a-tab-pane key="raw" :title="t('apiTestGrpc.responseRawTab')">
          <MsCodeEditor v-if="rawJson" :model-value="rawJson" theme="vs" :language="LanguageEnum.JSON" is-adaptive :show-full-screen="false" :show-theme-change="false" read-only />
        </a-tab-pane>
      </a-tabs>
    </div>
  </div>
</template>

<script setup lang="ts">
  import MsCodeEditor from '@/components/pure/ms-code-editor/index.vue';
  import { LanguageEnum } from '@/components/pure/ms-code-editor/types';
  import { useI18n } from '@/hooks/useI18n';
  import type { GrpcInvokeResult } from '@/models/apiTest/grpc';
  import { calcGrpcResponseSize, formatGrpcDebugSummary } from './utils';
  const props = defineProps<{ response?: GrpcInvokeResult; loading?: boolean }>();
  const { t } = useI18n();
  const activeTab = ref('body');
  const debugSummary = computed(() => formatGrpcDebugSummary(props.response));
  const responseSize = computed(() => calcGrpcResponseSize(props.response));
  const formattedResponseJson = computed(() => {
    if (!props.response?.responseJson) return '';
    try { return JSON.stringify(JSON.parse(props.response.responseJson), null, 2); } catch { return props.response.responseJson; }
  });
  const rawJson = computed(() => (props.response ? JSON.stringify(props.response, null, 2) : ''));
  const metadataRows = computed(() => {
    const md = (props.response as any)?.responseMetadata ?? (props.response as any)?.metadata;
    if (!md) return [] as { key: string; value: string }[];
    if (Array.isArray(md)) return md.map((item: any) => ({ key: String(item.key ?? item.name ?? '-'), value: String(item.value ?? '') }));
    return Object.entries(md as Record<string, string>).map(([key, value]) => ({ key, value: String(value) }));
  });
</script>
