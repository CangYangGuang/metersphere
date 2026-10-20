<template>
  <div class="grpc-scenario-response flex h-full flex-col overflow-hidden">
    <a-alert v-if="failureReason" type="error" class="mb-[12px]">
      <div><span class="font-medium">错误码：</span>{{ displayErrorCode }}</div>
      <div><span class="font-medium">失败原因：</span>{{ failureReason }}</div>
    </a-alert>
    <div class="mb-[12px] grid grid-cols-2 gap-[12px]" :class="{ 'md:grid-cols-4': showDetailFields }">
      <div v-if="showDetailFields">
        <div class="text-[12px] text-[var(--color-text-3)]">{{ t('apiTestGrpc.service') }}</div>
        <div>{{ serviceName }}</div>
      </div>
      <div v-if="showDetailFields">
        <div class="text-[12px] text-[var(--color-text-3)]">{{ t('apiTestGrpc.method') }}</div>
        <div>{{ methodName }}</div>
      </div>
      <div>
        <div class="text-[12px] text-[var(--color-text-3)]">gRPC Status</div>
        <div>{{ grpcStatus }}</div>
      </div>
      <div>
        <div class="text-[12px] text-[var(--color-text-3)]">gRPC Code</div>
        <div>{{ grpcCode }}</div>
      </div>
    </div>
    <div v-if="showDetailFields && requestJson" class="mb-[12px]">
      <div class="mb-[8px] font-medium">{{ t('apiTestGrpc.requestJson') }}</div>
      <pre class="response-header-pre">{{ requestJson }}</pre>
    </div>
    <div v-if="assertionResults.length" class="mb-[12px]">
      <div class="mb-[8px] font-medium">{{ t('apiScenario.grpc.assertionResults') }}</div>
      <a-table :columns="assertionColumns" :data="assertionResults" :pagination="false" size="small">
        <template #pass="{ record }">
          <span :class="record.pass ? 'text-[rgb(var(--success-6))]' : 'text-[rgb(var(--danger-6))]'">
            {{ record.pass ? t('common.pass') : t('common.fail') }}
          </span>
        </template>
      </a-table>
    </div>
    <div v-if="extractResults.length" class="mb-[12px]">
      <div class="mb-[8px] font-medium">{{ t('apiScenario.grpc.extractResults') }}</div>
      <a-table :columns="extractColumns" :data="extractResults" :pagination="false" size="small" />
    </div>
    <div class="flex-1 overflow-auto">
      <div class="mb-[8px] font-medium">{{ t('apiTestGrpc.responseJson') }}</div>
      <pre class="response-header-pre">{{ responseJson }}</pre>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { useI18n } from '@/hooks/useI18n';

  import type { RequestResult } from '@/models/apiTest/common';
  import type { GrpcAssertionResult } from '@/models/apiTest/grpc';

  import { formatGrpcExecutionErrorMessage } from '@/views/api-test/components/requestComposition/grpc/utils';

  const props = defineProps<{
    requestResult?: RequestResult;
    showDetailFields?: boolean;
  }>();

  const { t } = useI18n();

  const grpcResult = computed(() => props.requestResult as (RequestResult & Record<string, any>) | undefined);
  const grpcResponse = computed(() => props.requestResult?.responseResult as Record<string, any> | undefined);
  const grpcStatus = computed(() => grpcResult.value?.grpcStatus || grpcResponse.value?.responseMessage || '-');
  const grpcCode = computed(() => grpcResult.value?.grpcCode ?? grpcResponse.value?.responseCode ?? '-');
  const requestJson = computed(() => grpcResult.value?.requestJson || props.requestResult?.body || '-');
  const responseJson = computed(
    () => grpcResult.value?.responseJson || props.requestResult?.responseResult?.body || '-'
  );
  const errorCode = computed(() => grpcResult.value?.errorCode || props.requestResult?.fakeErrorCode || '');
  const assertionResults = computed(
    () =>
      (grpcResult.value?.assertionResults ||
        props.requestResult?.responseResult?.assertions ||
        []) as GrpcAssertionResult[]
  );
  const failedAssertion = computed(() => assertionResults.value.find((item) => item?.pass === false));
  const displayErrorCode = computed(() => errorCode.value || (failedAssertion.value ? 'ASSERTION_FAILED' : '-'));
  function displayValue(value?: string) {
    return value === undefined || value === null || value === '' ? '（空）' : String(value);
  }
  const failureReason = computed(() => {
    if (failedAssertion.value) {
      const assertion = failedAssertion.value;
      return `${assertion.name || '断言'}未通过：期望 ${displayValue(assertion.expectedValue)}，实际 ${displayValue(
        assertion.actualValue
      )}${assertion.message ? `；${assertion.message}` : ''}`;
    }
    const message = formatGrpcExecutionErrorMessage(
      errorCode.value,
      grpcResult.value?.errorMessage || grpcResult.value?.fakeErrorMessage
    );
    if (message) {
      return message;
    }
    if (grpcResult.value?.success === false || props.requestResult?.isSuccessful === false) {
      return `${grpcStatus.value}（code ${grpcCode.value}）`;
    }
    return '';
  });
  function parseGrpcUrl(url?: string) {
    if (!url) {
      return { serviceName: '-', methodName: '-' };
    }
    const parts = url.split('/').filter(Boolean);
    if (parts.length < 2) {
      return { serviceName: '-', methodName: '-' };
    }
    return {
      serviceName: parts[parts.length - 2] || '-',
      methodName: parts[parts.length - 1] || '-',
    };
  }
  const serviceName = computed(
    () => grpcResult.value?.serviceName || parseGrpcUrl(props.requestResult?.url).serviceName
  );
  const methodName = computed(() => grpcResult.value?.methodName || parseGrpcUrl(props.requestResult?.url).methodName);
  const extractResults = computed(
    () => grpcResult.value?.extractResults || props.requestResult?.responseResult?.extractResults || []
  );

  const assertionColumns = [
    { title: t('apiScenario.grpc.assertionName'), dataIndex: 'name' },
    { title: t('apiScenario.grpc.assertionType'), dataIndex: 'assertionType' },
    { title: t('apiScenario.grpc.assertionCondition'), dataIndex: 'condition' },
    { title: t('apiScenario.grpc.assertionExpected'), dataIndex: 'expectedValue' },
    { title: t('apiScenario.grpc.assertionActual'), dataIndex: 'actualValue' },
    { title: t('apiScenario.grpc.assertionPass'), dataIndex: 'pass', slotName: 'pass' },
    { title: t('apiScenario.grpc.assertionMessage'), dataIndex: 'message' },
  ];

  const extractColumns = [
    { title: t('apiScenario.grpc.extractName'), dataIndex: 'name' },
    { title: t('apiScenario.grpc.extractValue'), dataIndex: 'value' },
  ];
</script>

<style lang="less" scoped>
  .response-header-pre {
    @apply h-full overflow-auto;

    padding: 8px 12px;
    border-radius: var(--border-radius-small);
    white-space: pre-wrap;
    background-color: var(--color-text-fff);
    word-break: break-all;
  }
</style>
