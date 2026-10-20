<template>
  <div class="grpc-settings-tab flex flex-col gap-[16px]">
    <div class="grid grid-cols-1 gap-[16px] md:grid-cols-2">
      <a-form-item :label="t('apiTestGrpc.timeout')" class="mb-0">
        <a-input-number v-model:model-value="grpcState.timeout" :min="1000" :step="1000" class="w-full" @change="handleTimeoutChange" />
        <div class="mt-[4px] text-[12px] text-[var(--color-text-3)]">{{ t('apiTestGrpc.timeoutTip', { value: DEFAULT_GRPC_TIMEOUT }) }}</div>
      </a-form-item>
      <a-form-item :label="t('apiTestGrpc.requestTls')" class="mb-0">
        <a-switch v-model:model-value="useTls" size="small" @change="emitChange">
          <template #checked>{{ t('apiTestGrpc.reflectionUseTls') }}</template>
          <template #unchecked>{{ t('apiTestGrpc.reflectionPlaintext') }}</template>
        </a-switch>
      </a-form-item>
    </div>
    <a-form-item :label="t('apiTestGrpc.authority')" class="mb-0">
      <a-input v-model:model-value="grpcState.authority" :max-length="255" :placeholder="t('apiTestGrpc.authorityPlaceholder')" allow-clear @change="emitChange" />
    </a-form-item>
  </div>
</template>
<script setup lang="ts">
  import { useVModel } from '@vueuse/core';
  import { useI18n } from '@/hooks/useI18n';
  import type { GrpcRequestState } from '@/models/apiTest/grpc';
  import { DEFAULT_GRPC_TIMEOUT } from './constants';
  const props = defineProps<{ grpc: GrpcRequestState }>();
  const emit = defineEmits<{ (e: 'update:grpc', value: GrpcRequestState): void; (e: 'change'): void }>();
  useI18n();
  const grpcState = useVModel(props, 'grpc', emit);
  const useTls = computed({ get: () => grpcState.value.useTls === true, set: (v: boolean) => { grpcState.value.useTls = v; } });
  function emitChange() { emit('change'); }
  function handleTimeoutChange(value: number | undefined) { if (!value) grpcState.value.timeout = DEFAULT_GRPC_TIMEOUT; emitChange(); }
</script>
<style scoped lang="less">.grpc-settings-tab :deep(.arco-form-item-label){font-weight:500;}</style>
