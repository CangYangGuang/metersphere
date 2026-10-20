<template>
  <a-modal
    v-model:visible="visible"
    :title="modalTitle"
    :ok-loading="loading || parsing"
    width="720px"
    @before-ok="handleSubmit"
  >
    <a-form :model="formModel" layout="vertical">
      <a-form-item :label="t('apiTestGrpc.batchModule')" required>
        <a-select v-model:model-value="moduleId" allow-search>
          <a-option v-for="item in moduleOptions" :key="item.value" :value="item.value">
            {{ item.label }}
          </a-option>
        </a-select>
      </a-form-item>
      <a-form-item :label="t('apiTestGrpc.serverAddress')" required>
        <a-input
          v-model:model-value="serverAddress"
          allow-clear
          :placeholder="t('apiTestGrpc.serverAddressPlaceholder')"
        />
      </a-form-item>
      <a-form-item :label="t('apiTestGrpc.batchMethods')" required>
        <div class="max-h-[360px] w-full overflow-auto rounded border border-[var(--color-border-2)] p-[12px]">
          <div v-for="service in availableServices" :key="service.fullName || service.name" class="mb-[12px]">
            <div class="mb-[6px] font-medium">{{ service.fullName || service.name }}</div>
            <a-checkbox-group v-model:model-value="selectedMethods" direction="vertical">
              <a-checkbox
                v-for="method in service.methods || []"
                :key="method.fullName || `${service.name}/${method.name}`"
                :value="method.fullName || `${service.fullName || service.name}/${method.name}`"
                :disabled="!isUnary(method)"
              >
                {{ method.name }}
                <a-tag size="small" :color="isUnary(method) ? 'green' : 'orange'">
                  {{ method.methodType || 'UNKNOWN' }}
                </a-tag>
              </a-checkbox>
            </a-checkbox-group>
          </div>
        </div>
      </a-form-item>
    </a-form>
    <a-alert v-if="lastResult?.failedCount" type="error" class="mt-[12px]">
      <div class="font-medium">{{ resultMessage }}</div>
      <div class="mt-[8px] max-h-[180px] overflow-auto text-[12px]">
        <div v-for="item in visibleFailedItems" :key="item.fullMethodName" class="mb-[6px] break-all">
          <span class="font-medium">{{ item.fullMethodName }}</span>
          <span>：{{ item.errorMessage || t('apiTestGrpc.batchUnknownError') }}</span>
        </div>
        <div v-if="hiddenFailedCount > 0" class="text-[var(--color-text-3)]">
          {{ t('apiTestGrpc.batchMoreFailures', { count: hiddenFailedCount }) }}
        </div>
      </div>
    </a-alert>
  </a-modal>
</template>

<script setup lang="ts">
  import { Message } from '@arco-design/web-vue';

  import { batchSaveGrpcDebugRequests, batchSaveGrpcDefinitions } from '@/api/modules/api-test/grpc';
  import { getModuleTreeOnlyModules } from '@/api/modules/api-test/management';
  import { useI18n } from '@/hooks/useI18n';

  import type {
    GrpcBatchGenerateItem,
    GrpcBatchGenerateResult,
    GrpcMethodInfo,
    GrpcRequestState,
    GrpcServiceInfo,
  } from '@/models/apiTest/grpc';
  import type { ModuleTreeNode } from '@/models/common';

  import {
    buildGrpcDefinitionSaveParams,
    buildGrpcParseParams,
    extractApiErrorMessage,
    parseGrpcProtoWithErrorDetail,
  } from './utils';

  const props = withDefaults(
    defineProps<{
      grpc: GrpcRequestState;
      moduleTree?: ModuleTreeNode[];
      projectId: string;
      defaultModuleId?: string;
      mode?: 'debug' | 'definition';
    }>(),
    {
      mode: 'debug',
    }
  );
  const emit = defineEmits<{ (e: 'done', result: GrpcBatchGenerateResult): void }>();
  const visible = defineModel<boolean>('visible', { default: false });
  const { t } = useI18n();
  const loading = ref(false);
  const parsing = ref(false);
  const lastResult = ref<GrpcBatchGenerateResult>();
  const availableServices = ref<GrpcServiceInfo[]>([]);
  const moduleId = ref('root');
  const selectedMethods = ref<string[]>([]);
  const serverAddress = ref('');
  const formModel = reactive({ moduleId, serverAddress, selectedMethods });
  const modalTitle = computed(() =>
    t(props.mode === 'definition' ? 'apiTestGrpc.batchSaveAsApiTitle' : 'apiTestGrpc.batchGenerateTitle')
  );
  const definitionModuleTree = ref<ModuleTreeNode[]>([]);
  const targetModuleTree = computed(() =>
    props.mode === 'definition' ? definitionModuleTree.value : props.moduleTree || []
  );
  const resultMessage = computed(() => {
    if (!lastResult.value) {
      return '';
    }
    return t(props.mode === 'definition' ? 'apiTestGrpc.batchSaveAsApiResult' : 'apiTestGrpc.batchGenerateResult', {
      createdCount: lastResult.value.createdCount,
      updatedCount: lastResult.value.updatedCount,
      failedCount: lastResult.value.failedCount,
    });
  });
  const failedItems = computed<GrpcBatchGenerateItem[]>(() =>
    (lastResult.value?.items || []).filter((item) => item.action === 'FAILED')
  );
  const visibleFailedItems = computed(() => failedItems.value.slice(0, 10));
  const hiddenFailedCount = computed(() => Math.max(0, failedItems.value.length - visibleFailedItems.value.length));

  const moduleOptions = computed(() => {
    const result: Array<{ label: string; value: string }> = [
      { label: t('apiTestGrpc.batchRootModule'), value: 'root' },
    ];
    const walk = (nodes: ModuleTreeNode[], prefix = '') => {
      nodes.forEach((node) => {
        if (node.type !== 'API' && node.id !== 'root') {
          result.push({ label: `${prefix}${node.name}`, value: String(node.id) });
          walk(node.children || [], `${prefix}${node.name} / `);
        }
      });
    };
    walk(targetModuleTree.value);
    return result;
  });

  function isUnary(method: GrpcMethodInfo) {
    return method.invokable !== false && String(method.methodType || '').toUpperCase() === 'UNARY';
  }

  function resetSelection() {
    selectedMethods.value = availableServices.value.flatMap((service) =>
      (service.methods || [])
        .filter(isUnary)
        .map((method) => method.fullName || `${service.fullName || service.name}/${method.name}`)
    );
  }

  async function refreshAvailableServices() {
    availableServices.value = props.grpc.services || [];
    const parseParams = buildGrpcParseParams(props.grpc);
    if (!parseParams.protoContent?.trim() && !parseParams.protoFiles?.length) {
      return;
    }
    try {
      parsing.value = true;
      const parsed = await parseGrpcProtoWithErrorDetail(parseParams);
      if (parsed.services?.length) {
        availableServices.value = parsed.services;
      }
    } catch (error) {
      const message = extractApiErrorMessage(error);
      if (message) {
        Message.error(message);
      }
    } finally {
      parsing.value = false;
    }
  }

  async function refreshDefinitionModuleTree() {
    if (props.mode !== 'definition') {
      return;
    }
    definitionModuleTree.value = await getModuleTreeOnlyModules({
      keyword: '',
      protocols: ['GRPC'],
      projectId: props.projectId,
      moduleIds: [],
    });
  }

  watch(visible, async (value) => {
    if (value) {
      await refreshDefinitionModuleTree();
      const defaultModuleId = props.mode === 'definition' ? 'root' : props.defaultModuleId || 'root';
      moduleId.value = moduleOptions.value.some((item) => item.value === defaultModuleId) ? defaultModuleId : 'root';
      serverAddress.value = props.grpc.serverAddress || '';
      lastResult.value = undefined;
      await refreshAvailableServices();
      resetSelection();
    }
  });

  async function handleSubmit(done: (closed: boolean) => void) {
    if (parsing.value || !moduleId.value || !selectedMethods.value.length) {
      Message.warning(t('apiTestGrpc.batchSelectionRequired'));
      done(false);
      return;
    }
    if (!serverAddress.value.trim()) {
      Message.warning(t('apiTestGrpc.batchServerAddressRequired'));
      done(false);
      return;
    }
    try {
      loading.value = true;
      const source = buildGrpcDefinitionSaveParams(props.grpc);
      const saveApi = props.mode === 'definition' ? batchSaveGrpcDefinitions : batchSaveGrpcDebugRequests;
      const result = await saveApi({
        protoContent: source.protoContent,
        fileName: source.fileName,
        protoFiles: source.protoFiles,
        mainFileName: source.mainFileName,
        descriptorContent: source.descriptorContent,
        reflection: source.reflection,
        reflectionSourceType: source.reflectionSourceType,
        reflectionServerAddress: source.reflectionServerAddress,
        reflectionUseTls: source.reflectionUseTls,
        reflectionAuthority: source.reflectionAuthority,
        reflectionServiceFullName: source.reflectionServiceFullName,
        reflectionDiscoveredAt: source.reflectionDiscoveredAt,
        projectId: props.projectId,
        parentModuleId: moduleId.value,
        serverAddress: serverAddress.value.trim(),
        methodFullNames: selectedMethods.value,
      });
      lastResult.value = result;
      const message = t(
        props.mode === 'definition' ? 'apiTestGrpc.batchSaveAsApiResult' : 'apiTestGrpc.batchGenerateResult',
        {
          createdCount: result.createdCount,
          updatedCount: result.updatedCount,
          failedCount: result.failedCount,
        }
      );
      if (result.failedCount === result.items.length) {
        Message.error(message);
      } else if (result.failedCount > 0) {
        Message.warning(message);
      } else {
        Message.success(message);
      }
      if (result.createdCount > 0 || result.updatedCount > 0) {
        emit('done', result);
      }
      done(result.failedCount === 0);
    } catch (error) {
      const message = extractApiErrorMessage(error);
      if (message) {
        Message.error(message);
      }
      done(false);
    } finally {
      loading.value = false;
    }
  }
</script>
