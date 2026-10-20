<template>
  <div v-if="visible" class="mt-[8px] flex flex-col gap-[8px]">
    <div class="text-[12px] text-[var(--color-text-3)]">{{ t('apiTestGrpc.protoChangeImpactTitle') }}</div>
    <a-alert :type="alertType">
      <div class="flex flex-col gap-[4px] text-[13px]">
        <div>{{ impactLabel }}: {{ impact?.impactSummary || '-' }}</div>
        <div v-if="impact?.changedServices?.length">
          {{ t('apiTestGrpc.protoChangeImpactChangedServices') }}: {{ impact.changedServices.join('; ') }}
        </div>
        <div v-if="impact?.changedMethods?.length">
          {{ t('apiTestGrpc.protoChangeImpactChangedMethods') }}: {{ impact.changedMethods.join('; ') }}
        </div>
        <div v-if="messageSummaries.length">
          {{ t('apiTestGrpc.protoChangeImpactChangedFields') }}: {{ messageSummaries.join('; ') }}
        </div>
        <div v-if="impact?.affectedDefinitions?.length">
          {{ t('apiTestGrpc.protoChangeImpactAffectedDefinitions') }}: {{ impact.affectedDefinitions.length }}
        </div>
        <div v-if="impact?.affectedCases?.length">
          {{ t('apiTestGrpc.protoChangeImpactAffectedCases') }}: {{ impact.affectedCases.length }}
        </div>
      </div>
    </a-alert>
  </div>
</template>

<script setup lang="ts">
  import { useI18n } from '@/hooks/useI18n';

  import type { GrpcProtoChangeImpact } from '@/models/apiTest/grpc';

  const props = defineProps<{
    impact?: GrpcProtoChangeImpact;
  }>();

  const { t } = useI18n();

  const visible = computed(() => !!props.impact?.impactLevel && props.impact.impactLevel !== 'NONE');

  const alertType = computed(() => {
    switch (props.impact?.impactLevel) {
      case 'BREAKING':
        return 'error';
      case 'WARNING':
        return 'warning';
      case 'INFO':
        return 'info';
      default:
        return 'info';
    }
  });

  const impactLabel = computed(() => {
    switch (props.impact?.impactLevel) {
      case 'BREAKING':
        return t('apiTestGrpc.protoChangeImpactBreaking');
      case 'WARNING':
        return t('apiTestGrpc.protoChangeImpactWarning');
      case 'INFO':
        return t('apiTestGrpc.protoChangeImpactInfo');
      default:
        return props.impact?.impactLevel || '-';
    }
  });

  const messageSummaries = computed(() =>
    (props.impact?.changedMessages || [])
      .map((item) => item.detail || `${item.messageName || ''}.${item.fieldName || ''} (${item.changeType || ''})`)
      .filter(Boolean)
  );
</script>
