<template>
  <div class="grpc-panel flex flex-col gap-[16px]">
    <a-alert v-if="parseError" type="error" closable @close="parseError = ''">
      {{ parseError }}
    </a-alert>

    <div v-if="isCase" class="flex flex-col gap-[8px]">
      <div class="font-medium">{{ t('apiTestGrpc.serverAddress') }}</div>
      <a-input
        v-model:model-value="grpcState.serverAddress"
        :max-length="255"
        :placeholder="t('apiTestGrpc.serverAddressPlaceholder')"
        allow-clear
        @change="emitChange"
      />
    </div>

    <div v-if="!isCase && !isReflectionOnly" class="flex flex-col gap-[8px]">
      <div class="flex items-center justify-between">
        <div class="font-medium">{{ t('apiTestGrpc.protoContent') }}</div>
        <div class="flex items-center gap-[8px]">
          <a-upload :show-file-list="false" accept=".proto,text/plain" :custom-request="handleUploadProto">
            <a-button type="outline" size="small" class="arco-btn-outline--secondary">
              {{ t('apiTestGrpc.uploadProto') }}
            </a-button>
          </a-upload>
          <a-upload
            :show-file-list="false"
            accept=".proto,text/plain"
            multiple
            :custom-request="handleUploadDependencyProto"
          >
            <a-button type="outline" size="small" class="arco-btn-outline--secondary">
              {{ t('apiTestGrpc.uploadDependencyProto') }}
            </a-button>
          </a-upload>
          <a-button type="primary" size="small" :loading="grpcState.parseLoading" @click="handleParse">
            {{ t('apiTestGrpc.parseProto') }}
          </a-button>
        </div>
      </div>
      <a-textarea
        v-model:model-value="grpcState.protoContent"
        :placeholder="t('apiTestGrpc.protoContentPlaceholder')"
        :auto-size="{ minRows: 8, maxRows: 16 }"
        @change="handleProtoChange"
      />
      <div v-if="dependencyProtoFiles.length" class="flex flex-col gap-[4px] text-[12px] text-[var(--color-text-3)]">
        <div class="font-medium text-[var(--color-text-2)]">{{ t('apiTestGrpc.protoBundleFiles') }}</div>
        <div v-for="item in dependencyProtoFiles" :key="item.fileName" class="flex items-center gap-[8px]">
          <span>{{ item.fileName }}</span>
          <a-tag v-if="item.fileName === mainProtoFileName" size="small" color="arcoblue">
            {{ t('apiTestGrpc.mainProtoFile') }}
          </a-tag>
        </div>
      </div>
    </div>

    <div v-if="!isCase" class="flex flex-col gap-[12px] rounded-[4px] border border-[var(--color-text-n8)] p-[12px]">
      <div class="font-medium">{{ t('apiTestGrpc.reflectionDiscoverTitle') }}</div>
      <div class="text-[12px] text-[var(--color-text-3)]">{{ t('apiTestGrpc.reflectionDiscoverTip') }}</div>
      <div class="grid grid-cols-1 gap-[12px] md:grid-cols-2">
        <a-form-item :label="t('apiTestGrpc.serverAddress')" class="mb-0">
          <a-input
            v-model:model-value="grpcState.serverAddress"
            :max-length="255"
            :placeholder="t('apiTestGrpc.serverAddressPlaceholder')"
            allow-clear
            @change="emitChange"
          />
        </a-form-item>
        <a-form-item :label="t('apiTestGrpc.reflectionTimeout')" class="mb-0">
          <a-input-number
            v-model:model-value="reflectionTimeout"
            :min="1000"
            :max="60000"
            :step="1000"
            class="w-full"
          />
        </a-form-item>
      </div>
      <div class="flex flex-wrap items-center gap-[12px]">
        <a-switch v-model:model-value="reflectionUseTls" size="small">
          <template #checked>{{ t('apiTestGrpc.reflectionUseTls') }}</template>
          <template #unchecked>{{ t('apiTestGrpc.reflectionPlaintext') }}</template>
        </a-switch>
        <a-button type="primary" size="small" :loading="reflectionLoading" @click="handleReflectionDiscover">
          {{ t('apiTestGrpc.reflectionDiscover') }}
        </a-button>
      </div>
      <a-alert v-if="isReflectionOnly" type="info" class="text-[12px]">
        {{ t('apiTestGrpc.reflectionSourceTip', { address: reflectionSourceAddress }) }}
      </a-alert>
      <a-alert v-else-if="reflectionDiscovered" type="info" class="text-[12px]">
        {{ t('apiTestGrpc.reflectionDiscoveredTip') }}
      </a-alert>
    </div>

    <div v-else class="detail-item !pt-0">
      <div class="font-medium">{{ t('apiTestGrpc.summary') }}</div>
      <a-alert v-if="caseProtoVersionOutdated" type="warning" class="mt-[8px]">
        {{ t('apiTestGrpc.protoVersionOutdatedTip') }}
      </a-alert>
      <grpcProtoChangeImpactPanel v-if="isCase" :impact="grpcCaseConfig?.protoChangeImpact" />
      <div v-if="caseProtoVersionDisplay" class="mt-[8px] text-[13px]">
        <span class="text-[12px] text-[var(--color-text-3)]">{{ t('apiTestGrpc.protoVersion') }}: </span>
        {{ caseProtoVersionDisplay }}
      </div>
      <a-alert v-if="methodTypeDisplay && !grpcState.invokable" type="warning" class="mt-[8px]">
        {{ t('apiTestGrpc.streamingNotSupported') }}
      </a-alert>
      <div class="mt-[8px] grid grid-cols-1 gap-[8px] md:grid-cols-3">
        <div>
          <div class="text-[12px] text-[var(--color-text-3)]">{{ t('apiTestGrpc.service') }}</div>
          <div>{{ grpcState.serviceName || '-' }}</div>
        </div>
        <div>
          <div class="text-[12px] text-[var(--color-text-3)]">{{ t('apiTestGrpc.method') }}</div>
          <div>{{ grpcState.methodName || '-' }}</div>
        </div>
        <div>
          <div class="text-[12px] text-[var(--color-text-3)]">{{ t('apiTestGrpc.methodType') }}</div>
          <div>{{ methodTypeDisplay || '-' }}</div>
        </div>
      </div>
    </div>

    <div
      v-if="!isCase && grpcDefinition?.protoVersion"
      class="rounded-[4px] border border-[var(--color-text-n8)] p-[12px]"
    >
      <grpcProtoVersionPanel
        :version="grpcDefinition.protoVersion"
        :proto-id="grpcDefinition.protoId"
        :history="grpcDefinition.protoVersionHistory"
        :impact="grpcDefinition.protoChangeImpact"
      />
    </div>

    <div v-if="!isCase" class="grid grid-cols-1 gap-[16px] md:grid-cols-3">
      <a-form-item :label="t('apiTestGrpc.service')" class="mb-0">
        <a-select
          v-model:model-value="grpcState.serviceName"
          :placeholder="t('apiTestGrpc.selectService')"
          allow-search
          @change="handleServiceChange"
        >
          <a-option v-for="item in serviceOptions" :key="item.value" :value="item.value">
            {{ item.label }}
          </a-option>
        </a-select>
      </a-form-item>
      <a-form-item :label="t('apiTestGrpc.method')" class="mb-0">
        <a-select
          v-model:model-value="grpcState.methodName"
          :placeholder="t('apiTestGrpc.selectMethod')"
          allow-search
          @change="handleMethodChange"
        >
          <a-option v-for="item in methodOptions" :key="item.value" :value="item.value">
            {{ item.label }}
          </a-option>
        </a-select>
      </a-form-item>
      <a-form-item :label="t('apiTestGrpc.methodType')" class="mb-0">
        <a-input :model-value="methodTypeDisplay" readonly />
      </a-form-item>
    </div>

    <a-alert v-if="!isCase && methodTypeDisplay && !grpcState.invokable" type="warning">
      {{ t('apiTestGrpc.streamingNotSupported') }}
    </a-alert>

    <div
      v-if="
        !isCase &&
        !grpcState.descriptorContent &&
        grpcState.protoContent.trim() &&
        !reflectionDiscovered &&
        !isReflectionOnly
      "
      class="text-[12px] text-[var(--color-text-3)]"
    >
      {{ t('apiTestGrpc.parseRequiredBeforeSend') }}
    </div>

    <div class="flex flex-col gap-[8px]">
      <div class="flex items-center justify-between">
        <div class="font-medium">{{ t('apiTestGrpc.requestJson') }}</div>
        <a-button
          v-if="canGenerateTemplate"
          type="outline"
          size="small"
          class="arco-btn-outline--secondary"
          @click="handleGenerateTemplate"
        >
          {{ t('apiTestGrpc.generateTemplate') }}
        </a-button>
      </div>
      <a-textarea
        v-model:model-value="grpcState.requestJson"
        :placeholder="t('apiTestGrpc.requestJson')"
        :auto-size="{ minRows: 6, maxRows: 14 }"
        :status="requestJsonError ? 'error' : undefined"
        @change="handleRequestJsonChange"
      />
      <div v-if="requestJsonError" class="text-[12px] text-[rgb(var(--danger-6))]">
        {{ requestJsonError }}
      </div>
    </div>

    <div class="flex flex-col gap-[8px]">
      <div class="font-medium">Metadata</div>
      <div class="text-[12px] text-[var(--color-text-3)]">{{ t('apiTestGrpc.metadataTip') }}</div>
      <httpHeader v-model:params="grpcState.metadata" layout="vertical" @change="emitChange" />
    </div>

    <div class="grid grid-cols-1 gap-[16px] md:grid-cols-2">
      <a-form-item :label="t('apiTestGrpc.timeout')" class="mb-0">
        <a-input-number
          v-model:model-value="grpcState.timeout"
          :min="1000"
          :step="1000"
          class="w-full"
          @change="handleTimeoutChange"
        />
        <div class="mt-[4px] text-[12px] text-[var(--color-text-3)]">
          {{ t('apiTestGrpc.timeoutTip', { value: DEFAULT_GRPC_TIMEOUT }) }}
        </div>
      </a-form-item>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { useVModel } from '@vueuse/core';
  import { Message } from '@arco-design/web-vue';

  import httpHeader from '../header.vue';
  import grpcProtoChangeImpactPanel from './grpcProtoChangeImpactPanel.vue';
  import grpcProtoVersionPanel from './grpcProtoVersionPanel.vue';

  import { describeGrpcReflection, parseGrpcProto } from '@/api/modules/api-test/grpc';
  import { useI18n } from '@/hooks/useI18n';

  import type {
    GrpcCaseConfigDetail,
    GrpcDefinitionDetail,
    GrpcMethodInfo,
    GrpcRequestState,
  } from '@/models/apiTest/grpc';

  import { DEFAULT_GRPC_TIMEOUT, ensureGrpcMethodOptions, ensureGrpcServiceOptions } from './constants';
  import {
    applyGrpcParseResult,
    applyRequestJsonTemplateIfEmpty,
    buildGrpcMetadata,
    buildGrpcParseParams,
    ensureMainProtoFile,
    extractApiErrorMessage,
    findGrpcMethodTemplate,
    formatGrpcProtoVersionLabel,
    isRequestJsonEmpty,
    resolveMethodInvokable,
    syncMainProtoContent,
    syncMethodInfoFromServices,
    validateRequestJson,
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
  const parseError = ref('');
  const requestJsonError = ref('');
  const reflectionLoading = ref(false);
  const reflectionUseTls = ref(false);
  const reflectionTimeout = ref(10000);

  const isReflectionOnly = computed(() =>
    Boolean(props.grpcDefinition?.reflection || grpcState.value.reflectionDiscovered)
  );

  const reflectionDiscovered = computed(() => Boolean(grpcState.value.reflectionDiscovered));

  const reflectionSourceAddress = computed(
    () => props.grpcDefinition?.reflectionServerAddress || grpcState.value.serverAddress?.trim() || '-'
  );

  const serviceOptions = computed(() => ensureGrpcServiceOptions(grpcState.value));

  const currentService = computed(() =>
    grpcState.value.services.find((service) => service.name === grpcState.value.serviceName)
  );

  const methodOptions = computed(() => ensureGrpcMethodOptions(grpcState.value));

  const methodTypeDisplay = computed(() => grpcState.value.methodType || '');

  const canGenerateTemplate = computed(() => !!findGrpcMethodTemplate(grpcState.value) && !!grpcState.value.methodName);

  const caseProtoVersionDisplay = computed(() => {
    if (!props.isCase) {
      return '';
    }
    return formatGrpcProtoVersionLabel(props.grpcCaseConfig?.protoVersion, props.grpcCaseConfig?.protoId);
  });

  const caseProtoVersionOutdated = computed(() => props.isCase && props.grpcCaseConfig?.protoVersionOutdated === true);

  const mainProtoFileName = computed(() => grpcState.value.mainFileName || grpcState.value.fileName);

  const dependencyProtoFiles = computed(() => {
    const files = grpcState.value.protoFiles || [];
    return files.length > 1 ? files : [];
  });

  function emitChange() {
    emit('change');
  }

  function applyMethodInfo(method?: GrpcMethodInfo) {
    if (!method) {
      grpcState.value.methodType = '';
      grpcState.value.invokable = false;
      return;
    }
    grpcState.value.methodType = method.methodType || '';
    grpcState.value.invokable = resolveMethodInvokable(method.methodType, method.invokable);
  }

  function handleProtoChange() {
    parseError.value = '';
    syncMainProtoContent(grpcState.value);
    emitChange();
  }

  function handleRequestJsonChange() {
    const result = validateRequestJson(grpcState.value.requestJson);
    requestJsonError.value = result.valid ? '' : t('apiTestGrpc.requestJsonInvalid', { msg: result.message });
    emitChange();
  }

  function handleTimeoutChange(value: number | undefined) {
    if (!value) {
      grpcState.value.timeout = DEFAULT_GRPC_TIMEOUT;
    }
    emitChange();
  }

  function handleServiceChange() {
    grpcState.value.methodName = '';
    applyMethodInfo(undefined);
    emitChange();
  }

  function handleMethodChange(
    methodName: string | number | boolean | Record<string, any> | (string | number | boolean | Record<string, any>)[]
  ) {
    const name = String(methodName);
    const method = currentService.value?.methods?.find((item) => item.name === name);
    grpcState.value.methodName = name;
    applyMethodInfo(method);
    if (!method) {
      syncMethodInfoFromServices(grpcState.value);
    }
    applyRequestJsonTemplateIfEmpty(grpcState.value);
    emitChange();
  }

  function handleGenerateTemplate() {
    const template = findGrpcMethodTemplate(grpcState.value);
    if (!template) {
      Message.warning(t('apiTestGrpc.templateNotAvailable'));
      return;
    }
    const wasEmpty = isRequestJsonEmpty(grpcState.value.requestJson);
    applyRequestJsonTemplateIfEmpty(grpcState.value, true);
    Message.success(wasEmpty ? t('apiTestGrpc.templateApplySuccess') : t('apiTestGrpc.templateResetSuccess'));
    handleRequestJsonChange();
    emitChange();
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
  }

  function handleUploadProto(option: RequestOption) {
    const { fileItem, onSuccess, onError } = option;
    const { file } = fileItem;
    if (!file) {
      onError?.(new Error('empty file'));
      return { abort() {} };
    }
    const reader = new FileReader();
    reader.onload = () => {
      setMainProtoFile(String(reader.result || ''), file.name);
      parseError.value = '';
      emitChange();
      onSuccess?.({});
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
    reader.onload = () => {
      ensureMainProtoFile(grpcState.value);
      const fileName = file.name;
      const content = String(reader.result || '');
      const files = grpcState.value.protoFiles || [];
      const existing = files.find((item) => item.fileName === fileName);
      if (existing) {
        existing.content = content;
      } else {
        files.push({ fileName, content, main: false });
      }
      grpcState.value.protoFiles = files;
      parseError.value = '';
      emitChange();
      onSuccess?.({});
    };
    reader.onerror = () => {
      onError?.(new Error('read failed'));
    };
    reader.readAsText(file);
    return { abort() {} };
  }

  async function handleReflectionDiscover() {
    if (!grpcState.value.serverAddress?.trim()) {
      Message.warning(t('apiTestGrpc.reflectionServerAddressRequired'));
      return;
    }
    try {
      parseError.value = '';
      reflectionLoading.value = true;
      const res = await describeGrpcReflection({
        serverAddress: grpcState.value.serverAddress.trim(),
        useTls: reflectionUseTls.value,
        timeout: reflectionTimeout.value,
        metadata: buildGrpcMetadata(grpcState.value.metadata),
      });
      applyGrpcParseResult(grpcState.value, res);
      grpcState.value.reflectionDiscovered = true;
      grpcState.value.reflectionServerAddress = grpcState.value.serverAddress.trim();
      grpcState.value.reflectionUseTls = reflectionUseTls.value;
      grpcState.value.reflectionDiscoveredAt = new Date().toISOString();
      grpcState.value.protoContent = '';
      grpcState.value.protoFiles = [];
      grpcState.value.fileName = '';
      grpcState.value.mainFileName = '';
      if (res.warnings?.length) {
        Message.warning(res.warnings.join('; '));
      }
      if (!res.services?.length) {
        Message.warning(t('apiTestGrpc.reflectionNoServices'));
      } else {
        Message.success(t('apiTestGrpc.reflectionDiscoverSuccess'));
      }
      emitChange();
    } catch (error) {
      const message = extractApiErrorMessage(error) || t('apiTestGrpc.reflectionDiscoverFailed');
      parseError.value = message;
      Message.error(message);
    } finally {
      reflectionLoading.value = false;
    }
  }

  async function handleParse() {
    if (!grpcState.value.protoContent.trim()) {
      Message.warning(t('apiTestGrpc.protoContentRequired'));
      return;
    }
    try {
      parseError.value = '';
      grpcState.value.parseLoading = true;
      ensureMainProtoFile(grpcState.value);
      syncMainProtoContent(grpcState.value);
      grpcState.value.reflectionDiscovered = false;
      grpcState.value.reflectionServerAddress = undefined;
      grpcState.value.reflectionDiscoveredAt = undefined;
      grpcState.value.reflectionUseTls = false;
      const res = await parseGrpcProto(buildGrpcParseParams(grpcState.value));
      applyGrpcParseResult(grpcState.value, res);
      if (res.warnings?.length) {
        Message.warning(res.warnings.join('; '));
      }
      Message.success(t('apiTestGrpc.parseSuccess'));
      emitChange();
    } catch (error) {
      const message = extractApiErrorMessage(error) || t('apiTestGrpc.parseFailed');
      parseError.value = message;
      Message.error(message);
    } finally {
      grpcState.value.parseLoading = false;
    }
  }

  watch(
    () => [grpcState.value.serviceName, grpcState.value.methodName, grpcState.value.services],
    () => {
      syncMethodInfoFromServices(grpcState.value);
    },
    { immediate: true, deep: true }
  );
</script>

<style scoped lang="less">
  .grpc-panel {
    :deep(.arco-form-item-label) {
      font-weight: 500;
    }
  }
</style>
