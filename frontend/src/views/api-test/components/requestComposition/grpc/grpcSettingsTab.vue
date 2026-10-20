<template>
  <div class="grpc-settings-tab flex flex-col gap-[16px]">
    <a-alert type="warning" class="text-[12px]">{{ t('apiTestGrpc.settingsBackendTodo') }}</a-alert>
    <div class="grid grid-cols-1 gap-[16px] md:grid-cols-2">
      <a-form-item :label="t('apiTestGrpc.requestTls')" class="mb-0">
        <a-switch v-model:model-value="useTls" size="small" @change="emitChange">
          <template #checked>{{ t('apiTestGrpc.reflectionUseTls') }}</template>
          <template #unchecked>{{ t('apiTestGrpc.reflectionPlaintext') }}</template>
        </a-switch>
      </a-form-item>
      <a-form-item :label="t('apiTestGrpc.verifyServerCertificate')" class="mb-0">
        <a-switch
          v-model:model-value="grpcState.verifyServerCertificate"
          size="small"
          :disabled="!useTls"
          @change="emitChange"
        />
      </a-form-item>
    </div>

    <a-form-item :label="t('apiTestGrpc.serverNameOverride')" class="mb-0">
      <a-input
        v-model:model-value="grpcState.serverNameOverride"
        :max-length="255"
        :placeholder="t('apiTestGrpc.serverNameOverridePlaceholder')"
        allow-clear
        @change="emitChange"
      />
    </a-form-item>

    <a-form-item :label="t('apiTestGrpc.authority')" class="mb-0">
      <a-input
        v-model:model-value="grpcState.authority"
        :max-length="255"
        :placeholder="t('apiTestGrpc.authorityPlaceholder')"
        allow-clear
        @change="emitChange"
      />
    </a-form-item>

    <a-form-item :label="t('apiTestGrpc.includeDefaultValues')" class="mb-0">
      <a-switch v-model:model-value="grpcState.includeDefaultValues" size="small" @change="emitChange" />
    </a-form-item>

    <div class="grid grid-cols-1 gap-[16px] md:grid-cols-2">
      <a-form-item :label="t('apiTestGrpc.maxResponseSize')" class="mb-0">
        <a-input-number
          v-model:model-value="grpcState.maxResponseSizeMb"
          :min="1"
          :max="2048"
          :step="1"
          class="w-full"
          allow-clear
          @change="emitChange"
        />
      </a-form-item>
      <a-form-item :label="t('apiTestGrpc.connectTimeout')" class="mb-0">
        <a-input-number
          v-model:model-value="grpcState.connectTimeout"
          :min="0"
          :step="1000"
          class="w-full"
          allow-clear
          @change="emitChange"
        />
      </a-form-item>
    </div>

    <a-form-item :label="t('apiTestGrpc.callTimeout')" class="mb-0 md:max-w-[50%]">
      <a-input-number
        v-model:model-value="grpcState.timeout"
        :min="1000"
        :step="1000"
        class="w-full"
        @change="handleTimeoutChange"
      />
      <div class="mt-[4px] text-[12px] text-[var(--color-text-3)]">{{
        t('apiTestGrpc.timeoutTip', { value: DEFAULT_GRPC_TIMEOUT })
      }}</div>
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
  const { t } = useI18n();
  const grpcState = useVModel(props, 'grpc', emit);
  const useTls = computed({
    get: () => grpcState.value.useTls === true,
    set: (v: boolean) => {
      grpcState.value.useTls = v;
    },
  });
  function emitChange() {
    emit('change');
  }
  function handleTimeoutChange(value: number | undefined) {
    if (!value) grpcState.value.timeout = DEFAULT_GRPC_TIMEOUT;
    emitChange();
  }
</script>

<style scoped lang="less">
  .grpc-settings-tab :deep(.arco-form-item-label) {
    font-weight: 500;
  }
</style>
