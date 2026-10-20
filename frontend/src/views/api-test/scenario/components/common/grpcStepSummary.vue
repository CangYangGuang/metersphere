<template>
  <a-popover position="bl" content-class="grpc-step-summary-popover" @popup-visible-change="handleVisibleChange">
    <span class="one-line-text max-w-[220px] text-[12px] text-[var(--color-text-3)]">{{ inlineSummary }}</span>
    <template #content>
      <a-spin class="w-full" :loading="loading">
        <div class="flex flex-col gap-[12px] text-[13px]">
          <div>
            <div class="mb-[4px] text-[var(--color-text-4)]">{{ t('apiScenario.grpc.serviceMethod') }}</div>
            <div>{{ serviceMethodText }}</div>
          </div>
          <div>
            <div class="mb-[4px] text-[var(--color-text-4)]">{{ t('apiScenario.grpc.requestJson') }}</div>
            <div class="break-all text-[var(--color-text-2)]">{{ requestJsonText }}</div>
          </div>
          <div>
            <div class="mb-[4px] text-[var(--color-text-4)]">{{ t('apiScenario.grpc.metadata') }}</div>
            <div class="break-all text-[var(--color-text-2)]">{{ metadataText }}</div>
          </div>
          <div>
            <div class="mb-[4px] text-[var(--color-text-4)]">{{ t('apiScenario.grpc.timeout') }}</div>
            <div>{{ timeoutText }}</div>
          </div>
        </div>
      </a-spin>
    </template>
  </a-popover>
</template>

<script setup lang="ts">
  import { getCaseDetail } from '@/api/modules/api-test/management';
  import { useI18n } from '@/hooks/useI18n';

  import type { ApiCaseDetail } from '@/models/apiTest/management';
  import type { ScenarioStepItem } from '@/models/apiTest/scenario';

  const props = defineProps<{
    step: ScenarioStepItem;
  }>();

  const { t } = useI18n();
  const loading = ref(false);
  const caseDetail = ref<ApiCaseDetail>();

  const serviceMethodText = computed(() => {
    const config = caseDetail.value?.grpcCaseConfig;
    if (!config?.serviceName && !config?.methodName) {
      return '-';
    }
    return `${config?.serviceName || '-'} / ${config?.methodName || '-'}`;
  });

  const inlineSummary = computed(() => serviceMethodText.value);

  const requestJsonText = computed(() => {
    const json = caseDetail.value?.grpcCaseConfig?.requestJson;
    return json?.trim() ? json : '-';
  });

  const metadataText = computed(() => {
    const metadata = caseDetail.value?.grpcCaseConfig?.metadata;
    if (!metadata?.length) {
      return '-';
    }
    return metadata.map((item) => `${item.key}: ${item.value}`).join('; ');
  });

  const timeoutText = computed(() => {
    const timeout = caseDetail.value?.grpcCaseConfig?.timeout;
    return timeout != null ? `${timeout} ms` : '-';
  });

  async function handleVisibleChange(visible: boolean) {
    if (!visible || !props.step.resourceId || caseDetail.value) {
      return;
    }
    try {
      loading.value = true;
      caseDetail.value = await getCaseDetail(props.step.resourceId);
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log(error);
    } finally {
      loading.value = false;
    }
  }
</script>

<style lang="less">
  .grpc-step-summary-popover {
    width: 360px;
  }
</style>
