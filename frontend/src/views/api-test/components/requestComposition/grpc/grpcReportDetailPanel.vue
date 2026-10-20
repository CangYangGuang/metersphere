<template>
  <div class="grpc-report-detail flex h-full flex-col overflow-hidden">
    <div class="flex w-full items-center justify-between rounded bg-[var(--color-text-n9)] px-[16px] py-[8px]">
      <div class="font-medium text-[rgb(var(--primary-5))]">{{ t('apiTestGrpc.summary') }}</div>
      <div class="text-[rgb(var(--success-7))]">
        {{ content?.duration != null ? `${content.duration} ms` : '-' }}
      </div>
    </div>
    <div class="flex-1 overflow-auto p-[16px]">
      <div class="mb-[16px] grid grid-cols-2 gap-[12px] md:grid-cols-4">
        <div>
          <div class="text-[12px] text-[var(--color-text-3)]">{{ t('apiTestGrpc.service') }}</div>
          <div>{{ content?.serviceName || '-' }}</div>
        </div>
        <div>
          <div class="text-[12px] text-[var(--color-text-3)]">{{ t('apiTestGrpc.method') }}</div>
          <div>{{ content?.methodName || '-' }}</div>
        </div>
        <div>
          <div class="text-[12px] text-[var(--color-text-3)]">gRPC Status</div>
          <div>{{ content?.grpcStatus || '-' }}</div>
        </div>
        <div>
          <div class="text-[12px] text-[var(--color-text-3)]">gRPC Code</div>
          <div>{{ content?.grpcCode ?? '-' }}</div>
        </div>
      </div>
      <div v-if="content?.requestJson" class="mb-[16px]">
        <div class="mb-[8px] font-medium">{{ t('apiTestGrpc.requestJson') }}</div>
        <MsCodeEditor
          :model-value="formattedRequestJson"
          theme="vs"
          :language="LanguageEnum.JSON"
          is-adaptive
          :show-full-screen="false"
          :show-theme-change="false"
          read-only
        />
      </div>
      <GrpcResponsePanel :response="invokeResult" :loading="loading" />
    </div>
  </div>
</template>

<script setup lang="ts">
  import MsCodeEditor from '@/components/pure/ms-code-editor/index.vue';
  import { LanguageEnum } from '@/components/pure/ms-code-editor/types';
  import GrpcResponsePanel from './grpcResponsePanel.vue';

  import { useI18n } from '@/hooks/useI18n';

  import type { GrpcCaseReportContent } from '@/models/apiTest/grpc';

  import { formatGrpcReportRequestJson, mapGrpcReportContentToInvokeResult } from './utils';

  const props = defineProps<{
    content?: GrpcCaseReportContent | null;
    loading?: boolean;
  }>();

  const { t } = useI18n();

  const invokeResult = computed(() => mapGrpcReportContentToInvokeResult(props.content));
  const formattedRequestJson = computed(() => formatGrpcReportRequestJson(props.content?.requestJson));
</script>
