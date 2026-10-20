<template>
  <div v-if="displayText" class="flex flex-col gap-[4px]">
    <div class="text-[12px] text-[var(--color-text-3)]">{{ t('apiTestGrpc.protoVersion') }}</div>
    <div class="text-[13px]">{{ displayText }}</div>
    <a-alert v-if="showOutdatedWarning" type="warning" class="mt-[4px]">
      {{ t('apiTestGrpc.protoVersionOutdatedTip') }}
    </a-alert>
    <grpcProtoChangeImpactPanel :impact="impact" />
    <div v-if="historyItems.length > 1" class="mt-[4px] text-[12px] text-[var(--color-text-3)]">
      {{ t('apiTestGrpc.protoVersionHistory', { count: historyItems.length }) }}
    </div>
  </div>
</template>

<script setup lang="ts">
  import grpcProtoChangeImpactPanel from './grpcProtoChangeImpactPanel.vue';

  import { useI18n } from '@/hooks/useI18n';

  import type { GrpcProtoChangeImpact, GrpcProtoVersionInfo } from '@/models/apiTest/grpc';

  import { formatGrpcProtoVersionLabel } from './utils';

  const props = defineProps<{
    version?: GrpcProtoVersionInfo;
    protoId?: string;
    history?: GrpcProtoVersionInfo[];
    outdated?: boolean;
    impact?: GrpcProtoChangeImpact;
  }>();

  const { t } = useI18n();

  const displayText = computed(() => formatGrpcProtoVersionLabel(props.version, props.protoId));
  const historyItems = computed(() => props.history || []);
  const showOutdatedWarning = computed(() => props.outdated === true);
</script>
