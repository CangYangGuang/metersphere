<template>
  <a-collapse v-model:active-key="activeDetailKey" :bordered="false">
    <a-collapse-item key="request">
      <template #header>
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-[4px]">
            <div class="font-medium">{{ t('apiTestManagement.requestParams') }}</div>
            <div v-if="activeDetailKey.includes('request')" class="down-icon">
              <icon-down :size="10" class="block" />
            </div>
            <div v-else class="h-[16px] w-[16px] !rounded-full p-[4px]">
              <icon-right :size="10" class="block" />
            </div>
          </div>
        </div>
      </template>
      <div class="detail-collapse-item">
        <div class="detail-item">
          <div class="detail-item-title">
            <div class="detail-item-title-text">{{ t('apiTestGrpc.summary') }}</div>
          </div>
          <a-alert
            v-if="grpcDefinition?.methodType && grpcDefinition?.invokable === false"
            type="warning"
            class="mb-[8px]"
          >
            {{ t('apiTestGrpc.streamingNotSupported') }}
          </a-alert>
          <MsFormTable :columns="summaryColumns" :data="summaryData" :selectable="false" :scroll="{ x: '100%' }" />
          <div v-if="caseProtoVersionDisplay" class="mt-[8px] text-[13px]">
            <span class="text-[12px] text-[var(--color-text-3)]">{{ t('apiTestGrpc.protoVersion') }}: </span>
            {{ caseProtoVersionDisplay }}
          </div>
          <a-alert v-if="caseProtoVersionOutdated" type="warning" class="mt-[8px]">
            {{ t('apiTestGrpc.protoVersionOutdatedTip') }}
          </a-alert>
          <grpcProtoChangeImpactPanel :impact="caseConfig?.protoChangeImpact" />
          <a-divider type="dashed" :margin="0" class="!mt-[16px] border-[var(--color-text-n8)]" />
        </div>
        <div v-if="caseConfig?.requestJson != null" class="detail-item">
          <div class="detail-item-title">
            <div class="detail-item-title-text">{{ t('apiTestGrpc.requestJson') }}</div>
            <div class="detail-item-title-right">
              <a-button
                type="outline"
                class="arco-btn-outline--secondary p-[0_8px]"
                size="small"
                @click="copyScript(caseConfig.requestJson || '')"
              >
                <template #icon>
                  <MsIcon type="icon-icon_copy_outlined" class="text-var(--color-text-4)" />
                </template>
                {{ t('common.copy') }}
              </a-button>
            </div>
          </div>
          <MsCodeEditor
            :model-value="requestJsonDisplay"
            theme="vs"
            :language="LanguageEnum.JSON"
            is-adaptive
            :show-full-screen="false"
            :show-theme-change="false"
            read-only
          />
          <a-divider type="dashed" :margin="0" class="!mt-[16px] border-[var(--color-text-n8)]" />
        </div>
        <div v-if="metadataData.length > 0" class="detail-item">
          <div class="detail-item-title">
            <div class="detail-item-title-text">Metadata</div>
          </div>
          <MsFormTable :columns="summaryColumns" :data="metadataData" :selectable="false" :scroll="{ x: '100%' }" />
          <a-divider type="dashed" :margin="0" class="!mt-[16px] border-[var(--color-text-n8)]" />
        </div>
        <div v-if="caseConfig?.timeout != null" class="detail-item">
          <div class="detail-item-title">
            <div class="detail-item-title-text">{{ t('apiTestGrpc.timeout') }}</div>
          </div>
          <div>{{ caseConfig.timeout }} ms</div>
          <a-divider type="dashed" :margin="0" class="!mt-[16px] border-[var(--color-text-n8)]" />
        </div>
        <div v-if="assertionData.length > 0" class="detail-item">
          <div class="detail-item-title">
            <div class="detail-item-title-text">{{ t('apiTestGrpc.assertionTitle') }}</div>
          </div>
          <MsFormTable :columns="assertionColumns" :data="assertionData" :selectable="false" :scroll="{ x: '100%' }" />
          <a-divider type="dashed" :margin="0" class="!mt-[16px] border-[var(--color-text-n8)]" />
        </div>
        <div v-if="extractData.length > 0" class="detail-item">
          <div class="detail-item-title">
            <div class="detail-item-title-text">{{ t('apiTestGrpc.extractTitle') }}</div>
          </div>
          <MsFormTable :columns="extractColumns" :data="extractData" :selectable="false" :scroll="{ x: '100%' }" />
        </div>
      </div>
    </a-collapse-item>
    <a-collapse-item key="response">
      <template #header>
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-[4px]">
            <div class="font-medium">{{ t('apiTestManagement.responseContent') }}</div>
            <div v-if="activeDetailKey.includes('response')" class="down-icon">
              <icon-down :size="10" class="block" />
            </div>
            <div v-else class="h-[16px] w-[16px] !rounded-full p-[4px]">
              <icon-right :size="10" class="block" />
            </div>
          </div>
        </div>
      </template>
      <div class="min-h-[240px] overflow-hidden rounded-[4px] border border-[var(--color-text-n8)]">
        <grpcResponsePanel :response="detail.grpc?.debugResponse" :loading="detail.executeLoading" />
      </div>
    </a-collapse-item>
  </a-collapse>
</template>

<script setup lang="ts">
  import { useClipboard } from '@vueuse/core';
  import { Message } from '@arco-design/web-vue';
  import { cloneDeep } from 'lodash-es';

  import MsCodeEditor from '@/components/pure/ms-code-editor/index.vue';
  import { LanguageEnum } from '@/components/pure/ms-code-editor/types';
  import MsFormTable, { FormTableColumn } from '@/components/pure/ms-form-table/index.vue';
  import MsIcon from '@/components/pure/ms-icon-font/index.vue';
  import grpcProtoChangeImpactPanel from '@/views/api-test/components/requestComposition/grpc/grpcProtoChangeImpactPanel.vue';
  import grpcResponsePanel from '@/views/api-test/components/requestComposition/grpc/grpcResponsePanel.vue';

  import { useI18n } from '@/hooks/useI18n';

  import type { GrpcCaseConfigDetail, GrpcDefinitionDetail } from '@/models/apiTest/grpc';

  import {
    formatGrpcProtoVersionLabel,
    mapGrpcConditionFromBackend,
  } from '@/views/api-test/components/requestComposition/grpc/utils';
  import type { RequestParam } from '@/views/api-test/components/requestComposition/index.vue';

  const props = defineProps<{
    detail: RequestParam;
  }>();

  const { t } = useI18n();
  const { copy, isSupported } = useClipboard({ legacy: true });

  const activeDetailKey = ref(['request', 'response']);
  const detail = ref<RequestParam>(cloneDeep(props.detail));

  const grpcDefinition = computed<GrpcDefinitionDetail | undefined>(() => detail.value.grpcDefinition);
  const caseConfig = computed<GrpcCaseConfigDetail | undefined>(() => {
    if (detail.value.grpcCaseConfig) {
      return detail.value.grpcCaseConfig;
    }
    if (detail.value.grpc) {
      return {
        serviceName: detail.value.grpc.serviceName,
        methodName: detail.value.grpc.methodName,
        requestJson: detail.value.grpc.requestJson,
        metadata: detail.value.grpc.metadata
          ?.filter((item) => item.enable && item.key)
          .map((item) => ({ key: item.key, value: item.value })),
        timeout: detail.value.grpc.timeout,
        assertionConfig: detail.value.grpc.assertionConfig,
        extractConfig: detail.value.grpc.extractConfig,
      };
    }
    return undefined;
  });

  const summaryColumns: FormTableColumn[] = [
    { title: 'apiTestManagement.paramName', dataIndex: 'key', inputType: 'text', width: 220 },
    { title: 'apiTestManagement.paramVal', dataIndex: 'value', inputType: 'text' },
  ];

  const summaryData = computed(() => {
    const def = grpcDefinition.value;
    const config = caseConfig.value;
    return [
      { key: 'Service', value: config?.serviceName || def?.serviceName || '-' },
      { key: 'Method', value: config?.methodName || def?.methodName || '-' },
      { key: t('apiTestGrpc.methodType'), value: def?.methodType || '-' },
      { key: t('apiTestGrpc.fullMethodName'), value: def?.fullMethodName || '-' },
    ];
  });

  const caseProtoVersionDisplay = computed(() =>
    formatGrpcProtoVersionLabel(caseConfig.value?.protoVersion, caseConfig.value?.protoId)
  );

  const caseProtoVersionOutdated = computed(() => caseConfig.value?.protoVersionOutdated === true);

  const metadataData = computed(() =>
    (caseConfig.value?.metadata || []).map((item) => ({
      key: item.key,
      value: item.value,
    }))
  );

  const assertionColumns: FormTableColumn[] = [
    { title: 'apiTestGrpc.assertionType', dataIndex: 'type', inputType: 'text', width: 120 },
    { title: 'apiTestGrpc.assertionName', dataIndex: 'name', inputType: 'text', width: 140 },
    { title: 'apiTestGrpc.jsonPathExpression', dataIndex: 'expression', inputType: 'text' },
    { title: 'apiTestGrpc.assertionCondition', dataIndex: 'condition', inputType: 'text', width: 120 },
    { title: 'apiTestGrpc.assertionExpected', dataIndex: 'expectedValue', inputType: 'text', width: 140 },
  ];

  const extractColumns: FormTableColumn[] = [
    { title: 'apiTestGrpc.extractName', dataIndex: 'variableName', inputType: 'text', width: 160 },
    { title: 'apiTestGrpc.jsonPathExpression', dataIndex: 'expression', inputType: 'text' },
  ];

  const assertionData = computed(() =>
    (caseConfig.value?.assertionConfig?.items || [])
      .filter((item) => item.enable !== false)
      .map((item) => ({
        type: item.type,
        name: item.name || '-',
        expression: item.expression || '-',
        condition: mapGrpcConditionFromBackend(item.condition as string),
        expectedValue: item.expectedValue || '-',
      }))
  );

  const extractData = computed(() =>
    (caseConfig.value?.extractConfig?.items || [])
      .filter((item) => item.enable !== false)
      .map((item) => ({
        variableName: item.variableName,
        expression: item.expression,
      }))
  );

  const requestJsonDisplay = computed(() => {
    const raw = caseConfig.value?.requestJson;
    if (!raw) {
      return '';
    }
    try {
      return JSON.stringify(JSON.parse(raw), null, 2);
    } catch {
      return raw;
    }
  });

  function copyScript(value: string) {
    if (!isSupported) {
      Message.error(t('common.copyNotSupport'));
      return;
    }
    copy(value);
    Message.success(t('common.copySuccess'));
  }

  watch(
    () => props.detail,
    () => {
      detail.value = cloneDeep(props.detail);
    },
    { deep: true }
  );
</script>

<style scoped lang="less">
  .detail-item {
    padding-top: 16px;
    &-title {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 8px;
      &-text {
        font-weight: 500;
      }
    }
  }
  .down-icon {
    display: flex;
    justify-content: center;
    align-items: center;
    padding: 4px;
    width: 16px;
    height: 16px;
    border-radius: 9999px;
    background-color: var(--color-text-n8);
  }
</style>
