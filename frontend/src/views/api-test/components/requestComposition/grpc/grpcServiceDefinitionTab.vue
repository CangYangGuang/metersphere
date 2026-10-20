<template>
  <div class="grpc-service-definition-tab flex flex-col gap-[16px]">
    <a-alert v-if="parseError" type="error" closable @close="parseError = null">
      <div class="flex flex-col gap-[8px] text-[13px] leading-[20px]">
        <div class="font-medium">{{ t('apiTestGrpc.parseFailed') }}</div>
        <div v-if="parseError.message">
          <div class="text-[var(--color-text-3)]">{{ t('apiTestGrpc.parseErrorMessageLabel') }}</div>
          <div class="break-all">{{ parseError.message }}</div>
        </div>
        <div v-if="parseError.missingImports.length">
          <div class="text-[var(--color-text-3)]">{{ t('apiTestGrpc.parseErrorMissingImports') }}</div>
          <ul class="my-0 pl-[18px]">
            <li v-for="item in parseError.missingImports" :key="item" class="break-all">{{ item }}</li>
          </ul>
        </div>
        <div v-if="parseError.messageDetail">
          <div class="text-[var(--color-text-3)]">{{ t('apiTestGrpc.parseErrorRawDetail') }}</div>
          <div class="whitespace-pre-wrap break-all">{{ parseError.messageDetail }}</div>
        </div>
        <div v-if="parseError.messageDetail || parseError.missingImports.length">
          <div class="text-[var(--color-text-3)]">{{ t('apiTestGrpc.parseErrorSuggestion') }}</div>
          <div>{{ t('apiTestGrpc.parseErrorSuggestionText') }}</div>
        </div>
      </div>
    </a-alert>

    <a-form-item :label="t('apiTestGrpc.currentMainProtoFile')" class="mb-0">
      <div class="flex flex-wrap items-center gap-[8px]">
        <a-select
          :model-value="mainProtoFileName"
          class="min-w-[240px] flex-1"
          :placeholder="t('apiTestGrpc.selectMainProtoPlaceholder')"
          allow-clear
          @change="(val) => handleSwitchMainProto(val as string)"
        >
          <a-option v-for="item in mainProtoOptions" :key="item.fileName" :value="item.fileName">
            <div class="flex w-full items-center justify-between gap-[8px]">
              <span class="truncate">{{ item.fileName }}</span>
              <icon-close
                class="shrink-0 cursor-pointer text-[var(--color-text-3)] hover:text-[rgb(var(--danger-6))]"
                @click.stop="handleRemoveMainProto(item.fileName)"
              />
            </div>
          </a-option>
        </a-select>
        <a-upload :show-file-list="false" accept=".proto,text/plain" :custom-request="handleUploadMainProto">
          <template #upload-button>
            <a-button type="outline" size="small" class="arco-btn-outline--secondary pointer-events-none">{{
              t('apiTestGrpc.uploadMainProto')
            }}</a-button>
          </template>
        </a-upload>
        <a-upload
          :show-file-list="false"
          accept=".proto,text/plain"
          multiple
          :custom-request="handleUploadDependencyProto"
        >
          <template #upload-button>
            <a-button type="outline" size="small" class="arco-btn-outline--secondary pointer-events-none">{{
              t('apiTestGrpc.uploadDependencyProto')
            }}</a-button>
          </template>
        </a-upload>
      </div>
    </a-form-item>

    <div v-if="dependencyCount > 0" class="text-[12px] text-[var(--color-text-3)]">
      {{ t('apiTestGrpc.linkedDependencyProtoCount', { count: dependencyCount }) }}
    </div>
  </div>
</template>

<script setup lang="ts">
  import { useVModel } from '@vueuse/core';
  import { Message } from '@arco-design/web-vue';

  import { useI18n } from '@/hooks/useI18n';

  import type { GrpcCaseConfigDetail, GrpcDefinitionDetail, GrpcRequestState } from '@/models/apiTest/grpc';

  import type { GrpcParseErrorInfo } from './utils';
  import {
    applyGrpcParseResult,
    applyUploadedMainProto,
    buildGrpcParseParams,
    countDependencyProtos,
    ensureMainProtoFile,
    extractGrpcParseErrorInfo,
    parseGrpcProtoWithErrorDetail,
    removeUploadedMainProto,
    resolveGrpcParseMissingImports,
    resolveProtoUploadFileName,
    syncMainProtoContent,
    upsertUploadedMainProto,
  } from './utils';
  import type { RequestOption } from '@arco-design/web-vue';

  const props = defineProps<{
    grpc: GrpcRequestState;
    grpcDefinition?: GrpcDefinitionDetail;
    grpcCaseConfig?: GrpcCaseConfigDetail;
    isCase?: boolean;
  }>();

  const emit = defineEmits<{
    (e: 'update:grpc', value: GrpcRequestState): void;
    (e: 'change'): void;
  }>();

  const { t } = useI18n();
  const grpcState = useVModel(props, 'grpc', emit);
  const parseError = ref<GrpcParseErrorInfo | null>(null);

  const mainProtoFileName = computed(() => grpcState.value.mainFileName || grpcState.value.fileName || '');
  const mainProtoOptions = computed(() => grpcState.value.uploadedMainProtos || []);
  const dependencyCount = computed(() => countDependencyProtos(grpcState.value));

  function emitChange() {
    emit('change');
  }

  function setMainProtoFile(content: string, fileName: string) {
    grpcState.value.protoContent = content;
    grpcState.value.fileName = fileName;
    grpcState.value.mainFileName = fileName;
    const files = grpcState.value.protoFiles || [];
    const existing = files.find((item) => item.fileName === fileName);
    if (existing) {
      existing.content = content;
      existing.main = true;
      files.forEach((item) => {
        item.main = item.fileName === fileName;
      });
    } else {
      files.forEach((item) => {
        item.main = false;
      });
      files.unshift({ fileName, content, main: true });
    }
    grpcState.value.protoFiles = files;
    upsertUploadedMainProto(grpcState.value);
  }

  async function handleParse() {
    if (!grpcState.value.protoContent.trim()) {
      Message.warning(t('apiTestGrpc.protoContentRequired'));
      return;
    }
    try {
      parseError.value = null;
      grpcState.value.parseLoading = true;
      ensureMainProtoFile(grpcState.value);
      syncMainProtoContent(grpcState.value);
      upsertUploadedMainProto(grpcState.value);
      grpcState.value.reflectionDiscovered = false;
      grpcState.value.reflectionServerAddress = undefined;
      grpcState.value.reflectionDiscoveredAt = undefined;
      grpcState.value.reflectionUseTls = false;
      const res = await parseGrpcProtoWithErrorDetail(buildGrpcParseParams(grpcState.value));
      applyGrpcParseResult(grpcState.value, res);
      if (res.warnings?.length) {
        Message.warning(res.warnings.join('; '));
      }
      Message.success(t('apiTestGrpc.parseSuccess'));
      emitChange();
    } catch (error) {
      const info = extractGrpcParseErrorInfo(error);
      const missingImports = resolveGrpcParseMissingImports(info, grpcState.value.protoContent);
      parseError.value = {
        ...info,
        message: info.message || t('apiTestGrpc.parseFailed'),
        missingImports,
      };
      Message.error(parseError.value.message);
    } finally {
      grpcState.value.parseLoading = false;
    }
  }

  function handleUploadMainProto(option: RequestOption) {
    const { fileItem, onSuccess, onError } = option;
    const { file } = fileItem;
    if (!file) {
      onError?.(new Error('empty file'));
      return { abort() {} };
    }
    const reader = new FileReader();
    reader.onload = async () => {
      const fileName = resolveProtoUploadFileName(file, grpcState.value);
      setMainProtoFile(String(reader.result || ''), fileName);
      grpcState.value.serviceName = '';
      grpcState.value.methodName = '';
      parseError.value = null;
      emitChange();
      onSuccess?.({});
      await handleParse();
    };
    reader.onerror = () => {
      onError?.(new Error('read failed'));
    };
    reader.readAsText(file);
    return { abort() {} };
  }

  function handleUploadDependencyProto(option: RequestOption) {
    const { fileItem, onSuccess, onError } = option;
    const { file } = fileItem;
    if (!file) {
      onError?.(new Error('empty file'));
      return { abort() {} };
    }
    const reader = new FileReader();
    reader.onload = async () => {
      ensureMainProtoFile(grpcState.value);
      const fileName = resolveProtoUploadFileName(file, grpcState.value);
      const content = String(reader.result || '');
      const files = grpcState.value.protoFiles || [];
      const existing = files.find((item) => item.fileName === fileName);
      if (existing) {
        existing.content = content;
      } else {
        files.push({ fileName, content, main: false });
      }
      grpcState.value.protoFiles = files;
      upsertUploadedMainProto(grpcState.value);
      parseError.value = null;
      emitChange();
      onSuccess?.({});
      if (grpcState.value.protoContent.trim()) {
        await handleParse();
      }
    };
    reader.onerror = () => {
      onError?.(new Error('read failed'));
    };
    reader.readAsText(file);
    return { abort() {} };
  }

  async function handleSwitchMainProto(fileName: string) {
    if (!fileName || fileName === mainProtoFileName.value) {
      return;
    }
    applyUploadedMainProto(grpcState.value, fileName);
    grpcState.value.serviceName = '';
    grpcState.value.methodName = '';
    emitChange();
    await handleParse();
  }

  async function handleRemoveMainProto(fileName: string) {
    removeUploadedMainProto(grpcState.value, fileName);
    emitChange();
    if (mainProtoFileName.value && grpcState.value.protoContent.trim()) {
      await handleParse();
    }
  }

  onMounted(() => {
    if (!grpcState.value.uploadedMainProtos?.length && grpcState.value.protoContent.trim()) {
      upsertUploadedMainProto(grpcState.value);
    }
  });
</script>
