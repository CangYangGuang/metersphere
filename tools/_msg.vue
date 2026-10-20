<template>
  <div class="grpc-message-tab flex flex-col gap-[12px]">
    <div v-if="isCase" class="flex flex-col gap-[8px]">
      <div class="font-medium">{{ t('apiTestGrpc.serverAddress') }}</div>
      <a-input v-model:model-value="grpcState.serverAddress" :max-length="255" :placeholder="t('apiTestGrpc.serverAddressPlaceholder')" allow-clear @change="emitChange" />
    </div>
    <a-alert v-if="!grpcState.methodName" type="info">{{ t('apiTestGrpc.messageSelectMethodTip') }}</a-alert>
    <div class="flex flex-col gap-[8px]">
      <div class="flex flex-wrap items-center justify-between gap-[8px]">
        <div class="font-medium">{{ t('apiTestGrpc.requestJsonTitle') }}</div>
        <div class="flex flex-wrap items-center gap-[8px]">
          <a-button type="outline" size="small" class="arco-btn-outline--secondary" :disabled="!canGenerateTemplate" @click="handleGenerateTemplate">{{ t('apiTestGrpc.generateTemplateOnly') }}</a-button>
          <a-button type="outline" size="small" class="arco-btn-outline--secondary" :disabled="!canGenerateTemplate" @click="handleResetTemplate">{{ t('apiTestGrpc.resetTemplate') }}</a-button>
          <a-button type="outline" size="small" class="arco-btn-outline--secondary" @click="handleFormatJson">{{ t('apiTestGrpc.formatJson') }}</a-button>
        </div>
      </div>
      <a-alert v-if="grpcState.methodName && isRequestJsonEmpty(grpcState.requestJson)" type="info">{{ t('apiTestGrpc.messageEmptyRequestTip') }}</a-alert>
      <a-textarea v-model:model-value="grpcState.requestJson" :placeholder="t('apiTestGrpc.requestJson')" :auto-size="{ minRows: 8, maxRows: 20 }" :status="requestJsonError ? 'error' : undefined" @change="handleRequestJsonChange" />
      <div v-if="requestJsonError" class="text-[12px] text-[rgb(var(--danger-6))]">{{ requestJsonError }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { useVModel } from '@vueuse/core';
  import { Modal } from '@arco-design/web-vue';
  import { useI18n } from '@/hooks/useI18n';
  import type { GrpcRequestState } from '@/models/apiTest/grpc';
  import { applyRequestJsonTemplateIfEmpty, findGrpcMethodTemplate, formatGrpcRequestJson, isRequestJsonEmpty, validateRequestJson } from './utils';
  const props = defineProps<{ grpc: GrpcRequestState; isCase?: boolean }>();
  const emit = defineEmits<{ (e: 'update:grpc', value: GrpcRequestState): void; (e: 'change'): void }>();
  const { t } = useI18n();
  const grpcState = useVModel(props, 'grpc', emit);
  const requestJsonError = ref('');
  const canGenerateTemplate = computed(() => !!findGrpcMethodTemplate(grpcState.value) && !!grpcState.value.methodName);
  function emitChange() { emit('change'); }
  function handleRequestJsonChange() {
    const result = validateRequestJson(grpcState.value.requestJson);
    requestJsonError.value = result.valid ? '' : t('apiTestGrpc.requestJsonInvalid', { msg: result.message });
    emitChange();
  }
  function confirmOverwrite(onOk: () => void) {
    Modal.confirm({ title: t('apiTestGrpc.templateOverwriteTitle'), content: t('apiTestGrpc.templateOverwriteContent'), okText: t('apiTestGrpc.overwriteTemplate'), cancelText: t('apiTestGrpc.keepRequestJson'), onOk });
  }
  function handleGenerateTemplate() {
    const template = findGrpcMethodTemplate(grpcState.value);
    if (!template) return;
    const apply = () => { applyRequestJsonTemplateIfEmpty(grpcState.value, true); handleRequestJsonChange(); emitChange(); };
    if (isRequestJsonEmpty(grpcState.value.requestJson)) apply(); else confirmOverwrite(apply);
  }
  function handleResetTemplate() {
    const template = findGrpcMethodTemplate(grpcState.value);
    if (!template) return;
    confirmOverwrite(() => { grpcState.value.requestJson = template; handleRequestJsonChange(); emitChange(); });
  }
  function handleFormatJson() { grpcState.value.requestJson = formatGrpcRequestJson(grpcState.value.requestJson); handleRequestJsonChange(); emitChange(); }
</script>
