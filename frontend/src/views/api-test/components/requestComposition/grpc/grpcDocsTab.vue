<template>
  <div class="grpc-docs-tab flex flex-col gap-[16px]">
    <a-alert v-if="!currentMethod" type="info">{{ t('apiTestGrpc.docsNoMethod') }}</a-alert>
    <template v-else>
      <a-descriptions :column="1" size="medium" bordered>
        <a-descriptions-item :label="t('apiTestGrpc.docsServiceMethod')">
          <span class="break-all">{{ serviceLabel }} / {{ currentMethod.name }}</span>
        </a-descriptions-item>
        <a-descriptions-item :label="t('apiTestGrpc.docsMethodType')">
          <a-tag v-if="currentMethod.methodType" size="small">{{ currentMethod.methodType }}</a-tag>
          <span v-else>-</span>
        </a-descriptions-item>
        <a-descriptions-item :label="t('apiTestGrpc.docsMethodDescription')">
          <span class="whitespace-pre-line break-all">{{ methodDescription }}</span>
        </a-descriptions-item>
        <a-descriptions-item :label="t('apiTestGrpc.docsRequestMessage')">
          <span class="break-all">{{ currentMethod.inputType || '-' }}</span>
        </a-descriptions-item>
        <a-descriptions-item :label="t('apiTestGrpc.docsResponseMessage')">
          <span class="break-all">{{ currentMethod.outputType || '-' }}</span>
        </a-descriptions-item>
      </a-descriptions>

      <div class="flex flex-col gap-[8px]">
        <div class="font-medium">{{ t('apiTestGrpc.docsRequestFields') }}</div>
        <a-table
          v-if="requestFields.length"
          :columns="fieldColumns"
          :data="requestFields"
          :pagination="false"
          size="small"
          :bordered="{ cell: true }"
        />
        <div v-else class="text-[12px] text-[var(--color-text-3)]">{{
          t('apiTestGrpc.docsRequestFieldsUnavailable')
        }}</div>
        <div class="text-[12px] text-[var(--color-text-3)]">{{ t('apiTestGrpc.docsFieldsFromTemplate') }}</div>
      </div>

      <div class="flex flex-col gap-[8px]">
        <div class="font-medium">{{ t('apiTestGrpc.docsResponseFields') }}</div>
        <div class="text-[12px] text-[var(--color-text-3)]">{{ t('apiTestGrpc.docsResponseFieldsUnavailable') }}</div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
  import { useI18n } from '@/hooks/useI18n';

  import type { GrpcMethodInfo, GrpcRequestState } from '@/models/apiTest/grpc';

  import { findGrpcMethodTemplate } from './utils';

  const props = defineProps<{ grpc: GrpcRequestState }>();
  const { t } = useI18n();

  interface DocsField {
    field: string;
    type: string;
    description: string;
  }

  const currentService = computed(() =>
    (props.grpc.services || []).find((service) => service.name === props.grpc.serviceName)
  );

  const serviceLabel = computed(() => currentService.value?.fullName || props.grpc.serviceName || '-');

  const currentMethod = computed<GrpcMethodInfo | undefined>(() =>
    currentService.value?.methods?.find((method) => method.name === props.grpc.methodName)
  );

  // parse 结果暂不返回方法注释，统一显示 '-'，待后端补充 description
  const methodDescription = computed(() => '-');

  const fieldColumns = computed(() => [
    { title: t('apiTestGrpc.docsFieldName'), dataIndex: 'field' },
    { title: t('apiTestGrpc.docsFieldType'), dataIndex: 'type', width: 160 },
    { title: t('apiTestGrpc.docsFieldDescription'), dataIndex: 'description', width: 120 },
  ]);

  function inferType(value: unknown): string {
    if (value === null || value === undefined) {
      return '-';
    }
    if (Array.isArray(value)) {
      return 'repeated';
    }
    if (typeof value === 'object') {
      return 'message';
    }
    return typeof value;
  }

  // 请求字段：从当前 method 的 requestJsonTemplate 推断（后端 parse 结果未提供字段级 schema）
  const requestFields = computed<DocsField[]>(() => {
    const template = currentMethod.value ? findGrpcMethodTemplate(props.grpc) : undefined;
    if (!template) {
      return [];
    }
    try {
      const parsed = JSON.parse(template);
      if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
        return [];
      }
      return Object.entries(parsed as Record<string, unknown>).map(([field, value]) => ({
        field,
        type: inferType(value),
        description: '-',
      }));
    } catch {
      return [];
    }
  });
</script>
