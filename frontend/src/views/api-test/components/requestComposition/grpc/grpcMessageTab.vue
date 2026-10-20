<template>
  <div class="grpc-message-tab flex h-full min-h-[280px] w-full flex-col">
    <div v-if="isCase" class="mb-[8px] flex flex-col gap-[8px]">
      <div class="font-medium">{{ t('apiTestGrpc.serverAddress') }}</div>
      <a-input
        v-model:model-value="grpcState.serverAddress"
        :max-length="255"
        :placeholder="t('apiTestGrpc.serverAddressPlaceholder')"
        allow-clear
        @change="emitChange"
      />
    </div>
    <a-alert v-if="!grpcState.methodName" type="info" class="mb-[8px]">{{
      t('apiTestGrpc.messageSelectMethodTip')
    }}</a-alert>
    <div class="mb-[8px] flex flex-wrap items-center gap-[8px]">
      <a-button
        type="outline"
        size="small"
        class="arco-btn-outline--secondary"
        :disabled="!canGenerateTemplate"
        @click="handleGenerateTemplate"
        >{{ t('apiTestGrpc.generateTemplateOnly') }}</a-button
      >
      <a-button type="outline" size="small" class="arco-btn-outline--secondary" @click="handleFormatJson">{{
        t('apiTestGrpc.formatJson')
      }}</a-button>
    </div>
    <a-alert v-if="grpcState.methodName && isRequestJsonEmpty(grpcState.requestJson)" type="info" class="mb-[8px]">{{
      t('apiTestGrpc.messageEmptyRequestTip')
    }}</a-alert>
    <div class="min-h-[240px] flex-1">
      <MsCodeEditor
        v-model:model-value="grpcState.requestJson"
        theme="vs"
        height="100%"
        :language="LanguageEnum.JSON"
        is-adaptive
        :show-full-screen="false"
        :show-theme-change="false"
        :show-code-format="false"
        @change="handleRequestJsonChange"
      />
    </div>
    <div v-if="requestJsonError" class="mt-[4px] text-[12px] text-[rgb(var(--danger-6))]">{{ requestJsonError }}</div>
  </div>
</template>

<script setup lang="ts">
  import { useVModel } from '@vueuse/core';
  import { Modal } from '@arco-design/web-vue';

  import MsCodeEditor from '@/components/pure/ms-code-editor/index.vue';
  import { LanguageEnum } from '@/components/pure/ms-code-editor/types';

  import { useI18n } from '@/hooks/useI18n';

  import type { GrpcRequestState } from '@/models/apiTest/grpc';

  import {
    applyRequestJsonTemplateIfEmpty,
    findGrpcMethodTemplate,
    formatGrpcRequestJson,
    isRequestJsonEmpty,
    validateRequestJson,
  } from './utils';

  const props = defineProps<{ grpc: GrpcRequestState; isCase?: boolean }>();
  const emit = defineEmits<{ (e: 'update:grpc', value: GrpcRequestState): void; (e: 'change'): void }>();
  const { t } = useI18n();
  const grpcState = useVModel(props, 'grpc', emit);
  const requestJsonError = ref('');
  const canGenerateTemplate = computed(() => !!findGrpcMethodTemplate(grpcState.value) && !!grpcState.value.methodName);

  function emitChange() {
    emit('change');
  }
  function handleRequestJsonChange() {
    const result = validateRequestJson(grpcState.value.requestJson);
    requestJsonError.value = result.valid ? '' : t('apiTestGrpc.requestJsonInvalid', { msg: result.message });
    emitChange();
  }
  function confirmOverwrite(onOk: () => void) {
    Modal.confirm({
      title: t('apiTestGrpc.templateOverwriteTitle'),
      content: t('apiTestGrpc.templateOverwriteContent'),
      okText: t('apiTestGrpc.overwriteTemplate'),
      cancelText: t('apiTestGrpc.keepRequestJson'),
      onOk,
    });
  }
  function handleGenerateTemplate() {
    const template = findGrpcMethodTemplate(grpcState.value);
    if (!template) {
      return;
    }
    const apply = () => {
      applyRequestJsonTemplateIfEmpty(grpcState.value, true);
      handleRequestJsonChange();
      emitChange();
    };
    if (isRequestJsonEmpty(grpcState.value.requestJson)) {
      apply();
    } else {
      confirmOverwrite(apply);
    }
  }
  function handleFormatJson() {
    grpcState.value.requestJson = formatGrpcRequestJson(grpcState.value.requestJson);
    handleRequestJsonChange();
    emitChange();
  }
</script>
