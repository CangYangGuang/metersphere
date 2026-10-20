<template>
  <div class="grpc-assertion-tab flex flex-col gap-[24px]">
    <div class="flex flex-col gap-[12px]">
      <div class="flex items-center justify-between">
        <div class="font-medium">{{ t('apiTestGrpc.assertionTitle') }}</div>
        <a-button type="outline" size="small" class="arco-btn-outline--secondary" @click="addAssertion">
          <template #icon><icon-plus /></template>
          {{ t('apiTestGrpc.addAssertion') }}
        </a-button>
      </div>
      <div v-if="!assertionItems.length" class="text-[12px] text-[var(--color-text-3)]">
        {{ t('apiTestGrpc.assertionEmpty') }}
      </div>
      <div
        v-for="(item, index) in assertionItems"
        :key="`assertion-${index}`"
        class="rounded-[4px] border border-[var(--color-text-n8)] p-[12px]"
      >
        <div class="mb-[12px] flex items-center justify-between">
          <a-switch v-model:model-value="item.enable" size="small" @change="emitChange" />
          <a-button type="text" status="danger" size="mini" @click="removeAssertion(index)">
            {{ t('common.delete') }}
          </a-button>
        </div>
        <div class="grid grid-cols-1 gap-[12px] md:grid-cols-2">
          <a-form-item :label="t('apiTestGrpc.assertionType')" class="mb-0">
            <a-select v-model:model-value="item.type" @change="handleAssertionTypeChange(item)">
              <a-option value="JSON_PATH">{{ t('apiTestGrpc.assertionTypeJsonPath') }}</a-option>
              <a-option value="GRPC_STATUS">{{ t('apiTestGrpc.assertionTypeGrpcStatus') }}</a-option>
              <a-option value="GRPC_CODE">{{ t('apiTestGrpc.assertionTypeGrpcCode') }}</a-option>
            </a-select>
          </a-form-item>
          <a-form-item :label="t('apiTestGrpc.assertionName')" class="mb-0">
            <a-input v-model:model-value="item.name" :placeholder="t('apiTestGrpc.assertionNamePlaceholder')" @change="emitChange" />
          </a-form-item>
          <a-form-item
            v-if="item.type === 'JSON_PATH'"
            :label="t('apiTestGrpc.jsonPathExpression')"
            class="mb-0 md:col-span-2"
          >
            <a-input
              v-model:model-value="item.expression"
              placeholder="$.message"
              @change="emitChange"
            />
          </a-form-item>
          <a-form-item :label="t('apiTestGrpc.assertionCondition')" class="mb-0">
            <a-select v-model:model-value="item.condition" @change="emitChange">
              <a-option v-for="opt in conditionOptions" :key="opt.value" :value="opt.value">
                {{ opt.label }}
              </a-option>
            </a-select>
          </a-form-item>
          <a-form-item
            v-if="showExpectedValue(item)"
            :label="t('apiTestGrpc.assertionExpected')"
            class="mb-0"
          >
            <a-input v-model:model-value="item.expectedValue" @change="emitChange" />
          </a-form-item>
        </div>
      </div>
    </div>

    <div class="flex flex-col gap-[12px]">
      <div class="flex items-center justify-between">
        <div class="font-medium">{{ t('apiTestGrpc.extractTitle') }}</div>
        <a-button type="outline" size="small" class="arco-btn-outline--secondary" @click="addExtract">
          <template #icon><icon-plus /></template>
          {{ t('apiTestGrpc.addExtract') }}
        </a-button>
      </div>
      <div v-if="!extractItems.length" class="text-[12px] text-[var(--color-text-3)]">
        {{ t('apiTestGrpc.extractEmpty') }}
      </div>
      <div
        v-for="(item, index) in extractItems"
        :key="`extract-${index}`"
        class="rounded-[4px] border border-[var(--color-text-n8)] p-[12px]"
      >
        <div class="mb-[12px] flex items-center justify-between">
          <a-switch v-model:model-value="item.enable" size="small" @change="emitChange" />
          <a-button type="text" status="danger" size="mini" @click="removeExtract(index)">
            {{ t('common.delete') }}
          </a-button>
        </div>
        <div class="grid grid-cols-1 gap-[12px] md:grid-cols-2">
          <a-form-item :label="t('apiTestGrpc.extractName')" class="mb-0">
            <a-input v-model:model-value="item.variableName" @change="emitChange" />
          </a-form-item>
          <a-form-item :label="t('apiTestGrpc.jsonPathExpression')" class="mb-0">
            <a-input v-model:model-value="item.expression" placeholder="$.message" @change="emitChange" />
          </a-form-item>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { useVModel } from '@vueuse/core';

  import { useI18n } from '@/hooks/useI18n';

  import type { GrpcAssertionItem, GrpcExtractItem, GrpcRequestState } from '@/models/apiTest/grpc';

  import {
    createDefaultGrpcAssertionItem,
    createDefaultGrpcExtractItem,
  } from './utils';

  const props = defineProps<{
    grpc: GrpcRequestState;
  }>();

  const emit = defineEmits<{
    (e: 'update:grpc', value: GrpcRequestState): void;
    (e: 'change'): void;
  }>();

  const { t } = useI18n();
  const grpcState = useVModel(props, 'grpc', emit);

  const assertionItems = computed(() => grpcState.value.assertionConfig?.items || []);
  const extractItems = computed(() => grpcState.value.extractConfig?.items || []);

  const conditionOptions = computed(() => [
    { value: 'EQUALS', label: t('apiTestGrpc.conditionEquals') },
    { value: 'NOT_EQUALS', label: t('apiTestGrpc.conditionNotEquals') },
    { value: 'CONTAINS', label: t('apiTestGrpc.conditionContains') },
    { value: 'EXISTS', label: t('apiTestGrpc.conditionExists') },
    { value: 'NOT_EXISTS', label: t('apiTestGrpc.conditionNotExists') },
  ]);

  function emitChange() {
    emit('change');
  }

  function showExpectedValue(item: GrpcAssertionItem) {
    return item.condition !== 'EXISTS' && item.condition !== 'NOT_EXISTS';
  }

  function handleAssertionTypeChange(item: GrpcAssertionItem) {
    if (item.type !== 'JSON_PATH') {
      item.expression = '';
    }
    emitChange();
  }

  function addAssertion() {
    if (!grpcState.value.assertionConfig) {
      grpcState.value.assertionConfig = { items: [] };
    }
    grpcState.value.assertionConfig.items.push(createDefaultGrpcAssertionItem());
    emitChange();
  }

  function removeAssertion(index: number) {
    grpcState.value.assertionConfig?.items.splice(index, 1);
    emitChange();
  }

  function addExtract() {
    if (!grpcState.value.extractConfig) {
      grpcState.value.extractConfig = { items: [] };
    }
    grpcState.value.extractConfig.items.push(createDefaultGrpcExtractItem());
    emitChange();
  }

  function removeExtract(index: number) {
    grpcState.value.extractConfig?.items.splice(index, 1);
    emitChange();
  }
</script>
