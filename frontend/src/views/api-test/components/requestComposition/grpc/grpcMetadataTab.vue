<template>
  <div class="grpc-metadata-tab flex flex-col gap-[8px]">
    <div class="font-medium">Metadata</div>
    <div class="text-[12px] text-[var(--color-text-3)]">{{ t('apiTestGrpc.metadataTip') }}</div>
    <httpHeader v-model:params="grpcState.metadata" layout="vertical" @change="emitChange" />
  </div>
</template>

<script setup lang="ts">
  import { useVModel } from '@vueuse/core';

  import httpHeader from '../header.vue';

  import { useI18n } from '@/hooks/useI18n';

  import type { GrpcRequestState } from '@/models/apiTest/grpc';

  const props = defineProps<{ grpc: GrpcRequestState }>();
  const emit = defineEmits<{ (e: 'update:grpc', value: GrpcRequestState): void; (e: 'change'): void }>();
  const { t } = useI18n();
  const grpcState = useVModel(props, 'grpc', emit);
  function emitChange() {
    emit('change');
  }
</script>
